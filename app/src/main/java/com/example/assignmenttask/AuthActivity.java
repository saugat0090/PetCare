package com.example.assignmenttask;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Locale;

/**
 * Sign up, log in and "forgot password" in one screen, all through Firebase Authentication.
 * The "mode" extra ("login", "signup" or "forgot") decides which fields and texts are shown.
 */
public class AuthActivity extends AppCompatActivity {

    private boolean signup;
    private boolean forgot;
    private String submitLabel;

    private MaterialButton submit;
    private TextInputLayout nameLayout, emailLayout, passwordLayout, confirmLayout;
    private TextInputEditText nameInput, emailInput, passwordInput, confirmInput;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode") == null ? "login" : getIntent().getStringExtra("mode");
        signup = mode.equals("signup");
        forgot = mode.equals("forgot");

        setContentView(R.layout.activity_auth);
        Ui.applyInsets(findViewById(R.id.root));
        findViews();
        showTextsAndFieldsForMode();

        submit.setOnClickListener(v -> submit());

        // The link and the switch button move between the three modes.
        TextView forgotLink = findViewById(R.id.forgot);
        forgotLink.setVisibility(mode.equals("login") ? View.VISIBLE : View.GONE);
        forgotLink.setOnClickListener(v -> openMode("forgot"));

        MaterialButton switchMode = findViewById(R.id.switchMode);
        switchMode.setText(signup ? "Already have an account? Log in" : forgot ? "Back to log in" : "New here? Create an account");
        switchMode.setOnClickListener(v -> {
            openMode(signup || forgot ? "login" : "signup");
            finish();
        });
    }

    private void findViews() {
        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmLayout = findViewById(R.id.confirmLayout);
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmInput = findViewById(R.id.confirmInput);
        submit = findViewById(R.id.submit);
    }

    /** Sets the title, subtitle, button label and visible fields for the current mode. */
    private void showTextsAndFieldsForMode() {
        TextView title = findViewById(R.id.authTitle);
        TextView subtitle = findViewById(R.id.authSubtitle);

        if (signup) {
            title.setText("Create your account");
            subtitle.setText("Sign up to keep your pets and their care in one place.");
            submitLabel = "Sign up";
        } else if (forgot) {
            title.setText("Forgot password?");
            subtitle.setText("Enter your account email and we will send you a link to reset your password.");
            submitLabel = "Send reset link";
        } else {
            title.setText("Welcome back");
            subtitle.setText("Log in to manage your pets and their care.");
            submitLabel = "Log in";
        }
        submit.setText(submitLabel);

        nameLayout.setVisibility(signup ? View.VISIBLE : View.GONE);
        passwordLayout.setVisibility(forgot ? View.GONE : View.VISIBLE);
        confirmLayout.setVisibility(signup ? View.VISIBLE : View.GONE);
    }

    private void openMode(String mode) {
        startActivity(new Intent(this, AuthActivity.class).putExtra("mode", mode));
    }

    /** The text in an input, or "" when it is empty. */
    private static String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    /** Disables the button and shows "Please wait" while a request is running (this prevents double submits). */
    private void setBusy(boolean busy) {
        submit.setEnabled(!busy);
        submit.setText(busy ? "Please wait…" : submitLabel);
    }

    /** Checks the fields for the current mode, then calls Accounts. */
    private void submit() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);

        String email = value(emailInput).trim().toLowerCase(Locale.ROOT);
        String password = value(passwordInput);

        if (forgot) {
            submitForgot(email);
        } else if (signup) {
            submitSignup(email, password);
        } else {
            submitLogin(email, password);
        }
    }

    private void submitForgot(String email) {
        if (!isValidEmail(email)) {
            return;
        }
        setBusy(true);
        Accounts.sendReset(email, (field, message) -> {
            if (message != null) {
                showError(field, message);
                return;
            }
            setBusy(false);
            Toast.makeText(this, "If an account exists for this email, a reset link has been sent.", Toast.LENGTH_LONG).show();
            openMode("login");
            finish();
        });
    }

    private void submitSignup(String email, String password) {
        String name = value(nameInput).trim();
        if (name.isEmpty()) {
            nameLayout.setError("Enter your name");
            nameInput.requestFocus();
            return;
        }
        if (!isValidEmail(email)) {
            return;
        }
        if (password.length() < 6) {
            passwordLayout.setError("Use at least 6 characters");
            passwordInput.requestFocus();
            return;
        }
        if (!password.equals(value(confirmInput))) {
            confirmLayout.setError("Passwords do not match");
            confirmInput.requestFocus();
            return;
        }
        setBusy(true);
        Accounts.register(name, email, password, this::onFinished);
    }

    private void submitLogin(String email, String password) {
        if (email.isEmpty()) {
            emailLayout.setError("Enter your email");
            emailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            passwordLayout.setError("Enter your password");
            passwordInput.requestFocus();
            return;
        }
        setBusy(true);
        Accounts.login(email, password, this::onFinished);
    }

    /** Checks the email format. Shows an error on the field and returns false when it is wrong. */
    private boolean isValidEmail(String email) {
        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return true;
        }
        emailLayout.setError("Enter a valid email address");
        emailInput.requestFocus();
        return false;
    }

    /** Called when sign up or log in finishes: opens the Dashboard on success, or shows the problem. */
    private void onFinished(String field, String message) {
        if (message != null) {
            showError(field, message);
            return;
        }
        setBusy(false);
        Intent intent = new Intent(this, Dashboard.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Shows the message on the input it belongs to, or as a toast when it is not about one input. */
    private void showError(String field, String message) {
        setBusy(false);
        if ("email".equals(field)) {
            emailLayout.setError(message);
            emailInput.requestFocus();
        } else if ("password".equals(field)) {
            passwordLayout.setError(message);
            passwordInput.requestFocus();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
