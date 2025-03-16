package com.example.cargotrackingapp;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class WorkManagerHelper {

    private static final String TAG = "WorkManagerHelper";
    private static final String WORK_NAME = "location_sync_work";

    public static void schedulePeriodicWork(Context context) {
        // Define constraints - only run when device is idle and has network
        Constraints constraints = new Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Create periodic work request - run every 15 minutes
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                LocationSyncWorker.class,
                15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        // Enqueue the work
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
        );

        Log.d(TAG, "Periodic work scheduled");
    }

    public static void cancelWork() {
        WorkManager.getInstance().cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "Periodic work cancelled");
    }

    public static class LocationSyncWorker extends Worker {

        public LocationSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            Log.d(TAG, "Performing background sync of location data");

            // Here you would typically:
            // 1. Check if there's any unsynchronized location data
            // 2. Sync with the server if needed
            // 3. Perform any cleanup or optimization tasks

            // For this example, we'll just log that the work was done
            return Result.success();
        }
    }
}

