package com.v8td.batterywidget;

import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Единый размер цифр для всего диапазона 0–99 и отдельный размер для 100.
 * Ширина измеряется реально, по всем ста комбинациям, высота — по FontMetrics.
 */
final class WidgetTextSizer {

    static final class Size {
        final float twoDigits;
        final float threeDigits;

        Size(float twoDigits, float threeDigits) {
            this.twoDigits = twoDigits;
            this.threeDigits = threeDigits;
        }
    }

    private static final Typeface FONT = Typeface.create("sans-serif", Typeface.BOLD);
    private static final String[] ALL_00_99 = new String[100];
    private static final String[] ONLY_100 = {"100"};

    static {
        for (int i = 0; i < 100; i++) ALL_00_99[i] = String.valueOf(i);
    }

    private static final LinkedHashMap<String, Size> CACHE =
            new LinkedHashMap<String, Size>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Size> eldest) {
                    return size() > 24;
                }
            };

    private WidgetTextSizer() {
    }

    static Typeface font() {
        return FONT;
    }

    static synchronized Size get(int widthPx, int heightPx, float density, float fontScale) {
        String key = widthPx + "x" + heightPx + "|" + density + "|" + fontScale;
        Size cached = CACHE.get(key);
        if (cached != null) return cached;

        int pad = Math.max(Math.round(2f * density),
                Math.round(Math.min(widthPx, heightPx) * 0.04f));
        int availW = Math.max(4, widthPx - pad * 2);
        int availH = Math.max(4, heightPx - pad * 2);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(FONT);

        float two = fit(paint, ALL_00_99, availW, availH);
        float three = fit(paint, ONLY_100, availW, availH);

        Size size = new Size(two, three);
        CACHE.put(key, size);
        return size;
    }

    private static float fit(Paint paint, String[] samples, int availW, int availH) {
        final float reference = 100f;
        paint.setTextSize(reference);

        float maxWidth = 0f;
        for (String s : samples) maxWidth = Math.max(maxWidth, paint.measureText(s));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float height = fm.descent - fm.ascent;
        if (maxWidth <= 0f || height <= 0f) return 8f;

        float size = Math.min(availW / maxWidth * reference, availH / height * reference);
        size = Math.max(6f, (float) Math.floor(size));

        // Контрольная проверка реальными метриками и точная подгонка вниз.
        for (int i = 0; i < 400 && size > 6f; i++) {
            paint.setTextSize(size);
            float w = 0f;
            for (String s : samples) w = Math.max(w, paint.measureText(s));
            Paint.FontMetrics f = paint.getFontMetrics();
            if (w <= availW && (f.descent - f.ascent) <= availH) break;
            size -= 1f;
        }
        return Math.max(6f, size);
    }
}
