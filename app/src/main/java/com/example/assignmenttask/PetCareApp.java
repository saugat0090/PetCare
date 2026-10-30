package com.example.assignmenttask;

import android.app.Application;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Runs when the app starts: restores the signed-in Firebase user (if any) and starts syncing their data. */
public class PetCareApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                Repo.start(user.getUid(), user.getEmail());
            }
        } catch (RuntimeException e) {
            Log.w("PetCare", "Firebase is not available", e);
        }
    }
}
