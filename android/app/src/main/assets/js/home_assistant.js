/*
 * Lena's side of Home Assistant's page on the wall display (DashboardActivity).
 *
 * It's added to the page before Home Assistant's own scripts run, and only for your Home
 * Assistant's address. It talks to the app through `lenaBridge`, a message channel the app
 * opens for that address alone, with messages of the form {kind, payload}.
 *
 * Android adds it to every frame on that address, and other apps can be served from it too:
 * add-on ingress and the Ingress Proxy integration put apps like Music Assistant or Zigbee2MQTT
 * there, in frames inside Home Assistant or, after a "new window" link, as the whole page. Those
 * get nothing of Lena's: the script stops in any frame, and on any page that isn't Home
 * Assistant's frontend. (The app checks the same before answering.)
 *
 * It does three things:
 *
 *  1. Signs the page in. Opened with `?external_auth=1`, Home Assistant's frontend asks
 *     `window.externalApp` for a token instead of showing its login page; that's how the official
 *     companion app works. The requests go to the app, which answers with the token from Lena's
 *     settings by calling the callback Home Assistant named.
 *
 *  2. Hides Home Assistant's own header, so Lena's bar is the only one (also the Ingress Proxy
 *     integration's toolbar over the apps it serves), and puts Lena's Apps and Settings at the
 *     bottom of Home Assistant's menu, out of her bar. The header lives inside
 *     several shadow roots, which a page-wide stylesheet can't reach, so a small style is added
 *     inside each one as the pages appear. Edit mode keeps its header (its Done button is there).
 *
 *  3. Tells the app the dashboard's views as they change, so its bar can show them as tabs, and
 *     gives it `window.lena` to call back with: back, menu, show a view, open a path.
 *
 * Home Assistant's frontend isn't an API: the element names and private fields used here
 * (hui-root, _curView, _goBack, _navigateToView) come from its source and can change in an
 * update. Everything is wrapped so that if they do, the page still works and just shows its own
 * header again.
 */
