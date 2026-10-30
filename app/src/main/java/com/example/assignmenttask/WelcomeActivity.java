package com.example.assignmenttask;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** The first screen: Log in and Sign up. People who are already signed in go straight to the Dashboard. */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        if (new Store(this).loggedIn()) {
            startActivity(new Intent(this, Dashboard.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);
        Ui.applyInsets(findViewById(R.id.root));
        findViewById(R.id.btnLogin).setOnClickListener(v -> openAuth("login"));
        findViewById(R.id.btnSignup).setOnClickListener(v -> openAuth("signup"));
    }

    /** Opens the auth screen in "login" or "signup" mode. */
    private void openAuth(String mode) {
        startActivity(new Intent(this, AuthActivity.class).putExtra("mode", mode));
    }
}
