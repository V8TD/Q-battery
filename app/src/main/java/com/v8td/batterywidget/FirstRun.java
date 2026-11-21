package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Окно первого запуска. Кнопки нарисованы внутри самого окна,
 * поэтому оболочка телефона не может их «съесть».
 */
final class FirstRun {
    private static final String KEY = "first_run_dialog_shown";
    private static final int OK = 0xFF3FBF7F;
    private static final int WARN = 0xFFE0A030;
    private static final int MUTED = 0xFF93A3B8;

    private FirstRun() {
    }

    static void showIfNeeded(Activity a) {
        SharedPreferences p = Prefs.get(a);
        if (p.getBoolean(KEY, false)) return;
        p.edit().putBoolean(KEY, true).apply();
        show(a);
    }

    static boolean batteryUnrestricted(Activity a) {
        try {
            PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    static void show(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 22), dp(a, 10), dp(a, 22), dp(a, 6));

        TextView intro = new TextView(a);
        intro.setText("Виджет узнаёт о заряде из сообщений самой системы. "
                + "Постоянной службы и уведомления нет — это экономит батарею.\n\n"
                + "Настройки открываются тапом по виджету или по значку Q-battery "
                + "в списке приложений. Значок включён: так система показывает "
                + "приложение в списках батареи. Выключить его можно в разделе "
                + "«Системные настройки».\n\n"
                + "Чтобы оболочка телефона не выгружала приложение из памяти, "
                + "нужны разрешения ниже. Кнопки открывают нужные разделы "
                + "и не закрывают это окно.");
        intro.setTextSize(15);
        intro.setLineSpacing(dp(a, 3), 1f);
        box.addView(intro);

        Button autostart = new Button(a);
        autostart.setAllCaps(false);
        autostart.setText("1. Автозапуск — открыть раздел");
        autostart.setOnClickListener(v -> SystemLinks.openAutostart(a));
        box.addView(autostart, spaced(a, 18));

        TextView autostartState = new TextView(a);
        autostartState.setTextSize(12);
        autostartState.setTextColor(MUTED);
        autostartState.setText("Состояние автозапуска приложение узнать не может. "
                + "В открывшемся разделе включите переключатель для Q-battery. "
                + "На телефонах не Xiaomi этого раздела может не быть — "
                + "тогда просто пропустите пункт.");
        autostartState.setPadding(0, dp(a, 4), 0, 0);
        box.addView(autostartState);

        Button battery = new Button(a);
        battery.setAllCaps(false);
        battery.setOnClickListener(v -> SystemLinks.openBatterySettings(a));
        box.addView(battery, spaced(a, 16));

        TextView batteryState = new TextView(a);
        batteryState.setTextSize(13);
        batteryState.setPadding(0, dp(a, 4), 0, dp(a, 6));
        box.addView(batteryState);

        Runnable update = () -> {
            boolean free = batteryUnrestricted(a);
            battery.setText(free
                    ? "2. Экономия заряда — без ограничений ✓"
                    : "2. Экономия заряда — ограничена ✗");
            batteryState.setText(free
                    ? "Этот пункт уже в порядке, трогать не нужно."
                    : "Нажмите кнопку и выберите для Q-battery «Без ограничений». "
                    + "Если Q-battery нет в списке — откройте его карточку кнопкой ниже.");
            batteryState.setTextColor(free ? OK : WARN);
        };
        update.run();

        Button appInfo = new Button(a);
        appInfo.setAllCaps(false);
        appInfo.setText("Q-battery нет в списке? Открыть карточку приложения");
        appInfo.setOnClickListener(v -> SystemLinks.openAppDetails(a));
        box.addView(appInfo, spaced(a, 14));

        TextView footer = new TextView(a);
        footer.setTextSize(12);
        footer.setTextColor(MUTED);
        footer.setText("Все эти переходы позже будут доступны в настройках: "
                + "раздел «Системные настройки». Само приложение не может "
                + "отключить системные ограничения — оно только открывает раздел.");
        footer.setPadding(0, dp(a, 10), 0, 0);
        box.addView(footer);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Разрешите работу в фоне")
                .setView(scroll)
                .setNegativeButton("Готово", null)
                .create();
        dialog.show();

        // После возврата из системных настроек состояние обновляется само.
        View decor = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (decor != null) {
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
                if (hasFocus) update.run();
            });
        }
    }

    private static LinearLayout.LayoutParams spaced(Activity a, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, topDp);
        return lp;
    }

    private static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
