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
            "Стандарт", "Неон", "Пастель", "Карамель", "Особая"
    };

    /** Заводские цвета. Порядок: от 91–100% вниз до 0–10%. */
    static final int[][] PALETTES = {
            {0xFF2ECC71, 0xFF57D06A, 0xFF8BD450, 0xFFC9D63F, 0xFFFFD23F,
             0xFFFFB03A, 0xFFFF8A34, 0xFFFF6B35, 0xFFF0453A, 0xFFD32F2F},
            {0xFF2EFF2E, 0xFF27C927, 0xFF76FF03, 0xFFC6FF00, 0xFFFFEA00,
             0xFFFFC400, 0xFFFF9100, 0xFFFF3D00, 0xFFFF1744, 0xFFC41212},
            {0xFFA5D6A7, 0xFFB9E0A5, 0xFFD7E8A8, 0xFFF3E9A9, 0xFFFFE0B2,
             0xFFFFCCBC, 0xFFFFAB91, 0xFFEF9A9A, 0xFFE5989B, 0xFFCE93D8},
            {0xFF00806A, 0xFF1B8A3A, 0xFF4C8C1E, 0xFF7D8A0A, 0xFF9A7B00,
             0xFFB26A00, 0xFFC05500, 0xFFC62828, 0xFFA01020, 0xFF7A0A18}
    };

    static final String[] BAND_NAMES = {
            "91–100%", "81–90%", "71–80%", "61–70%", "51–60%",
            "41–50%", "31–40%", "21–30%", "11–20%", "0–10%"
    };

    private static final String MIGRATED = "palettes_v2";

    private static SharedPreferences sp;
    private static volatile int revision = 0;

    private Prefs() {
    }

    static synchronized void init(Context c) {
        if (sp == null) {
            sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
            migrate(sp);
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

    /** Всего схем: четыре заводские плюс Особая. */
    static int paletteCount() {
        return PALETTES.length + 1;
    }

    /** Индекс Особой схемы. Имя сохранено для совместимости. */
    static int userPaletteIndex() {
        return PALETTES.length;
    }

    static int palette(Context c) {
        int p = get(c).getInt("palette", 0);
        return Math.max(0, Math.min(paletteCount() - 1, p));
    }

    /** Только переключает активную схему. Ничего никуда не копирует. */
    static void setPalette(Context c, int index) {
        int idx = Math.max(0, Math.min(paletteCount() - 1, index));
        putInt(get(c), "palette", idx);
        bump();
    }

    /** Цвет диапазона активной схемы. */
    static int color(Context c, int band) {
        return colorOf(c, palette(c), band);
    }

    /** Цвет диапазона любой схемы. */
    static int colorOf(Context c, int paletteIndex, int band) {
        int pal = clampPalette(paletteIndex);
        int idx = clampBand(band);
        return get(c).getInt(key(pal, idx), factory(pal, idx));
    }

    /** Записывает цвет в указанную схему. */
    static void setColor(Context c, int paletteIndex, int band, int color) {
        int pal = clampPalette(paletteIndex);
        int idx = clampBand(band);
        putInt(get(c), key(pal, idx), color);
        bump();
    }

    /** Правит активную схему. Старое имя метода сохранено. */
    static void setUserColor(Context c, int band, int color) {
        setColor(c, palette(c), band, color);
    }

    /** Отличается ли схема от заводского состояния. */
    static boolean isModified(Context c, int paletteIndex) {
        int pal = clampPalette(paletteIndex);
        for (int b = 0; b < BANDS; b++) {
            if (colorOf(c, pal, b) != factory(pal, b)) return true;
        }
        return false;
    }

    /** Возвращает схему к заводским цветам. Для Особой заводское = Стандарт. */
    static void resetPalette(Context c, int paletteIndex) {
        int pal = clampPalette(paletteIndex);
        SharedPreferences.Editor e = get(c).edit();
        for (int b = 0; b < BANDS; b++) e.putInt(key(pal, b), factory(pal, b));
        e.apply();
        bump();
    }

    /** Возвращает заводской цвет одного диапазона. */
    static void resetBand(Context c, int paletteIndex, int band) {
        int pal = clampPalette(paletteIndex);
        int idx = clampBand(band);
        putInt(get(c), key(pal, idx), factory(pal, idx));
        bump();
    }

    /** Заводской цвет диапазона указанной схемы. */
    static int factory(int paletteIndex, int band) {
        int pal = clampPalette(paletteIndex);
        int idx = clampBand(band);
        return pal < PALETTES.length ? PALETTES[pal][idx] : PALETTES[0][idx];
    }

    private static int clampPalette(int p) {
        return Math.max(0, Math.min(paletteCount() - 1, p));
    }

    private static int clampBand(int b) {
        return Math.max(0, Math.min(BANDS - 1, b));
    }

    private static String key(int pal, int band) {
        return "pal_" + pal + "_" + band;
    }

    /** Перенос настроек на новое хранение. Один раз, старые ключи не удаляются. */
    private static void migrate(SharedPreferences p) {
        if (p.getBoolean(MIGRATED, false)) return;
        SharedPreferences.Editor e = p.edit();
        for (int pal = 0; pal < PALETTES.length; pal++) {
            for (int b = 0; b < BANDS; b++) e.putInt(key(pal, b), PALETTES[pal][b]);
        }
        for (int b = 0; b < BANDS; b++) {
            e.putInt(key(userPaletteIndex(), b), p.getInt("user_" + b, PALETTES[0][b]));
        }
        e.putBoolean(MIGRATED, true);
        e.apply();
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
        SharedPreferences.Editor e = get(c).edit();
        e.clear();
        for (int pal = 0; pal < paletteCount(); pal++) {
            for (int b = 0; b < BANDS; b++) e.putInt(key(pal, b), factory(pal, b));
        }
        e.putBoolean(MIGRATED, true);
        e.apply();
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

    private static void putInt(SharedPreferences p, String key, int value) {
        if (p.getInt(key, Integer.MIN_VALUE) == value) return;
        p.edit().putInt(key, value).apply();
    }

    private static void putBoolean(SharedPreferences p, String key, boolean value) {
        if (p.contains(key) && p.getBoolean(key, !value) == value) return;
        p.edit().putBoolean(key, value).apply();
    }
}
