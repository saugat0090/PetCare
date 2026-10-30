package com.example.assignmenttask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Checks that every screen builds without crashing, and that the main flows show the right things. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ScreenTest {

    private Context context;
    private Store store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Repo.startForTest("test-user", "tester@example.com", "Tester");
        store = new Store(context);
    }

    /** The position of a button with this label among the children of a layout, or -1 if there is none. */
    private static int indexOfButton(LinearLayout parent, String label) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof Button && ((Button) child).getText().toString().equals(label)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void allEmptySectionsRenderAndAllEditorsOpen() {
        try (ActivityController<Dashboard> controller = Robolectric.buildActivity(Dashboard.class).setup()) {
            for (String section : new String[]{"home", "pets", "routines", "checklist", "locations", "more", "expenses"}) {
                controller.get().showSection(section);
                assertTrue(controller.get().content.getChildCount() > 0);
            }
        }

        // Locations are included too: without Google Play services (as here) the form shows a
        // "map unavailable" notice instead of crashing.
        for (String type : new String[]{"pets", "routines", "expenses", "locations"}) {
            Intent intent = new Intent(context, EditorActivity.class).putExtra("type", type).putExtra("id", "");
            try (ActivityController<EditorActivity> controller = Robolectric.buildActivity(EditorActivity.class, intent).setup()) {
                assertTrue(controller.get().content.getChildCount() > 0);
            }
        }

        for (String mode : new String[]{"login", "signup", "forgot"}) {
            Intent intent = new Intent(context, AuthActivity.class).putExtra("mode", mode);
            try (ActivityController<AuthActivity> controller = Robolectric.buildActivity(AuthActivity.class, intent).setup()) {
                assertNotNull(controller.get().findViewById(R.id.submit));
            }
        }

        try (ActivityController<DelegateActivity> controller = Robolectric.buildActivity(DelegateActivity.class).setup()) {
            assertTrue(controller.get().content.getChildCount() > 0);
        }
    }

    @Test
    public void findNearbyMovesIntoLocationEditor() {
        try (ActivityController<Dashboard> controller = Robolectric.buildActivity(Dashboard.class).setup()) {
            controller.get().showSection("locations");
            LinearLayout content = controller.get().content;

            int add = indexOfButton(content, "Add location");
            int nearby = indexOfButton(content, "Find nearby");
            assertTrue(add >= 0);
            assertEquals(-1, nearby);
        }
    }

    @Test
    public void locationEditorHasNearbyButNoCategoryOrUseMyLocation() {
        Intent intent = new Intent(context, EditorActivity.class).putExtra("type", "locations");
        try (ActivityController<EditorActivity> controller = Robolectric.buildActivity(EditorActivity.class, intent).setup()) {
            LinearLayout content = controller.get().content;
            assertTrue(indexOfButton(content, "Find nearby") >= 0);
            assertEquals(-1, indexOfButton(content, "Use my location"));
            for (int i = 0; i < content.getChildCount(); i++) {
                assertFalse(content.getChildAt(i) instanceof android.widget.Spinner);
            }
        }
    }

    private static View findText(View view, String text) {
        if (view instanceof android.widget.TextView && text.equals(((android.widget.TextView) view).getText().toString())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findText(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }

    @Test
    public void completedRoutineDisappearsAfterDelayButRemainsInRoutines() {
        JSONObject routine = new JSONObject();
        Store.set(routine, "name", "Morning walk");
        Store.set(routine, "frequency", "Daily");
        Store.set(routine, "time", "08:00");
        String id = store.save("routines", routine);
        try (ActivityController<Dashboard> controller = Robolectric.buildActivity(Dashboard.class).setup()) {
            Dashboard dashboard = controller.get();
            ((View) findText(dashboard.content, "Pending").getParent()).performClick();
            assertNotNull(findText(dashboard.content, "Morning walk"));
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
            assertNotNull(findText(dashboard.content, "Morning walk"));
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
            assertTrue(findText(dashboard.content, "Morning walk") == null);
            dashboard.showSection("routines");
            assertNotNull(findText(dashboard.content, "Morning walk"));
            assertNotNull(findText(dashboard.content, "Completed"));
            assertTrue(store.completed(store.find("routines", id)));
            ((View) findText(dashboard.content, "Completed").getParent()).performClick();
            dashboard.showSection("home");
            assertNotNull(findText(dashboard.content, "Morning walk"));
        }
    }

    @Test
    public void signedOutUsersSeeWelcomeAndSignedInUsersSkipIt() {
        Repo.stop();
        try (ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class).setup()) {
            assertNotNull(controller.get().findViewById(R.id.btnLogin));
            assertNotNull(controller.get().findViewById(R.id.btnSignup));
            assertFalse(controller.get().isFinishing());
        }

        Repo.startForTest("test-user", "tester@example.com", "Tester");
        try (ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class).setup()) {
            assertTrue(controller.get().isFinishing());
        }
    }

    @Test
    public void detailsRenderSavedDataAndCloseWhenDeleted() {
        for (String type : new String[]{"pets", "routines", "locations", "expenses"}) {
            JSONObject item = new JSONObject();
            Store.set(item, "name", "Test record");
            Store.set(item, "amount", "125.50");
            String id = store.save(type, item);

            Intent intent = new Intent(context, DetailActivity.class).putExtra("type", type).putExtra("id", id);
            try (ActivityController<DetailActivity> controller = Robolectric.buildActivity(DetailActivity.class, intent).setup()) {
                assertEquals(expectedTitle(type), controller.get().toolbar.getTitle().toString());

                // Deleting the record while the screen is paused makes it close when it resumes.
                controller.pause();
                store.delete(type, id);
                controller.resume();
                assertTrue(controller.get().isFinishing());
            }
        }
    }

    /** The toolbar title each detail screen should show. Pets show the pet's name. */
    private static String expectedTitle(String type) {
        switch (type) {
            case "routines": return "Routine Details";
            case "locations": return "Location Details";
            case "expenses": return "Expense Details";
            default: return "Test record";
        }
    }
}
