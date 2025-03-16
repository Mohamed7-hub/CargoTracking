package com.example.cargotrackingapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private Button btnStartTracking, btnStopTracking;
    private TextView tvLatitude, tvLongitude;
    private GoogleMap mMap;
    private List<LatLng> trackingPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        btnStartTracking = findViewById(R.id.btnStartTracking);
        btnStopTracking = findViewById(R.id.btnStopTracking);
        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);

        // Initialize Google Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Set up button click listeners
        btnStartTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkLocationPermissionAndStartTracking();
            }
        });

        btnStopTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLocationTracking();
            }
        });

        // Register broadcast receiver for location updates
        LocationUpdateReceiver.registerReceiver(this, new LocationUpdateReceiver.LocationUpdateListener() {
            @Override
            public void onLocationUpdate(double latitude, double longitude) {
                updateLocationUI(latitude, longitude);
                updateMapWithNewLocation(latitude, longitude);
            }
        });
    }

    private void checkLocationPermissionAndStartTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationTracking();
        }
    }

    private void startLocationTracking() {
        // Clear any previous tracking points
        trackingPoints.clear();

        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction(LocationService.ACTION_START_TRACKING);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        btnStartTracking.setEnabled(false);
        btnStopTracking.setEnabled(true);
        Toast.makeText(this, "Location tracking started", Toast.LENGTH_SHORT).show();

        // Schedule WorkManager for battery optimization
        WorkManagerHelper.schedulePeriodicWork(this);
    }

    private void stopLocationTracking() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction(LocationService.ACTION_STOP_TRACKING);
        startService(serviceIntent);

        btnStartTracking.setEnabled(true);
        btnStopTracking.setEnabled(false);
        Toast.makeText(this, "Location tracking stopped", Toast.LENGTH_SHORT).show();

        // Cancel WorkManager tasks
        WorkManagerHelper.cancelWork();
    }

    private void updateLocationUI(double latitude, double longitude) {
        tvLatitude.setText(String.format("Latitude: %.6f", latitude));
        tvLongitude.setText(String.format("Longitude: %.6f", longitude));
    }

    private void updateMapWithNewLocation(double latitude, double longitude) {
        if (mMap != null) {
            LatLng newLocation = new LatLng(latitude, longitude);

            // Only add the point if it's the first one or within a reasonable distance from the last one
            boolean shouldAddPoint = true;

            if (!trackingPoints.isEmpty()) {
                LatLng lastPoint = trackingPoints.get(trackingPoints.size() - 1);
                float[] results = new float[1];
                Location.distanceBetween(
                        lastPoint.latitude, lastPoint.longitude,
                        latitude, longitude,
                        results);

                // Filter out points that are too far (e.g., more than 1km)
                shouldAddPoint = results[0] < 1000;
            }

            if (shouldAddPoint) {
                trackingPoints.add(newLocation);
            }

            // Clear previous markers and add new one
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(newLocation).title("Current Location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 15));

            // Draw path only if we have valid points
            if (trackingPoints.size() > 1) {
                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(trackingPoints)
                        .width(5)
                        .color(ContextCompat.getColor(this, R.color.colorPolyline));
                mMap.addPolyline(polylineOptions);
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable my location button if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        // Load previous tracking data from Firestore
        loadTrackingDataFromFirestore();
    }

    private void loadTrackingDataFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("locations")
                .orderBy("timestamp")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        trackingPoints.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            double lat = document.getDouble("latitude");
                            double lng = document.getDouble("longitude");
                            trackingPoints.add(new LatLng(lat, lng));
                        }

                        // After retrieving points from Firestore but before drawing the polyline
                        if (trackingPoints.size() > 1) {
                            // Filter out points that are too far apart (likely errors)
                            List<LatLng> filteredPoints = new ArrayList<>();
                            LatLng previousPoint = null;

                            for (LatLng point : trackingPoints) {
                                if (previousPoint == null) {
                                    filteredPoints.add(point);
                                    previousPoint = point;
                                } else {
                                    // Calculate distance between points
                                    float[] results = new float[1];
                                    Location.distanceBetween(
                                            previousPoint.latitude, previousPoint.longitude,
                                            point.latitude, point.longitude,
                                            results);

                                    // Only add points that are within a reasonable distance (e.g., 10km)
                                    if (results[0] < 10000) {
                                        filteredPoints.add(point);
                                        previousPoint = point;
                                    } else {
                                        Log.d("MainActivity", "Filtered out distant point: " + point.toString());
                                    }
                                }
                            }

                            // Replace original points with filtered points
                            trackingPoints = filteredPoints;
                        }

                        // Draw the complete path
                        if (trackingPoints.size() > 0) {
                            LatLng lastPoint = trackingPoints.get(trackingPoints.size() - 1);
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 15));

                            if (trackingPoints.size() > 1) {
                                PolylineOptions polylineOptions = new PolylineOptions()
                                        .addAll(trackingPoints)
                                        .width(5)
                                        .color(ContextCompat.getColor(this, R.color.colorPolyline));
                                mMap.addPolyline(polylineOptions);
                            }
                        }
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocationUpdateReceiver.unregisterReceiver(this);
    }
}

