package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private static final int REQ_SOUND = 4107;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean configuring;

    private TextView subtitle, status, tvThreshold, tvSoundName;
    private Switch swSound;
    private SeekBar seek;
    private LinearLayout palettePreview;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);

        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        configuring = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
        if (configuring) {
            setResult(RESULT_CANCELED, new Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        }

        applyInsets();
        bind();

        findViewById(R.id.config_box).setVisibility(configuring ? View.VISIBLE : View.GONE);
        findViewById(R.id.settings_box).setVisibility(configuring ? View.GONE : View.VISIBLE);

        if (!configuring) {
            App.exec(() -> WidgetUpdater.refresh(this, true));
            App.watch(this);
        }
        refreshUi();
        FirstRun.showIfNeeded(this);
    }

    private void applyInsets() {
        final ScrollView scroll = findViewById(R.id.settings_scroll);
        final int base = dp(20);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                top = i.top;
                bottom = i.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(base, base + top, base, base + bottom);
            return insets;
        });
    }

    private void bind() {
        subtitle = findViewById(R.id.subtitle);
        status = findViewById(R.id.status_card);
        tvThreshold = findViewById(R.id.tv_threshold);
        tvSoundName = findViewById(R.id.tv_sound_name);
        swSound = findViewById(R.id.sw_sound);
        seek = findViewById(R.id.seek_threshold);
        palettePreview = findViewById(R.id.palette_preview);

        subtitle.setText("Версия " + BuildConfig.VERSION_NAME + " · виджет 1×1");

        Button add = findViewById(R.id.btn_add);
        add.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent()
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
            App.exec(() -> WidgetUpdater.refresh(this, true));
            Scheduler.ensure(this);
            finish();
        });

        Button palette = findViewById(R.id.btn_palette);
        palette.setOnClickListener(v -> showPalettes());

        Button bands = findViewById(R.id.btn_bands);
        bands.setOnClickListener(v -> showBands());

        swSound.setChecked(Prefs.soundEnabled(this));
        swSound.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setSoundEnabled(this, checked);
            refreshWidget();
        });

        seek.setMax(50);
        seek.setProgress(Prefs.threshold(this) - 50);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tvThreshold.setText("Порог сигнала: " + (progress + 50) + "%");
                if (fromUser) Prefs.setThreshold(MainActivity.this, progress + 50);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        Button pick = findViewById(R.id.btn_sound_pick);
        pick.setOnClickListener(v -> pickSound());

        Button test = findViewById(R.id.btn_sound_test);
        test.setOnClickListener(v -> App.exec(() -> SoundPlayer.play(this)));

        Button autostart = findViewById(R.id.btn_autostart);
        autostart.setOnClickListener(v -> SystemLinks.openAutostart(this));

        Button battery = findViewById(R.id.btn_battery);
        battery.setOnClickListener(v -> SystemLinks.openBatterySettings(this));

        Button appInfo = findViewById(R.id.btn_appinfo);
        appInfo.setOnClickListener(v -> SystemLinks.openAppDetails(this));

        Button help = findViewById(R.id.btn_help);
        help.setOnClickListener(v -> showHelp());

        Button about = findViewById(R.id.btn_about);
        about.setOnClickListener(v -> showAbout());

        Button reset = findViewById(R.id.btn_reset);
        reset.setOnClickListener(v -> confirmReset());
    }

    private void refreshWidget() {
        App.exec(() -> WidgetUpdater.refresh(this, true));
    }

    private void refreshUi() {
        tvThreshold.setText("Порог сигнала: " + Prefs.threshold(this) + "%");
        swSound.setChecked(Prefs.soundEnabled(this));
        seek.setProgress(Prefs.threshold(this) - 50);

        palettePreview.removeAllViews();
        for (int i = 0; i < Prefs.BANDS; i++) {
            View chip = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(Prefs.color(this, i));
            shape.setCornerRadius(dp(4));
            chip.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(14), 1f);
            lp.setMargins(dp(1), 0, dp(1), 0);
            palettePreview.addView(chip, lp);
        }

        BatteryState s = BatteryReader.read(this);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean free = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());

        StringBuilder sb = new StringBuilder();
        sb.append("Сейчас: ");
        sb.append(s.valid ? s.percent + "%" : "данные недоступны");
        sb.append(s.charging ? " · зарядка подключена" : " · от батареи");
        sb.append("\nСхема: ").append(Prefs.PALETTE_NAMES[Prefs.palette(this)]);
        sb.append("\nОбновление: по событиям системы, при включении экрана и раз в 15 минут");
        sb.append("\nОптимизация батареи: ").append(free ? "без ограничений" : "ограничена");
        status.setText(sb.toString());

        refreshSoundName();
        refreshBackgroundStates();
    }

    private void refreshSoundName() {
        String saved = Prefs.soundUri(this);
        Uri uri = saved == null
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                : Uri.parse(saved);
        String name = "системный звук уведомления";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r != null) name = r.getTitle(this);
        } catch (Throwable ignored) {
        }
        tvSoundName.setText("Звук: " + name);
    }

    private void showPalettes() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Цветовая схема")
                .setView(scroll)
                .setNegativeButton("Закрыть", null)
                .create();

        for (int i = 0; i < Prefs.PALETTE_NAMES.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(6), dp(10), dp(6), dp(10));

            TextView name = new TextView(this);
            name.setText(Prefs.PALETTE_NAMES[i] + (Prefs.palette(this) == i ? "  ✓" : ""));
            name.setTextSize(16);
            name.setTextColor(0xFFE8EEF7);
            row.addView(name);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(0, dp(6), 0, 0);
            for (int b = 0; b < Prefs.BANDS; b++) {
                int color = i < Prefs.PALETTES.length
                        ? Prefs.PALETTES[i][b]
                        : Prefs.get(this).getInt("user_" + b, Prefs.PALETTES[0][b]);
                View chip = new View(this);
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(color);
                shape.setCornerRadius(dp(3));
                chip.setBackground(shape);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(14), 1f);
                lp.setMargins(dp(1), 0, dp(1), 0);
                chips.addView(chip, lp);
            }
            row.addView(chips);
            row.setOnClickListener(v -> {
                Prefs.setPalette(this, index);
                refreshWidget();
                refreshUi();
                dialog.dismiss();
                Toast.makeText(this, Prefs.PALETTE_NAMES[index], Toast.LENGTH_SHORT).show();
            });
            list.addView(row);
        }
        dialog.show();
    }

    private void showBands() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Цвет по диапазонам")
                .setView(scroll)
                .setNegativeButton("Готово", null)
                .create();

        for (int i = 0; i < Prefs.BANDS; i++) {
            final int band = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(10), dp(4), dp(10));

            View swatch = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(Prefs.color(this, i));
            shape.setCornerRadius(dp(5));
            shape.setStroke(dp(1), 0x66889099);
            swatch.setBackground(shape);
            row.addView(swatch, new LinearLayout.LayoutParams(dp(32), dp(26)));

            TextView label = new TextView(this);
            label.setText(Prefs.BAND_NAMES[i]);
            label.setTextSize(16);
            label.setTextColor(0xFFE8EEF7);
            label.setPadding(dp(14), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView edit = new TextView(this);
            edit.setText("Изменить ›");
            edit.setTextSize(14);
            edit.setTextColor(0xFF35D9FF);
            row.addView(edit);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                ColorPicker.open(this, Prefs.BAND_NAMES[band], Prefs.color(this, band), color -> {
                    Prefs.setUserColor(this, band, color);
                    refreshWidget();
                    refreshUi();
                    showBands();
                });
            });
            list.addView(row);
        }
        dialog.show();
    }

    private void pickSound() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук сигнала батареи");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        String saved = Prefs.soundUri(this);
        Uri existing = saved == null
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                : Uri.parse(saved);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing);
        try {
            startActivityForResult(intent, REQ_SOUND);
        } catch (Throwable t) {
            Toast.makeText(this, "Системный выбор звуков недоступен", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SOUND && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Throwable ignored) {
                }
            }
            Prefs.setSoundUri(this, uri == null ? null : uri.toString());
            refreshSoundName();
            Toast.makeText(this, "Звук сохранён", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Если виджет отстаёт")
                .setMessage("Виджет обновляется по сообщениям системы и сразу при включении экрана. "
                        + "При включённом экране есть страховка раз в 30 секунд, "
                        + "при выключенном — проверок нет вовсе.\n\n"
                        + "На Xiaomi (MIUI / HyperOS) разрешите:\n"
                        + "• Автозапуск приложения;\n"
                        + "• Экономию заряда — «Без ограничений»;\n"
                        + "• Фоновую активность, если такой пункт есть.\n\n"
                        + "На других телефонах названия пунктов отличаются. Приложение не может "
                        + "отключить системные ограничения само — оно только открывает нужный раздел.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Q-battery " + BuildConfig.VERSION_NAME)
                .setMessage("Виджет заряда 1×1. Прозрачный фон, крупные цифры, одинаковый размер "
                        + "для всех значений 0–99 и отдельный для 100.\n\n"
                        + "Без интернета, без рекламы, без постоянного уведомления.\n\n"
                        + "Удаление: Настройки Android → Приложения → Q-battery → Удалить.")
                .setPositiveButton("Настройки приложения", (d, w) -> SystemLinks.openAppDetails(this))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Сбросить настройки?")
                .setMessage("Цвета и параметры звука вернутся к исходным. Виджет останется на месте, "
                        + "системные разрешения и автозапуск не изменятся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (d, w) -> {
                    Prefs.reset(this);
                    WidgetUpdater.forget();
                    refreshWidget();
                    refreshUi();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.watch(this);
        Scheduler.ensure(this);
        App.exec(() -> WidgetUpdater.refresh(this, true));
        if (!configuring) refreshUi();
    }

    private void refreshBackgroundStates() {
        boolean free = FirstRun.batteryUnrestricted(this);

        Button battery = findViewById(R.id.btn_battery);
        battery.setText(twoLine("Экономия заряда и фоновая работа",
                free ? "Без ограничений ✓" : "Ограничена ✗",
                free ? 0xFF3FBF7F : 0xFFE0A030));

        Button autostart = findViewById(R.id.btn_autostart);
        autostart.setText(twoLine("Автозапуск (Xiaomi / HyperOS)",
                "Состояние неизвестно — проверьте вручную", 0xFF93A3B8));
    }

    private CharSequence twoLine(String title, String state, int stateColor) {
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(title);
        s.append("\n");
        int start = s.length();
        s.append(state);
        s.setSpan(new android.text.style.RelativeSizeSpan(0.8f), start, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new android.text.style.ForegroundColorSpan(stateColor), start, s.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
