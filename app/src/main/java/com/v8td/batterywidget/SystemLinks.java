package com.v8td.batterywidget;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

/** Только безопасные переходы: сначала проверяем, что раздел существует. */
final class SystemLinks {

    private SystemLinks() {
    }

    static void openAutostart(Activity a) {
        String[][] targets = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"}
        };
        for (String[] t : targets) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(t[0], t[1]));
            if (start(a, intent)) return;
        }
        Toast.makeText(a, "Отдельный раздел автозапуска не найден. Откройте: Безопасность → Разрешения → Автозапуск.",
                Toast.LENGTH_LONG).show();
        openAppDetails(a);
    }

    static void openBatterySettings(Activity a) {
        if (start(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return;
        if (start(a, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) return;
        openAppDetails(a);
    }

    static void openAppDetails(Activity a) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName()));
        if (start(a, intent)) return;
        start(a, new Intent(Settings.ACTION_SETTINGS));
    }

    private static boolean start(Activity a, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (a.getPackageManager().resolveActivity(intent, 0) == null) return false;
            a.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
