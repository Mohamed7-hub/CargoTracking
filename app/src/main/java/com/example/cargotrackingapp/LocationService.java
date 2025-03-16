package com.example.cargotrackingapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "location_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START_TRACKING = "com.example.cargotracking.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.example.cargotracking.STOP_TRACKING";
    public static final String ACTION_LOCATION_UPDATE = "com.example.cargotracking.LOCATION_UPDATE";
    public static final String EXTRA_LATITUDE = "extra_latitude";
    public static final String EXTRA_LONGITUDE = "extra_longitude";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private boolean isTracking = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Create location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    // Process the location update
                    processLocationUpdate(location);
                }
            }
        };

        // Create notification channel for Android O and above
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_START_TRACKING.equals(action) && !isTracking) {
                startLocationTracking();
            } else if (ACTION_STOP_TRACKING.equals(action)) {
                stopLocationTracking();
                stopSelf();
            }
        }

        return START_STICKY;
    }

    private void startLocationTracking() {
        // Create location request
        LocationRequest locationRequest = new LocationRequest.Builder(10000) // 10 seconds
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000) // 5 seconds
                .build();

        try {
            // Start location updates
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );

            isTracking = true;

            // Start foreground service with notification
            startForeground();

            Log.d(TAG, "Location tracking started");
        } catch (SecurityException e) {
            Log.e(TAG, "Error starting location tracking", e);
        }
    }

    private void stopLocationTracking() {
        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;

        // Stop foreground service
        stopForeground(true);

        Log.d(TAG, "Location tracking stopped");
    }

    private void processLocationUpdate(Location location) {
        // Check if the location is valid (not 0,0 which is in the ocean)
        if (location.getLatitude() == 0 && location.getLongitude() == 0) {
            Log.d(TAG, "Ignoring invalid location at 0,0");
            return;
        }

        // Check if the location has reasonable accuracy
        if (location.hasAccuracy() && location.getAccuracy() > 100) {  // More than 100 meters
            Log.d(TAG, "Ignoring inaccurate location: " + location.getAccuracy() + "m");
            return;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Update foreground notification
        updateNotification(latitude, longitude);

        // Send a separate location update notification
        sendLocationUpdateNotification(latitude, longitude);

        // Send broadcast to update UI
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.putExtra(EXTRA_LATITUDE, latitude);
        intent.putExtra(EXTRA_LONGITUDE, longitude);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // Save to Firestore
        saveLocationToFirestore(latitude, longitude);

        Log.d(TAG, "Location update: " + latitude + ", " + longitude);
    }

    private void saveLocationToFirestore(double latitude, double longitude) {
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("timestamp", System.currentTimeMillis());

        db.collection("locations")
                .add(locationData)
                .addOnSuccessListener(documentReference ->
                        Log.d(TAG, "Location saved to Firestore: " + documentReference.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error saving location to Firestore", e));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking Channel",
                    NotificationManager.IMPORTANCE_HIGH);  // Change from IMPORTANCE_LOW to IMPORTANCE_HIGH
            channel.setDescription("Used for location tracking notifications");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startForeground() {
        // Create notification for foreground service
        Notification notification = createNotification("Tracking location...");

        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(double latitude, double longitude) {
        String notificationText = String.format("Location: %.6f, %.6f", latitude, longitude);
        Notification notification = createNotification(notificationText);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private Notification createNotification(String text) {
        // Create intent for notification click
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cargo Tracking")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // Change from PRIORITY_LOW to PRIORITY_HIGH
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build();
    }

    private void sendLocationUpdateNotification(double latitude, double longitude) {
        String notificationText = String.format("New location update: %.6f, %.6f", latitude, longitude);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Update")
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        // Use a different notification ID for each update
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (isTracking) {
            stopLocationTracking();
        }
        super.onDestroy();
    }
}

