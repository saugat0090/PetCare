package com.example.assignmenttask;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;

/**
 * Finds the phone's current position with the Fused Location Provider.
 *
 * It asks for the location permission the first time it is needed, so the permission is only
 * requested when the user taps a button that needs it ("My location", "Use my location", "Find nearby"),
 * never when the app starts. Create it as a field of an Activity so the permission launcher
 * is registered before the Activity starts.
 */
final class MyLocation {

    /** Receives the position on the main thread. */
    interface Callback {
        void found(LatLng position);
    }

    private final ComponentActivity activity;
    private final ActivityResultLauncher<String[]> permissionLauncher;
    private Callback pending;   // what to do once the permission dialog has been answered

    MyLocation(ComponentActivity activity) {
        this.activity = activity;
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (granted()) {
                        fetch();
                    } else {
                        pending = null;
                        toast("Location permission was denied, so your position cannot be used.");
                    }
                });
    }

    /** True when the user has allowed precise or approximate location. */
    boolean granted() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Asks for permission if needed, then reports the current position to the callback. */
    void locate(Callback callback) {
        pending = callback;
        if (granted()) {
            fetch();
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @SuppressLint("MissingPermission")   // fetch() only runs after granted() has been checked
    private void fetch() {
        Callback callback = pending;
        pending = null;
        if (callback == null) {
            return;
        }
        if (!Maps.available(activity)) {
            toast("Google Play services is required to find your location.");
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(activity);
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    if (location == null) {
                        toast("Could not get your location. Turn on location services and try again.");
                    } else {
                        callback.found(new LatLng(location.getLatitude(), location.getLongitude()));
                    }
                })
                .addOnFailureListener(e -> toast("Could not get your location. Check that location services are on."));
    }

    /** Shows a message, unless the screen has already closed. */
    private void toast(String text) {
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
        }
    }
}
