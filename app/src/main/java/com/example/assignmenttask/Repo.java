package com.example.assignmenttask;

import android.util.Log;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An in-memory copy of the signed-in user's data, kept in sync with Cloud Firestore.
 *
 * Firestore layout:
 *   users/{uid}                                          the profile (name, email, photo file name)
 *   users/{uid}/pets | routines | locations | expenses   one document per record
 *
 * Reads are instant because they come from the in-memory copy. Writes update that copy first and
 * then go to Firestore, whose own offline cache holds them until the phone is online again.
 * Photos stay on the phone, so only their file names are stored.
 */
final class Repo {

    /** Where data is written to. A fake one is used in tests so they need no network. */
    interface Remote {
        void write(String type, String id, Map<String, Object> data);
        void remove(String type, String id);
        void writeProfile(Map<String, Object> data);
    }

    /** Told when the data changes, so a screen can redraw. */
    interface Listener {
        void changed();
    }

    static final String[] TYPES = {"pets", "routines", "locations", "expenses"};

    private static final Remote NO_REMOTE = new Remote() {
        @Override public void write(String type, String id, Map<String, Object> data) { }
        @Override public void remove(String type, String id) { }
        @Override public void writeProfile(Map<String, Object> data) { }
    };

    private static final Map<String, List<JSONObject>> cache = new HashMap<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static final List<ListenerRegistration> registrations = new ArrayList<>();
    private static Remote remote = NO_REMOTE;

    // The signed-in user.
    private static String uid = "";
    private static String email = "";
    private static String profileName = "";
    private static String profilePhoto = "";

    /** The last sync problem, waiting to be shown once. */
    private static String error = null;

    private Repo() { }

    // ---------------------------------------------------------------------------------------------
    // Session
    // ---------------------------------------------------------------------------------------------

    static boolean signedIn() {
        return !uid.isEmpty();
    }

    /** The signed-in user's email address. */
    static String email() {
        return email;
    }

    /** File name of the profile picture (the file is stored on this phone). */
    static String profilePhoto() {
        return profilePhoto;
    }

