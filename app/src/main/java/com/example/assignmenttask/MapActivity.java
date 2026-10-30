package com.example.assignmenttask;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen Google Map with a pin for every saved location. Pass an "id" extra to focus one location.
 * Tapping a pin opens a bottom panel with a shortcut to that location's details. "My location" shows
 * the phone's position (the permission is only requested when that button is tapped).
 */
public class MapActivity extends AppCompatActivity {

    private Store store;
    private MapView mapView;    // null when Google Play services is unavailable, so every use checks for that
    private GoogleMap google;
    private final MyLocation myLocation = new MyLocation(this);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_map);
        Ui.applyInsets(findViewById(R.id.root));
        store = new Store(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Locations");
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialButton locateButton = findViewById(R.id.myLocation);

        // Some emulators have no Google Play services: explain instead of showing a blank screen.
        if (!Maps.available(this)) {
            locateButton.setVisibility(View.GONE);
            showHint("The map needs Google Play services, which is not available on this device.");
            return;
        }

        mapView = new MapView(this);
        ((FrameLayout) findViewById(R.id.mapHost)).addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        mapView.onCreate(null);
        mapView.getMapAsync(this::onMapReady);

        locateButton.setOnClickListener(v -> myLocation.locate(position -> {
            showMyLocationDot();
            if (google != null) {
                google.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
            }
        }));
    }

    /** Adds a pin for each saved location, then frames them all (or focuses the one in the "id" extra). */
    private void onMapReady(GoogleMap map) {
        google = map;
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(false);   // the layout has its own "My location" button
        if (myLocation.granted()) {
            showMyLocationDot();
        }

        String focusId = getIntent().getStringExtra("id");
        List<LatLng> points = new ArrayList<>();
        JSONObject focus = null;

        for (JSONObject location : store.all("locations")) {
            LatLng point = BaseActivity.latLng(location);
            if (point == null) {
                continue;
            }
            points.add(point);
            Marker marker = map.addMarker(Maps.markerOptions(this, point, location.optString("category"))
                    .title(location.optString("name")));
            if (marker != null) {
                marker.setTag(location);
            }
            if (location.optString("id").equals(focusId)) {
                focus = location;
            }
        }

        map.setOnMarkerClickListener(marker -> {
            if (marker.getTag() instanceof JSONObject) {
                showPanel((JSONObject) marker.getTag());
            }
            map.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
            return true;   // handled: our own panel replaces the default info window
        });
        map.setOnMapClickListener(point -> findViewById(R.id.panel).setVisibility(View.GONE));

        if (points.isEmpty()) {
            Maps.frame(mapView, map, points, 15f, 0);
            showHint("No saved locations have a map pin yet.");
        } else if (focus != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(BaseActivity.latLng(focus), 16f));
            showPanel(focus);
        } else {
            int padding = Math.round(48 * getResources().getDisplayMetrics().density);
            Maps.frame(mapView, map, points, 15f, padding);
        }
    }

    /** Shows a short message card over the map. */
    private void showHint(String text) {
        TextView hint = findViewById(R.id.mapHint);
        hint.setText(text);
        hint.setVisibility(View.VISIBLE);
    }

    /** Turns on the blue "you are here" dot. Only called once the location permission is granted. */
    @SuppressLint("MissingPermission")
    private void showMyLocationDot() {
        if (google != null && myLocation.granted()) {
            google.setMyLocationEnabled(true);
        }
    }

    /** Fills in and shows the bottom panel for the tapped location. */
    private void showPanel(JSONObject location) {
        String id = location.optString("id");
        int linked = store.linkedCount("routines", id);

        ((TextView) findViewById(R.id.panelTitle)).setText(location.optString("name"));
        ((TextView) findViewById(R.id.panelBody)).setText(location.optString("category") + "\nLinked: " + linked + " routine(s)");
        findViewById(R.id.panelDetails).setOnClickListener(v ->
                startActivity(new Intent(this, DetailActivity.class).putExtra("type", "locations").putExtra("id", id)));
        findViewById(R.id.panel).setVisibility(View.VISIBLE);
    }

    // A MapView needs every lifecycle callback forwarded to it.

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }
}
