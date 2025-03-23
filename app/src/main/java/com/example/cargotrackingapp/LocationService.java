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

    // Constants
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "location_channel";
    private static final int NOTIFICATION_ID = 1;

    // Action constants
    public static final String ACTION_START_TRACKING = "com.example.cargotracking.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.example.cargotracking.STOP_TRACKING";
    public static final String ACTION_LOCATION_UPDATE = "com.example.cargotracking.LOCATION_UPDATE";
    public static final String EXTRA_LATITUDE = "extra_latitude";
    public static final String EXTRA_LONGITUDE = "extra_longitude";

    // Service components
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private boolean isTracking = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase and location services
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set up location callback
        setupLocationCallback();

        // Create notification channel for foreground service
        createNotificationChannel();
    }

    // Define how to handle location updates
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    for (Location location : locationResult.getLocations()) {
                        processLocationUpdate(location);
                    }
                }
            }
        };
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
        return START_STICKY; // Service will restart if killed
    }

    // Start tracking location updates
    private void startLocationTracking() {
        LocationRequest locationRequest = new LocationRequest.Builder(10000) // Update every 10s
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000) // Minimum 5s between updates
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            isTracking = true;
            startForeground();
            Log.d(TAG, "Location tracking started");
        } catch (SecurityException e) {
            Log.e(TAG, "Error starting location tracking", e);
        }
    }

    // Stop tracking location updates
    private void stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTracking = false;
        stopForeground(true);
        Log.d(TAG, "Location tracking stopped");
    }

    // Process a new location update
    private void processLocationUpdate(Location location) {
        // Filter out invalid or inaccurate locations
        if (!isValidLocation(location)) return;

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Update UI and storage
        updateNotification(latitude, longitude);
        sendLocationUpdateNotification(latitude, longitude);
        broadcastLocationUpdate(latitude, longitude);
        saveLocationToFirestore(latitude, longitude);

        Log.d(TAG, "Location update: " + latitude + ", " + longitude);
    }

    // Validate location data
    private boolean isValidLocation(Location location) {
        if (location.getLatitude() == 0 && location.getLongitude() == 0) {
            Log.d(TAG, "Ignoring invalid location at 0,0");
            return false;
        }
        if (location.hasAccuracy() && location.getAccuracy() > 100) {
            Log.d(TAG, "Ignoring inaccurate location: " + location.getAccuracy() + "m");
            return false;
        }
        return true;
    }

    // Save location to Firestore
    private void saveLocationToFirestore(double latitude, double longitude) {
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("timestamp", System.currentTimeMillis());

        db.collection("locations")
                .add(locationData)
                .addOnSuccessListener(doc -> Log.d(TAG, "Location saved: " + doc.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving location", e));
    }

    // Create notification channel for Android O+
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Location Tracking Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Used for location tracking notifications");
            channel.enableLights(true);
            channel.enableVibration(true);

            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // Start service in foreground mode
    private void startForeground() {
        Notification notification = createNotification("Tracking location...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    // Update ongoing notification with current location
    private void updateNotification(double latitude, double longitude) {
        String text = String.format("Location: %.6f, %.6f", latitude, longitude);
        Notification notification = createNotification(text);
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    // Create a notification with given text
    private Notification createNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cargo Tracking")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build();
    }

    // Send a separate notification for each location update
    private void sendLocationUpdateNotification(double latitude, double longitude) {
        String text = String.format("New location update: %.6f, %.6f", latitude, longitude);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Update")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        getSystemService(NotificationManager.class).notify((int) System.currentTimeMillis(), notification);
    }

    // Broadcast location update to MainActivity
    private void broadcastLocationUpdate(double latitude, double longitude) {
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.putExtra(EXTRA_LATITUDE, latitude);
        intent.putExtra(EXTRA_LONGITUDE, longitude);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (isTracking) stopLocationTracking();
        super.onDestroy();
    }
}