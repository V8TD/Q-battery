package com.v8td.batterywidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.Random;

/** Рисует цифры картинкой: фон прозрачный, центрирование по реальным метрикам.
 *  В режиме фулл-хаоса каждая цифра получает свой случайный яркий цвет. */
final class WidgetRenderer {

    private WidgetRenderer() {
    }

    /** Случайный яркий цвет: не пропадает ни на тёмном, ни на светлом фоне. */
    static int randomBright(Random rnd) {
        return Color.HSVToColor(new float[]{
                rnd.nextFloat() * 360f,
                0.70f + rnd.nextFloat() * 0.30f,
                0.75f + rnd.nextFloat() * 0.25f});
    }

    static Bitmap render(int widthPx, int heightPx, int percent, int color, float textSizePx) {
        return render(widthPx, heightPx, percent, color, textSizePx, false);
    }

    static Bitmap render(int widthPx, int heightPx, int percent, int color,
                         float textSizePx, boolean fullChaos) {
        int w = Math.max(1, widthPx);
        int h = Math.max(1, heightPx);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0x00000000);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(WidgetTextSizer.font());
        paint.setTextSize(textSizePx);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.LEFT);

        String text = String.valueOf(percent);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;

        if (!fullChaos) {
            float x = (w - paint.measureText(text)) / 2f;
            canvas.drawText(text, x, baseline, paint);
            return bitmap;
        }

        // Фулл хаос: каждая цифра своим цветом, совпадения допустимы.
        Random rnd = new Random();
        int n = text.length();
        float[] widths = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            widths[i] = paint.measureText(text, i, i + 1);
            total += widths[i];
        }
        float x = (w - total) / 2f;
        for (int i = 0; i < n; i++) {
            paint.setColor(randomBright(rnd));
            canvas.drawText(text, i, i + 1, x, baseline, paint);
            x += widths[i];
        }
        return bitmap;
    }
}
