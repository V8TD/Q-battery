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
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQ_SOUND = 4107;
    private static final String[] SCALE_LABELS =
            {"100", "90", "80", "70", "60", "50", "40", "30", "20", "10"};

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean configuring;
    private TextView subtitle, status, tvThreshold;
    private Switch swSound, swChargingBlue;
    private SeekBar seek;
    private LinearLayout palettePreview;
    private Button btnSoundPick;
    private ImageButton btnSoundTest;

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
        swSound = findViewById(R.id.sw_sound);
        seek = findViewById(R.id.seek_threshold);
        palettePreview = findViewById(R.id.palette_preview);
        btnSoundPick = findViewById(R.id.btn_sound_pick);
        btnSoundTest = findViewById(R.id.btn_sound_test);
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
        bands.setText("Цвета схемы");
        bands.setOnClickListener(v -> showBands(Prefs.palette(this)));

        swChargingBlue = findViewById(R.id.sw_charging_blue);
        swChargingBlue.setChecked(Prefs.chargingBlue(this));
        swChargingBlue.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setChargingBlue(this, checked);
            refreshWidget();
        });

        swSound.setChecked(Prefs.soundEnabled(this));
        swSound.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setSoundEnabled(this, checked);
            applySoundEnabled();
            refreshWidget();
        });

        seek.setMax(50);
        seek.setProgress(Prefs.threshold(this) - 50);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tvThreshold.setText("когда заряд дойдёт до " + (progress + 50) + "%");
                if (fromUser) Prefs.setThreshold(MainActivity.this, progress + 50);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        btnSoundPick.setOnClickListener(v -> pickSound());
        btnSoundTest.setOnClickListener(v -> App.exec(() -> SoundPlayer.play(this)));

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
        tvThreshold.setText("когда заряд дойдёт до " + Prefs.threshold(this) + "%");
        swSound.setChecked(Prefs.soundEnabled(this));
        seek.setProgress(Prefs.threshold(this) - 50);

                swChargingBlue.setChecked(Prefs.chargingBlue(this));
        
        palettePreview.removeAllViews();
        for (int i = 0; i < Prefs.BANDS; i++) {
            final int band = i;
            int color = Prefs.color(this, i);
            TextView cell = new TextView(this);
            cell.setText(SCALE_LABELS[i]);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(10);
            cell.setTextColor(ColorPicker.contrast(color));
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(color);
            shape.setCornerRadius(dp(5));
            cell.setBackground(shape);
            cell.setOnClickListener(v -> openBandEditor(band));
            cell.setOnLongClickListener(v -> {
                confirmResetBand(Prefs.palette(this), band, false);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(28), 1f);
            lp.setMargins(dp(1), 0, dp(1), 0);
            palettePreview.addView(cell, lp);
        }

        BatteryState s = BatteryReader.read(this);
        boolean free = FirstRun.batteryUnrestricted(this);
        int active = Prefs.palette(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Сейчас: ");
        sb.append(s.valid ? s.percent + "%" : "данные недоступны");
        sb.append(s.charging ? " · зарядка подключена" : " · от батареи");
        sb.append("\nСхема: ").append(Prefs.PALETTE_NAMES[active]);
        if (Prefs.isModified(this, active)) sb.append(" · изменена");
        sb.append("\nОбновление: по событиям системы и при включении экрана");
        sb.append("\nОптимизация батареи: ").append(free ? "без ограничений" : "ограничена");
        status.setText(sb.toString());

        refreshSoundButton();
        refreshBackgroundStates();
        applySoundEnabled();
    }

    private void applySoundEnabled() {
        boolean on = Prefs.soundEnabled(this);
        float alpha = on ? 1f : 0.45f;
        seek.setEnabled(on);
        btnSoundPick.setEnabled(on);
        btnSoundTest.setEnabled(on);
        seek.setAlpha(alpha);
        tvThreshold.setAlpha(alpha);
        btnSoundPick.setAlpha(alpha);
        btnSoundTest.setAlpha(alpha);
    }

    private void refreshSoundButton() {
        String saved = Prefs.soundUri(this);
        Uri uri = saved == null
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                : Uri.parse(saved);
        String name = "системный звук";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r != null && r.getTitle(this) != null) name = r.getTitle(this);
        } catch (Throwable ignored) {
        }
        btnSoundPick.setText(twoLine("Выбор сигнала", name, getColor(R.color.muted)));
    }

    private void openBandEditor(int band) {
        ColorPicker.open(this, Prefs.BAND_NAMES[band], Prefs.color(this, band), color -> {
            Prefs.setColor(this, Prefs.palette(this), band, color);
            refreshWidget();
            refreshUi();
        });
    }

    private void showPalettes() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Нажми на схему — сделать её активной. Любую схему можно настроить и сбросить.");
        hint.setTextSize(12);
        hint.setTextColor(getColor(R.color.muted));
        hint.setPadding(dp(6), 0, dp(6), dp(8));
        list.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Цветовая схема")
                .setView(scroll)
                .setNegativeButton("Закрыть", null)
                .create();

        for (int i = 0; i < Prefs.paletteCount(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(6), dp(10), dp(6), dp(10));

            TextView name = new TextView(this);
            String title = Prefs.PALETTE_NAMES[i];
            if (Prefs.palette(this) == i) title += "  ✓";
            if (Prefs.isModified(this, i)) title += "  · изменена";
            name.setText(title);
            name.setTextSize(16);
            name.setTextColor(getColor(R.color.text));
            row.addView(name);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(0, dp(6), 0, 0);
            for (int b = 0; b < Prefs.BANDS; b++) {
                View chip = new View(this);
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Prefs.colorOf(this, i, b));
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

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(6), 0, 0);

            TextView edit = new TextView(this);
            edit.setText("Настроить");
            edit.setTextSize(13);
            edit.setTextColor(getColor(R.color.text));
            edit.setPadding(dp(6), dp(6), dp(20), dp(6));
            edit.setOnClickListener(v -> {
                dialog.dismiss();
                showBands(index);
            });
            actions.addView(edit);

            TextView reset = new TextView(this);
            reset.setText("Сбросить");
            reset.setTextSize(13);
            reset.setTextColor(getColor(R.color.muted));
            reset.setPadding(dp(6), dp(6), dp(6), dp(6));
            reset.setOnClickListener(v -> {
                dialog.dismiss();
                confirmResetPalette(index);
            });
            actions.addView(reset);

            row.addView(actions);
            list.addView(row);
        }
        dialog.show();
    }

    private void showBands(int paletteIndex) {
        final int pal = paletteIndex;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Нажми — изменить цвет. Долгое нажатие — вернуть заводской цвет диапазона.");
        hint.setTextSize(12);
        hint.setTextColor(getColor(R.color.muted));
        hint.setPadding(0, 0, 0, dp(8));
        list.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Цвета: " + Prefs.PALETTE_NAMES[pal])
                .setView(scroll)
                .setNeutralButton("Сбросить схему", (d, w) -> confirmResetPalette(pal))
                .setNegativeButton("Готово", null)
                .create();

        for (int i = 0; i < Prefs.BANDS; i++) {
            final int band = i;
            int color = Prefs.colorOf(this, pal, i);
            int fg = ColorPicker.contrast(color);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(dp(8));
            bg.setStroke(dp(1), 0x33888888);
            row.setBackground(bg);

            TextView label = new TextView(this);
            label.setText(Prefs.BAND_NAMES[i]);
            label.setTextSize(15);
            label.setTextColor(fg);
            label.setPadding(dp(14), dp(12), 0, dp(12));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextSize(18);
            arrow.setTextColor(fg);
            arrow.setPadding(0, 0, dp(14), 0);
            row.addView(arrow);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                ColorPicker.open(this, Prefs.BAND_NAMES[band],
                        Prefs.colorOf(this, pal, band), picked -> {
                            Prefs.setColor(this, pal, band, picked);
                            refreshWidget();
                            refreshUi();
                            showBands(pal);
                        });
            });
            row.setOnLongClickListener(v -> {
                dialog.dismiss();
                confirmResetBand(pal, band, true);
                return true;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(3);
            lp.bottomMargin = dp(3);
            list.addView(row, lp);
        }
        dialog.show();
    }

    private void confirmResetPalette(int paletteIndex) {
        final int pal = paletteIndex;
        String msg = pal == Prefs.userPaletteIndex()
                ? "Схема «Особая» вернётся к цветам Стандарта. Остальные схемы не изменятся."
                : "Цвета схемы «" + Prefs.PALETTE_NAMES[pal]
                + "» вернутся к заводским. Остальные схемы не изменятся.";
        new AlertDialog.Builder(this)
                .setTitle("Сбросить схему?")
                .setMessage(msg)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (d, w) -> {
                    Prefs.resetPalette(this, pal);
                    WidgetUpdater.forget();
                    refreshWidget();
                    refreshUi();
                    Toast.makeText(this, "Схема сброшена", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmResetBand(int paletteIndex, int band, boolean reopen) {
        final int pal = paletteIndex;
        final int b = band;
        new AlertDialog.Builder(this)
                .setTitle("Вернуть заводской цвет?")
                .setMessage(Prefs.BAND_NAMES[b] + " — цвет вернётся к заводскому для схемы «"
                        + Prefs.PALETTE_NAMES[pal] + "».")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Вернуть", (d, w) -> {
                    Prefs.resetBand(this, pal, b);
                    WidgetUpdater.forget();
                    refreshWidget();
                    refreshUi();
                    if (reopen) showBands(pal);
                })
                .show();
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
            refreshSoundButton();
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
                .setMessage("Все схемы вернутся к заводским цветам, «Особая» — к цветам Стандарта. "
                        + "Параметры звука сбросятся. Виджет останется на месте, "
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
                getColor(free ? R.color.ok : R.color.warn)));

        Button autostart = findViewById(R.id.btn_autostart);
        autostart.setText(twoLine("Автозапуск (Xiaomi / HyperOS)",
                "Состояние неизвестно — проверьте вручную", getColor(R.color.muted)));
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
