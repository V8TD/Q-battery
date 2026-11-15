package com.v8td.batterywidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class BatteryWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context app = context.getApplicationContext();
        final String action = intent.getAction();
        final int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        App.exec(() -> {
            try {
                if (AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(action)) {
                    WidgetUpdater.forget();
                    Scheduler.cancel(app);
                } else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
                    WidgetUpdater.forget(id);
                    WidgetUpdater.refresh(app, true);
                } else {
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetUpdater.forget(id);
                    WidgetUpdater.refresh(app, true);
                }
            } catch (Throwable ignored) {
            } finally {
                App.watch(app);
                result.finish();
            }
        });
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetUpdater.refresh(context, true);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int id, Bundle newOptions) {
        WidgetUpdater.forget(id);
        WidgetUpdater.refresh(context, true);
    }

    @Override
    public void onEnabled(Context context) {
        Scheduler.ensure(context);
        WidgetUpdater.refresh(context, true);
    }

    @Override
    public void onDisabled(Context context) {
        Scheduler.cancel(context);
        WidgetUpdater.forget();
    }
}
