package com.v8td.batterywidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

import java.util.HashMap;

/** Единственная точка обновления всех экземпляров виджета. */
final class WidgetUpdater {

    private static final HashMap<Integer, String> LAST = new HashMap<>();
    private static final int FALLBACK_DP = 56;
    private static final int MAX_PX = 512;

    private WidgetUpdater() {
    }

    static synchronized void forget() {
        LAST.clear();
    }

    static synchronized void forget(int widgetId) {
        LAST.remove(widgetId);
    }

    static void refresh(Context context, boolean force) {
        final Context app = context.getApplicationContext();
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, BatteryWidgetProvider.class));
            if (ids == null || ids.length == 0) {
                forget();
                Scheduler.cancel(app);
                return;
            }

            BatteryState state = BatteryReader.read(app);
            if (!state.valid) {
                Scheduler.ensure(app);
                return;
            }

            ChargeAlert.onState(app, state);

            int color = (state.charging && Prefs.chargingBlue(app))
                    ? Prefs.CHARGING_COLOR
                    : Prefs.color(app, BatteryState.band(state.percent));
            int revision = Prefs.revision();

            DisplayMetrics metrics = app.getResources().getDisplayMetrics();
            float density = metrics.density > 0 ? metrics.density : 1f;
            float fontScale = app.getResources().getConfiguration().fontScale;
            if (fontScale <= 0f) fontScale = 1f;

            PendingIntent click = clickIntent(app);

            for (int id : ids) {
                try {
                    int[] wh = geometry(app, manager, id, density);
                    String signature = state.percent + "|" + state.charging + "|" + color
                            + "|" + revision + "|" + wh[0] + "x" + wh[1];
                    if (!force && signature.equals(LAST.get(id))) continue;

                    WidgetTextSizer.Size size = WidgetTextSizer.get(wh[0], wh[1], density, fontScale);
                    float textSize = state.percent == 100 ? size.threeDigits : size.twoDigits;

                    Bitmap bitmap = WidgetRenderer.render(wh[0], wh[1], state.percent, color, textSize);

                    RemoteViews views = new RemoteViews(app.getPackageName(), R.layout.widget_battery);
                    views.setImageViewBitmap(R.id.widget_digits, bitmap);
                    views.setContentDescription(R.id.widget_digits, "Заряд " + state.percent + "%");
                    views.setOnClickPendingIntent(R.id.widget_root, click);
                    manager.updateAppWidget(id, views);

                    LAST.put(id, signature);
                } catch (Throwable ignored) {
                }
            }

            Scheduler.ensure(app);
        } catch (Throwable ignored) {
        }
    }

    /** Берём меньшую из известных сторон — цифры влезут и в портрете, и в ландшафте. */
    private static int[] geometry(Context app, AppWidgetManager manager, int id, float density) {
        int minW = 0, maxW = 0, minH = 0, maxH = 0;
        try {
            Bundle o = manager.getAppWidgetOptions(id);
            if (o != null) {
                minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
                minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
                maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            }
        } catch (Throwable ignored) {
        }

        int wDp = pick(minW, maxW);
        int hDp = pick(minH, maxH);

        int w = Math.round(wDp * density);
        int h = Math.round(hDp * density);
        w = Math.max(24, Math.min(MAX_PX, w));
        h = Math.max(24, Math.min(MAX_PX, h));
        return new int[]{w, h};
    }

    private static int pick(int a, int b) {
        if (a > 0 && b > 0) return Math.min(a, b);
        if (a > 0) return a;
        if (b > 0) return b;
        return FALLBACK_DP;
    }

    private static PendingIntent clickIntent(Context app) {
        Intent intent = new Intent(app, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(app, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
