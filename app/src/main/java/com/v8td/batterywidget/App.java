package com.v8td.batterywidget;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Единая рабочая очередь и короткоживущий приёмник изменений заряда. */
public final class App extends Application {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "q-battery-worker");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long WATCH_WINDOW_MS = 90_000L;

    private static Context appContext;
    private static BroadcastReceiver watcher;

    private static final Runnable STOP_WATCH = App::stopWatching;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        Prefs.init(appContext);
    }

    /** Все обновления идут строго по одному, конфликтов не бывает. */
    public static void exec(Runnable task) {
        try {
            WORKER.execute(task);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Пока процесс жив, слушаем системные сообщения о заряде — это не опрос,
     * система сообщает сама. Через 90 секунд тишины приёмник снимается.
     * Никаких пробуждений устройства это не создаёт.
     */
    public static synchronized void watch(Context context) {
        final Context app = context.getApplicationContext();
        if (watcher == null) {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    exec(() -> WidgetUpdater.refresh(app, false));
                    MAIN.removeCallbacks(STOP_WATCH);
                    MAIN.postDelayed(STOP_WATCH, WATCH_WINDOW_MS);
                }
            };
            try {
                app.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                watcher = receiver;
            } catch (Throwable t) {
                watcher = null;
                return;
            }
        }
        MAIN.removeCallbacks(STOP_WATCH);
        MAIN.postDelayed(STOP_WATCH, WATCH_WINDOW_MS);
    }

    private static synchronized void stopWatching() {
        if (watcher == null || appContext == null) return;
        try {
            appContext.unregisterReceiver(watcher);
        } catch (Throwable ignored) {
        }
        watcher = null;
    }
}
