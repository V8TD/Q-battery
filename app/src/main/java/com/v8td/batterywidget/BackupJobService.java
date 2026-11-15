package com.v8td.batterywidget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

public final class BackupJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        final Context app = getApplicationContext();
        App.exec(() -> {
            try {
                WidgetUpdater.refresh(app, false);
            } catch (Throwable ignored) {
            } finally {
                App.watch(app);
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
