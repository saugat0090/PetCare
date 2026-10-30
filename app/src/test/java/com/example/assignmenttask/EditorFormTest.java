package com.example.assignmenttask;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Verify that moving form controls to XML preserves editing, validation and drafts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EditorFormTest {
    private Context context;
    private Store store;
    private String petId;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Repo.startForTest("test-user", "tester@example.com", "Tester");
        store = new Store(context);
        JSONObject pet = new JSONObject();
        Store.set(pet, "name", "Luna");
        petId = store.save("pets", pet);
    }

    private ActivityController<EditorActivity> editor(String type, String id) {
        Intent intent = new Intent(context, EditorActivity.class)
                .putExtra("type", type).putExtra("id", id);
        return Robolectric.buildActivity(EditorActivity.class, intent).setup();
    }

    private void save(EditorActivity activity) {
        for (int i = 0; i < activity.content.getChildCount(); i++) {
            View view = activity.content.getChildAt(i);
            if (view instanceof Button && ((Button) view).getText().toString().startsWith("Save")) {
                view.performClick();
                return;
            }
        }
        fail("Save button missing");
    }

    @Test
    public void eachXmlFormLoadsAndSavesItsRecord() {
        String[] types = {"pets", "routines", "expenses", "locations"};
        int[] nameInputs = {R.id.petNameInput, R.id.routineNameInput, R.id.expenseNameInput, R.id.locationNameInput};
        for (int i = 0; i < types.length; i++) {
            JSONObject record = new JSONObject();
            Store.set(record, "name", "Original");
            Store.set(record, "petId", petId);
            Store.set(record, "breed", "Labrador");
            Store.set(record, "age", "2");
            Store.set(record, "weight", "12");
            Store.set(record, "amount", "100");
            Store.set(record, "time", "09:30");
            Store.set(record, "date", "2026-10-05");
            Store.set(record, "notes", "Keep these notes");
            Store.set(record, "latitude", "27.7172");
            Store.set(record, "longitude", "85.3240");
            String id = store.save(types[i], record);
            try (ActivityController<EditorActivity> controller = editor(types[i], id)) {
                EditText name = controller.get().findViewById(nameInputs[i]);
                assertEquals("Original", name.getText().toString());
                name.setText("Updated");
                save(controller.get());
                assertTrue(types[i], controller.get().isFinishing());
                JSONObject saved = store.find(types[i], id);
                assertEquals("Updated", saved.optString("name"));
                assertEquals("Keep these notes", saved.optString("notes"));
                assertEquals("27.7172", saved.optString("latitude"));
            }
        }
    }

    @Test
    public void routineDraftAndWeeklySelectionSurviveRecreation() {
        try (ActivityController<EditorActivity> controller = editor("routines", "")) {
            ((EditText) controller.get().findViewById(R.id.routineNameInput)).setText("Evening walk");
            ((Spinner) controller.get().findViewById(R.id.routineFrequencySpinner)).setSelection(1);
            ((Spinner) controller.get().findViewById(R.id.routineDaySpinner)).setSelection(5);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, controller.get().findViewById(R.id.routineDaySpinner).getVisibility());
            controller.recreate();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("Evening walk", ((EditText) controller.get().findViewById(R.id.routineNameInput)).getText().toString());
            assertEquals("Weekly", ((Spinner) controller.get().findViewById(R.id.routineFrequencySpinner)).getSelectedItem());
            assertEquals("SATURDAY", ((Spinner) controller.get().findViewById(R.id.routineDaySpinner)).getSelectedItem());
            ((Spinner) controller.get().findViewById(R.id.routineFrequencySpinner)).setSelection(0);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, controller.get().findViewById(R.id.routineDayLabel).getVisibility());
            assertEquals(View.GONE, controller.get().findViewById(R.id.routineDaySpinner).getVisibility());
        }
    }

    @Test
    public void emptyLocationCannotBeSavedWithoutNameAndPin() {
        try (ActivityController<EditorActivity> controller = editor("locations", "")) {
            save(controller.get());
            assertFalse(controller.get().isFinishing());
            assertNotNull(((EditText) controller.get().findViewById(R.id.locationNameInput)).getError());
            assertTrue(store.all("locations").isEmpty());
        }
    }
}
