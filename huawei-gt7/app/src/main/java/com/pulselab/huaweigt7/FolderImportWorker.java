package com.pulselab.huaweigt7;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Android schedules best-effort scanning; only consented folders, no Huawei account access. */
public final class FolderImportWorker extends Worker {
    static final String WORK_NAME = "pulselab_huawei_folder_scan_v20";

    public FolderImportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(Context ctx) {
        PeriodicWorkRequest task = new PeriodicWorkRequest.Builder(
            FolderImportWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, task);
    }

    static void stop(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME);
    }

    @NonNull @Override public Result doWork() {
        String value = FileStore.prefs(getApplicationContext())
            .getString(FileStore.KEY_FOLDER_URI, "");
        if (value == null || value.isEmpty()) return Result.success();
        try {
            FileStore.scanTree(getApplicationContext(), Uri.parse(value));
            return Result.success();
        } catch (Exception e) {
            FileStore.note(getApplicationContext(), "Scan automatique interrompu : " + e.getMessage());
            return Result.retry();
        }
    }
}
