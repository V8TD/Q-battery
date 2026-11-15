package com.v8td.batterywidget;

/** Снимок состояния аккумулятора. */
final class BatteryState {

    final boolean valid;
    final int percent;
    final boolean charging;
    final int status;

    BatteryState(boolean valid, int percent, boolean charging, int status) {
        this.valid = valid;
        this.percent = percent;
        this.charging = charging;
        this.status = status;
    }

    static int band(int percent) {
        if (percent >= 91) return 0;
        if (percent >= 81) return 1;
        if (percent >= 71) return 2;
        if (percent >= 61) return 3;
        if (percent >= 51) return 4;
        if (percent >= 41) return 5;
        if (percent >= 31) return 6;
        if (percent >= 21) return 7;
        if (percent >= 11) return 8;
        return 9;
    }
}
