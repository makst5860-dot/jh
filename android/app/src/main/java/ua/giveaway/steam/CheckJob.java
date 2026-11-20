package ua.giveaway.steam;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Фонова перевірка, яку Android запускає приблизно раз на 15 хв (і після перезавантаження). */
public class CheckJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                Checker.run(getApplicationContext());
            } catch (Exception ignored) {
                // немає інтернету чи сервер недоступний — спробуємо наступного разу
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
