package ua.giveaway.steam;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Копіювання посилання в буфер обміну. */
final class Clip {
    static void copy(Context ctx, final String text, final boolean toast) {
        if (text == null || text.isEmpty()) return;
        final Context c = ctx.getApplicationContext();
        Runnable r = () -> {
            try {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setPrimaryClip(ClipData.newPlainText("Роздача Steam", text));
                // Android 13+ сам показує підтвердження копіювання
                if (toast && Build.VERSION.SDK_INT < 33) {
                    Toast.makeText(c, "Посилання скопійовано", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else new Handler(Looper.getMainLooper()).post(r);
    }
}
