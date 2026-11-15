package com.v8td.batterywidget;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Единая рабочая очередь и приёмник системных событий, пока жив процесс. */
public final class App extends Application {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "q-battery-worker");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static BroadcastReceiver watcher;
    private static volatile String lastSignal = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Context app = getApplicationContext();
        Prefs.init(app);
        if (Prefs.hasWidgets(app)) watch(app);
    }

    public static void exec(Runnable task) {
        try {
            WORKER.execute(task);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Слушаем заряд и состояние экрана. Это не опрос: система сообщает сама.
     * Погас экран — страховочные проверки снимаются. Зажёгся — обновляем сразу.
     */
    public static synchronized void watch(Context context) {
        final Context app = context.getApplicationContext();
        if (watcher != null) return;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();

                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Scheduler.cancelTick(app);
                    return;
                }

                if (Intent.ACTION_SCREEN_ON.equals(action)
                        || Intent.ACTION_USER_PRESENT.equals(action)) {
                    exec(() -> {
                        WidgetUpdater.refresh(app, true);
                        Scheduler.ensure(app);
                    });
                    return;
                }

                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);

                String signal = level + "/" + scale + "/" + plugged + "/" + status;
                if (signal.equals(lastSignal)) return;
                lastSignal = signal;

                exec(() -> WidgetUpdater.refresh(app, false));
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        try {
            app.registerReceiver(receiver, filter);
            watcher = receiver;
        } catch (Throwable t) {
            watcher = null;
        }
    }
}
