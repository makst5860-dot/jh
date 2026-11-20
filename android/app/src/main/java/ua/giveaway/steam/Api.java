package ua.giveaway.steam;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Офіційні безкоштовні роздачі для Steam з публічного API GamerPower. */
final class Api {
    private static final String URL_STR =
            "https://www.gamerpower.com/api/giveaways?platform=steam&type=game";

    static final class Giveaway {
        final String id, title, worth, platforms, url, endDate;

        Giveaway(JSONObject o) {
            id = String.valueOf(o.opt("id"));
            title = o.optString("title", "Без назви");
            worth = o.optString("worth", "");
            platforms = o.optString("platforms", "");
            String u = o.optString("open_giveaway_url", "");
            if (u.isEmpty()) u = o.optString("open_giveaway", "");
            url = u;
            endDate = o.optString("end_date", "");
        }

        String meta() {
            StringBuilder sb = new StringBuilder();
            if (!worth.isEmpty() && !"N/A".equalsIgnoreCase(worth)) sb.append("було ").append(worth);
            if (!endDate.isEmpty() && !"N/A".equalsIgnoreCase(endDate)) {
                if (sb.length() > 0) sb.append("  •  ");
                String d = endDate.length() >= 10 ? endDate.substring(0, 10) : endDate;
                sb.append("до ").append(d);
            }
            if (sb.length() == 0) sb.append("Безкоштовно зараз");
            return sb.toString();
        }
    }

    static List<Giveaway> fetch() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(URL_STR).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "steam-giveaway-catcher-android");
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : readAll(in).trim();
            List<Giveaway> out = new ArrayList<>();
            if (!body.startsWith("[")) {
                // API повертає {"status":0,...}, коли активних роздач немає
                if (code >= 400 && code != 404) throw new IOException("HTTP " + code);
                return out;
            }
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Giveaway g = new Giveaway(o);
                // подвійна перевірка: лише Steam
                if (g.platforms.toLowerCase(Locale.ROOT).contains("steam") && !g.url.isEmpty()) {
                    out.add(g);
                }
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Некоректна відповідь сервера", e);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
