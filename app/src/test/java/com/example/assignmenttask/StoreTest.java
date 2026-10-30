package com.example.assignmenttask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.time.LocalDate;

/** Record logic only. The Firestore connection is not used here (Repo runs without a network in tests). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StoreTest {

    private Context context;
    private Store store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Repo.startForTest("test-user", "tester@example.com", "Tester");
        store = new Store(context);
    }

    /** A record that only has a name. */
    private JSONObject record(String name) {
        JSONObject record = new JSONObject();
        Store.set(record, "name", name);
        return record;
    }

    @Test
    public void recordsPersistAndUpdateWithoutDuplicates() {
        JSONObject pet = record("Max");
        String id = store.save("pets", pet);

        Store reopened = new Store(context);
        assertEquals("Max", reopened.find("pets", id).optString("name"));

        Store.set(pet, "name", "Luna");
        reopened.save("pets", pet);
        assertEquals(1, store.all("pets").size());
        assertEquals("Luna", store.name("pets", id));
    }

    @Test
    public void editingACopyDoesNotChangeStoredDataUntilSaved() {
        String id = store.save("pets", record("Max"));

        JSONObject copy = store.find("pets", id);
        Store.set(copy, "name", "Changed");

        assertEquals("Max", store.name("pets", id));
    }

    @Test
    public void deletingPetCascadesOnlyItsCareRecords() {
        String petId = store.save("pets", record("Max"));

        JSONObject routine = record("Feed");
        Store.set(routine, "petId", petId);
        store.save("routines", routine);

        JSONObject unrelated = record("Other pet");
        Store.set(unrelated, "petId", "different-pet");
        store.save("routines", unrelated);

        store.delete("pets", petId);

        assertEquals(0, store.all("pets").size());
        assertEquals(1, store.all("routines").size());
    }

    @Test
    public void deletingLocationClearsRoutineLinks() {
        String locationId = store.save("locations", record("Park"));

        JSONObject routine = record("Walk");
        Store.set(routine, "locationId", locationId);
        String routineId = store.save("routines", routine);
        assertEquals(1, store.linkedCount("routines", locationId));

        store.delete("locations", locationId);

        assertEquals("", store.find("routines", routineId).optString("locationId"));
        assertEquals(0, store.linkedCount("routines", locationId));
    }

    @Test
    public void profileNameFallsBackToEmailAndCanBeChanged() {
        assertEquals("Tester", store.profile());
        store.profile("Sam");
        assertEquals("Sam", store.profile());

        Repo.startForTest("u2", "ana@example.com", "");
        assertEquals("ana", store.profile());
    }

    @Test
    public void dataChangesNotifyListeners() {
        int[] calls = {0};
        Repo.Listener listener = () -> calls[0]++;

        Repo.addListener(listener);
        store.save("pets", record("Max"));
        Repo.removeListener(listener);

        assertTrue(calls[0] > 0);
    }

    @Test
    public void weeklyScheduleAndCompletionRespectToday() {
        JSONObject daily = record("Daily");
        Store.set(daily, "frequency", "Daily");
        Store.set(daily, "time", "08:00");
        store.save("routines", daily);

        JSONObject weekly = record("Weekly");
        Store.set(weekly, "frequency", "Weekly");
        Store.set(weekly, "day", LocalDate.now().getDayOfWeek().name());
        Store.set(weekly, "time", "09:00");
        store.save("routines", weekly);

        JSONObject tomorrow = record("Tomorrow");
        Store.set(tomorrow, "frequency", "Weekly");
        Store.set(tomorrow, "day", LocalDate.now().plusDays(1).getDayOfWeek().name());
        store.save("routines", tomorrow);

        // Daily and "weekly on today" are due, in time order. "Weekly on tomorrow" is not.
        assertEquals(2, store.dueToday().size());
        assertEquals("Daily", store.dueToday().get(0).optString("name"));
        assertTrue(store.isDueToday(weekly));
        assertFalse(store.isDueToday(tomorrow));

        // Completed yesterday does not count as completed today.
        Store.set(daily, "completedOn", LocalDate.now().minusDays(1).toString());
        assertFalse(store.completed(daily));

        store.complete(daily, true);
        assertTrue(new Store(context).completed(store.find("routines", daily.optString("id"))));

        store.complete(daily, false);
        assertFalse(store.completed(daily));
    }
}
