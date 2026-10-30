package com.example.assignmenttask;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Needs a device or emulator. Adds and edits a pet, then visits every tab. */
@RunWith(AndroidJUnit4.class)
public class NavigationTest {

    @Test
    public void addEditPetAndNavigateAcrossAllSections() {
        Context context = ApplicationProvider.getApplicationContext();
        Repo.startForTest("test-user", "tester@example.com", "Tester");   // no network: data stays in memory

        try (ActivityScenario<Dashboard> scenario = ActivityScenario.launch(Dashboard.class)) {
            // Add a pet. Saving an empty form must fail validation.
            onView(withId(R.id.nav_petmate)).perform(click());
            onView(withText("Add pet")).perform(click());
            onView(withText("Save pet")).perform(scrollTo(), click());
            assertTrue(new Store(context).all("pets").isEmpty());

            onView(withHint("Name *")).perform(scrollTo(), replaceText("Max"), closeSoftKeyboard());
            onView(withHint("Breed *")).perform(scrollTo(), replaceText("Golden Retriever"), closeSoftKeyboard());
            onView(withHint("Age in years *")).perform(scrollTo(), replaceText("4"), closeSoftKeyboard());
            onView(withHint("Weight in kg *")).perform(scrollTo(), replaceText("28"), closeSoftKeyboard());
            onView(withText("Save pet")).perform(scrollTo(), click());
            onView(withText("Max")).check(matches(isDisplayed()));

            // Open the pet and edit it.
            onView(withText("Max")).perform(click());
            onView(withText("Medical")).perform(click());
            onView(withText("Vaccinations and medical history")).check(matches(isDisplayed()));
            onView(withText("Edit Pet")).perform(scrollTo(), click());
            onView(withHint("Name *")).perform(scrollTo(), replaceText("Maxwell"), closeSoftKeyboard());
            onView(withText("Save pet")).perform(scrollTo(), click());
            assertEquals("Maxwell", new Store(context).all("pets").get(0).optString("name"));
            pressBack();

            // Visit the other tabs.
            onView(withId(R.id.nav_routines)).perform(click());
            onView(withText("Add care routine")).check(matches(isDisplayed()));

            onView(withId(R.id.nav_store)).perform(click());
            onView(withText("Add location")).check(matches(isDisplayed()));
            onView(withText("Find nearby")).check(matches(isDisplayed()));
            onView(withText("Saved Locations")).check(matches(isDisplayed()));

            onView(withId(R.id.nav_profile)).perform(click());
            onView(withText("Log out")).check(matches(isDisplayed()));
            onView(withText("Expenses")).perform(scrollTo(), click());
            onView(withText("Add Expense")).check(matches(isDisplayed()));
            pressBack();

            onView(withId(R.id.nav_home)).perform(click());
            onView(withText("Tester!")).check(matches(isDisplayed()));

            // The data survives the screen being recreated.
            scenario.recreate();
            assertEquals(1, new Store(context).all("pets").size());
        }
    }
}
