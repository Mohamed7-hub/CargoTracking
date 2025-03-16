package com.example.cargotrackingapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class LocationUpdateReceiver extends BroadcastReceiver {

    private LocationUpdateListener listener;

    public interface LocationUpdateListener {
        void onLocationUpdate(double latitude, double longitude);
    }

    public LocationUpdateReceiver(LocationUpdateListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LocationService.ACTION_LOCATION_UPDATE.equals(intent.getAction())) {
            double latitude = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0);
            double longitude = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0);

            if (listener != null) {
                listener.onLocationUpdate(latitude, longitude);
            }
        }
    }

    public static void registerReceiver(Context context, LocationUpdateListener listener) {
        LocationUpdateReceiver receiver = new LocationUpdateReceiver(listener);
        IntentFilter filter = new IntentFilter(LocationService.ACTION_LOCATION_UPDATE);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    public static void unregisterReceiver(Context context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(new LocationUpdateReceiver(null));
    }
}

