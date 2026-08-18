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
    private static final long URGENT_DEADLINE = TimeUnit.MINUTES.toMillis(1);

    public static void schedule() {
        schedule(false);
    }

    public static void scheduleImmediate() {
        schedule(true);
    }

    private static void schedule(boolean immediate) {
        if (!Setting.isWebDAVAutoSync()) return;
        JobScheduler scheduler = (JobScheduler) App.get().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || (!immediate && scheduler.getPendingJob(JOB_ID) != null)) return;
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, new ComponentName(App.get(), WebDAVSyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        if (immediate) {
            builder.setOverrideDeadline(URGENT_DEADLINE);
        } else {
            builder.setMinimumLatency(DELAY).setOverrideDeadline(DELAY * 2);
        }
        scheduler.schedule(builder.build());
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
