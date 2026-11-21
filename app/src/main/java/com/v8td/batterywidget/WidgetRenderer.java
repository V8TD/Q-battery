package com.v8td.batterywidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Рисует виджет картинкой: прозрачный фон, цифры по реальным метрикам,
 *  значки («%» и молния) и оформление цифр (тень или тонкий контур).
 *  Молния — векторная фигура, одинаковая на всех устройствах. */
final class WidgetRenderer {

    /** Доля стороны под угловой значок и под значок по центру. */
    private static final float ICON_CORNER = 0.16f;
    private static final float ICON_CENTER = 0.18f;
    private static final float ICON_PAD = 0.02f;

    /** Молния в единичном квадрате: начало координат сверху слева. */
    private static final Path BOLT = new Path();

    static {
        BOLT.moveTo(0.62f, 0.00f);
        BOLT.lineTo(0.10f, 0.56f);
        BOLT.lineTo(0.42f, 0.56f);
        BOLT.lineTo(0.36f, 1.00f);
        BOLT.lineTo(0.90f, 0.40f);
        BOLT.lineTo(0.56f, 0.40f);
        BOLT.close();
    }

    private WidgetRenderer() {
    }

    /** Сколько пикселей резервируем сверху и снизу под значки выбранного режима.
     *  Зарядка на резерв не влияет: цифры не прыгают и не уменьшаются. */
    static int[] reserve(int mode, int widthPx, int heightPx) {
        int side = Math.max(1, Math.min(widthPx, heightPx));
        int corner = Math.round(side * (ICON_CORNER + ICON_PAD * 2f));
        int center = Math.round(side * (ICON_CENTER + ICON_PAD * 2f));
        switch (mode) {
            case 1:
            case 3:
                return new int[]{corner, 0};
            case 2:
            case 4:
                return new int[]{0, center};
            case 5:
                return new int[]{corner, center};
            case 6:
                return new int[]{corner, center};
            default:
                return new int[]{0, 0};
        }
    }

    static Bitmap render(int widthPx, int heightPx, int percent, int color, float textSizePx,
                         boolean charging, int mode, int outline) {
        return render(widthPx, heightPx, String.valueOf(percent), color, textSizePx,
                charging, mode, outline);
    }

    /** Тот же рисунок, но с произвольным текстом — используется для превью в настройках. */
    static Bitmap render(int widthPx, int heightPx, String text, int color, float textSizePx,
                         boolean charging, int mode, int outline) {
        int w = Math.max(1, widthPx);
        int h = Math.max(1, heightPx);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0x00000000);
        Canvas canvas = new Canvas(bitmap);

        int m = Math.max(0, Math.min(Prefs.MODES - 1, mode));
        int[] res = reserve(m, w, h);
        int side = Math.min(w, h);
        boolean corner = m == 1 || m == 3 || m == 5 || m == 6;
        float iconSize = side * (corner ? ICON_CORNER : ICON_CENTER);
        float pad = side * ICON_PAD;

        // Молния позади цифр: большая, полупрозрачная, без оформления.
        if (m == 7 && charging) {
            Paint behind = new Paint(Paint.ANTI_ALIAS_FLAG);
            behind.setColor(color);
            behind.setAlpha(70);
            behind.setStyle(Paint.Style.FILL);
            float bs = side * 0.86f;
            canvas.save();
            canvas.translate((w - bs) / 2f, (h - bs) / 2f);
            canvas.scale(bs, bs);
            canvas.drawPath(BOLT, behind);
            canvas.restore();
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(WidgetTextSizer.font());
        paint.setTextSize(Math.max(6f, textSizePx));
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.LEFT);

        int areaTop = res[0];
        int areaH = Math.max(1, h - res[0] - res[1]);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float x = (w - paint.measureText(text)) / 2f;
        float baseline = areaTop + areaH / 2f - (fm.ascent + fm.descent) / 2f;

        if (outline == 1) {
            paint.setShadowLayer(Math.max(2f, textSizePx * 0.09f), 0f, 0f, 0xCC000000);
            canvas.drawText(text, x, baseline, paint);
            paint.clearShadowLayer();
        } else if (outline == 2) {
            canvas.drawText(text, x, baseline, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, textSizePx * 0.035f));
            paint.setColor(0xE6000000);
            canvas.drawText(text, x, baseline, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        } else {
            canvas.drawText(text, x, baseline, paint);
        }

        if (m != 0 && m != 7) {
            drawIcon(canvas, paint, m, charging, color, outline, w, h, iconSize, pad, res);
        }
        return bitmap;
    }

    private static void drawIcon(Canvas canvas, Paint paint, int mode, boolean charging,
                                 int color, int outline, int w, int h,
                                 float size, float pad, int[] res) {
        boolean top;
        boolean centered;
        boolean bolt;
        switch (mode) {
            case 1:
                top = true;
                centered = false;
                bolt = true;
                break;
            case 2:
                top = false;
                centered = true;
                bolt = true;
                break;
            case 3:
                top = true;
                centered = false;
                bolt = false;
                break;
            case 4:
                top = false;
                centered = true;
                bolt = false;
                break;
            case 5:
                top = false;
                centered = true;
                bolt = true;
                break;
            case 6:
                top = true;
                centered = false;
                bolt = true;
                break;
            default:
                return;
        }
        // «%» виден всегда, молния — только при зарядке.
        if (bolt && !charging) return;

        float left = centered ? (w - size) / 2f : w - pad - size;
        float boxTop = top ? pad : h - res[1] + (res[1] - size) / 2f;

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        if (outline == 1) {
            paint.setShadowLayer(Math.max(1.5f, size * 0.14f), 0f, 0f, 0xCC000000);
        }
        if (bolt) {
            drawBolt(canvas, paint, left, boxTop, size, outline);
        } else {
            drawPercent(canvas, paint, left, boxTop, size);
        }
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private static void drawBolt(Canvas canvas, Paint paint, float left, float top,
                                 float size, int outline) {
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(size, size);
        canvas.drawPath(BOLT, paint);
        if (outline == 2) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.06f);
            paint.setColor(0xE6000000);
            canvas.drawPath(BOLT, paint);
        }
        canvas.restore();
    }

    private static void drawPercent(Canvas canvas, Paint paint, float left, float top, float size) {
        float old = paint.getTextSize();
        paint.setTextSize(size * 1.15f);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float tw = paint.measureText("%");
        float x = left + (size - tw) / 2f;
        float baseline = top + size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("%", x, baseline, paint);
        paint.setTextSize(old);
    }
}
