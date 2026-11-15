package com.v8td.batterywidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** Резервная проверка раз в 15 минут. Переживает перезагрузку, не дублируется. */
final class Scheduler {

    private static final int JOB_ID = 8801;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

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
            if (js.getPendingJob(JOB_ID) != null) return;

            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(app, BackupJobService.class))
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(false)
                    .build();
            js.schedule(job);
        } catch (Throwable ignored) {
        }
    }

    static void cancel(Context context) {
        try {
            JobScheduler js = (JobScheduler) context.getApplicationContext()
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_ID);
        } catch (Throwable ignored) {
        }
    }
}
