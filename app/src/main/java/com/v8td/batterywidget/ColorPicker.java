package com.v8td.batterywidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Редактор цвета: оттенок/насыщенность/яркость + 7 групп × 5 стилей × 15 оттенков. */
final class ColorPicker {

    interface Callback {
        void onColor(int color);
    }

    private static final String[] GROUP_NAMES =
            {"Красный", "Оранжевый", "Жёлтый", "Зелёный", "Бирюзовый", "Синий", "Фиолетовый"};
    private static final int[] GROUP_HUES = {0, 28, 55, 120, 178, 220, 278};
    private static final String[] STYLE_NAMES =
            {"Standard", "Neon", "Pastel", "Pearlescent", "Metallic"};

    private ColorPicker() {
    }

    static void open(Activity a, String title, int initial, Callback callback) {
        float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 8));

        TextView preview = new TextView(a);
        preview.setText("  88  ");
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(30);
        preview.setPadding(0, dp(a, 14), 0, dp(a, 14));
        box.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        SeekBar hue = new SeekBar(a);
        hue.setMax(360);
        hue.setProgress(Math.round(hsv[0]));
        SeekBar sat = new SeekBar(a);
        sat.setMax(100);
        sat.setProgress(Math.round(hsv[1] * 100));
        SeekBar val = new SeekBar(a);
        val.setMax(100);
        val.setProgress(Math.round(hsv[2] * 100));

        Runnable paint = () -> {
            int color = Color.HSVToColor(new float[]{
                    hue.getProgress(), sat.getProgress() / 100f, val.getProgress() / 100f});
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color);
            bg.setCornerRadius(dp(a, 10));
            preview.setBackground(bg);
            int luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
            preview.setTextColor(luma > 145 ? 0xFF10161F : 0xFFFFFFFF);
        };

        addSlider(a, box, "Оттенок", hue);
        addSlider(a, box, "Насыщенность", sat);
        addSlider(a, box, "Яркость", val);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { paint.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        };
        hue.setOnSeekBarChangeListener(listener);
        sat.setOnSeekBarChangeListener(listener);
        val.setOnSeekBarChangeListener(listener);

        Button presets = new Button(a);
        presets.setText("Готовые цвета: 7 групп × 5 стилей × 15 оттенков");
        presets.setAllCaps(false);
        presets.setOnClickListener(view -> showPresets(a, hue, sat, val, paint));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 12);
        box.addView(presets, lp);

        paint.run();

        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);

        new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d, w) -> callback.onColor(Color.HSVToColor(new float[]{
                        hue.getProgress(), sat.getProgress() / 100f, val.getProgress() / 100f})))
                .show();
    }

    private static void showPresets(Activity a, SeekBar hue, SeekBar sat, SeekBar val, Runnable paint) {
        final int[] group = {0};
        final int[] style = {0};

        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));

        content.addView(label(a, "Цветовая группа"));
        GridLayout groups = new GridLayout(a);
        groups.setColumnCount(7);
        content.addView(groups);

        content.addView(label(a, "Стиль"));
        LinearLayout styles = new LinearLayout(a);
        styles.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(styles, new LinearLayout.LayoutParams(-1, dp(a, 40)));

        content.addView(label(a, "Оттенки: сверху тёмные, снизу светлые"));
        GridLayout shades = new GridLayout(a);
        shades.setColumnCount(7);
        ScrollView shadeScroll = new ScrollView(a);
        shadeScroll.addView(shades);
        content.addView(shadeScroll, new LinearLayout.LayoutParams(-1, dp(a, 330)));

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Готовые цвета")
                .setView(content)
                .setNegativeButton("Закрыть", null)
                .create();

        final Runnable[] drawGroups = new Runnable[1];
        final Runnable[] drawStyles = new Runnable[1];
        final Runnable[] drawShades = new Runnable[1];

        drawGroups[0] = () -> {
            groups.removeAllViews();
            for (int i = 0; i < 7; i++) {
                final int index = i;
                View chip = new View(a);
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Color.HSVToColor(new float[]{GROUP_HUES[i], 0.88f, 0.92f}));
                shape.setCornerRadius(dp(a, 6));
                shape.setStroke(dp(a, i == group[0] ? 3 : 1),
                        i == group[0] ? 0xFF35D9FF : 0x66889099);
                chip.setBackground(shape);
                chip.setContentDescription(GROUP_NAMES[i]);
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = 0;
                glp.height = dp(a, 38);
                glp.columnSpec = GridLayout.spec(i, 1f);
                glp.setMargins(dp(a, 2), dp(a, 3), dp(a, 2), dp(a, 3));
                groups.addView(chip, glp);
                chip.setOnClickListener(view -> {
                    group[0] = index;
                    drawGroups[0].run();
                    drawShades[0].run();
                });
            }
        };

        drawStyles[0] = () -> {
            styles.removeAllViews();
            for (int i = 0; i < STYLE_NAMES.length; i++) {
                final int index = i;
                Button b = new Button(a);
                b.setText(STYLE_NAMES[i]);
                b.setAllCaps(false);
                b.setTextSize(9);
                b.setMinWidth(0);
                b.setMinimumWidth(0);
                b.setPadding(0, 0, 0, 0);
                b.setTextColor(0xFFE8EEF7);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(a, 8));
                bg.setColor(i == style[0] ? 0xFF1D3D4D : 0xFF1B2330);
                bg.setStroke(dp(a, 1), i == style[0] ? 0xFF35D9FF : 0xFF2A3547);
                b.setBackground(bg);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(a, 34), 1f);
                blp.setMargins(dp(a, 1), dp(a, 2), dp(a, 1), dp(a, 2));
                styles.addView(b, blp);
                b.setOnClickListener(view -> {
                    style[0] = index;
                    drawStyles[0].run();
                    drawShades[0].run();
                });
            }
        };

        drawShades[0] = () -> {
            shades.removeAllViews();
            for (int row = 0; row < 15; row++) {
                for (int col = 0; col < 7; col++) {
                    float hueDeg = GROUP_HUES[group[0]];
                    float saturation;
                    float brightness;
                    switch (style[0]) {
                        case 1:
                            saturation = 0.82f + col * 0.03f;
                            brightness = 0.20f + 0.80f * row / 14f;
                            break;
                        case 2:
                            saturation = 0.18f + col * 0.045f;
                            brightness = 0.58f + 0.42f * row / 14f;
                            break;
                        case 3:
                            saturation = 0.12f + col * 0.065f;
                            brightness = 0.48f + 0.52f * row / 14f;
                            hueDeg = (hueDeg + (col - 3) * 1.7f + 360f) % 360f;
                            break;
                        case 4:
                            saturation = 0.08f + col * 0.075f;
                            brightness = 0.18f + 0.82f * row / 14f;
                            break;
                        default:
                            saturation = 0.30f + col * 0.115f;
                            brightness = 0.16f + 0.84f * row / 14f;
                            break;
                    }
                    final int color = Color.HSVToColor(new float[]{
                            hueDeg, Math.min(1f, saturation), Math.min(1f, brightness)});

                    View chip = new View(a);
                    GradientDrawable shape = new GradientDrawable();
                    shape.setColor(color);
                    shape.setCornerRadius(dp(a, 4));
                    shape.setStroke(dp(a, 1), 0x55889099);
                    chip.setBackground(shape);
                    GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                    glp.width = 0;
                    glp.height = dp(a, 30);
                    glp.columnSpec = GridLayout.spec(col, 1f);
                    glp.setMargins(dp(a, 2), dp(a, 2), dp(a, 2), dp(a, 2));
                    shades.addView(chip, glp);

                    chip.setOnClickListener(view -> {
                        float[] picked = new float[3];
                        Color.colorToHSV(color, picked);
                        hue.setProgress(Math.round(picked[0]));
                        sat.setProgress(Math.round(picked[1] * 100));
                        val.setProgress(Math.round(picked[2] * 100));
                        paint.run();
                        dialog.dismiss();
                    });
                }
            }
        };

        drawGroups[0].run();
        drawStyles[0].run();
        drawShades[0].run();
        dialog.show();
    }

    private static TextView label(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(0xFF93A3B8);
        t.setPadding(0, dp(a, 8), 0, dp(a, 4));
        return t;
    }

    private static void addSlider(Activity a, LinearLayout box, String name, SeekBar bar) {
        box.addView(label(a, name));
        box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    private static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}