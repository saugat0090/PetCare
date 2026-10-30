package com.example.assignmenttask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.ViewTreeObserver;
import androidx.appcompat.content.res.AppCompatResources;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import java.util.List;

/**
 * Google Maps helpers shared by the location screens: a Play-services check,
 * category-coloured pins, and camera framing that is safe to call before the map is laid out.
 */
final class Maps {

    /** Where the map starts when nothing is saved yet (central Nepal, zoomed out). */
    static final LatLng DEFAULT_CENTER = new LatLng(28.3949, 84.1240);

    /** The tip of the pin drawing sits at the bottom-centre of the ic_pin vector (22 of 24 units down). */
    private static final float PIN_ANCHOR_X = 0.5f;
    private static final float PIN_ANCHOR_Y = 0.92f;

    private Maps() { }

    /** True when Google Play services can show maps on this device (false on some emulators and in unit tests). */
    static boolean available(Context context) {
        try {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
        } catch (RuntimeException e) {
            return false;   // treat any failure as "no maps" rather than crashing
        }
    }

    /** Draws the pin vector in the category colour. Only call this once the map is ready. */
    static BitmapDescriptor pinIcon(Context context, String category) {
        Drawable drawable = AppCompatResources.getDrawable(context, R.drawable.ic_pin).mutate();
        drawable.setTint(BaseActivity.placeColor(category));

        int size = Math.round(40 * context.getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(new Canvas(bitmap));
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /** Marker options for a saved location: a category-coloured pin whose tip sits exactly on the coordinate. */
    static MarkerOptions markerOptions(Context context, LatLng position, String category) {
        return new MarkerOptions()
                .position(position)
                .icon(pinIcon(context, category))
                .anchor(PIN_ANCHOR_X, PIN_ANCHOR_Y);
    }

    /**
     * Moves the camera so every point is visible.
     * - No points: show the default region.
     * - One point (or several identical ones): zoom to singleZoom.
     * - Several points: fit them all in view.
     *
     * If the view has not been measured yet, the move waits for the first layout,
     * because Google Maps rejects bounds updates on a 0x0 map.
     */
    static void frame(MapView view, GoogleMap map, List<LatLng> points, float singleZoom, int paddingPx) {
        if (points.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 6f));
            return;
        }
        if (allSamePoint(points)) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), singleZoom));
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        LatLngBounds bounds = builder.build();

        Runnable fitBounds = () -> {
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx));
            } catch (IllegalStateException | IllegalArgumentException e) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 10f));
            }
        };

        if (view.getWidth() > 0 && view.getHeight() > 0) {
            fitBounds.run();
            return;
        }
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (view.getWidth() == 0 || view.getHeight() == 0) {
                    return;
                }
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                fitBounds.run();
            }
        });
    }

    /** True when every point is at the same position. */
    private static boolean allSamePoint(List<LatLng> points) {
        for (LatLng point : points) {
            if (!point.equals(points.get(0))) {
                return false;
            }
        }
        return true;
    }
}
