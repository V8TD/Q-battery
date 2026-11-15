package com.v8td.batterywidget;

import android.content.Context;

/**
 * Сигнал только при пересечении порога снизу вверх на зарядке.
 * Задним числом не играет: первая точка сессии просто запоминается.
 */
final class ChargeAlert {

    private ChargeAlert() {
    }

    static void onState(Context context, BatteryState state) {
        if (!state.valid) return;

        final int threshold = Prefs.threshold(context);
        final int prevLevel = Prefs.lastLevel(context);
        final boolean prevCharging = Prefs.lastCharging(context);
        boolean fired = Prefs.alertFired(context);
        boolean play = false;

        if (!state.charging) {
            fired = false;
        } else {
            if (prevLevel < 0 || !prevCharging) {
                fired = state.percent >= threshold;
            } else if (!fired && prevLevel < threshold && state.percent >= threshold) {
                play = true;
                fired = true;
            }
            if (state.percent < threshold) fired = false;
        }

        Prefs.setLastLevel(context, state.percent);
        Prefs.setLastCharging(context, state.charging);
        Prefs.setAlertFired(context, fired);

        if (play && Prefs.soundEnabled(context)) {
            SoundPlayer.play(context);
        }
    }
}
