package io.github.soullink9.lena;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Open WebUI full screen, locked to the server (and sign-in addresses) set in Settings. */
public class MainActivity extends Activity {

    static final String ACTION_CALL = "io.github.soullink9.lena.CALL";
    static final String ACTION_SETTINGS = "io.github.soullink9.lena.SETTINGS";
    private static final int REQ_MIC = 1;
    private static final int REQ_FILE = 2;

    private WebView web;
    private Uri server;
    private final List<Uri> signIn = new ArrayList<>(); // e.g. Authentik: may load, never gets the mic
    private PermissionRequest pendingMic;
    private ValueCallback<Uri[]> pendingFiles;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String url = Prefs.server(this);
        if (url == null || ACTION_SETTINGS.equals(getIntent().getAction())) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        }
        server = Uri.parse(url);
        for (String s : Prefs.signIn(this).split(" ")) {
            if (!s.isEmpty()) signIn.add(Uri.parse(s));
        }

        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true); // keep clear of status bar, nav bar and keyboard
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        root.addView(web);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);                  // Open WebUI is a JavaScript app
        s.setDomStorageEnabled(true);                  // keeps you signed in
        s.setMediaPlaybackRequiresUserGesture(false);  // lets call-mode replies play out loud
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSupportMultipleWindows(false);            // new-tab links come through shouldOverrideUrlLoading
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String action = intent.getAction();
        if (ACTION_SETTINGS.equals(action)) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (ACTION_CALL.equals(action) || Intent.ACTION_ASSIST.equals(action)) {
            load(intent);
        }
        // Opening from the launcher just brings back whatever was on screen.
    }

    /** Assistant gesture and the Call shortcut start a fresh chat; everything else opens the home page. */
    private void load(Intent intent) {
        String action = intent.getAction();
        boolean assist = Intent.ACTION_ASSIST.equals(action);
        boolean call = ACTION_CALL.equals(action) || (assist && Prefs.callOnAssist(this));

        Uri.Builder b = server.buildUpon();
        if (call || assist) {
            String model = Prefs.model(this);
            if (model != null) b.appendQueryParameter("model", model);
        }
        if (call) {
            b.appendQueryParameter("call", "true");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        web.loadUrl(b.build().toString());
    }

    private boolean isServer(Uri u) {
        return sameOrigin(u, server);
    }

    /** Your server or one of your sign-in addresses. */
    private boolean isAllowed(Uri u) {
        if (isServer(u)) return true;
        for (Uri s : signIn) {
            if (sameOrigin(u, s)) return true;
        }
        return false;
    }

    private static boolean sameOrigin(Uri u, Uri base) {
        return u != null
                && "https".equals(u.getScheme())
                && base.getHost() != null
                && base.getHost().equalsIgnoreCase(u.getHost())
                && port(u) == port(base);
    }

    private static int port(Uri u) {
        return u.getPort() == -1 ? 443 : u.getPort();
    }

    private void goBack() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() { // Android 11 and 12 only; newer versions use the callback above
        goBack();
    }

    /** Only your server and sign-in addresses load in the app. Other links open in your normal browser. */
    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (isAllowed(u)) return false;
            String scheme = u.getScheme();
            if (!req.isForMainFrame()) {
                // Open WebUI draws some previews in frames built from local content; allow those only.
                return !("about".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme));
            }
            if ("https".equals(scheme) || "http".equals(scheme)) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE));
                } catch (ActivityNotFoundException ignored) {
                    // no browser installed; drop the link
                }
            }
            return true; // intent:, file:, javascript: and anything else is blocked
        }
    }

    /** Mic for your server only; no camera, no location. Plus the file picker for attachments. */
    private final class Chrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(PermissionRequest req) {
            boolean mic = Arrays.asList(req.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            if (!mic || !isServer(req.getOrigin())) {
                req.deny();
                return;
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                if (pendingMic != null) pendingMic.deny();
                pendingMic = req;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (pendingFiles != null) pendingFiles.onReceiveValue(null);
            pendingFiles = callback;
            Intent pick = params.createIntent();
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                startActivityForResult(pick, REQ_FILE);
                return true;
            } catch (ActivityNotFoundException e) {
                pendingFiles = null;
                return false;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != REQ_MIC || pendingMic == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pendingMic.deny();
        }
        pendingMic = null;
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code != REQ_FILE || pendingFiles == null) return;
        Uri[] uris = null;
        if (result == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                uris = new Uri[clip.getItemCount()];
                for (int i = 0; i < uris.length; i++) uris[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                uris = new Uri[]{data.getData()};
            }
        }
        pendingFiles.onReceiveValue(uris);
        pendingFiles = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();
            CookieManager.getInstance().flush();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
