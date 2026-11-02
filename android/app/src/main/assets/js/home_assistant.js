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
 *  2. Hides Home Assistant's own header, so Lena's bar is the only one. The header lives inside
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

  // The same for the other built-in pages (Calendar, To-do, Map, Logbook, History, Media...),
  // which use Home Assistant's standard app bar: its row goes, a second row of filters stays.
  var APP_BAR_CSS =
    '.top-app-bar .row{display:none!important}' +
    '.top-app-bar-fixed-adjust{padding-top:calc(var(--sub-row-height,0px) + var(--safe-area-inset-top,0px))!important}' +
    '.shadow-container{display:none!important}';

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
  // Pages are built and swapped long after this script runs, so it looks again a few times a
  // second. Only a change is reported.

  var lastReport = '';
  setInterval(function () {
    var state = {found: false};
    try {
      var p = panel();
      var bars = shadow(p) ? p.shadowRoot.querySelectorAll('ha-top-app-bar-fixed, ha-two-pane-top-app-bar-fixed') : [];
      for (var i = 0; i < bars.length; i++) addStyle(bars[i], 'lena-bar', APP_BAR_CSS);

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
    var report = JSON.stringify(state);
    if (report !== lastReport) {
      lastReport = report;
      post('header', report);
    }
  }, 600);

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
