package ua.giveaway.steam;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Одна перевірка: знайти нові роздачі, сповістити, скопіювати посилання. */
final class Checker {
    static final class Outcome {
        final List<Api.Giveaway> all;
        final List<Api.Giveaway> hits;

        Outcome(List<Api.Giveaway> all, List<Api.Giveaway> hits) {
            this.all = all;
            this.hits = hits;
        }
    }

    static synchronized Outcome run(Context ctx) throws IOException {
        Context c = ctx.getApplicationContext();
        List<Api.Giveaway> all = Api.fetch();
        Set<String> seen = Store.seen(c);
        // Перший запуск: запам'ятовуємо поточні роздачі мовчки (вони й так видно на екрані).
        boolean init = Store.initialized(c);
        List<Api.Giveaway> hits = new ArrayList<>();
        for (Api.Giveaway g : all) {
            if (seen.contains(g.id)) continue;
            seen.add(g.id);
            if (init && Store.matches(c, g.title)) hits.add(g);
        }
        Store.saveSeen(c, seen);

        for (Api.Giveaway g : hits) Notifier.show(c, g);
        if (!hits.isEmpty() && Store.autoCopy(c)) {
            Clip.copy(c, hits.get(hits.size() - 1).url, false);
        }
        return new Outcome(all, hits);
    }
}
