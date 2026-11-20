package ua.giveaway.steam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TextView status;
    private Button checkBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Notifier.ensureChannel(this);
        Scheduler.apply(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        root.addView(text("🎁 Ловець роздач Steam", 22, true));
        TextView sub = text("Офіційні безкоштовні роздачі ігор для Steam (джерело: GamerPower). "
                + "З'явилась нова — прийде сповіщення, а посилання скопіюється в буфер.", 14, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        Switch bg = new Switch(this);
        bg.setText("Ловити фоном (кожні ~15 хв)");
        bg.setChecked(Store.enabled(this));
        bg.setOnCheckedChangeListener((v, on) -> {
            Store.setEnabled(this, on);
            Scheduler.apply(this);
            toast(on ? "Фонова перевірка увімкнена" : "Фонова перевірка вимкнена");
        });
        root.addView(bg, fullWidth());

        Switch copy = new Switch(this);
        copy.setText("Автоматично копіювати посилання");
        copy.setChecked(Store.autoCopy(this));
        copy.setOnCheckedChangeListener((v, on) -> Store.setAutoCopy(this, on));
        copy.setPadding(0, dp(8), 0, 0);
        root.addView(copy, fullWidth());

        TextView kwLabel = text("Ключові слова (через кому; порожньо = усі ігри):", 14, false);
        kwLabel.setPadding(0, dp(16), 0, 0);
        root.addView(kwLabel);

        LinearLayout kwRow = new LinearLayout(this);
        kwRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText kw = new EditText(this);
        kw.setSingleLine(true);
        kw.setHint("напр. resident evil, silent hill");
        kw.setText(Store.keywords(this));
        kwRow.addView(kw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button save = new Button(this);
        save.setText("Зберегти");
        save.setOnClickListener(v -> {
            Store.setKeywords(this, kw.getText().toString());
            toast("Збережено");
            refresh();
        });
        kwRow.addView(save);
        root.addView(kwRow, fullWidth());

        checkBtn = new Button(this);
        checkBtn.setText("Перевірити зараз");
        checkBtn.setOnClickListener(v -> refresh());
        LinearLayout.LayoutParams lp = fullWidth();
        lp.topMargin = dp(8);
        root.addView(checkBtn, lp);

        Button battery = new Button(this);
        battery.setText("Не дати телефону вимикати фон (батарея)");
        battery.setOnClickListener(v -> openBatterySettings());
        root.addView(battery, fullWidth());

        status = text("", 13, false);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        root.addView(text("Активні роздачі для Steam:", 16, true));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, fullWidth());
        return scroll;
    }

    private void refresh() {
        checkBtn.setEnabled(false);
        status.setText("Перевіряю…");
        io.execute(() -> {
            try {
                Checker.Outcome o = Checker.run(getApplicationContext());
                runOnUiThread(() -> show(o));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    checkBtn.setEnabled(true);
                    status.setText("Не вдалося зв'язатися з сервером роздач ("
                            + e.getMessage() + "). Перевір інтернет і спробуй ще раз.");
                });
            }
        });
    }

    private void show(Checker.Outcome o) {
        if (isFinishing() || isDestroyed()) return;
        checkBtn.setEnabled(true);
        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        status.setText("Перевірено о " + when
                + (o.hits.isEmpty() ? "" : "  •  нових: " + o.hits.size()));
        list.removeAllViews();
        int shown = 0;
        for (Api.Giveaway g : o.all) {
            if (!Store.matches(this, g.title)) continue;
            list.addView(row(g), fullWidth());
            shown++;
        }
        if (shown == 0) {
            TextView t = text(o.all.isEmpty()
                    ? "Зараз немає активних роздач для Steam. Я повідомлю, щойно з'явиться нова."
                    : "Немає роздач за твоїми ключовими словами.", 14, false);
            t.setPadding(0, dp(8), 0, 0);
            list.addView(t);
        }
    }

    private View row(final Api.Giveaway g) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(12), 0, dp(4));

        r.addView(text(g.title, 16, true));
        TextView meta = text(g.meta(), 13, false);
        meta.setAlpha(0.75f);
        r.addView(meta);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this);
        open.setText("Відкрити");
        open.setOnClickListener(v -> openUrl(g.url));
        Button cp = new Button(this);
        cp.setText("Копіювати");
        cp.setOnClickListener(v -> Clip.copy(this, g.url, true));
        btns.addView(open);
        btns.addView(cp);
        r.addView(btns);

        View divider = new View(this);
        divider.setBackgroundColor(0x33888888);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dl.topMargin = dp(8);
        r.addView(divider, dl);
        return r;
    }

    private void openUrl(String u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            toast("Не вдалося відкрити посилання");
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            toast("Знайди «Ловець роздач Steam» і вибери «Не оптимізувати»");
        } catch (Exception e) {
            toast("Відкрий Налаштування → Батарея вручну");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
