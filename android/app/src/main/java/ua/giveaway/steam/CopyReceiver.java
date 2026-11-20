package ua.giveaway.steam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Кнопка «Копіювати посилання» у сповіщенні. */
public class CopyReceiver extends BroadcastReceiver {
    static final String EXTRA_URL = "url";

    @Override
    public void onReceive(Context context, Intent intent) {
        String url = intent.getStringExtra(EXTRA_URL);
        if (url != null && !url.isEmpty()) Clip.copy(context, url, true);
    }
}