    /** The display name, or the part of the email before "@" when no name was set. */
    static String profileName() {
        if (!profileName.isEmpty()) {
            return profileName;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : "Pet Parent";
    }

    /** Starts syncing this user's data from Firestore. */
    static void start(String userId, String userEmail) {
        stop();
        uid = userId;
        email = userEmail == null ? "" : userEmail;
        remote = new FirestoreRemote(userId);

        DocumentReference user = FirebaseFirestore.getInstance().collection("users").document(userId);

        // Listen to the profile document.
        registrations.add(user.addSnapshotListener((doc, e) -> {
            if (e != null) {
                reportError(e);
                return;
            }
            if (doc != null && doc.exists()) {
                readProfile(doc);
                notifyChanged();
            }
        }));

        // Listen to each record collection.
        for (String type : TYPES) {
            registrations.add(user.collection(type).addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    reportError(e);
                    return;
                }
                if (snapshot == null) {
                    return;
                }
                List<JSONObject> records = new ArrayList<>();
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    records.add(fromDocument(document));
                }
                cache.put(type, records);
                notifyChanged();
            }));
        }
    }

    /** Copies the name, photo and email out of the profile document. */
    private static void readProfile(DocumentSnapshot doc) {
        String name = doc.getString("name");
        String photo = doc.getString("photo");
        String mail = doc.getString("email");
        profileName = name == null ? "" : name;
        profilePhoto = photo == null ? "" : photo;
        if (mail != null && !mail.isEmpty()) {
            email = mail;
        }
    }

    /** For tests: pretends a user is signed in, with no network. */
    static void startForTest(String userId, String userEmail, String name) {
        stop();
        uid = userId;
        email = userEmail;
        profileName = name;
        remote = NO_REMOTE;
    }

    /** Stops every Firestore listener and wipes the in-memory data (used when logging out). */
    static void stop() {
        for (ListenerRegistration registration : registrations) {
            registration.remove();
        }
        registrations.clear();
        cache.clear();
        remote = NO_REMOTE;
        uid = "";
        email = "";
        profileName = "";
        profilePhoto = "";
        error = null;
    }

    // ---------------------------------------------------------------------------------------------
    // Records
    // ---------------------------------------------------------------------------------------------

    /** Returns copies, so a caller can edit a record freely and only saving makes the change permanent. */
    static List<JSONObject> list(String type) {
        List<JSONObject> copies = new ArrayList<>();
        List<JSONObject> stored = cache.get(type);
        if (stored != null) {
            for (JSONObject item : stored) {
                copies.add(copy(item));
            }
        }
        return copies;
    }

    /** Adds or replaces a record: updates the in-memory copy first, then writes to Firestore. */
    static void put(String type, JSONObject record) {
        String id = record.optString("id");
        List<JSONObject> stored = cache.get(type);
        if (stored == null) {
            stored = new ArrayList<>();
            cache.put(type, stored);
        }
        stored.removeIf(item -> item.optString("id").equals(id));
        stored.add(copy(record));

        remote.write(type, id, toMap(record));
        notifyChanged();
    }

    /** Deletes a record from the in-memory copy and from Firestore. */
    static void remove(String type, String id) {
        List<JSONObject> stored = cache.get(type);
        if (stored != null) {
            stored.removeIf(item -> item.optString("id").equals(id));
        }
        remote.remove(type, id);
        notifyChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Profile
    // ---------------------------------------------------------------------------------------------

    static void setProfileName(String name) {
        profileName = name;
        pushProfile();
    }

    /** Remembers the profile picture's file name. */
    static void setProfilePhoto(String fileName) {
        profilePhoto = fileName;
        pushProfile();
    }

    /** Writes the profile document to Firestore (merging with what is already there). */
    private static void pushProfile() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", profileName);
        data.put("photo", profilePhoto);
        data.put("email", email);
        remote.writeProfile(data);
        notifyChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Change notifications and errors
    // ---------------------------------------------------------------------------------------------

    static void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Stops telling a screen about data changes. */
    static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Tells every listening screen to redraw. */
    static void notifyChanged() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.changed();
        }
    }

    /** Remembers a Firestore error so the next screen can show it once. */
    static void reportError(Exception e) {
        Log.w("PetCare", "Firestore error", e);
        error = e == null ? "Could not reach the database" : e.getLocalizedMessage();
        notifyChanged();
    }

    /** Returns the last sync problem once (for a toast), then forgets it. */
    static String consumeError() {
        String message = error;
        error = null;
        return message;
    }

    // ---------------------------------------------------------------------------------------------
    // Converting between JSON records and Firestore documents
    // ---------------------------------------------------------------------------------------------

    private static JSONObject copy(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A JSON record as a Firestore document map. Every value is stored as text. */
    private static Map<String, Object> toMap(JSONObject record) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = record.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, record.optString(key));
        }
        return map;
    }

    /** A Firestore document as a JSON record, with the document id stored as "id". */
    private static JSONObject fromDocument(DocumentSnapshot document) {
        JSONObject record = new JSONObject();
        Map<String, Object> data = document.getData();
        try {
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    record.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                }
            }
            record.put("id", document.getId());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return record;
    }

    /** Writes to the real Cloud Firestore database. Failures are reported through reportError. */
    private static final class FirestoreRemote implements Remote {
        private final DocumentReference user;

        FirestoreRemote(String uid) {
            user = FirebaseFirestore.getInstance().collection("users").document(uid);
        }

        @Override
        public void write(String type, String id, Map<String, Object> data) {
            user.collection(type).document(id).set(data).addOnFailureListener(Repo::reportError);
        }

        @Override
        public void remove(String type, String id) {
            user.collection(type).document(id).delete().addOnFailureListener(Repo::reportError);
        }

        @Override
        public void writeProfile(Map<String, Object> data) {
            user.set(data, SetOptions.merge()).addOnFailureListener(Repo::reportError);
        }
    }
}
