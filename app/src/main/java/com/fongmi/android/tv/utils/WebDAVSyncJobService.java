package com.fongmi.android.tv.utils;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;

import java.util.concurrent.TimeUnit;

/** Best-effort fallback for pending WebDAV changes after the task leaves Recents. */
public class WebDAVSyncJobService extends JobService {

    private static final int JOB_ID = 0x58594258;
    private static final long DELAY = TimeUnit.MINUTES.toMillis(5);

    public static void schedule() {
        if (!Setting.isWebDAVAutoSync()) return;
        JobScheduler scheduler = (JobScheduler) App.get().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.getPendingJob(JOB_ID) != null) return;
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(App.get(), WebDAVSyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(DELAY)
                .setOverrideDeadline(DELAY * 2)
                .build();
        scheduler.schedule(info);
    }

    public static void cancel() {
        JobScheduler scheduler = (JobScheduler) App.get().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        App.execute(() -> {
            WebDAVSyncManager.SyncResult result = WebDAVSyncManager.get().syncNow();
            jobFinished(params, !result.success);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