(function () {
  // Paths that aren't Home Assistant's frontend: apps and views under /api/, your www files, HACS's
  // files, the frontend's static files. Keep in step with NOT_FRONTEND in Dashboard.kt.
  var NOT_FRONTEND = ['/api/', '/local/', '/hacsfiles/', '/static/'];

  if (window.top !== window) return; // proxied apps and iframe cards get nothing of Lena's
  for (var i = 0; i < NOT_FRONTEND.length; i++) {
    if (location.pathname.indexOf(NOT_FRONTEND[i]) === 0) return; // another app opened as the whole page
  }
  if (window.lena) return; // already added to this page

  // Hides a dashboard's header (Overview and Energy are dashboards too) and the space it took.
  var DASHBOARD_CSS =
    'div:not(.edit-mode)>.header{display:none!important}' +
    'div:not(.edit-mode)>hui-view-container{padding-top:var(--safe-area-inset-top,0px)!important}';

  // The same for the other built-in pages (Calendar, To-do, Activity, Map, History, Media...),
  // which use Home Assistant's standard app bar: its row goes, a second row of filters stays.
  // The page sits below the bar by the bar's height (--header-height), so setting that to nothing
  // moves the page up into the space, however the bar positions it (newer frontends place the
  // page with `top`, older ones with padding).
  var APP_BAR_CSS =
    ':host{--header-height:0px!important}' +
    '.top-app-bar .row{display:none!important}' +
    '.shadow-container{display:none!important}';

  // Apps served through the Ingress Proxy integration (Music Assistant and the like) have the
  // proxy's own slim toolbar above them; Lena's bar replaces it. The app itself is in a frame
  // underneath, which this script never touches.
  var PROXY_PANEL_CSS = '.toolbar{display:none!important}';

  // Lena's Apps and Settings, at the bottom of Home Assistant's menu rather than in her bar. They're
  // Home Assistant's own menu rows, so they look like the rest of the menu.
  var APPS_ICON = 'M16,20H20V16H16M16,14H20V10H16M10,8H14V4H10M16,8H20V4H16M10,14H14V10H10M4,14H8V10H4M4,20H8V16H4M10,20H14V16H10M4,8H8V4H4V8Z';
  var SETTINGS_ICON = 'M12,15.5A3.5,3.5 0 0,1 8.5,12A3.5,3.5 0 0,1 12,8.5A3.5,3.5 0 0,1 15.5,12A3.5,3.5 0 0,1 12,15.5M19.43,12.97C19.47,12.65 19.5,12.33 19.5,12C19.5,11.67 19.47,11.34 19.43,11L21.54,9.37C21.73,9.22 21.78,8.95 21.66,8.73L19.66,5.27C19.54,5.05 19.27,4.96 19.05,5.05L16.56,6.05C16.04,5.66 15.5,5.32 14.87,5.07L14.5,2.42C14.46,2.18 14.25,2 14,2H10C9.75,2 9.54,2.18 9.5,2.42L9.13,5.07C8.5,5.32 7.96,5.66 7.44,6.05L4.95,5.05C4.73,4.96 4.46,5.05 4.34,5.27L2.34,8.73C2.21,8.95 2.27,9.22 2.46,9.37L4.57,11C4.53,11.34 4.5,11.67 4.5,12C4.5,12.33 4.53,12.65 4.57,12.97L2.46,14.63C2.27,14.78 2.21,15.05 2.34,15.27L4.34,18.73C4.46,18.95 4.73,19.03 4.95,18.95L7.44,17.94C7.96,18.34 8.5,18.68 9.13,18.93L9.5,21.58C9.54,21.82 9.75,22 10,22H14C14.25,22 14.46,21.82 14.5,21.58L14.87,18.93C15.5,18.67 16.04,18.34 16.56,17.94L19.05,18.95C19.27,19.03 19.54,18.95 19.66,18.73L21.66,15.27C21.78,15.05 21.73,14.78 21.54,14.63L19.43,12.97Z';
  var MENU_ITEMS_CSS =
    '#lena-items{display:flex;flex-direction:column;flex:none;padding-top:var(--ha-space-1,4px);' +
    'border-top:1px solid var(--divider-color)}';

  function post(kind, payload) {
    try { lenaBridge.postMessage(JSON.stringify({kind: kind, payload: payload})); } catch (e) {}
  }

  function shadow(el) { return el && el.shadowRoot; }

  // ---- 1. Signing in, and the companion app's message bus ------------------------------------

  window.externalApp = {
    getExternalAuth: function (p) { post('auth', p); },
    revokeExternalAuth: function (p) { post('revoke', p); },
    externalBus: function (p) { post('bus', p); }
  };

  // Home Assistant's sidebar starts folded away: Lena's bar has a button for it.
  try { localStorage.setItem('dockedSidebar', '"always_hidden"'); } catch (e) {}

  // ---- Finding things inside Home Assistant's page ---------------------------------------------

  function main() {
    var ha = document.querySelector('home-assistant');
    return shadow(ha) && ha.shadowRoot.querySelector('home-assistant-main');
  }

  // The page showing now. Home Assistant can keep earlier pages around hidden, so it's the last
  // one that takes up space.
  function panel() {
    var m = main();
    var resolver = shadow(m) && m.shadowRoot.querySelector('partial-panel-resolver');
    if (!resolver) return null;
    var shown = [].slice.call(resolver.children).filter(function (k) { return k.getClientRects().length > 0; });
    return shown[shown.length - 1] || resolver.lastElementChild;
  }

  // Home Assistant's menu (the sidebar).
  function sidebar() {
    var m = main();
    return shadow(m) && m.shadowRoot.querySelector('ha-sidebar');
  }

  // One of Lena's rows for the bottom of the menu: Home Assistant's own row element, with its icon
  // and name, telling the app what was tapped (and closing the menu, as its own rows do).
  function menuRow(icon, text, kind) {
    var row = document.createElement('ha-list-item-button');
    var glyph = document.createElement('ha-svg-icon');
    glyph.setAttribute('slot', 'start');
    glyph.path = icon;
    var label = document.createElement('span');
    label.className = 'item-text';
    label.setAttribute('slot', 'headline');
    label.textContent = text;
    row.appendChild(glyph);
    row.appendChild(label);
    row.addEventListener('click', function () {
      window.lena.menu();
      post(kind, '');
    });
    return row;
  }

  // Adds Lena's Apps and Settings at the bottom of the menu, once. Returns whether they're there,
  // so the app knows it can leave them out of its bar.
  function addMenuItems() {
    var bar = sidebar();
    if (!shadow(bar)) return false;
    if (!bar.shadowRoot.getElementById('lena-items')) {
      addStyle(bar, 'lena-items-style', MENU_ITEMS_CSS);
      var box = document.createElement('div');
      box.id = 'lena-items';
      box.appendChild(menuRow(APPS_ICON, 'Apps', 'apps'));
      box.appendChild(menuRow(SETTINGS_ICON, 'Lena settings', 'settings'));
      bar.shadowRoot.appendChild(box);
    }
    return true;
  }

  // The dashboard on that page, if it is one.
  function dashboard() {
    var p = panel();
    return shadow(p) && p.shadowRoot.querySelector('hui-root');
  }

  function addStyle(host, id, css) {
    if (shadow(host) && !host.shadowRoot.getElementById(id)) {
      var style = document.createElement('style');
      style.id = id;
      style.textContent = css;
      host.shadowRoot.appendChild(style);
    }
  }

  // ---- 2 and 3. Hiding the header, and reporting the views -------------------------------------
  // Pages are built and swapped long after this script runs, so it looks again: often (every 0.6 s)
  // for a few seconds after the page loads, after a navigation and after a tap, when things change,
  // and every 2 s otherwise, since each look makes the page work out its layout. Never while the page
  // is hidden. Only a change is reported.

  var FAST_MS = 600;
  var SLOW_MS = 2000;
  var FAST_FOR_MS = 8000;
  var lastReport = '';
  var busyUntil = Date.now() + FAST_FOR_MS;

  // Something may be changing: look often for a while.
  function soon() { busyUntil = Date.now() + FAST_FOR_MS; }
  window.addEventListener('location-changed', soon);
  window.addEventListener('popstate', soon);
  document.addEventListener('click', soon, true);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) { soon(); look(); } });

  function look() {
    if (document.hidden) return;
    var state = {found: false};
    try {
      var p = panel();
      var bars = shadow(p) ? p.shadowRoot.querySelectorAll('ha-top-app-bar-fixed, ha-two-pane-top-app-bar-fixed') : [];
      for (var i = 0; i < bars.length; i++) addStyle(bars[i], 'lena-bar', APP_BAR_CSS);

      // A custom panel (ha-panel-custom) holds its element as a plain child.
      var custom = p && p.tagName === 'HA-PANEL-CUSTOM' ? p.firstElementChild : null;
      if (custom && custom.tagName.toLowerCase().indexOf('ingress-proxy-panel') === 0) {
        addStyle(custom, 'lena-bar', PROXY_PANEL_CSS);
      }

      var root = dashboard();
      if (root && root.shadowRoot) {
        addStyle(root, 'lena-header', DASHBOARD_CSS);
        var config = (root.lovelace && root.lovelace.config) || {};
        var views = (config.views || []).map(function (v, index) {
          return {i: index, title: v.title || '', path: String(v.path || index), sub: !!v.subview, hidden: v.visible === false};
        });
        var current = typeof root._curView === 'number' ? root._curView : 0;
        state = {
          found: true,
          current: current,
          subview: !!(views[current] && views[current].sub),
          edit: !!(root.lovelace && root.lovelace.editMode),
          // Subviews and hidden views aren't tabs in Home Assistant either.
          views: views.filter(function (v) { return !v.sub && !v.hidden; })
        };
      }
    } catch (e) {}
    try { state.menuItems = addMenuItems(); } catch (e) { state.menuItems = false; }
    var report = JSON.stringify(state);
    if (report !== lastReport) {
      lastReport = report;
      post('header', report);
    }
  }

  (function loop() {
    look();
    setTimeout(loop, Date.now() < busyUntil ? FAST_MS : SLOW_MS);
  })();

  // ---- What the app can ask the page to do ---------------------------------------------------

  // Moves to a path inside the running frontend, the way its own links do, without reloading.
  function navigate(path) {
    history.pushState(null, '', path);
    window.dispatchEvent(new CustomEvent('location-changed', {detail: {replace: false}}));
  }

  window.lena = {
    // Up out of a subview, as Home Assistant's own back arrow does.
    back: function () {
      var root = dashboard();
      if (root && root._goBack) root._goBack(); else history.back();
    },

    // Opens or closes Home Assistant's sidebar.
    menu: function () {
      var m = main();
      if (m) m.dispatchEvent(new CustomEvent('hass-toggle-menu', {bubbles: true, composed: true}));
    },

    // One of the dashboard's views, by its path (or index), the way its own tabs switch.
    showView: function (path) {
      var root = dashboard();
      if (root && root._navigateToView) { root._navigateToView(path); return; }
      var prefix = (root && root.route && root.route.prefix) || location.pathname.split('/').slice(0, 2).join('/');
      navigate(prefix + '/' + path);
    },

    // Another dashboard, by its path ("/lovelace").
    open: navigate
  };
})();
