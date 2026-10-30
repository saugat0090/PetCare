package com.example.assignmenttask;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONObject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only detail screen for one record (pet, routine, location or expense).
 * The Intent carries the record "type" and "id". Each type gets its own layout and action buttons.
 */
public class DetailActivity extends BaseActivity {

    private String type;
    private String id;
    private JSONObject record;
    private int petTab;   // which tab is open on a pet: 0 Info, 1 Medical, 2 Diet, 3 Notes

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        type = getIntent().getStringExtra("type");
        id = getIntent().getStringExtra("id");
        if (state != null) {
            petTab = state.getInt("petTab", 0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("petTab", petTab);
        super.onSaveInstanceState(state);
    }

    /** Closes the screen if the record was deleted; otherwise redraws it with fresh data. */
    @Override
    protected void onResume() {
        super.onResume();
        if (type == null || id == null) {
            finish();
            return;
        }
        record = store.find(type, id);
        if (record == null) {
            finish();
            return;
        }
        render();
    }

    /** Redraws when Firestore delivers a change, or closes if the record disappeared. */
    @Override
    protected void onDataChanged() {
        super.onDataChanged();
        if (type == null || id == null || record == null) {
            return;
        }
        record = store.find(type, id);
        if (record == null) {
            finish();
            return;
        }
        render();
    }

    /** Draws the screen for the record's type. */
    private void render() {
        releaseMaps();
        content.removeAllViews();
        title(screenTitle(), true);
        select(type);

        switch (type) {
            case "routines":
                routineDetail();
                break;
            case "locations":
                locationDetail();
                break;
            case "expenses":
                expenseDetail();
                break;
            default:
                petDetail();
        }
    }

    /** The toolbar title: a fixed title for most types, the pet's name for pets. */
    private String screenTitle() {
        switch (type) {
            case "routines": return "Routine Details";
            case "locations": return "Location Details";
            case "expenses": return "Expense Details";
            default: return record.optString("name");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared pieces
    // ---------------------------------------------------------------------------------------------

    /** Adds the Edit and Delete buttons, for example "Edit Pet" and "Delete Pet". */
    private void addEditDeleteButtons(String label) {
        outlineButton("Edit " + label, R.drawable.ic_edit, R.color.brand_deep, () -> edit(type, id));
        outlineButton("Delete " + label, R.drawable.ic_trash, R.color.danger, this::confirmDelete);
    }

    /** Asks before deleting. Deleting a pet also deletes its routines; deleting a location only clears routine links. */
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + record.optString("name") + "?")
                .setMessage(EditorActivity.deleteMessage(type))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.delete(type, id);
                    finish();
                })
                .show();
    }

    /** Adds the large header at the top: a badge with a title and a subtitle. */
    private void addHeader(View badge, String title, String subtitle, boolean boxed) {
        LinearLayout header = row();
        if (boxed) {
            header.setBackgroundResource(R.drawable.card_background);
            header.setPadding(dp(16), dp(16), dp(16), dp(16));
        } else {
            header.setPadding(0, dp(4), 0, dp(4));
        }
        header.addView(badge);
        header.addView(column(tv(title, 20, R.color.ink, true), tv(subtitle, 14, R.color.muted, false)), fill(16));
        content.addView(header, params(-1, -2, 0, dp(12)));
    }

    /** A card that holds a list of detail rows. */
    private LinearLayout infoCard(int verticalPaddingDp) {
        LinearLayout card = column();
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(16), dp(verticalPaddingDp), dp(16), dp(verticalPaddingDp));
        return card;
    }

    /** A detail row with an icon, a small label and a value. */
    private void iconRow(LinearLayout parent, int iconRes, String label, String value, boolean divider) {
        LinearLayout row = row();
        row.setPadding(0, dp(14), 0, dp(14));
        row.addView(icon(iconRes, 24, R.color.brand_deep));
        row.addView(column(tv(label, 13, R.color.muted, false), tv(value, 16, R.color.ink, true)), fill(16));
        parent.addView(row);
        if (divider) {
            addDivider(parent, 0);
        }
    }

    /** A detail row with a bold label above its value. */
    private void detailRow(LinearLayout parent, String label, String value, boolean divider) {
        TextView valueView = tv(value, 15, R.color.muted, false);
        valueView.setPadding(0, dp(4), 0, 0);

        LinearLayout row = column(tv(label, 14, R.color.ink, true), valueView);
        row.setPadding(0, dp(10), 0, dp(10));
        parent.addView(row);
        if (divider) {
            addDivider(parent, 0);
        }
    }

    /** Text to show for an optional field: the value, or "None" when it is empty. */
    private static String orNone(String value) {
        return value.isEmpty() ? "None" : value;
    }

    // ---------------------------------------------------------------------------------------------
    // Expense
    // ---------------------------------------------------------------------------------------------

    /** Expense details: pet, amount, date and notes. */
    private void expenseDetail() {
        String category = record.optString("category");
        addHeader(expenseBadge(category, 64), record.optString("name"), category, true);

        LinearLayout info = infoCard(4);
        iconRow(info, R.drawable.ic_paw, "Pet", store.name("pets", record.optString("petId")), true);
        iconRow(info, R.drawable.ic_wallet, "Amount", Money.format(record.optString("amount")), true);
        iconRow(info, R.drawable.ic_calendar, "Date", dateText(record.optString("date")), true);
        iconRow(info, R.drawable.ic_notes, "Notes", orNone(record.optString("notes")), false);
        content.addView(info, params(-1, -2, 0, dp(16)));

        addEditDeleteButtons("Expense");
    }

    // ---------------------------------------------------------------------------------------------
    // Routine
    // ---------------------------------------------------------------------------------------------

    /** Routine details with Mark as Completed (for today only), View on map, Edit, Delegate and Delete. */
    private void routineDetail() {
        boolean done = store.completed(record);
        String petName = store.name("pets", record.optString("petId"));
        String category = record.optString("category");
        JSONObject place = store.find("locations", record.optString("locationId"));

        addHeader(iconBadge(categoryIcon(category), 56), record.optString("name"), petName + " • " + category, true);

        LinearLayout info = infoCard(8);
        detailRow(info, "Schedule", scheduleText() + time12(record.optString("time")), true);
        detailRow(info, "Required Supplies", orNone(record.optString("supplies")), true);
        detailRow(info, "Location", place == null ? "None" : place.optString("name"), true);
        detailRow(info, "Care Instructions", orNone(record.optString("notes")), true);
        info.addView(statusBlock(done));
        content.addView(info, params(-1, -2, 0, dp(16)));

        MaterialButton mark = button(done ? "Mark as Pending" : "Mark as Completed", () -> {
            store.complete(record, !done);
            render();
        });
        addIcon(mark, R.drawable.ic_check);
        mark.setCornerRadius(dp(10));

        if (place != null && latLng(place) != null) {
            outlineButton("View on map", R.drawable.ic_place, R.color.brand_deep, () -> openMap(place.optString("id")));
        }
        outlineButton("Edit Routine", R.drawable.ic_edit, R.color.brand_deep, () -> edit(type, id));
        outlineButton("Delegate via SMS", R.drawable.ic_send, R.color.brand_deep,
                () -> startActivity(new Intent(this, DelegateActivity.class).putExtra("routineId", id)));
        outlineButton("Delete Routine", R.drawable.ic_trash, R.color.danger, this::confirmDelete);
    }

    /** "Daily • " or "Weekly (Mon) • ", ready to have the time added on the end. */
    private String scheduleText() {
        if (!record.optString("frequency").equals("Weekly")) {
            return "Daily • ";
        }
        String day = record.optString("day");
        if (day.isEmpty()) {
            return "Weekly • ";
        }
        return "Weekly (" + day.substring(0, 1) + day.substring(1).toLowerCase(Locale.ROOT) + ") • ";
    }

    /** The "Status" row of a routine: a tick or an empty ring, and the word Completed or Pending. */
    private LinearLayout statusBlock(boolean done) {
        TextView label = tv(done ? "Completed" : "Pending", 15, done ? R.color.brand_deep : R.color.muted, false);
        label.setPadding(dp(10), 0, 0, 0);

        LinearLayout state = row();
        state.setPadding(0, dp(6), 0, 0);
        state.addView(statusMark(done, 24));
        state.addView(label);

        LinearLayout block = column(tv("Status", 14, R.color.ink, true), state);
        block.setPadding(0, dp(10), 0, dp(10));
        return block;
    }

    // ---------------------------------------------------------------------------------------------
    // Location
    // ---------------------------------------------------------------------------------------------

    /** Location details: map preview, linked routines, Link Routine, Edit and Delete. */
    private void locationDetail() {
        String category = record.optString("category");
        addHeader(placeBadge(category, 56), record.optString("name"), category, false);

        if (latLng(record) != null) {
            mapPreview(Collections.singletonList(record), id, () -> openMap(id));
        } else {
            text("No map pin saved. Edit this location and tap the map to set it.");
        }

        sectionTitle("Linked Care");
        int linked = 0;
        for (JSONObject routine : store.all("routines")) {
            if (id.equals(routine.optString("locationId"))) {
                linked++;
                addLinkedRoutine(routine);
            }
        }
        if (linked == 0) {
            text("Nothing linked yet.");
        }

        outlineButton("Link Routine", R.drawable.ic_add, R.color.brand_deep, this::showLinkDialog);
        outlineButton("Edit Location", R.drawable.ic_edit, R.color.brand_deep, () -> edit(type, id));
        outlineButton("Delete Location", R.drawable.ic_trash, R.color.danger, this::confirmDelete);
    }

    /** A tappable row for a routine that is linked to this location. */
    private void addLinkedRoutine(JSONObject routine) {
        String petName = store.name("pets", routine.optString("petId"));
        LinearLayout text = column(
                tv(petName + " • " + routine.optString("name"), 16, R.color.ink, true),
                tv(routine.optString("frequency") + " • " + time12(routine.optString("time")), 14, R.color.muted, false));
        listRow(iconBadge(categoryIcon(routine.optString("category")), 52),
                text,
                () -> detail("routines", routine.optString("id")),
                chevron());
    }

    /** Opens the full-screen map focused on one saved location. */
    private void openMap(String locationId) {
        startActivity(new Intent(this, MapActivity.class).putExtra("mode", "view").putExtra("id", locationId));
    }

    /** Choose between linking an existing routine and creating a new one for this location. */
    private void showLinkDialog() {
        String[] options = {"Link an existing routine", "Create a new routine here"};
        new AlertDialog.Builder(this)
                .setTitle("Link to " + record.optString("name"))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickRoutineToLink();
                    } else {
                        startActivity(new Intent(this, EditorActivity.class)
                                .putExtra("type", "routines")
                                .putExtra("id", "")
                                .putExtra("locationId", id));
                    }
                })
                .show();
    }

    /** Lists the routines that are not linked here yet and links the chosen one to this location. */
    private void pickRoutineToLink() {
        List<JSONObject> routines = store.all("routines");
        routines.removeIf(routine -> id.equals(routine.optString("locationId")));
        if (routines.isEmpty()) {
            message("Nothing to link", "There are no other routines to link. Choose “Create a new routine here” instead.");
            return;
        }

        String[] names = new String[routines.size()];
        for (int i = 0; i < names.length; i++) {
            JSONObject routine = routines.get(i);
            names[i] = store.name("pets", routine.optString("petId")) + " • " + routine.optString("name");
        }

        new AlertDialog.Builder(this)
                .setTitle("Choose a routine")
                .setItems(names, (dialog, which) -> {
                    JSONObject routine = routines.get(which);
                    Store.set(routine, "locationId", id);
                    store.save("routines", routine);
                    render();
                })
                .show();
    }

    // ---------------------------------------------------------------------------------------------
    // Pet
    // ---------------------------------------------------------------------------------------------

    /** Pet details: a summary card, four tabs (Info, Medical, Diet, Notes), then Edit and Delete. */
    private void petDetail() {
        String species = record.optString("species");
        String summary = (species.isEmpty() ? "" : species + " · ") + record.optString("breed")
                + " · " + record.optString("age") + " years · " + record.optString("weight") + " kg";
        card(record.optString("name"), summary, record.optString("photo"), null);

        TabLayout tabs = new TabLayout(this);
        for (String name : new String[]{"Info", "Medical", "Diet", "Notes"}) {
            tabs.addTab(tabs.newTab().setText(name));
        }
        content.addView(tabs);
        tabs.selectTab(tabs.getTabAt(petTab));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                petTab = tab.getPosition();
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        if (petTab == 0) {
            petField("Pet type", "species");
            petField("Breed", "breed");
            petField("Age in years", "age");
            petField("Weight in kg", "weight");
            petField("Gender", "gender");
        } else if (petTab == 1) {
            petField("Vaccinations and medical history", "medical");
            petField("Allergies", "allergies");
        } else if (petTab == 2) {
            petField("Dietary preferences", "diet");
        } else {
            petField("Notes / favourite toys", "notes");
        }

        addEditDeleteButtons("Pet");
    }

    /** A card showing one pet field, or "Not recorded" when it is empty. */
    private void petField(String label, String key) {
        String value = record.optString(key);
        card(label, value.isEmpty() ? "Not recorded" : value, null);
    }
}
