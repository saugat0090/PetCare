package com.example.assignmenttask;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationBarView;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base class for every screen that uses the shared layout: a toolbar, a scrolling content column
 * and the bottom navigation bar (a navigation rail on tablets).
 *
 * It also holds the small helpers used to build views in code (text, icons, cards, buttons)
 * and the helpers for showing Google Maps.
 */
public abstract class BaseActivity extends AppCompatActivity {

    // Standard sizes used when building rows in code (in dp).
    private static final int ROW_PADDING = 16;
    private static final int ROW_GAP = 12;

    protected Store store;
    protected LinearLayout content;
    protected MaterialToolbar toolbar;
    protected NavigationBarView navigation;

    /** Every MapView created by this screen. Each one must be forwarded the Activity lifecycle. */
    private final List<MapView> maps = new ArrayList<>();

    /** Remember the Activity state so a map created late (for example after a tab switch) can catch up. */
    private boolean started;
    private boolean resumed;

    /** Redraws the screen whenever the synced data changes. */
    private final Repo.Listener dataListener = this::onDataChanged;

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_base);

        store = new Store(this);
        content = findViewById(R.id.content);
        toolbar = findViewById(R.id.toolbar);
        navigation = findViewById(R.id.bottomNav);

        setUpWindowInsets();
        setUpNavigation();
    }

    /** Keeps the content clear of the system bars and the keyboard. */
    private void setUpWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));

            // Hide the bottom bar while the keyboard is open so it does not ride up above it.
            if (navigation instanceof BottomNavigationView) {
                int visibility = insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE;
                navigation.setVisibility(visibility);
                View divider = findViewById(R.id.navDivider);
                if (divider != null) {
                    divider.setVisibility(visibility);
                }
            }
            return insets;
        });
    }

    /** Opens the tapped section: directly on the Dashboard, or by returning to it from another screen. */
    private void setUpNavigation() {
        navigation.setOnItemSelectedListener(item -> {
            String section = sectionFor(item.getItemId());
            if (this instanceof Dashboard) {
                ((Dashboard) this).showSection(section);
            } else {
                Intent intent = new Intent(this, Dashboard.class).putExtra("section", section);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        Repo.addListener(dataListener);
        for (MapView map : maps) {
            map.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        for (MapView map : maps) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        for (MapView map : maps) {
            map.onPause();
        }
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        for (MapView map : maps) {
            map.onStop();
        }
        started = false;
        Repo.removeListener(dataListener);
        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        for (MapView map : maps) {
            map.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        releaseMaps();
        super.onDestroy();
    }

    /** Called when the synced data changes. Screens override this to redraw. */
    protected void onDataChanged() {
        String problem = Repo.consumeError();
        if (problem != null) {
            Toast.makeText(this, "Sync problem: " + problem, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------------

    /** Maps a navigation menu item to the Dashboard section it opens. */
    protected static String sectionFor(int menuItemId) {
        if (menuItemId == R.id.nav_petmate) return "pets";
        if (menuItemId == R.id.nav_routines) return "routines";
        if (menuItemId == R.id.nav_store) return "locations";
        if (menuItemId == R.id.nav_profile) return "more";
        return "home";
    }

    /** Highlights the navigation item that belongs to a section. */
    protected void select(String section) {
        int itemId;
        switch (section) {
            case "pets":
                itemId = R.id.nav_petmate;
                break;
            case "routines":
            case "checklist":
                itemId = R.id.nav_routines;
                break;
            case "locations":
                itemId = R.id.nav_store;
                break;
            case "more":
            case "expenses":
                itemId = R.id.nav_profile;
                break;
            default:
                itemId = R.id.nav_home;
        }
        navigation.getMenu().findItem(itemId).setChecked(true);
    }

    /** Sets the toolbar title and shows or hides the back arrow. */
    protected void title(String title, boolean showBack) {
        toolbar.setTitle(title);
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
        } else {
            toolbar.setNavigationIcon(null);
        }
        toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    /** Opens the add/edit form for a record. An empty id means "add a new one". */
    protected void edit(String type, String id) {
        startActivity(new Intent(this, EditorActivity.class).putExtra("type", type).putExtra("id", id));
    }

    /** Opens the read-only detail screen for a record. */
    protected void detail(String type, String id) {
        startActivity(new Intent(this, DetailActivity.class).putExtra("type", type).putExtra("id", id));
    }

    /** Starts another app (SMS, contacts, maps...) and shows a dialog instead of crashing if none can handle it. */
    protected void external(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            message("No compatible app installed", "Install an app that can handle this action and try again.");
        }
    }

    /** Shows a simple dialog with an OK button. */
    protected void message(String title, String body) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------------------------------------------------------------------------------------------
    // Small view helpers
    // ---------------------------------------------------------------------------------------------

    /** Converts density-independent pixels to device pixels. */
    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Resolves a colour resource. */
    protected int color(int colorRes) {
        return getColor(colorRes);
    }

    /** Layout params with a top and bottom margin, used when adding rows in code. */
    protected static LinearLayout.LayoutParams params(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    /** Layout params for a view that takes the remaining width of a row, with a gap before it. */
    protected LinearLayout.LayoutParams fill(int startMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginStart(dp(startMarginDp));
        return params;
    }

    /** Rounded-rectangle background in a theme colour. */
    protected GradientDrawable rounded(int colorRes, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(colorRes));
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    /** Circular background in a theme colour. */
    protected GradientDrawable oval(int colorRes) {
        return ovalColor(color(colorRes));
    }

    /** Circular background in a raw ARGB colour. */
    protected GradientDrawable ovalColor(int argb) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(argb);
        return shape;
    }

    /** Adds the standard touch ripple so a custom row looks and feels tappable. */
    protected void ripple(View view) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        view.setForeground(AppCompatResources.getDrawable(this, value.resourceId));
        view.setClickable(true);
        view.setFocusable(true);
    }

    /** Creates a TextView with a size, a colour and optional bold text. */
    protected TextView tv(String text, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color(colorRes));
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    /** Creates a tinted icon of a given size. */
    protected ImageView icon(int drawableRes, int sizeDp, int tintRes) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawableRes);
        view.setImageTintList(ColorStateList.valueOf(color(tintRes)));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /** The small arrow shown at the end of a tappable row. */
    protected ImageView chevron() {
        return icon(R.drawable.ic_chevron, 24, R.color.muted);
    }

    /** A green-tinted icon on a soft circle. */
    protected ImageView iconBadge(int drawableRes, int sizeDp) {
        ImageView badge = icon(drawableRes, sizeDp, R.color.brand_deep);
        badge.setBackground(oval(R.color.mint_strong));
        int padding = dp(sizeDp / 4);
        badge.setPadding(padding, padding, padding, padding);
        return badge;
    }

    /** A tick in a filled circle when done, or an empty ring when pending. */
    protected View statusMark(boolean done, int sizeDp) {
        if (done) {
            ImageView check = icon(R.drawable.ic_check, sizeDp, R.color.white);
            check.setBackground(oval(R.color.brand_deep));
            check.setPadding(dp(4), dp(4), dp(4), dp(4));
            return check;
        }
        GradientDrawable ring = oval(R.color.white);
        ring.setStroke(dp(2), color(R.color.border));
        View view = new View(this);
        view.setBackground(ring);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    /** A horizontal row whose children are centred vertically. */
    protected LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    /** A vertical stack of views, for example a title above a subtitle. */
    protected LinearLayout column(View... children) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        for (View child : children) {
            column.addView(child);
        }
        return column;
    }

    /** A thin line between the rows of a list. */
    protected void addDivider(LinearLayout parent, int startMarginDp) {
        View line = new View(this);
        line.setBackgroundColor(color(R.color.border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        params.setMarginStart(dp(startMarginDp));
        parent.addView(line, params);
    }

    // ---------------------------------------------------------------------------------------------
    // Content builders (everything below adds to the content column)
    // ---------------------------------------------------------------------------------------------

    /** Adds a body-text paragraph. */
    protected TextView text(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(color(R.color.ink));
        view.setPadding(0, dp(8), 0, dp(16));
        content.addView(view);
        return view;
    }

    /** Adds a bold section title with the standard spacing (24dp above, 12dp below). */
    protected TextView sectionTitle(String title) {
        TextView view = tv(title, 20, R.color.ink, true);
        int top = content.getChildCount() == 0 ? 0 : dp(24);
        content.addView(view, params(-1, -2, top, dp(12)));
        return view;
    }

    /** Adds a card with a title and a subtitle. */
    protected View card(String title, String subtitle, Runnable action) {
        return card(title, subtitle, null, action);
    }

    /** Adds a card with an optional pet photo, a title and a subtitle. Tapping it runs the action (if any). */
    protected View card(String title, String subtitle, String photo, Runnable action) {
        View card = getLayoutInflater().inflate(R.layout.item_card, content, false);
        ((TextView) card.findViewById(R.id.cardTitle)).setText(title);
        ((TextView) card.findViewById(R.id.cardSubtitle)).setText(subtitle);

        Bitmap bitmap = Images.load(this, photo, 256);
        if (bitmap != null) {
            ImageView image = card.findViewById(R.id.cardImage);
            image.setImageBitmap(bitmap);
            image.setContentDescription(title + " photo");
            image.setVisibility(View.VISIBLE);
        }

        if (action != null) {
            card.setOnClickListener(v -> action.run());
            ripple(card);
        }
        content.addView(card);
        return card;
    }

    /**
     * Adds a tappable row to the content column: a leading view (usually an icon badge), a text block,
     * and any trailing views such as a status or an arrow.
     */
    protected void listRow(View leading, View textBlock, Runnable onClick, View... trailing) {
        LinearLayout row = row();
        row.setBackgroundResource(R.drawable.card_background);
        row.setPadding(dp(ROW_PADDING), dp(ROW_PADDING), dp(ROW_GAP), dp(ROW_PADDING));
        row.addView(leading);
        row.addView(textBlock, fill(ROW_PADDING));
        for (View view : trailing) {
            row.addView(view);
        }
        row.setOnClickListener(v -> onClick.run());
        ripple(row);
        content.addView(row, params(-1, -2, 0, dp(ROW_GAP)));
    }

    /** Adds a filled primary button. */
    protected MaterialButton button(String label, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setCornerRadius(dp(12));
        button.setOnClickListener(v -> action.run());
        content.addView(button, params(-1, -2, 0, dp(12)));
        return button;
    }

    /** Puts an icon at the start of a button's label. */
    protected void addIcon(MaterialButton button, int drawableRes) {
        button.setIconResource(drawableRes);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(dp(8));
    }

    /** Adds an outlined secondary button with an icon. */
    protected MaterialButton outlineButton(String label, int iconRes, int colorRes, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(52));
        button.setCornerRadius(dp(10));
        button.setTextColor(color(colorRes));
        button.setStrokeColor(ColorStateList.valueOf(color(colorRes)));
        button.setStrokeWidth(dp(1));
        button.setIconTint(ColorStateList.valueOf(color(colorRes)));
        addIcon(button, iconRes);
        button.setOnClickListener(v -> action.run());
        content.addView(button, params(-1, -2, 0, dp(12)));
        return button;
    }

    // ---------------------------------------------------------------------------------------------
    // Formatting
    // ---------------------------------------------------------------------------------------------

    /** Formats a 24-hour "HH:mm" time as "8:00 AM". Returns the input unchanged if it cannot be read. */
    protected static String time12(String hhmm) {
        try {
            return LocalTime.parse(hhmm).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()));
        } catch (RuntimeException e) {
            return hhmm;
        }
    }

    /** Formats an ISO date (2026-09-21) as "21 Sep 2026". Returns the input unchanged if it cannot be read. */
    protected static String dateText(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()));
        } catch (RuntimeException e) {
            return iso;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Categories: icons and colours
    // ---------------------------------------------------------------------------------------------

    /** Icon for a routine category. */
    protected static int categoryIcon(String category) {
        switch (category == null ? "" : category) {
            case "Feeding": return R.drawable.ic_bowl;
            case "Grooming": return R.drawable.ic_scissors;
            case "Cleaning": return R.drawable.ic_cleaning;
            case "Medication": return R.drawable.ic_pill;
            case "Healthcare": return R.drawable.ic_heart;
            case "Other": return R.drawable.ic_bell;
            default: return R.drawable.ic_paw;
        }
    }

    /** Icon for a saved-location category. */
    protected static int placeIcon(String category) {
        switch (category == null ? "" : category) {
            case "Veterinary Clinic": return R.drawable.ic_medical;
            case "Grooming Salon": return R.drawable.ic_scissors;
            case "Dog Park": return R.drawable.ic_paw;
            case "Pet Supply Store": return R.drawable.ic_cart;
            case "Animal Shelter": return R.drawable.ic_home;
            default: return R.drawable.ic_place;
        }
    }

    /** Pin and badge colour for a saved-location category. */
    protected static int placeColor(String category) {
        switch (category == null ? "" : category) {
            case "Veterinary Clinic": return 0xFFE5484D;
            case "Grooming Salon": return 0xFF7C4DDB;
            case "Dog Park": return 0xFF1D4636;
            case "Pet Supply Store": return 0xFFE8871E;
            case "Animal Shelter": return 0xFF3B82F6;
            default: return 0xFF5F6B7A;
        }
    }

    /** Icon for an expense category. */
    protected static int expenseIcon(String category) {
        switch (category == null ? "" : category) {
            case "Food": return R.drawable.ic_bowl;
            case "Veterinary": return R.drawable.ic_medical;
            case "Medication": return R.drawable.ic_pill;
            case "Grooming": return R.drawable.ic_scissors;
            case "Supplies": return R.drawable.ic_cart;
            default: return R.drawable.ic_paw;
        }
    }

    /** Colour for an expense category. */
    protected static int expenseColor(String category) {
        switch (category == null ? "" : category) {
            case "Food": return 0xFFE8871E;
            case "Veterinary": return 0xFF3B82F6;
            case "Medication": return 0xFF7C4DDB;
            case "Grooming": return 0xFF1D4636;
            case "Supplies": return 0xFFD9A400;
            default: return 0xFF66695F;
        }
    }

    /** Round badge for a location: the category colour with a white symbol. */
    protected View placeBadge(String category, int sizeDp) {
        ImageView badge = icon(placeIcon(category), sizeDp, R.color.white);
        badge.setBackground(ovalColor(placeColor(category)));
        int padding = dp(sizeDp / 4);
        badge.setPadding(padding, padding, padding, padding);
        return badge;
    }

    /** Round badge for an expense: a pale circle of the category colour with a coloured symbol. */
    protected View expenseBadge(String category, int sizeDp) {
        int colour = expenseColor(category);
        int paleColour = (colour & 0x00FFFFFF) | 0x26000000;   // same colour at about 15% opacity

        ImageView badge = new ImageView(this);
        badge.setImageResource(expenseIcon(category));
        badge.setImageTintList(ColorStateList.valueOf(colour));
        badge.setBackground(ovalColor(paleColour));
        int padding = dp(sizeDp / 4);
        badge.setPadding(padding, padding, padding, padding);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return badge;
    }

    /** Round profile picture, or the person's initial on a soft circle when there is no picture. */
    protected View profileAvatar(int sizeDp, String photo, String name) {
        Bitmap bitmap = Images.load(this, photo, dp(sizeDp) * 2);
        if (bitmap != null) {
            ImageView picture = new ImageView(this);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setImageBitmap(bitmap);
            picture.setBackground(oval(R.color.accent_soft));
            picture.setClipToOutline(true);
            picture.setContentDescription("Profile picture");
            return picture;
        }
        boolean hasName = name != null && !name.isEmpty();
        String initial = hasName ? name.substring(0, 1).toUpperCase(Locale.getDefault()) : "?";
        TextView letter = tv(initial, Math.max(14, sizeDp / 3), R.color.brand_deep, true);
        letter.setGravity(Gravity.CENTER);
        letter.setBackground(oval(R.color.accent_soft));
        return letter;
    }

    // ---------------------------------------------------------------------------------------------
    // Google Maps
    // ---------------------------------------------------------------------------------------------

    /** The saved map position of a location record, or null when it has no valid pin. */
    protected static LatLng latLng(JSONObject location) {
        String lat = location.optString("latitude");
        String lng = location.optString("longitude");
        if (lat.isEmpty() || lng.isEmpty()) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lng);
            boolean inRange = Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
            return inRange ? new LatLng(latitude, longitude) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Creates a Google MapView and ties it to this Activity's lifecycle (a MapView needs every
     * lifecycle call forwarded to it). Lite mode gives a cheap, static map for previews.
     * Returns null when Google Play services is unavailable, so callers must handle that.
     */
    protected MapView createMap(boolean lite) {
        if (!Maps.available(this)) {
            return null;
        }
        MapView map = new MapView(this, new GoogleMapOptions().liteMode(lite));
        map.onCreate(null);
        if (started) {
            map.onStart();
        }
        if (resumed) {
            map.onResume();
        }
        maps.add(map);
        return map;
    }

    /** Winds every map down (pause, stop, destroy). Call this before rebuilding a screen that contains maps. */
    protected void releaseMaps() {
        for (MapView map : maps) {
            if (resumed) {
                map.onPause();
            }
            if (started) {
                map.onStop();
            }
            map.onDestroy();
        }
        maps.clear();
    }

    /** Placeholder shown where a map would be when Google Play services cannot be used. */
    protected View mapUnavailable() {
        TextView message = tv("The map needs Google Play services, which is not available on this device.", 14, R.color.muted, false);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(16), dp(16), dp(16), dp(16));
        return message;
    }

    /**
     * Adds a non-interactive map preview with one pin per location. Tapping it runs onTap
     * (usually opening the full map). If focusId matches a location, the preview zooms in on it.
     */
    protected FrameLayout mapPreview(List<JSONObject> locations, String focusId, Runnable onTap) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(R.color.mint, 16));
        frame.setClipToOutline(true);

        MapView map = createMap(true);
        if (map == null) {
            frame.addView(mapUnavailable(), new FrameLayout.LayoutParams(-1, -1));
        } else {
            frame.addView(map, new FrameLayout.LayoutParams(-1, -1));
            map.getMapAsync(google -> showPreviewPins(map, google, locations, focusId));
        }

        // A transparent view on top catches the tap, because a lite map does not.
        View cover = new View(this);
        cover.setContentDescription("Open full map");
        cover.setOnClickListener(v -> onTap.run());
        frame.addView(cover, new FrameLayout.LayoutParams(-1, -1));

        content.addView(frame, params(-1, dp(210), 0, dp(12)));
        return frame;
    }

    /** Sets up a preview map: no gestures or toolbar, and one category-coloured pin per saved location. */
    private void showPreviewPins(MapView view, GoogleMap google, List<JSONObject> locations, String focusId) {
        google.getUiSettings().setAllGesturesEnabled(false);
        google.getUiSettings().setMapToolbarEnabled(false);
        google.getUiSettings().setZoomControlsEnabled(false);

        List<LatLng> points = new ArrayList<>();
        LatLng focus = null;
        for (JSONObject location : locations) {
            LatLng point = latLng(location);
            if (point == null) {
                continue;
            }
            points.add(point);
            google.addMarker(Maps.markerOptions(this, point, location.optString("category")));
            if (location.optString("id").equals(focusId)) {
                focus = point;
            }
        }

        // A focused preview centres on that one place; otherwise every pin is framed.
        if (focus != null) {
            points.clear();
            points.add(focus);
        }
        Maps.frame(view, google, points, focus != null ? 16f : 15f, dp(32));
    }
}
