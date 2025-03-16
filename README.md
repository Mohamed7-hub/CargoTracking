# CargoTracking(v.2.0)
 Android application for tracking cargo trucks in real-time. This app uses Android's Foreground Service to continuously track location even when the app is minimized, making it ideal for logistics and transportation monitoring.
## Features

- **Real-time Location Tracking**: Continuously tracks the device's location using GPS
- **Background Operation**: Keeps tracking even when the app is minimized
- **Foreground Service**: Shows persistent notification with current location
- **Google Maps Integration**: Displays the truck's path in real-time on a map
- **Firebase Firestore**: Stores location data for historical tracking
- **Battery Optimization**: Uses WorkManager for efficient background processing
- **Location Filtering**: Intelligently filters out inaccurate location readings

## Screenshots
![main_Screenshot](https://github.com/user-attachments/assets/700eda61-8e09-4498-a636-867aef8d7898)

## Technologies Used

- Java
- Android SDK
- Google Maps API
- Firebase Firestore
- Android WorkManager
- Foreground Services
- Location Services

### API Keys Required

- Google Maps API Key
- Firebase Project Configuration
## Usage
1. Launch the app
2. Grant location permissions when prompted
3. Press "Start Tracking" to begin tracking the device's location
4. The app will display the current location coordinates and draw the path on the map
5. Press "Stop Tracking" to end the tracking session
6. Location data is automatically saved to Firebase Firestore

## Project Structure

- `MainActivity.java`: Main UI and map handling
- `LocationService.java`: Foreground service for continuous location tracking
- `LocationUpdateReceiver.java`: Broadcasts location updates to the UI
- `WorkManagerHelper.java`: Handles battery-optimized background tasks
- `activity_main.xml`: Main UI layout with map and controls

