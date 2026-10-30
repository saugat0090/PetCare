package com.example.assignmenttask;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

/** Firebase Authentication: sign up, log in, password-reset email and log out. Callbacks run on the main thread. */
final class Accounts {

    /**
     * Reports the result of a request. On success both values are null.
     * On failure, message is a friendly explanation and field is "email" or "password"
     * when the problem belongs to that input (null when it is a general problem).
     */
    interface Callback {
        void done(String field, String message);
    }

    private Accounts() { }

    /** Creates the Firebase account, then saves the display name in the user's profile. */
    static void register(String name, String email, String password, Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                fail(task.getException(), false, callback);
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                callback.done(null, "Could not create the account. Please try again.");
                return;
            }
            Repo.start(user.getUid(), email);
            Repo.setProfileName(name);
            callback.done(null, null);
        });
    }

    /** Signs in with email and password, then starts syncing that user's data. */
    static void login(String email, String password, Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                fail(task.getException(), true, callback);
                return;
            }
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                callback.done(null, "Could not log in. Please try again.");
                return;
            }
            Repo.start(user.getUid(), email);
            callback.done(null, null);
        });
    }

    /** Sends Firebase's password-reset email. */
    static void sendReset(String email, Callback callback) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.done(null, null);
            } else {
                fail(task.getException(), false, callback);
            }
        });
    }

    /** Signs out and clears the in-memory copy of the user's data. */
    static void logout() {
        FirebaseAuth.getInstance().signOut();
        Repo.stop();
    }

    /** Turns a Firebase error into a short, friendly message and the input it belongs to. */
    private static void fail(Exception error, boolean signingIn, Callback callback) {
        if (error instanceof FirebaseAuthWeakPasswordException) {
            callback.done("password", "Choose a stronger password (at least 6 characters)");
        } else if (error instanceof FirebaseAuthUserCollisionException) {
            callback.done("email", "An account with this email already exists");
        } else if (error instanceof FirebaseAuthInvalidUserException || error instanceof FirebaseAuthInvalidCredentialsException) {
            if (signingIn) {
                callback.done("password", "Incorrect email or password");
            } else {
                callback.done("email", "Enter a valid email address");
            }
        } else if (error instanceof FirebaseNetworkException) {
            callback.done(null, "No internet connection. Check your network and try again.");
        } else if (error == null || error.getLocalizedMessage() == null) {
            callback.done(null, "Something went wrong. Please try again.");
        } else {
            callback.done(null, error.getLocalizedMessage());
        }
    }
}
