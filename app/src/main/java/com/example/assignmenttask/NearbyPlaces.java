package com.example.assignmenttask;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Looks up pet-related places around a position with the Google Places API (New) Nearby Search,
 * so the results can be drawn on the app's own map. Uses the same API key as the map
 * (MAPS_API_KEY in gradle.properties, read back from the manifest meta-data).
 *
 * Google's Nearby Search only filters by fixed place types, and it has types for vets, pet stores
 * and dog parks but none for groomers or shelters. Those two categories use Text Search (New)
 * with a text query instead; the request and the response format are otherwise the same.
 */
final class NearbyPlaces {

    /** One place returned by the search. */
    static final class Place {
        final String name;
        final String category;   // one of EditorActivity.LOCATION_TYPES
        final LatLng position;
        final float distanceMetres;   // straight-line distance from the searched position

        Place(String name, String category, LatLng position, float distanceMetres) {
            this.name = name;
            this.category = category;
            this.position = position;
            this.distanceMetres = distanceMetres;
        }
    }

    /** Receives the result on the main thread. A null list means the lookup failed (no network, bad key, API not enabled). */
    interface Callback {
        void done(List<Place> places);
    }

    private static final String NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String TEXT_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String FIELD_MASK = "places.displayName,places.location,places.types";
    private static final int[] RADII_METRES = {10000, 15000};   // search 10 km first, then widen to 15 km
    private static final int MAX_RESULTS = 20;                         // Google's maximum per request

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NearbyPlaces() { }

    /** Searches for places of one category ("" or null = every pet place) and reports on the main thread. */
    static void search(Context context, String category, LatLng around, Callback callback) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            String key = apiKey(app);
            String cert = certSha1(app);
            List<Place> result = null;
            if (!key.isEmpty()) {
                for (int radius : RADII_METRES) {
                    List<Place> found = query(app, key, cert, category, around, radius);
                    if (found == null) {
                        break;   // key or network problem: widening the radius will not help
                    }
                    result = found;
                    if (!found.isEmpty()) {
                        break;
                    }
                }
            } else {
                Log.w("NearbyPlaces", "No API key found in the manifest (MAPS_API_KEY is empty)");
            }
            List<Place> finalResult = result;
            MAIN.post(() -> callback.done(finalResult));
        });
    }

    /** Google place types for Nearby Search, or null when the category has no type and needs Text Search. */
    private static String[] types(String category) {
        if (category == null) {
            category = "";
        }
        switch (category) {
            case "Veterinary Clinic": return new String[]{"veterinary_care"};
            case "Dog Park": return new String[]{"dog_park"};
            case "Pet Supply Store": return new String[]{"pet_store"};
            case "Grooming Salon":
            case "Animal Shelter": return null;
            default: return new String[]{"veterinary_care", "pet_store", "dog_park"};
        }
    }

    private static String textQuery(String category) {
        return "Grooming Salon".equals(category) ? "pet grooming" : "animal shelter";
    }

    /** Runs one request. Returns null on failure, an empty list when nothing was found. */
    private static List<Place> query(Context context, String key, String cert,
                                     String category, LatLng around, int radius) {
        try {
            String[] types = types(category);
            JSONObject circle = new JSONObject()
                    .put("center", new JSONObject()
                            .put("latitude", around.latitude)
                            .put("longitude", around.longitude))
                    .put("radius", (double) radius);

            JSONObject body = new JSONObject();
            String url;
            if (types != null) {
                url = NEARBY_URL;
                body.put("includedTypes", new JSONArray(types));
                body.put("maxResultCount", MAX_RESULTS);
                body.put("rankPreference", "DISTANCE");
                body.put("locationRestriction", new JSONObject().put("circle", circle));
            } else {
                url = TEXT_URL;
                body.put("textQuery", textQuery(category));
                body.put("pageSize", MAX_RESULTS);
                // Text Search only accepts a rectangle as a hard limit, so use the square around the circle;
                // parse() then drops anything outside the real radius.
                double dLat = radius / 111320.0;
                double dLon = radius / (111320.0 * Math.max(0.1, Math.cos(Math.toRadians(around.latitude))));
                body.put("locationRestriction", new JSONObject().put("rectangle", new JSONObject()
                        .put("low", new JSONObject()
                                .put("latitude", around.latitude - dLat)
                                .put("longitude", around.longitude - dLon))
                        .put("high", new JSONObject()
                                .put("latitude", around.latitude + dLat)
                                .put("longitude", around.longitude + dLon))));
            }
            String json = post(context, url, key, cert, body.toString());
            return parse(json, category, around, radius);
        } catch (Exception e) {
            Log.w("NearbyPlaces", "Places request failed", e);   // visible in Logcat
            return null;
        }
    }

    private static String post(Context context, String url, String key, String cert, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("X-Goog-Api-Key", key);
            connection.setRequestProperty("X-Goog-FieldMask", FIELD_MASK);
            // Needed when the key is restricted to Android apps in Google Cloud.
            connection.setRequestProperty("X-Android-Package", context.getPackageName());
            if (cert != null) {
                connection.setRequestProperty("X-Android-Cert", cert);
            }
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            if (code != 200) {
                // Google explains the problem in the error body (API not enabled, key restricted, billing off...).
                Log.w("NearbyPlaces", "HTTP " + code + ": " + read(connection.getErrorStream()));
                throw new IllegalStateException("HTTP " + code);
            }
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        return text.toString();
    }

    private static List<Place> parse(String json, String requestedCategory, LatLng around, int radius) throws Exception {
        List<Place> places = new ArrayList<>();
        JSONArray array = new JSONObject(json).optJSONArray("places");   // missing when nothing matched
        if (array == null) {
            return places;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            JSONObject location = item.optJSONObject("location");
            if (location == null) {
                continue;
            }
            String category = categoryOf(item.optJSONArray("types"), requestedCategory);
            JSONObject displayName = item.optJSONObject("displayName");
            String name = displayName == null ? "" : displayName.optString("text", "").trim();
            if (name.isEmpty()) {
                name = category;
            }
            double lat = location.getDouble("latitude");
            double lon = location.getDouble("longitude");
            float[] distance = new float[1];
            Location.distanceBetween(around.latitude, around.longitude, lat, lon, distance);
            if (distance[0] > radius) {
                continue;   // only places really inside the search radius around the phone
            }
            places.add(new Place(name, category, new LatLng(lat, lon), distance[0]));
        }
        places.sort((a, b) -> Float.compare(a.distanceMetres, b.distanceMetres));   // nearest first
        return places;
    }

    /** A specific search keeps its category; an "any pet place" search works it out from Google's types. */
    private static String categoryOf(JSONArray types, String requestedCategory) {
        if (requestedCategory != null && !requestedCategory.isEmpty()) {
            return requestedCategory;
        }
        if (types != null) {
            for (int i = 0; i < types.length(); i++) {
                switch (types.optString(i)) {
                    case "veterinary_care": return "Veterinary Clinic";
                    case "dog_park": return "Dog Park";
                    case "pet_store": return "Pet Supply Store";
                    default: break;
                }
            }
        }
        return "Pet place";
    }

    /** The Maps key, read back from the manifest meta-data that build.gradle.kts fills in. */
    private static String apiKey(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData == null ? "" : info.metaData.getString("com.google.android.geo.API_KEY", "");
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** SHA-1 of the app's signing certificate (hex, no colons), or null if it cannot be read. */
    @SuppressWarnings("deprecation")
    private static String certSha1(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
