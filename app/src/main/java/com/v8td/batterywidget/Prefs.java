package com.v8td.batterywidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/** Единственное хранилище настроек приложения. */
final class Prefs {

    static final String FILE = "q_battery_prefs";
    static final int BANDS = 10;
    static final int DEFAULT_THRESHOLD = 100;
    static final int CHARGING_COLOR = 0xFF35D9FF;

    static final String[] PALETTE_NAMES = {
            "Standard", "Neon", "Pastel", "Pearlescent", "Metallic", "Пользовательская"
    };

    /** Порядок: от 91–100% вниз до 0–10%. */
    static final int[][] PALETTES = {
            {0xFF2ECC71, 0xFF57D06A, 0xFF8BD450, 0xFFC9D63F, 0xFFFFD23F,
             0xFFFFB03A, 0xFFFF8A34, 0xFFFF6B35, 0xFFF0453A, 0xFFD32F2F},
            {0xFF00E676, 0xFF39FF14, 0xFF76FF03, 0xFFC6FF00, 0xFFFFEA00,
             0xFFFFC400, 0xFFFF9100, 0xFFFF3D00, 0xFFFF1744, 0xFFD500F9},
            {0xFFA5D6A7, 0xFFB9E0A5, 0xFFD7E8A8, 0xFFF3E9A9, 0xFFFFE0B2,
             0xFFFFCCBC, 0xFFFFAB91, 0xFFEF9A9A, 0xFFE5989B, 0xFFCE93D8},
            {0xFF9FE8C8, 0xFF9FE0E8, 0xFFA8D2F0, 0xFFB9C6F2, 0xFFCDBBEF,
             0xFFE3B7E4, 0xFFF0B9CE, 0xFFF2C3B4, 0xFFEED9AE, 0xFFCFE6B4},
            {0xFFB8C6C2, 0xFFA9B7B0, 0xFFC0B49A, 0xFFD4AF7A, 0xFFCD9B62,
             0xFFB98A5E, 0xFFA87C63, 0xFF9E6B63, 0xFF8E6A72, 0xFF7E6A80}
    };

    static final String[] BAND_NAMES = {
            "91–100%", "81–90%", "71–80%", "61–70%", "51–60%",
            "41–50%", "31–40%", "21–30%", "11–20%", "0–10%"
    };

    private static SharedPreferences sp;
    private static volatile int revision = 0;

    private Prefs() {
    }

    static void init(Context c) {
        if (sp == null) {
            sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        }
    }

    static SharedPreferences get(Context c) {
        init(c);
        return sp;
    }

    static int revision() {
        return revision;
    }

    private static void bump() {
        revision++;
    }

    static int userPaletteIndex() {
        return PALETTES.length;
    }

    static int palette(Context c) {
        int p = get(c).getInt("palette", 0);
        return Math.max(0, Math.min(userPaletteIndex(), p));
    }

    static void setPalette(Context c, int index) {
        SharedPreferences p = get(c);
        if (index == userPaletteIndex() && palette(c) != userPaletteIndex()) {
            SharedPreferences.Editor e = p.edit();
            for (int i = 0; i < BANDS; i++) e.putInt("user_" + i, color(c, i));
            e.putInt("palette", index);
            e.apply();
        } else {
            putInt(p, "palette", index);
        }
        bump();
    }

    static int color(Context c, int band) {
        SharedPreferences p = get(c);
        int idx = Math.max(0, Math.min(BANDS - 1, band));
        if (palette(c) == userPaletteIndex()) {
            return p.getInt("user_" + idx, PALETTES[0][idx]);
        }
        return PALETTES[palette(c)][idx];
    }

    static void setUserColor(Context c, int band, int color) {
        SharedPreferences p = get(c);
        int idx = Math.max(0, Math.min(BANDS - 1, band));
        if (palette(c) != userPaletteIndex()) {
            SharedPreferences.Editor e = p.edit();
            for (int i = 0; i < BANDS; i++) e.putInt("user_" + i, color(c, i));
            e.putInt("palette", userPaletteIndex());
            e.putInt("user_" + idx, color);
            e.apply();
        } else {
            putInt(p, "user_" + idx, color);
        }
        bump();
    }

    static void resetBandToDefault(Context c, int band) {
        setUserColor(c, band, PALETTES[0][Math.max(0, Math.min(BANDS - 1, band))]);
    }

    static boolean soundEnabled(Context c) {
        return get(c).getBoolean("sound_enabled", true);
    }

    static void setSoundEnabled(Context c, boolean v) {
        putBoolean(get(c), "sound_enabled", v);
        bump();
    }

    static int threshold(Context c) {
        int t = get(c).getInt("sound_threshold", DEFAULT_THRESHOLD);
        return Math.max(50, Math.min(100, t));
    }

    static void setThreshold(Context c, int v) {
        putInt(get(c), "sound_threshold", Math.max(50, Math.min(100, v)));
    }

    static String soundUri(Context c) {
        return get(c).getString("sound_uri", null);
    }

    static void setSoundUri(Context c, String v) {
        SharedPreferences p = get(c);
        String old = p.getString("sound_uri", null);
        if (v == null ? old == null : v.equals(old)) return;
        p.edit().putString("sound_uri", v).apply();
    }

    static boolean alertFired(Context c) {
        return get(c).getBoolean("alert_fired", false);
    }

    static void setAlertFired(Context c, boolean v) {
        putBoolean(get(c), "alert_fired", v);
    }

    static int lastLevel(Context c) {
        return get(c).getInt("last_level", -1);
    }

    static void setLastLevel(Context c, int v) {
        putInt(get(c), "last_level", v);
    }

    static boolean lastCharging(Context c) {
        return get(c).getBoolean("last_charging", false);
    }

    static void setLastCharging(Context c, boolean v) {
        putBoolean(get(c), "last_charging", v);
    }

    /** Сбрасывает только настройки этого приложения. Системные разрешения не трогает. */
    static void reset(Context c) {
        get(c).edit().clear().apply();
        bump();
    }

    static boolean hasWidgets(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, BatteryWidgetProvider.class));
            return ids != null && ids.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // Запись только при реальном изменении — лишний раз память не тревожим.
    private static void putInt(SharedPreferences p, String key, int value) {
        if (p.getInt(key, Integer.MIN_VALUE) == value) return;
        p.edit().putInt(key, value).apply();
    }

    private static void putBoolean(SharedPreferences p, String key, boolean value) {
        if (p.contains(key) && p.getBoolean(key, !value) == value) return;
        p.edit().putBoolean(key, value).apply();
    }
}
