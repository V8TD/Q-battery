package com.v8td.batterywidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;

/**
 * Страховка работает только при включённом экране.
 * При погашенном экране проверок нет вовсе.
 * Редкая задача раз в 15 минут нужна лишь для оживления приёмника,
 * если оболочка выгрузила приложение из памяти.
 */
final class Scheduler {

    static final int JOB_TICK = 8802;
    private static final int JOB_PERIODIC = 8801;

    private static final long PERIOD_MS = 15L * 60L * 1000L;
    private static final long TICK_MS = 30_000L;

    private Scheduler() {
    }

    static void ensure(Context context) {
        Context app = context.getApplicationContext();
        try {
            JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;

            if (!Prefs.hasWidgets(app)) {
                cancel(app);
                return;
            }

            if (js.getPendingJob(JOB_PERIODIC) == null) {
                JobInfo periodic = new JobInfo.Builder(JOB_PERIODIC,
                        new ComponentName(app, BackupJobService.class))
                        .setPeriodic(PERIOD_MS)
                        .setPersisted(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                        .build();
                js.schedule(periodic);
            }

            scheduleTick(app);
        } catch (Throwable ignored) {
        }
    }

    static void scheduleTick(Context context) {
        Context app = context.getApplicationContext();
        try {
            JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            if (!Prefs.hasWidgets(app)) return;
            if (js.getPendingJob(JOB_TICK) != null) return;

            boolean screenOn = false;
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) screenOn = pm.isInteractive();
            } catch (Throwable ignored) {
            }
            if (!screenOn) return;

            JobInfo tick = new JobInfo.Builder(JOB_TICK,
                    new ComponentName(app, BackupJobService.class))
                    .setMinimumLatency(TICK_MS)
                    .setOverrideDeadline(TICK_MS * 4L)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            js.schedule(tick);
        } catch (Throwable ignored) {
        }
    }

    static void cancelTick(Context context) {
        try {
            JobScheduler js = (JobScheduler) context.getApplicationContext()
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_TICK);
        } catch (Throwable ignored) {
        }
    }

    static void cancel(Context context) {
        try {
            JobScheduler js = (JobScheduler) context.getApplicationContext()
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) {
                js.cancel(JOB_TICK);
                js.cancel(JOB_PERIODIC);
            }
        } catch (Throwable ignored) {
        }
    }
}
