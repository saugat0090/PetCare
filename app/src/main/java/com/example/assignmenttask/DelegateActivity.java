package com.example.assignmenttask;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.maps.model.LatLng;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Delegate care to someone else by SMS, in three steps:
 * 0) choose the routines, 1) enter the recipient, 2) preview the message and open the SMS app.
 * The message is only a draft in the user's own messaging app, so no SMS permission is needed.
 */
public class DelegateActivity extends BaseActivity {

    private static final int STEP_TASKS = 0;
    private static final int STEP_RECIPIENT = 1;
    private static final int STEP_PREVIEW = 2;

    private int step = STEP_TASKS;
    private ArrayList<String> selected = new ArrayList<>();   // ids of the chosen routines
    private String recipient = "";
    private String phone = "";
    private boolean includePin = true;

    private EditText recipientInput;
    private EditText phoneInput;

    /**
     * Lets the user pick a caregiver from their contacts. The system contact picker returns only the
     * one chosen row, so no READ_CONTACTS permission is needed.
     */
    private final ActivityResultLauncher<Intent> contactPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) {
                    return;
                }
                String[] columns = {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER};
                try (Cursor cursor = getContentResolver().query(data.getData(), columns, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        String name = cursor.getString(0);
                        String number = cursor.getString(1);
                        recipient = name == null ? "" : name;
                        phone = number == null ? "" : number.replaceAll("[^0-9+]", "");   // keep digits and a leading +
                        if (step == STEP_RECIPIENT) {
                            render();
                        }
                    }
                } catch (RuntimeException e) {
                    message("Contact unavailable", "The selected contact could not be read. Please type the number instead.");
                }
            });

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        select("routines");

        if (state != null) {
            // Restore the wizard after rotation.
            selected = state.getStringArrayList("selected");
            if (selected == null) {
                selected = new ArrayList<>();
            }
            recipient = state.getString("recipient", "");
            phone = state.getString("phone", "");
            step = state.getInt("step", STEP_TASKS);
            includePin = state.getBoolean("includePin", true);
        } else if (getIntent().hasExtra("routineId")) {
            // Opened from a routine's details: that routine is already chosen, so skip to the recipient.
            selected.add(getIntent().getStringExtra("routineId"));
            step = STEP_RECIPIENT;
        }

        // Back goes to the previous step before it leaves the screen.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                capture();
                if (step > STEP_TASKS) {
                    step--;
                    render();
                } else {
                    finish();
                }
            }
        });
        render();
    }

    /** Saves the wizard so rotating the phone does not lose what was typed. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        capture();
        state.putStringArrayList("selected", selected);
        state.putString("recipient", recipient);
        state.putString("phone", phone);
        state.putInt("step", step);
        state.putBoolean("includePin", includePin);
        super.onSaveInstanceState(state);
    }

    /** Copies the typed name and number out of the text fields (only the recipient step has them). */
    private void capture() {
        if (step == STEP_RECIPIENT && recipientInput != null) {
            recipient = recipientInput.getText().toString().trim();
            phone = phoneInput.getText().toString().trim();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The three steps
    // ---------------------------------------------------------------------------------------------

    /** Draws the current step. */
    private void render() {
        content.removeAllViews();
        if (step == STEP_TASKS) {
            showTasksStep();
        } else if (step == STEP_RECIPIENT) {
            showRecipientStep();
        } else {
            showPreviewStep();
        }
    }

    /** Step 0: tick the routines to share. */
    private void showTasksStep() {
        title("Select Tasks", true);
        text("Choose care routines to share with a caregiver.");

        List<JSONObject> routines = store.all("routines");
        if (routines.isEmpty()) {
            text("Add a care routine first.");
        }
        for (JSONObject routine : routines) {
            String routineId = routine.optString("id");
            String petName = store.name("pets", routine.optString("petId"));

            CheckBox check = new CheckBox(this);
            check.setMinHeight(dp(64));
            check.setText(getString(R.string.task_label, routine.optString("name"), petName, routine.optString("time")));
            check.setChecked(selected.contains(routineId));
            check.setOnCheckedChangeListener((view, checked) -> {
                if (checked && !selected.contains(routineId)) {
                    selected.add(routineId);
                } else if (!checked) {
                    selected.remove(routineId);
                }
            });
            content.addView(check);
        }

        button("Next: recipient", () -> {
            if (chosenRoutines().isEmpty()) {
                message("Choose a task", "Select at least one routine.");
                return;
            }
            step = STEP_RECIPIENT;
            render();
        });
    }

    /** Step 1: enter the caregiver's name and number (or pick them from contacts). */
    private void showRecipientStep() {
        title("Select Recipient", true);
        text("Enter the caregiver’s name and mobile number.");

        recipientInput = new EditText(this);
        recipientInput.setHint("Caregiver name");
        recipientInput.setSingleLine();
        recipientInput.setMinHeight(dp(56));
        recipientInput.setText(recipient);
        content.addView(recipientInput);

        phoneInput = new EditText(this);
        phoneInput.setHint("Mobile number");
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setMinHeight(dp(56));
        phoneInput.setText(phone);
        content.addView(phoneInput);

        outlineButton("Choose from contacts", R.drawable.ic_person, R.color.brand_deep, this::pickContact);
        addPinOption();

        button("Preview message", () -> {
            capture();
            if (recipient.isEmpty()) {
                recipientInput.setError("Enter a name");
                return;
            }
            if (!phone.matches("\\+?[0-9 ()-]{5,25}")) {
                phoneInput.setError("Enter a valid mobile number");
                return;
            }
            step = STEP_PREVIEW;
            render();
        });
    }

    /** Opens the contacts app, or explains if the phone has none. */
    private void pickContact() {
        capture();
        try {
            contactPicker.launch(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI));
        } catch (ActivityNotFoundException e) {
            message("No contacts app", "No contacts app was found. Please type the number instead.");
        }
    }

    /** Adds the "Include location pin" tick box, or a tip when none of the chosen routines has a pinned location. */
    private void addPinOption() {
        List<JSONObject> places = pinnedPlaces();
        if (places.isEmpty()) {
            TextView tip = tv("Tip: link a saved location with a map pin to a routine, and you can share the pin with this message.",
                    13, R.color.muted, false);
            content.addView(tip, params(-1, -2, 0, dp(16)));
            return;
        }

        StringBuilder names = new StringBuilder();
        for (JSONObject place : places) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(place.optString("name"));
        }

        CheckBox pin = new CheckBox(this);
        pin.setMinHeight(dp(56));
        pin.setChecked(includePin);
        pin.setText("Include location pin (" + names + ")");
        pin.setOnCheckedChangeListener((view, checked) -> includePin = checked);
        content.addView(pin);
    }

    /** Step 2: preview the message, then open a draft in the SMS app. */
    private void showPreviewStep() {
        title("SMS Preview", true);
        card("To: " + recipient, phone, null);
        text(buildSmsText());
        text("Your messaging app will open a draft. Review it there before sending; carrier charges may apply.");

        button("Open SMS draft", () -> {
            if (chosenRoutines().isEmpty()) {
                message("Tasks unavailable", "The selected routines no longer exist.");
                return;
            }
            String number = phone.replaceAll("[ ()-]", "");
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null));
            intent.putExtra("sms_body", buildSmsText());
            external(intent);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // The message
    // ---------------------------------------------------------------------------------------------

    /** The routines ticked in step 0. */
    private List<JSONObject> chosenRoutines() {
        List<JSONObject> routines = new ArrayList<>();
        for (JSONObject routine : store.all("routines")) {
            if (selected.contains(routine.optString("id"))) {
                routines.add(routine);
            }
        }
        return routines;
    }

    /** The saved locations with a map pin that the chosen routines point to (each one listed once). */
    private List<JSONObject> pinnedPlaces() {
        List<JSONObject> places = new ArrayList<>();
        List<String> seenIds = new ArrayList<>();
        for (JSONObject routine : chosenRoutines()) {
            JSONObject place = store.find("locations", routine.optString("locationId"));
            if (place == null || latLng(place) == null || seenIds.contains(place.optString("id"))) {
                continue;
            }
            seenIds.add(place.optString("id"));
            places.add(place);
        }
        return places;
    }

    /** Builds the SMS: a greeting, one bullet per routine, optional Google Maps links, and thanks. */
    private String buildSmsText() {
        StringBuilder text = new StringBuilder("Hi " + recipient + ", could you help with these PetCare routines?\n");

        for (JSONObject routine : chosenRoutines()) {
            String frequency = routine.optString("frequency");
            text.append("\n• ").append(store.name("pets", routine.optString("petId")))
                    .append(": ").append(routine.optString("name"))
                    .append(" — ").append(frequency);
            if (frequency.equals("Weekly")) {
                text.append(" ").append(routine.optString("day"));
            }
            text.append(" at ").append(routine.optString("time"));
            if (!routine.optString("supplies").isEmpty()) {
                text.append("\nSupplies: ").append(routine.optString("supplies"));
            }
            if (!routine.optString("notes").isEmpty()) {
                text.append("\n").append(routine.optString("notes"));
            }
        }

        List<JSONObject> places = includePin ? pinnedPlaces() : new ArrayList<>();
        if (!places.isEmpty()) {
            text.append("\n\nLocation pin").append(places.size() > 1 ? "s" : "").append(":");
            for (JSONObject place : places) {
                LatLng point = latLng(place);
                text.append("\n").append(place.optString("name")).append(": ")
                        .append(MapsConfig.pinLink(point.latitude, point.longitude));
            }
        }
        return text.append("\n\nThank you!").toString();
    }
}
