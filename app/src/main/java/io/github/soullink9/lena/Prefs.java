package io.github.soullink9.lena;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lena's settings, stored privately on the phone. */
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

    /** Sign-in addresses (such as Authentik), as https://host[:port], separated by spaces. */
    static String signIn(Context c) {
        return p(c).getString("sign_in", "");
    }

    static void save(Context c, String server, String model, boolean callOnAssist, String signIn) {
        p(c).edit()
                .putString("server", server)
                .putString("model", model)
                .putBoolean("call_on_assist", callOnAssist)
                .putString("sign_in", signIn)
                .apply();
    }

    /** Returns a clean https://host[:port][/path]/ address, or null if it isn't a usable https address. */
    static String normalize(String raw) {
        Uri u = Uri.parse(raw.trim());
        String origin = origin(u);
        if (origin == null) return null;
        String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
        return origin + path + "/";
    }

    /**
     * Cleans a list of sign-in addresses separated by spaces, commas or new lines into
     * "https://a https://b". Returns "" for an empty list and null if any address isn't https.
     */
    static String normalizeSignIn(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.trim().split("[\\s,]+")) {
            if (part.isEmpty()) continue;
            String origin = origin(Uri.parse(part));
            if (origin == null) return null;
            if (!out.contains(origin)) out.add(origin);
        }
        return String.join(" ", out);
    }

    /** https://host[:port] only, or null. */
    private static String origin(Uri u) {
        String host = u.getHost();
        if (!"https".equalsIgnoreCase(u.getScheme()) || host == null || host.isEmpty() || u.getUserInfo() != null) {
            return null;
        }
        String port = u.getPort() == -1 ? "" : ":" + u.getPort();
        return "https://" + host.toLowerCase(Locale.ROOT) + port;
    }
}
