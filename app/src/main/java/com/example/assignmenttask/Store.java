package com.example.assignmenttask;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The data API used by the screens. Records are plain JSON objects grouped by type
 * ("pets", "routines", "locations", "expenses"). The actual storage and Firestore sync live in {@link Repo}.
 */
public final class Store {

    private final Context context;

    public Store(Context context) {
        this.context = context;
    }

    // ---------------------------------------------------------------------------------------------
    // Account and profile
    // ---------------------------------------------------------------------------------------------

    public boolean loggedIn() {
        return Repo.signedIn();
    }

    /** The signed-in user's email. */
    public String email() {
        return Repo.email();
    }

    /** The profile display name. */
    public String profile() {
        return Repo.profileName();
    }

    /** Changes the profile display name. */
    public void profile(String name) {
        Repo.setProfileName(name);
    }

    /** File name of the profile picture. */
    public String profilePhoto() {
        return Repo.profilePhoto();
    }

    /** Changes the profile picture and deletes the old image file. */
    public void profilePhoto(String fileName) {
        String old = Repo.profilePhoto();
        String next = fileName == null ? "" : fileName;
        Repo.setProfilePhoto(next);
        if (!old.isEmpty() && !old.equals(next)) {
            Images.delete(context, old);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Records
    // ---------------------------------------------------------------------------------------------

    /** Every record of a type. The records are copies, so editing one changes nothing until it is saved. */
    public List<JSONObject> all(String type) {
        return Repo.list(type);
    }

    /** Finds one record by id, or returns null. */
    public JSONObject find(String type, String id) {
        for (JSONObject record : all(type)) {
            if (record.optString("id").equals(id)) {
                return record;
            }
        }
        return null;
    }

    /** Puts a value into a record (wraps the checked JSON exception). */
    public static void set(JSONObject record, String key, Object value) {
        try {
            record.put(key, value);
        } catch (JSONException e) {
            throw new IllegalArgumentException(key, e);
        }
    }

    /** Saves a record, giving new ones a random id, and returns the id. */
    public String save(String type, JSONObject record) {
        if (record.optString("id").isEmpty()) {
            set(record, "id", UUID.randomUUID().toString());
        }
        Repo.put(type, record);
        return record.optString("id");
    }

    /**
     * Deletes a record.
     * - A pet takes its routines and photo with it (expenses are kept as spending history).
     * - A location only has its links cleared from routines.
     */
    public void delete(String type, String id) {
        if (type.equals("pets")) {
            deletePetData(id);
        } else if (type.equals("locations")) {
            unlinkLocation(id);
        }
        Repo.remove(type, id);
    }

    /** Removes a pet's photo and all of its routines. */
    private void deletePetData(String petId) {
        JSONObject pet = find("pets", petId);
        if (pet != null) {
            Images.delete(context, pet.optString("photo"));
        }
        for (JSONObject routine : all("routines")) {
            if (routine.optString("petId").equals(petId)) {
                Repo.remove("routines", routine.optString("id"));
            }
        }
    }

    /** Clears the location link from every routine that used it. */
    private void unlinkLocation(String locationId) {
        for (JSONObject routine : all("routines")) {
            if (routine.optString("locationId").equals(locationId)) {
                set(routine, "locationId", "");
                Repo.put("routines", routine);
            }
        }
    }

    /** Display name of a linked record, or "Not linked". */
    public String name(String type, String id) {
        JSONObject record = find(type, id);
        return record == null ? "Not linked" : record.optString("name");
    }

    /** Number of routines linked to a saved location. */
    public int linkedCount(String type, String locationId) {
        int count = 0;
        for (JSONObject item : all(type)) {
            if (item.optString("locationId").equals(locationId)) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------------------------------------
    // Routines
    // ---------------------------------------------------------------------------------------------

    /** A routine is due today when it is daily, or weekly on today's weekday. */
    public boolean isDueToday(JSONObject routine) {
        boolean daily = routine.optString("frequency").equals("Daily");
        boolean todayIsItsDay = routine.optString("day").equals(LocalDate.now().getDayOfWeek().name());
        return daily || todayIsItsDay;
    }

    /** Routines due today, ordered by time. */
    public List<JSONObject> dueToday() {
        List<JSONObject> due = new ArrayList<>();
        for (JSONObject routine : all("routines")) {
            if (isDueToday(routine)) {
                due.add(routine);
            }
        }
        due.sort((a, b) -> a.optString("time").compareTo(b.optString("time")));
        return due;
    }

    /** A routine is done when completedOn is today's date, so recurring routines reset by themselves tomorrow. */
    public boolean completed(JSONObject routine) {
        return routine.optString("completedOn").equals(LocalDate.now().toString());
    }

    /** Marks a routine done for today (or undoes it) by storing or clearing completedOn. */
    public void complete(JSONObject routine, boolean done) {
        set(routine, "completedOn", done ? LocalDate.now().toString() : "");
        set(routine, "completedAt", done ? System.currentTimeMillis() : 0);
        save("routines", routine);
    }
}
