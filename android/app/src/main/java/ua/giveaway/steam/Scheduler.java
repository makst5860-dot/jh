package ua.giveaway.steam;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** Вмикає/вимикає фонову перевірку. Завдання переживає перезавантаження телефону. */
final class Scheduler {
    private static final int JOB_ID = 1001;
    private static final long PERIOD_MS = 15 * 60 * 1000L;

    static void apply(Context ctx) {
        Context c = ctx.getApplicationContext();
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!Store.enabled(c)) {
            js.cancel(JOB_ID);
            return;
        }
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(c, CheckJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        js.schedule(info);
    }
}
