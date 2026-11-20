package ua.giveaway.steam;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;

/** Сповіщення про нову роздачу з кнопками «Відкрити» і «Копіювати посилання». */
final class Notifier {
    private static final String CHANNEL = "giveaways";

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Роздачі Steam", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Нові безкоштовні роздачі ігор для Steam");
        nm.createNotificationChannel(ch);
    }

    static void show(Context c, Api.Giveaway g) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        int id = g.id.hashCode();
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(g.url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent piOpen = PendingIntent.getActivity(c, id, open, flags);

        Intent copy = new Intent(c, CopyReceiver.class).putExtra(CopyReceiver.EXTRA_URL, g.url);
        PendingIntent piCopy = PendingIntent.getBroadcast(c, id, copy, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        Icon icon = Icon.createWithResource(c, R.drawable.ic_notify);
        String text = g.meta() + "\nПосилання вже в буфері — або натисни «Відкрити».";
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("🎁 Роздача Steam: " + g.title)
                .setContentText(g.meta())
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(piOpen)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(icon, "Відкрити", piOpen).build())
                .addAction(new Notification.Action.Builder(icon, "Копіювати посилання", piCopy).build());
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        nm.notify(id, b.build());
    }
}
