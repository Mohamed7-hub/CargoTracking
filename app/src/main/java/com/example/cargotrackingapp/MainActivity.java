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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Constants
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private Button btnStartTracking, btnStopTracking;
    private TextView tvLatitude, tvLongitude;
    private GoogleMap mMap;

    // Tracking data
    private List<LatLng> trackingPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        initializeUIComponents();

        // Set up the Google Map
        setupMapFragment();

        // Configure button click listeners
        setupButtonListeners();

        // Register receiver for location updates
        registerLocationUpdateReceiver();
    }

    // Initialize all UI components
    private void initializeUIComponents() {
        btnStartTracking = findViewById(R.id.btnStartTracking);
        btnStopTracking = findViewById(R.id.btnStopTracking);
        tvLatitude = findViewById(R.id.tvLatitude);
        tvLongitude = findViewById(R.id.tvLongitude);
    }

    // Set up the map fragment
    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    // Configure click listeners for start/stop buttons
    private void setupButtonListeners() {
        btnStartTracking.setOnClickListener(v -> checkLocationPermissionAndStartTracking());
        btnStopTracking.setOnClickListener(v -> stopLocationTracking());
    }

    // Register broadcast receiver to receive location updates
    private void registerLocationUpdateReceiver() {
        LocationUpdateReceiver.registerReceiver(this, (latitude, longitude) -> {
            updateLocationUI(latitude, longitude);
            updateMapWithNewLocation(latitude, longitude);
        });
    }

    // Check for location permission before starting tracking
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

    // Start the location tracking service
    private void startLocationTracking() {
        trackingPoints.clear(); // Reset previous tracking points

        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction(LocationService.ACTION_START_TRACKING);

        // Start service based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Update button states and show feedback
        btnStartTracking.setEnabled(false);
        btnStopTracking.setEnabled(true);
        Toast.makeText(this, "Location tracking started", Toast.LENGTH_SHORT).show();

        WorkManagerHelper.schedulePeriodicWork(this); // Schedule background sync
    }

    // Stop the location tracking service
    private void stopLocationTracking() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction(LocationService.ACTION_STOP_TRACKING);
        startService(serviceIntent);

        // Update button states and show feedback
        btnStartTracking.setEnabled(true);
        btnStopTracking.setEnabled(false);
        Toast.makeText(this, "Location tracking stopped", Toast.LENGTH_SHORT).show();

        WorkManagerHelper.cancelWork(); // Cancel background sync
    }

    // Update UI with new location coordinates
    private void updateLocationUI(double latitude, double longitude) {
        tvLatitude.setText(String.format("Latitude: %.6f", latitude));
        tvLongitude.setText(String.format("Longitude: %.6f", longitude));
    }

    // Update map with new location and draw path
    private void updateMapWithNewLocation(double latitude, double longitude) {
        if (mMap == null) return;

        LatLng newLocation = new LatLng(latitude, longitude);

        // Filter out unreasonable jumps in location
        if (!trackingPoints.isEmpty()) {
            LatLng lastPoint = trackingPoints.get(trackingPoints.size() - 1);
            float[] results = new float[1];
            Location.distanceBetween(lastPoint.latitude, lastPoint.longitude,
                    latitude, longitude, results);
            if (results[0] >= 1000) return; // Skip if more than 1km jump
        }

        trackingPoints.add(newLocation);

        // Update map display
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(newLocation).title("Current Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 15));

        // Draw path if we have multiple points
        if (trackingPoints.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(trackingPoints)
                    .width(5)
                    .color(ContextCompat.getColor(this, R.color.colorPolyline));
            mMap.addPolyline(polylineOptions);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable location layer if permission granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        loadTrackingDataFromFirestore(); // Load previous tracking data
    }

    // Load and display previous tracking data from Firestore
    private void loadTrackingDataFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("locations")
                .orderBy("timestamp")
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        Log.d("MainActivity", "No data or error fetching from Firestore");
                        return;
                    }

                    trackingPoints.clear();

                    // Explicitly use QueryDocumentSnapshot instead of 'var'
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        // Safely retrieve latitude and longitude with null checks
                        Double latitude = doc.getDouble("latitude");
                        Double longitude = doc.getDouble("longitude");

                        if (latitude != null && longitude != null) {
                            trackingPoints.add(new LatLng(latitude, longitude));
                        } else {
                            Log.w("MainActivity", "Invalid lat/lng data in document: " + doc.getId());
                        }
                    }

                    // Filter out unreasonable jumps in stored data
                    List<LatLng> filteredPoints = filterTrackingPoints(trackingPoints);
                    trackingPoints = filteredPoints;

                    // Update map with stored data
                    if (!trackingPoints.isEmpty()) {
                        LatLng lastPoint = trackingPoints.get(trackingPoints.size() - 1);
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 15));
                        if (trackingPoints.size() > 1) {
                            mMap.addPolyline(new PolylineOptions()
                                    .addAll(trackingPoints)
                                    .width(5)
                                    .color(ContextCompat.getColor(this, R.color.colorPolyline)));
                        }
                    }
                });
    }

    // Filter tracking points to remove outliers
    private List<LatLng> filterTrackingPoints(List<LatLng> points) {
        List<LatLng> filtered = new ArrayList<>();
        LatLng previous = null;

        for (LatLng point : points) {
            if (previous == null || isReasonableDistance(previous, point)) {
                filtered.add(point);
                previous = point;
            } else {
                Log.d("MainActivity", "Filtered out distant point: " + point);
            }
        }
        return filtered;
    }

    // Check if distance between two points is reasonable (less than 10km)
    private boolean isReasonableDistance(LatLng p1, LatLng p2) {
        float[] results = new float[1];
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        return results[0] < 10000;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking();
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocationUpdateReceiver.unregisterReceiver(this);
    }
}