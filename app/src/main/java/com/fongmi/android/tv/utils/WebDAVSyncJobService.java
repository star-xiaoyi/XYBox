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

    private static final int DELAYED_JOB_ID = 0x58594258;
    private static final int URGENT_JOB_ID = 0x58594259;
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
        if (scheduler == null) return;
        if (immediate) {
            if (scheduler.getPendingJob(URGENT_JOB_ID) != null) return;
            scheduler.cancel(DELAYED_JOB_ID);
        } else if (scheduler.getPendingJob(DELAYED_JOB_ID) != null || scheduler.getPendingJob(URGENT_JOB_ID) != null) {
            return;
        }
        int jobId = immediate ? URGENT_JOB_ID : DELAYED_JOB_ID;
        JobInfo.Builder builder = new JobInfo.Builder(jobId, new ComponentName(App.get(), WebDAVSyncJobService.class))
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
        if (scheduler == null) return;
        scheduler.cancel(DELAYED_JOB_ID);
        scheduler.cancel(URGENT_JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        App.execute(() -> {
            WebDAVSyncManager manager = WebDAVSyncManager.get();
            WebDAVSyncManager.SyncResult result = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                result = manager.syncNow();
                if (!result.success || !manager.hasPendingSync()) break;
            }
            boolean retry = result == null || !result.success || manager.hasPendingSync();
            jobFinished(params, retry);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
