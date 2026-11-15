package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

/** Окно с пояснением при первом запуске. Кнопки остаются и в меню настроек. */
final class FirstRun {

    private static final String KEY = "first_run_dialog_shown";

    private FirstRun() {
    }

    static void showIfNeeded(Activity a) {
        SharedPreferences p = Prefs.get(a);
        if (p.getBoolean(KEY, false)) return;
        p.edit().putBoolean(KEY, true).apply();
        show(a);
    }

    static void show(Activity a) {
        boolean free = false;
        try {
            PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
            free = pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
        } catch (Throwable ignored) {
        }

        String text = "Виджет узнаёт о заряде из сообщений самой системы. "
                + "Постоянной службы и уведомления нет — это экономит батарею.\n\n"
                + "Чтобы оболочка телефона не выгружала приложение из памяти, "
                + "разрешите два пункта:\n\n"
                + "1. Автозапуск — включить.\n"
                + "2. Экономия заряда для приложения — «Без ограничений».\n\n"
                + "Ограничение батареи сейчас: " + (free ? "снято ✓" : "включено ✗") + "\n"
                + "Автозапуск приложение проверить не может — откройте раздел и посмотрите сами.\n\n"
                + "Кнопки не закрывают окно: можно открыть оба раздела по очереди. "
                + "Те же кнопки всегда есть в настройках виджета.";

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Разрешите работу в фоне")
                .setMessage(text)
                .setPositiveButton("Автозапуск", null)
                .setNeutralButton("Батарея", null)
                .setNegativeButton("Готово", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> SystemLinks.openAutostart(a));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> SystemLinks.openBatterySettings(a));
    }
}
