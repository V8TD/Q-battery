package com.v8td.batterywidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Загрузка телефона, обновление приложения, питание, критический заряд. */
public final class SystemReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context app = context.getApplicationContext();
        App.exec(() -> {
            try {
                WidgetUpdater.refresh(app, true);
            } catch (Throwable ignored) {
            } finally {
                App.watch(app);
                result.finish();
            }
        });
    }
}
