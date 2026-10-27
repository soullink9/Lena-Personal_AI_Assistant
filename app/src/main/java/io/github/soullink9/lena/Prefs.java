package io.github.soullink9.lena;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.Locale;

/** The three settings, stored privately on the phone. */
final class Prefs {

    private Prefs() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("lena", Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        return p(c).getString("server", null);
    }

    static String model(Context c) {
        String m = p(c).getString("model", "");
        return m.isEmpty() ? null : m;
    }

    static boolean callOnAssist(Context c) {
        return p(c).getBoolean("call_on_assist", true);
    }

    static void save(Context c, String server, String model, boolean callOnAssist) {
        p(c).edit()
                .putString("server", server)
                .putString("model", model)
                .putBoolean("call_on_assist", callOnAssist)
                .apply();
    }

    /** Returns a clean https://host[:port][/path]/ address, or null if it isn't a usable https address. */
    static String normalize(String raw) {
        Uri u = Uri.parse(raw.trim());
        String host = u.getHost();
        if (!"https".equalsIgnoreCase(u.getScheme()) || host == null || host.isEmpty() || u.getUserInfo() != null) {
            return null;
        }
        String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
        String port = u.getPort() == -1 ? "" : ":" + u.getPort();
        return "https://" + host.toLowerCase(Locale.ROOT) + port + path + "/";
    }
}
