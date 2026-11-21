package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private static final int REQ_SOUND = 4107;
    private static final String ALIAS = "com.v8td.batterywidget.LauncherAlias";
    private static final String[] SCALE_LABELS =
            {"100", "90", "80", "70", "60", "50", "40", "30", "20", "10"};

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean configuring;
    private TextView subtitle, status, tvThreshold;
    private Switch swSound, swChargingBlue, swLauncherIcon;
    private SeekBar seek;
    private LinearLayout palettePreview, systemBox;
    private Button btnSoundPick, btnSystem, btnIconMode, btnOutline;
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
        systemBox = findViewById(R.id.system_box);
        btnSystem = findViewById(R.id.btn_system);
        swLauncherIcon = findViewById(R.id.sw_launcher_icon);
        btnIconMode = findViewById(R.id.btn_icon_mode);
        btnOutline = findViewById(R.id.btn_outline);
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

        btnIconMode.setOnClickListener(v -> showIconModes());
        btnOutline.setOnClickListener(v -> showOutlines());

        swChargingBlue = findViewById(R.id.sw_charging_blue);
        swChargingBlue.setOnCheckedChangeListener(null);
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

        btnSystem.setOnClickListener(v -> {
            boolean show = systemBox.getVisibility() != View.VISIBLE;
            systemBox.setVisibility(show ? View.VISIBLE : View.GONE);
            updateSystemButton();
        });
        syncLauncherIcon();

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

    private void updateSystemButton() {
        boolean open = systemBox.getVisibility() == View.VISIBLE;
        btnSystem.setText(twoLine("Системные настройки",
                open ? "нажмите, чтобы свернуть ▲" : "автозапуск, экономия заряда, значок ▼",
                getColor(R.color.muted)));
    }

    /** Состояние значка. Если пользователь не менял — берём из манифеста, там включено. */
    private boolean launcherIconEnabled() {
        try {
            int st = getPackageManager().getComponentEnabledSetting(new ComponentName(this, ALIAS));
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
            if (st == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return false;
            return true;
        } catch (Throwable t) {
            return true;
        }
    }

    private void syncLauncherIcon() {
        swLauncherIcon.setOnCheckedChangeListener(null);
        swLauncherIcon.setChecked(launcherIconEnabled());
        swLauncherIcon.setOnCheckedChangeListener((b, checked) -> setLauncherIcon(checked));
    }

    private void setLauncherIcon(boolean on) {
        try {
            getPackageManager().setComponentEnabledSetting(new ComponentName(this, ALIAS),
                    on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            Toast.makeText(this, on
                    ? "Значок включён. Список приложений может обновиться не сразу."
                    : "Значок выключен.", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Не удалось изменить значок", Toast.LENGTH_LONG).show();
            syncLauncherIcon();
        }
    }

    private void refreshWidget() {
        App.exec(() -> WidgetUpdater.refresh(this, true));
    }

    private void refreshUi() {
        tvThreshold.setText("когда заряд дойдёт до " + Prefs.threshold(this) + "%");
        swSound.setChecked(Prefs.soundEnabled(this));
        seek.setProgress(Prefs.threshold(this) - 50);
        swChargingBlue.setOnCheckedChangeListener(null);
        swChargingBlue.setChecked(Prefs.chargingBlue(this));
        swChargingBlue.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setChargingBlue(this, checked);
            refreshWidget();
        });
        syncLauncherIcon();
        updateSystemButton();
        updateLookButtons();

        palettePreview.removeAllViews();
        for (int i = 0; i < Prefs.BANDS; i++) {
            final int band = Prefs.BANDS - 1 - i;
            int color = Prefs.color(this, band);
            TextView cell = new TextView(this);
            cell.setText(SCALE_LABELS[band]);
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
        sb.append("\nЗначок: ").append(Prefs.MODE_NAMES[Prefs.iconMode(this)]);
        sb.append(" · обводка: ").append(Prefs.OUTLINE_NAMES[Prefs.outline(this)]);
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
        final int pal = Prefs.palette(this);
        ColorPicker.open(this, Prefs.BAND_NAMES[band], Prefs.color(this, band),
                pal, band,
                color -> {
                    Prefs.setColor(this, pal, band, color);
                    refreshWidget();
                    refreshUi();
                },
                pal == Prefs.userPaletteIndex() ? null : colors -> applyScheme(pal, colors));
    }

    /** Записывает готовую схему генератора во все 10 диапазонов. */
    private void applyScheme(int pal, int[] colors) {
        for (int b = 0; b < Prefs.BANDS; b++) {
            Prefs.setColor(this, pal, b, colors[b]);
        }
        WidgetUpdater.forget();
        refreshWidget();
        refreshUi();
        Toast.makeText(this, "Схема собрана", Toast.LENGTH_SHORT).show();
    }

    private void updateLookButtons() {
        btnIconMode.setText(twoLine("Значок на виджете",
                Prefs.MODE_NAMES[Prefs.iconMode(this)], getColor(R.color.muted)));
        btnOutline.setText(twoLine("Обводка цифр",
                Prefs.OUTLINE_NAMES[Prefs.outline(this)], getColor(R.color.muted)));
    }

    /** Превью рисуются тем же кодом, что и настоящий виджет. */
    private Bitmap lookPreview(int px, int mode, int outline, int color, boolean charging) {
        float density = getResources().getDisplayMetrics().density;
        int[] res = WidgetRenderer.reserve(mode, px, px);
        WidgetTextSizer.Size size = WidgetTextSizer.get(px, px, density, 1f, res[0], res[1]);
        return WidgetRenderer.render(px, px, "88", color, size.twoDigits, charging, mode, outline);
    }

    private void roundCorners(ImageView iv, final float radiusPx) {
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        iv.setClipToOutline(true);
    }

    private void showIconModes() {
        final int current = Prefs.iconMode(this);
        final int outline = Prefs.outline(this);
        BatteryState st = BatteryReader.read(this);
        final int color = st.valid
                ? Prefs.color(this, BatteryState.band(st.percent))
                : getColor(R.color.accent);
        final int px = dp(48);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Молния появляется только при зарядке, знак % виден всегда."
                + " Превью показано с подключённой зарядкой. Под значки резервируется место,"
                + " поэтому при подключении зарядки цифры не прыгают и не уменьшаются.");
        hint.setTextSize(12);
        hint.setTextColor(getColor(R.color.muted));
        hint.setPadding(dp(4), 0, dp(4), dp(8));
        list.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Значок на виджете")
                .setView(scroll)
                .setNegativeButton("Закрыть", null)
                .create();

        for (int i = 0; i < Prefs.MODES; i++) {
            final int mode = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(8), dp(4), dp(8));

            ImageView preview = new ImageView(this);
            preview.setImageBitmap(lookPreview(px, mode, outline, color, true));
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.button));
            bg.setStroke(dp(1), getColor(R.color.stroke));
            preview.setBackground(bg);
            roundCorners(preview, dp(8));
            row.addView(preview, new LinearLayout.LayoutParams(px, px));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(8), 0);
            TextView name = new TextView(this);
            name.setText(Prefs.MODE_NAMES[i] + (i == current ? "  ✓" : ""));
            name.setTextSize(15);
            name.setTextColor(getColor(i == current ? R.color.accent : R.color.text));
            if (i == current) name.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(name);
            TextView desc = new TextView(this);
            desc.setText(Prefs.MODE_HINTS[i]);
            desc.setTextSize(11);
            desc.setTextColor(getColor(R.color.muted));
            texts.addView(desc);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

            row.setOnClickListener(v -> {
                Prefs.setIconMode(this, mode);
                refreshWidget();
                refreshUi();
                dialog.dismiss();
                Toast.makeText(this, Prefs.MODE_NAMES[mode], Toast.LENGTH_SHORT).show();
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        dialog.show();
    }

    private void showOutlines() {
        final int current = Prefs.outline(this);
        final int mode = Prefs.iconMode(this);
        BatteryState st = BatteryReader.read(this);
        final int color = st.valid
                ? Prefs.color(this, BatteryState.band(st.percent))
                : getColor(R.color.accent);
        final int px = dp(48);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView hint = new TextView(this);
        hint.setText("Превью на светлой подложке — как на светлых обоях."
                + " Оформление получает только текст: цифры и знак %."
                + " К теме телефона не привязано.");
        hint.setTextSize(12);
        hint.setTextColor(getColor(R.color.muted));
        hint.setPadding(dp(4), 0, dp(4), dp(8));
        list.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Обводка цифр")
                .setView(scroll)
                .setNegativeButton("Закрыть", null)
                .create();

        String[] hints = {
                "Как сейчас: только цвет цифр",
                "Мягкий тёмный ореол вокруг цифр",
                "Тонкая тёмная линия по краю цифр"
        };
        for (int i = 0; i < Prefs.OUTLINE_NAMES.length; i++) {
            final int style = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(8), dp(4), dp(8));

            ImageView preview = new ImageView(this);
            preview.setImageBitmap(lookPreview(px, mode, style, color, true));
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFF3F5F8);
            bg.setStroke(dp(1), getColor(R.color.stroke));
            preview.setBackground(bg);
            roundCorners(preview, dp(8));
            row.addView(preview, new LinearLayout.LayoutParams(px, px));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(8), 0);
            TextView name = new TextView(this);
            name.setText(Prefs.OUTLINE_NAMES[i] + (i == current ? "  ✓" : ""));
            name.setTextSize(15);
            name.setTextColor(getColor(i == current ? R.color.accent : R.color.text));
            if (i == current) name.setTypeface(Typeface.DEFAULT_BOLD);
            texts.addView(name);
            TextView desc = new TextView(this);
            desc.setText(hints[i]);
            desc.setTextSize(11);
            desc.setTextColor(getColor(R.color.muted));
            texts.addView(desc);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

            row.setOnClickListener(v -> {
                Prefs.setOutline(this, style);
                refreshWidget();
                refreshUi();
                dialog.dismiss();
                Toast.makeText(this, "Обводка: " + Prefs.OUTLINE_NAMES[style],
                        Toast.LENGTH_SHORT).show();
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        dialog.show();
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
            for (int j = 0; j < Prefs.BANDS; j++) {
                int band = Prefs.BANDS - 1 - j;
                View chip = new View(this);
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Prefs.colorOf(this, index, band));
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
        String hintText = "Нажми — изменить цвет. Долгое нажатие — вернуть заводской цвет диапазона.";
        if (pal == Prefs.userPaletteIndex()) {
            hintText += "\nАрлекин — мозаика: цвет не подсказывает уровень заряда.";
        }
        hint.setText(hintText);
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
        for (int j = 0; j < Prefs.BANDS; j++) {
            final int band = Prefs.BANDS - 1 - j;
            int color = Prefs.colorOf(this, pal, band);
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
            label.setText(Prefs.BAND_NAMES[band]);
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
                        Prefs.colorOf(this, pal, band),
                        pal, band,
                        picked -> {
                            Prefs.setColor(this, pal, band, picked);
                            refreshWidget();
                            refreshUi();
                            showBands(pal);
                        },
                        pal == Prefs.userPaletteIndex() ? null
                                : colors -> {
                            applyScheme(pal, colors);
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
                ? "Схема «Арлекин» вернётся к заводской мозаике. Остальные схемы не изменятся."
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
                        + "Значок Q-battery в списке приложений включён — так система показывает "
                        + "приложение в списках батареи. Если приложение там не находится, "
                        + "зайдите в его карточку через «Системные настройки приложения».\n\n"
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
                        + "Значки и обводка цифр настраиваются в разделе «Значки и читаемость».\n\n"
                        + "Без интернета, без рекламы, без постоянного уведомления.\n\n"
                        + "Удаление: Настройки Android → Приложения → Q-battery → Удалить.")
                .setPositiveButton("Настройки приложения", (d, w) -> SystemLinks.openAppDetails(this))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Сбросить настройки?")
                .setMessage("Все схемы вернутся к заводским цветам, «Арлекин» — к своей мозаике. "
                        + "Параметры звука сбросятся. Значок на виджете вернётся к «Только цифры», "
                        + "обводка выключится. Виджет останется на месте, системные разрешения, "
                        + "автозапуск и значок приложения не изменятся.")
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
