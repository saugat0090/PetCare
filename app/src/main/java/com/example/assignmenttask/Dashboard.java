package com.example.assignmenttask;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * The main screen. It shows one section at a time inside the shared layout:
 * Home, Pets, Routines, Locations and More (with Expenses reached from More).
 */
public class Dashboard extends BaseActivity {

    /** The section currently on screen: home, pets, routines, locations, more or expenses. */
    private String section = "home";

    // State for the "Edit profile" dialog.
    private AlertDialog profileDialog;
    private FrameLayout profileHolder;
    private EditText profileName;
    private TextView profileRemove;
    private String pendingPhoto = "";    // a picture chosen in the dialog but not saved yet
    private boolean removePhoto;         // true when the user tapped "Remove picture"

    // Use a monotonic deadline while this screen is alive, so clock changes cannot extend the delay.
    private final java.util.Map<String, Long> completionDeadlines = new java.util.HashMap<>();
    private final android.os.Handler homeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshHome = () -> {
        if ("home".equals(section)) showSection("home", false);
    };

    @Override
    protected void onPause() {
        homeHandler.removeCallbacks(refreshHome);
        super.onPause();
    }

    private final ActivityResultLauncher<String> profilePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onProfilePictureChosen);

    // ---------------------------------------------------------------------------------------------
    // Lifecycle and section switching
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        // Not signed in (for example after logging out): go back to the Welcome screen.
        if (!store.loggedIn()) {
            openWelcome();
            return;
        }

        // Restore the open section after rotation, or open the one requested by the Intent.
        if (state != null) {
            section = state.getString("section", "home");
        } else {
            String requested = getIntent().getStringExtra("section");
            section = requested == null ? "home" : requested;
        }

        // Back goes Expenses -> More -> Home, and only then leaves the app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (section.equals("expenses")) {
                    showSection("more");
                } else if (!section.equals("home")) {
                    showSection("home");
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showSection(intent.getStringExtra("section"));
    }

    /** Redraws the section so data changed on another screen shows straight away. */
    @Override
    protected void onResume() {
        super.onResume();
        showSection(section);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("section", section);
        super.onSaveInstanceState(state);
    }

    /** New data arrived from Firestore: redraw, but keep the scroll position. */
    @Override
    protected void onDataChanged() {
        super.onDataChanged();
        if (store.loggedIn() && !isFinishing()) {
            showSection(section, false);
        }
    }

    /** Switches to a section and scrolls to the top. A null section means Home. */
    public void showSection(String requested) {
        showSection(requested, true);
    }

    /** Rebuilds the content column for a section. Maps are released first so none leak. */
    private void showSection(String requested, boolean scrollToTop) {
        homeHandler.removeCallbacks(refreshHome);
        View scroll = findViewById(R.id.scroll);
        int scrollY = scroll.getScrollY();

        section = requested == null ? "home" : requested;
        releaseMaps();
        content.removeAllViews();
        select(section);
        setToolbarVisible(true);

        switch (section) {
            case "pets":
                pets();
                break;
            case "routines":
            case "checklist":
                section = "routines";
                routines();
                break;
            case "locations":
                locations();
                break;
            case "more":
                more();
                break;
            case "expenses":
                expenses();
                break;
            default:
                section = "home";
                home();
        }

        if (scrollToTop) {
            scroll.scrollTo(0, 0);
        } else {
            scroll.post(() -> scroll.scrollTo(0, scrollY));
        }
    }

    private void setToolbarVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        toolbar.setVisibility(visibility);
        findViewById(R.id.toolbarDivider).setVisibility(visibility);
    }

    private void openWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------------------------------

    /** Home tab: greeting, pet summaries, today's routines and quick actions. */
    private void home() {
        setToolbarVisible(false);   // Home has its own greeting instead of a toolbar title
        addGreeting();

        // My pets: two cards side by side (an "Add pet" card fills any empty slot).
        addHomeHeader("My Pets", () -> showSection("pets"));
        List<JSONObject> pets = store.all("pets");
        if (pets.isEmpty()) {
            card("Welcome to PetCare", "Add your first pet to start planning their care.", () -> edit("pets", ""));
        } else {
            LinearLayout cards = new LinearLayout(this);
            cards.setBaselineAligned(false);
            for (int i = 0; i < 2; i++) {
                View card = i < pets.size() ? homePetCard(pets.get(i)) : addPetCard();
                cards.addView(card, pairParams(-1, i == 0));
            }
            content.addView(cards, params(-1, -2, 0, dp(8)));
        }

        // Today's routines: the first three only.
        addHomeHeader("Today’s Routines", () -> showSection("routines"));
        List<JSONObject> dueToday = store.dueToday();
        long now = System.currentTimeMillis();
        long nextRefresh = Long.MAX_VALUE;
        java.util.Iterator<JSONObject> iterator = dueToday.iterator();
        while (iterator.hasNext()) {
            JSONObject routine = iterator.next();
            if (store.completed(routine)) {
                String completionKey = routine.optString("id") + ":" + routine.optLong("completedAt", 0);
                long uptime = android.os.SystemClock.uptimeMillis();
                Long deadline = completionDeadlines.get(completionKey);
                if (deadline == null) {
                    long delay = Math.max(0, Math.min(3000, routine.optLong("completedAt", 0) + 3000 - now));
                    deadline = uptime + delay;
                    completionDeadlines.put(completionKey, deadline);
                }
                long remaining = deadline - uptime;
                if (remaining <= 0) iterator.remove();
                else nextRefresh = Math.min(nextRefresh, remaining);
            }
        }
        if (nextRefresh != Long.MAX_VALUE) homeHandler.postDelayed(refreshHome, nextRefresh);
        if (dueToday.isEmpty()) {
            text(store.dueToday().isEmpty() ? "No routines due today." : "All done for today! Completed routines are in Routines.");
        }
        for (int i = 0; i < Math.min(3, dueToday.size()); i++) {
            routineCard(dueToday.get(i), false);
        }

        // Quick actions.
        sectionTitle("Quick Actions");
        LinearLayout tiles = new LinearLayout(this);
        tiles.setBaselineAligned(false);
        tiles.addView(actionTile(R.drawable.ic_paw, "Manage Pets", true, () -> showSection("pets")), pairParams(dp(68), true));
        tiles.addView(actionTile(R.drawable.ic_calendar_add, "Add Routine", false, () -> edit("routines", "")), pairParams(dp(68), false));
        content.addView(tiles, params(-1, -2, 0, dp(8)));
    }

    /** "Welcome back, Name!" with the profile picture in the corner. */
    private void addGreeting() {
        String fullName = store.profile().trim();
        String firstName = fullName.isEmpty() ? "there" : fullName.split("\\s+")[0];

        TextView subtitle = tv("Here’s your pet care at a glance.", 16, R.color.muted, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        LinearLayout greeting = column(
                tv("Welcome back,", 22, R.color.ink, false),
                tv(firstName + "!", 38, R.color.ink, true),
                subtitle);
        greeting.setPadding(0, 0, dp(64), 0);   // leave room for the profile picture

        FrameLayout header = new FrameLayout(this);
        header.addView(greeting, new FrameLayout.LayoutParams(-1, -2));
        header.addView(profileButton(), new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP | Gravity.END));
        content.addView(header, params(-1, -2, dp(8), dp(12)));
    }

    /** The round profile picture (or a person icon) that opens the More tab. */
    private ImageView profileButton() {
        ImageView button = icon(R.drawable.ic_person, 28, R.color.brand_deep);
        button.setBackground(oval(R.color.accent_soft));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setContentDescription("Profile");
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        Bitmap picture = Images.load(this, store.profilePhoto(), dp(112));
        if (picture != null) {
            button.setImageTintList(null);
            button.setPadding(0, 0, 0, 0);
            button.setScaleType(ImageView.ScaleType.CENTER_CROP);
            button.setImageBitmap(picture);
            button.setClipToOutline(true);
        }
        button.setOnClickListener(v -> showSection("more"));
        return button;
    }

    /** A section title with a "View All" link on the right. */
    private void addHomeHeader(String title, Runnable onViewAll) {
        TextView viewAll = tv("View All", 15, R.color.link, true);
        viewAll.setMinHeight(dp(44));
        viewAll.setGravity(Gravity.CENTER_VERTICAL);
        viewAll.setOnClickListener(v -> onViewAll.run());

        LinearLayout header = row();
        header.addView(tv(title, 20, R.color.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(viewAll);
        content.addView(header, params(-1, -2, dp(24), dp(4)));
    }

    /** Layout params for one of two equal-width views side by side, with a gap in the middle. */
    private LinearLayout.LayoutParams pairParams(int height, boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1);
        if (first) {
            params.setMarginEnd(dp(6));
        } else {
            params.setMarginStart(dp(6));
        }
        return params;
    }

    /** A round pet photo, or a paw icon when the pet has no photo. */
    private ImageView petPhoto(String photo, int sizeDp) {
        ImageView view = new ImageView(this);
        view.setBackground(oval(R.color.mint_strong));
        view.setClipToOutline(true);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));

        Bitmap bitmap = Images.load(this, photo, dp(sizeDp) * 2);
        if (bitmap != null) {
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(bitmap);
        } else {
            int padding = dp(sizeDp / 4);
            view.setPadding(padding, padding, padding, padding);
            view.setImageResource(R.drawable.ic_paw);
            view.setImageTintList(ColorStateList.valueOf(color(R.color.brand_deep)));
        }
        return view;
    }

    /** The small pet card shown on Home. */
    private View homePetCard(JSONObject pet) {
        TextView name = tv(pet.optString("name"), 18, R.color.ink, true);
        TextView breed = tv(pet.optString("breed"), 14, R.color.muted, false);
        LinearLayout details = column(name, breed);
        String age = pet.optString("age");
        if (!age.isEmpty()) {
            details.addView(tv(age + (age.equals("1") ? " year old" : " years old"), 14, R.color.muted, false));
        }

        LinearLayout card = row();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = rounded(R.color.mint, 18);
        background.setStroke(dp(1), color(R.color.border));
        card.setBackground(background);
        card.addView(petPhoto(pet.optString("photo"), 64));
        card.addView(details, fill(12));
        card.setOnClickListener(v -> detail("pets", pet.optString("id")));
        ripple(card);
        return card;
    }

    /** The "Add pet" card shown on Home when there is a free slot. */
    private View addPetCard() {
        TextView label = tv("Add pet", 15, R.color.brand_deep, true);
        label.setPadding(0, dp(6), 0, 0);

        LinearLayout card = column(icon(R.drawable.ic_add, 28, R.color.brand_deep), label);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = rounded(R.color.white, 18);
        background.setStroke(dp(1), color(R.color.brand));
        card.setBackground(background);
        card.setOnClickListener(v -> edit("pets", ""));
        ripple(card);
        return card;
    }

    /** A quick-action tile on Home. A filled tile is the main action. */
    private View actionTile(int iconRes, String label, boolean filled, Runnable action) {
        int foreground = filled ? R.color.white : R.color.brand_deep;

        GradientDrawable background = rounded(filled ? R.color.brand_deep : R.color.mint, 16);
        if (!filled) {
            background.setStroke(dp(1), color(R.color.border));
        }

        LinearLayout tile = row();
        tile.setPadding(dp(16), 0, dp(12), 0);
        tile.setBackground(background);
        tile.addView(icon(iconRes, 30, foreground));
        tile.addView(tv(label, 17, filled ? R.color.white : R.color.ink, false), fill(12));
        tile.addView(icon(R.drawable.ic_chevron, 24, foreground));
        tile.setOnClickListener(v -> action.run());
        ripple(tile);
        return tile;
    }

    // ---------------------------------------------------------------------------------------------
    // Pets
    // ---------------------------------------------------------------------------------------------

    /** Pets tab: a card for each pet and an Add pet button. */
    private void pets() {
        title("My Pets", false);
        List<JSONObject> pets = store.all("pets");
        if (pets.isEmpty()) {
            text("No pets yet. Add your first pet below.");
        }
        for (JSONObject pet : pets) {
            petCard(pet);
        }
        button("Add pet", () -> edit("pets", ""));
    }

    /** One pet card. Tapping it opens the pet's details. */
    private void petCard(JSONObject pet) {
        String species = pet.optString("species");
        String kind = (species.isEmpty() ? "" : species + " · ") + pet.optString("breed");
        String subtitle = kind + "\n" + pet.optString("age") + " years · " + pet.optString("weight") + " kg";
        card(pet.optString("name"), subtitle, pet.optString("photo"), () -> detail("pets", pet.optString("id")));
    }

    // ---------------------------------------------------------------------------------------------
    // Routines
    // ---------------------------------------------------------------------------------------------

    /** Routines tab: every routine sorted by time, with Add and Delegate-by-SMS buttons. */
    private void routines() {
        title("Care Routines", false);
        addIcon(button("Add care routine", () -> edit("routines", "")), R.drawable.ic_add);

        List<JSONObject> routines = store.all("routines");
        routines.sort((a, b) -> a.optString("time").compareTo(b.optString("time")));
        if (routines.isEmpty()) {
            text("No routines yet. Add a pet, then create a care routine.");
        }
        for (JSONObject routine : routines) {
            routineCard(routine, true);
        }

        // Delegating needs at least one routine to send.
        MaterialButton delegate = button("Delegate care by SMS", () -> startActivity(new Intent(this, DelegateActivity.class)));
        addIcon(delegate, R.drawable.ic_send);
        delegate.setEnabled(!routines.isEmpty());
        ((LinearLayout.LayoutParams) delegate.getLayoutParams()).topMargin = dp(8);
    }

    /**
     * One routine card: icon, text and a Pending / Completed status that can be tapped.
     * The detailed version (Routines tab) also shows the category and the schedule.
     */
    private void routineCard(JSONObject routine, boolean detailed) {
        String name = routine.optString("name");
        String petName = store.name("pets", routine.optString("petId"));
        String time = time12(routine.optString("time"));

        String firstLine = petName + " • " + (detailed ? routine.optString("category") : time);
        String secondLine = detailed ? time + " • " + frequencyLabel(routine) : routine.optString("frequency");
        LinearLayout text = column(
                tv(name, 17, R.color.ink, true),
                tv(firstLine, 14, R.color.muted, false),
                tv(secondLine, 14, R.color.muted, false));

        if (detailed && !routine.optString("completedOn").isEmpty()) {
            text.addView(tv("Last completed: " + dateText(routine.optString("completedOn")), 13, R.color.brand_deep, false));
        }

        listRow(iconBadge(categoryIcon(routine.optString("category")), 52),
                text,
                () -> detail("routines", routine.optString("id")),
                routineStatus(routine));
    }

    /** The Pending / Completed toggle on a routine card, or "Not today" for routines that are not due. */
    private View routineStatus(JSONObject routine) {
        LinearLayout status = row();
        status.setPadding(dp(8), dp(8), dp(4), dp(8));

        if (!store.isDueToday(routine)) {
            status.addView(tv("Not today", 14, R.color.muted, false));
            return status;
        }

        boolean done = store.completed(routine);
        TextView label = tv(done ? "Completed" : "Pending", 15, done ? R.color.brand_deep : R.color.muted, false);
        label.setPadding(dp(8), 0, 0, 0);

        status.addView(statusMark(done, 26));
        status.addView(label);
        status.setContentDescription("Mark " + routine.optString("name") + (done ? " as pending" : " as completed"));
        status.setOnClickListener(v -> {
            store.complete(routine, !done);
            showSection(section, false);
        });
        ripple(status);
        return status;
    }

    /** A short schedule label such as "Daily" or "Weekly (Sat)". */
    private String frequencyLabel(JSONObject routine) {
        String day = routine.optString("day");
        boolean weekly = routine.optString("frequency").equals("Weekly");
        if (weekly && day.length() >= 3) {
            return "Weekly (" + day.substring(0, 1) + day.substring(1, 3).toLowerCase(Locale.ROOT) + ")";
        }
        return routine.optString("frequency");
    }

    // ---------------------------------------------------------------------------------------------
    // Locations
    // ---------------------------------------------------------------------------------------------

    /** Locations tab: map preview, Add location, and the list of saved locations. */
    private void locations() {
        title("Locations", false);
        List<JSONObject> places = store.all("locations");

        if (anyHasPin(places)) {
            mapPreview(places, null, () -> startActivity(new Intent(this, MapActivity.class)));
        } else {
            TextView empty = tv("Saved places appear here on a map.", 14, R.color.muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(rounded(R.color.mint, 16));
            empty.setPadding(dp(16), dp(48), dp(16), dp(48));
            content.addView(empty, params(-1, -2, 0, dp(12)));
        }

        addIcon(button("Add location", () -> edit("locations", "")), R.drawable.ic_add);

        sectionTitle("Saved Locations");
        if (places.isEmpty()) {
            text("Save a vet clinic, grooming salon, dog park, pet supply store or animal shelter.");
        }
        for (JSONObject place : places) {
            String category = place.optString("category");
            LinearLayout text = column(
                    tv(place.optString("name"), 16, R.color.ink, true),
                    tv(category, 14, R.color.muted, false));
            listRow(placeBadge(category, 52), text, () -> detail("locations", place.optString("id")), chevron());
        }
    }

    /** True when at least one saved location has a valid map pin. */
    private boolean anyHasPin(List<JSONObject> places) {
        for (JSONObject place : places) {
            if (latLng(place) != null) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // More (profile, counts, expenses, log out)
    // ---------------------------------------------------------------------------------------------

    /** More tab: profile summary, counts, Expenses, Edit profile and Log out. */
    private void more() {
        title("More", false);
        String name = store.profile();

        View avatar = profileAvatar(92, store.profilePhoto(), name);
        avatar.setContentDescription("Edit profile picture");
        avatar.setOnClickListener(v -> showProfileDialog());
        ripple(avatar);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(92), dp(92));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        avatarParams.topMargin = dp(12);
        content.addView(avatar, avatarParams);

        TextView nameView = tv(name, 22, R.color.ink, true);
        nameView.setGravity(Gravity.CENTER);
        content.addView(nameView, params(-1, -2, dp(14), 0));

        TextView emailView = tv(store.email(), 14, R.color.muted, false);
        emailView.setGravity(Gravity.CENTER);
        content.addView(emailView, params(-1, -2, dp(2), dp(20)));

        // How many pets, routines and places are saved.
        LinearLayout stats = new LinearLayout(this);
        stats.setBaselineAligned(false);
        addStat(stats, store.all("pets").size(), "Pets");
        addStat(stats, store.all("routines").size(), "Routines");
        addStat(stats, store.all("locations").size(), "Places");
        content.addView(stats, params(-1, -2, 0, dp(20)));

        LinearLayout menu = column();
        menu.setBackgroundResource(R.drawable.card_background);
        addMenuRow(menu, R.drawable.ic_wallet, "Expenses", "Track pet care spending", () -> showSection("expenses"), true);
        addMenuRow(menu, R.drawable.ic_edit, "Edit profile", "View and edit your profile", this::showProfileDialog, false);
        content.addView(menu, params(-1, -2, 0, dp(20)));

        content.addView(logoutRow(), params(-1, -2, 0, dp(8)));
    }

    /** One count tile (pets, routines or places) on the More tab. */
    private void addStat(LinearLayout parent, int count, String label) {
        LinearLayout tile = column(
                tv(String.valueOf(count), 24, R.color.brand_deep, true),
                tv(label, 13, R.color.muted, false));
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(16), dp(8), dp(16));
        tile.setBackground(rounded(R.color.mint, 16));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) {
            params.setMarginStart(dp(12));
        }
        parent.addView(tile, params);
    }

    /** A tappable row inside the More menu card. */
    private void addMenuRow(LinearLayout menu, int iconRes, String title, String subtitle, Runnable action, boolean divider) {
        LinearLayout row = row();
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.addView(icon(iconRes, 22, R.color.brand_deep));
        row.addView(column(tv(title, 16, R.color.ink, true), tv(subtitle, 13, R.color.muted, false)), fill(16));
        row.addView(icon(R.drawable.ic_chevron, 22, R.color.muted));
        row.setOnClickListener(v -> action.run());
        ripple(row);
        menu.addView(row, new LinearLayout.LayoutParams(-1, -2));

        if (divider) {
            addDivider(menu, 54);
        }
    }

    /** The red "Log out" row. */
    private View logoutRow() {
        GradientDrawable background = rounded(R.color.danger_bg, 16);
        background.setStroke(dp(1), color(R.color.danger));

        LinearLayout logout = row();
        logout.setMinimumHeight(dp(56));
        logout.setPadding(dp(16), 0, dp(16), 0);
        logout.setBackground(background);
        logout.addView(icon(R.drawable.ic_power, 22, R.color.danger));
        logout.addView(tv("Log out", 16, R.color.danger, true), fill(16));
        logout.addView(icon(R.drawable.ic_chevron, 22, R.color.danger));
        logout.setOnClickListener(v -> confirmLogout());
        ripple(logout);
        return logout;
    }

    /** Asks for confirmation, then signs out and returns to the Welcome screen. */
    private void confirmLogout() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Log out of your account?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, which) -> {
                    Accounts.logout();
                    openWelcome();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color(R.color.danger)));
        dialog.show();
    }

    // ---------------------------------------------------------------------------------------------
    // Expenses (reached from More)
    // ---------------------------------------------------------------------------------------------

    /** Expenses screen: the total spent, every expense (newest first) and an Add button. */
    private void expenses() {
        title("Expenses", true);
        List<JSONObject> items = store.all("expenses");
        items.sort((a, b) -> b.optString("date").compareTo(a.optString("date")));

        BigDecimal total = BigDecimal.ZERO;
        for (JSONObject item : items) {
            total = total.add(Money.parse(item.optString("amount")));
        }
        content.addView(expenseSummary(total), params(-1, -2, 0, 0));

        sectionTitle("All Expenses");
        if (items.isEmpty()) {
            text("No expenses yet. Tap Add Expense to record one.");
        }
        for (JSONObject item : items) {
            String category = item.optString("category");
            String petName = store.name("pets", item.optString("petId"));
            LinearLayout text = column(
                    tv(item.optString("name"), 16, R.color.ink, true),
                    tv(petName + " • " + category, 13, R.color.muted, false),
                    tv(dateText(item.optString("date")), 13, R.color.muted, false));
            TextView amount = tv(Money.format(item.optString("amount")), 16, R.color.ink, true);
            listRow(expenseBadge(category, 52), text, () -> detail("expenses", item.optString("id")), amount, chevron());
        }

        addIcon(button("Add Expense", () -> edit("expenses", "")), R.drawable.ic_add);
    }

    /** The card at the top of the Expenses screen showing the total spent. */
    private View expenseSummary(BigDecimal total) {
        ImageView wallet = icon(R.drawable.ic_wallet, 28, R.color.brand_deep);
        wallet.setBackground(oval(R.color.mint_strong));
        wallet.setPadding(dp(10), dp(10), dp(10), dp(10));
        wallet.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView label = tv("Total Expenses", 15, R.color.muted, true);
        label.setPadding(0, dp(12), 0, 0);
        TextView amount = tv(Money.format(total), 34, R.color.ink, true);
        amount.setPadding(0, dp(4), 0, dp(4));
        TextView hint = tv("All your pet care costs in one place", 13, R.color.muted, false);
        hint.setGravity(Gravity.CENTER);

        GradientDrawable background = rounded(R.color.mint, 16);
        background.setStroke(dp(1), color(R.color.border));

        LinearLayout summary = column(wallet, label, amount, hint);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(16), dp(24), dp(16), dp(24));
        summary.setBackground(background);
        return summary;
    }

    // ---------------------------------------------------------------------------------------------
    // Edit profile dialog
    // ---------------------------------------------------------------------------------------------

    /** Called when the user picks a picture for their profile. */
    private void onProfilePictureChosen(Uri uri) {
        if (uri == null) {
            return;
        }
        String fileName = Images.save(this, uri);
        if (fileName == null) {
            message("Picture not added", "That image could not be read. Try a different photo.");
            return;
        }
        // The dialog was closed while the picker was open: throw the picture away.
        if (profileDialog == null || !profileDialog.isShowing()) {
            Images.delete(this, fileName);
            return;
        }
        discardPendingPhoto();
        pendingPhoto = fileName;
        removePhoto = false;
        refreshProfilePreview();
    }

    /** Deletes a profile picture that was picked but never saved. */
    private void discardPendingPhoto() {
        if (!pendingPhoto.isEmpty()) {
            Images.delete(this, pendingPhoto);
            pendingPhoto = "";
        }
    }

    /** Redraws the avatar inside the Edit profile dialog. */
    private void refreshProfilePreview() {
        String shown;
        if (!pendingPhoto.isEmpty()) {
            shown = pendingPhoto;
        } else if (removePhoto) {
            shown = "";
        } else {
            shown = store.profilePhoto();
        }

        View avatar = profileAvatar(96, shown, store.profile());
        avatar.setOnClickListener(v -> profilePicker.launch("image/*"));
        profileHolder.removeAllViews();
        profileHolder.addView(avatar, new FrameLayout.LayoutParams(dp(96), dp(96)));
        profileRemove.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The Edit profile dialog: change the name and the picture. */
    private void showProfileDialog() {
        pendingPhoto = "";
        removePhoto = false;

        profileHolder = new FrameLayout(this);

        TextView change = tv("Change picture", 15, R.color.link, true);
        change.setGravity(Gravity.CENTER);
        change.setMinHeight(dp(44));
        change.setOnClickListener(v -> profilePicker.launch("image/*"));

        profileRemove = tv("Remove picture", 14, R.color.danger, true);
        profileRemove.setGravity(Gravity.CENTER);
        profileRemove.setMinHeight(dp(40));
        profileRemove.setOnClickListener(v -> {
            discardPendingPhoto();
            removePhoto = true;
            refreshProfilePreview();
        });

        profileName = new EditText(this);
        profileName.setSingleLine();
        profileName.setText(store.profile());
        profileName.setHint("Name");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), 0);
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        holderParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(profileHolder, holderParams);
        box.addView(change, params(-1, -2, dp(4), 0));
        box.addView(profileRemove, params(-1, -2, 0, dp(8)));
        box.addView(profileName);
        refreshProfilePreview();

        profileDialog = new AlertDialog.Builder(this)
                .setTitle("Edit profile")
                .setView(box)
                .setNegativeButton("Cancel", (d, which) -> discardPendingPhoto())
                .setPositiveButton("Save", null)   // set below, so a bad name keeps the dialog open
                .create();
        profileDialog.setOnCancelListener(d -> discardPendingPhoto());
        profileDialog.setOnShowListener(d ->
                profileDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> saveProfile()));
        profileDialog.show();
    }

    /** Validates the name, then saves the name and picture and closes the dialog. */
    private void saveProfile() {
        String name = profileName.getText().toString().trim();
        if (name.isEmpty() || name.length() > 60) {
            profileName.setError("Enter 1–60 characters");
            return;
        }
        store.profile(name);
        if (!pendingPhoto.isEmpty()) {
            store.profilePhoto(pendingPhoto);
            pendingPhoto = "";
        } else if (removePhoto) {
            store.profilePhoto("");
        }
        profileDialog.dismiss();
        showSection("more");
    }
}
