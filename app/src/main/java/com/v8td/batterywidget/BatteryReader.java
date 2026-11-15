package com.v8td.batterywidget;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/** Читает текущее состояние аккумулятора без опроса и без службы в памяти. */
final class BatteryReader {

    private BatteryReader() {
    }

    static BatteryState read(Context context) {
        Intent intent = null;
        try {
            intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Throwable ignored) {
        }

        if (intent != null) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            boolean present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);

            if (present && level >= 0 && scale > 0) {
                int percent = Math.round(level * 100f / scale);
                percent = Math.max(0, Math.min(100, percent));
                boolean charging = plugged != 0
                        || status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                return new BatteryState(true, percent, charging, status);
            }
        }

        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (cap >= 0 && cap <= 100) {
                    return new BatteryState(true, cap, bm.isCharging(),
                            BatteryManager.BATTERY_STATUS_UNKNOWN);
                }
            }
        } catch (Throwable ignored) {
        }

        // Данных нет — виджет сохраняет предыдущую картинку, ничего не выдумываем.
        return new BatteryState(false, 0, false, BatteryManager.BATTERY_STATUS_UNKNOWN);
    }
}
