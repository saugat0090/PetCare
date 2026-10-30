package com.example.assignmenttask;

import android.net.Uri;
import com.google.android.gms.maps.model.LatLng;
import java.util.Locale;

/**
 * Builds the links that hand a location over to Google Maps (or the phone's default maps app).
 * None of these links need an API key.
 *
 * The Google Maps API key used by the in-app maps is set in gradle.properties (MAPS_API_KEY)
 * and reaches the Maps SDK through the manifest placeholder in app/build.gradle.kts.
 */
final class MapsConfig {

    /** Search text used when the user wants every kind of pet place. */
    static final String ANY_PET_PLACE = "pet services";

    private MapsConfig() { }

    /** A tappable link that opens one pin in Google Maps. Shared inside the delegation SMS. */
    static String pinLink(double latitude, double longitude) {
        return String.format(Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", latitude, longitude);
    }

    /**
     * The search text to use for a location category. Most categories work as they are
     * ("Veterinary Clinic" finds vets); "Grooming Salon" finds human salons, so it is reworded.
     * A null or empty category means "any pet place".
     */
    static String nearbyQuery(String category) {
        if (category == null || category.isEmpty()) {
            return ANY_PET_PLACE;
        }
        if (category.equals("Grooming Salon")) {
            return "pet grooming";
        }
        return category.toLowerCase(Locale.ROOT);
    }

    /**
     * A geo: link that asks the maps app to search for places near a position,
     * for example geo:27.680000,84.430000?q=dog%20park.
     */
    static Uri nearbySearchUri(String query, LatLng position) {
        String link = String.format(Locale.ROOT, "geo:%.6f,%.6f?q=%s", position.latitude, position.longitude, Uri.encode(query));
        return Uri.parse(link);
    }
}
