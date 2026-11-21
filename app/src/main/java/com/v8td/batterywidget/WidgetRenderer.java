package com.v8td.batterywidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Рисует цифры картинкой: фон прозрачный, центрирование по реальным метрикам. */
final class WidgetRenderer {

    private WidgetRenderer() {
    }

    static Bitmap render(int widthPx, int heightPx, int percent, int color, float textSizePx) {
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
        float x = (w - paint.measureText(text)) / 2f;
        float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, x, baseline, paint);
        return bitmap;
    }
}
