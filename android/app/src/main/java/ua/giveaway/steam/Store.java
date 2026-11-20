package ua.giveaway.steam;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Налаштування і список уже побачених роздач. */
final class Store {
    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("catcher", Context.MODE_PRIVATE);
    }

    static Set<String> seen(Context c) {
        return new HashSet<>(p(c).getStringSet("seen", Collections.<String>emptySet()));
    }

    static void saveSeen(Context c, Set<String> s) {
        p(c).edit().putStringSet("seen", new HashSet<>(s)).putBoolean("initialized", true).commit();
    }

    static boolean initialized(Context c) { return p(c).getBoolean("initialized", false); }

    static boolean enabled(Context c) { return p(c).getBoolean("enabled", true); }

    static void setEnabled(Context c, boolean v) { p(c).edit().putBoolean("enabled", v).apply(); }

    static boolean autoCopy(Context c) { return p(c).getBoolean("auto_copy", true); }

    static void setAutoCopy(Context c, boolean v) { p(c).edit().putBoolean("auto_copy", v).apply(); }

    static String keywords(Context c) { return p(c).getString("keywords", ""); }

    static void setKeywords(Context c, String v) { p(c).edit().putString("keywords", v == null ? "" : v.trim()).apply(); }

    /** Порожні ключові слова = усі ігри. */
    static boolean matches(Context c, String title) {
        String kw = keywords(c).trim();
        if (kw.isEmpty()) return true;
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        boolean any = false;
        for (String k : kw.split(",")) {
            k = k.trim().toLowerCase(Locale.ROOT);
            if (k.isEmpty()) continue;
            any = true;
            if (t.contains(k)) return true;
        }
        return !any;
    }
}
