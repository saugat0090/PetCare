package com.example.assignmenttask;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One screen for adding and editing every kind of record. The Intent carries the record "type"
 * (pets, routines, locations or expenses) and its "id" (empty when adding a new one).
 * The fields shown depend on the type. Everything typed is collected into a JSON record on save.
 */
public class EditorActivity extends BaseActivity {

    // Options for the drop-down lists.
    public static final String[] ROUTINE_CATEGORIES = {"Feeding", "Exercise", "Grooming", "Cleaning", "Medication", "Healthcare", "Other"};
    public static final String[] PET_TYPES = {"Dog", "Cat", "Bird", "Rabbit", "Hamster", "Fish", "Reptile", "Other"};
    public static final String[] EXPENSE_CATEGORIES = {"Food", "Veterinary", "Medication", "Grooming", "Supplies", "Other"};
    public static final String[] LOCATION_TYPES = {"Veterinary Clinic", "Grooming Salon", "Dog Park", "Pet Supply Store", "Animal Shelter"};
    private static final String[] WEEKDAYS = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

    private static final BigDecimal MAX_NUMBER = new BigDecimal("100000000");
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("0.1");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("150");

    private String type;
    private String id;
    private JSONObject record;               // the record being edited, updated from the form on save
    private String originalPhoto = "";       // the photo the record had before this edit
    private boolean saved;

    // The form controls, keyed by the record field they edit.
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, TextInputLayout> layouts = new LinkedHashMap<>();
    private final Map<String, Spinner> choices = new LinkedHashMap<>();
    private final Map<String, List<String>> choiceValues = new LinkedHashMap<>();   // stored value for each drop-down item

    // Pet photo.
    private ImageView photoPreview;
    private final List<String> createdPhotos = new ArrayList<>();   // photos picked during this edit
    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                String fileName = Images.save(this, uri);
                if (fileName == null) {
                    message("Photo not added", "That image could not be read. Try a different photo.");
                    return;
                }
                createdPhotos.add(fileName);
                Store.set(record, "photo", fileName);
                refreshPhoto();
            });

    // Location form: the map, its GoogleMap (null until loaded) and the single pin.
    private MapView formMap;
    private GoogleMap formGoogle;
    private Marker formMarker;
    private final List<Marker> nearbyMarkers = new ArrayList<>();   // pins from "Find nearby"
    private int nearbyRequest;
    private TextView mapCaption;
    private final MyLocation myLocation = new MyLocation(this);

    // ---------------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        type = getIntent().getStringExtra("type");
        id = getIntent().getStringExtra("id");
        if (type == null) {
            finish();
            return;
        }

        boolean editing = loadRecord(state);
        title((editing ? "Edit " : "Add ") + singular(type), true);
        select(type);

        switch (type) {
            case "pets":
                petForm();
                break;
            case "routines":
                routineForm();
                break;
            case "locations":
                locationForm();
                break;
            case "expenses":
                expenseForm();
                break;
            default:
                finish();
                return;
        }

        button(saveLabel(), this::save);
        if (editing) {
            MaterialButton delete = button("Delete " + singular(type).toLowerCase(Locale.ROOT), this::confirmDelete);
            delete.setBackgroundTintList(ColorStateList.valueOf(color(R.color.danger)));
        }
    }

    /**
     * Loads the record to edit, starts a new one, or restores an unsaved draft after rotation.
     * Returns true when an existing record is being edited.
     */
    private boolean loadRecord(Bundle state) {
        record = store.find(type, id);
        boolean editing = record != null;

        if (editing) {
            originalPhoto = record.optString("photo");
        } else {
            record = new JSONObject();
            if (getIntent().hasExtra("locationId")) {
                Store.set(record, "locationId", getIntent().getStringExtra("locationId"));
            }
        }

        if (state != null) {
            try {
                record = new JSONObject(state.getString("draft", "{}"));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            ArrayList<String> created = state.getStringArrayList("created");
            if (created != null) {
                createdPhotos.addAll(created);
            }
        }
        return editing;
    }

    /** The text on the Save button. */
    private String saveLabel() {
        switch (type) {
            case "pets": return "Save pet";
            case "routines": return "Save Routine";
            case "expenses": return "Save Expense";
            default: return "Save Location";
        }
    }

    /** Singular display name of a record type ("pets" becomes "Pet"). */
    public static String singular(String type) {
        switch (type) {
            case "routines": return "Care Routine";
            case "locations": return "Location";
            case "expenses": return "Expense";
            default: return "Pet";
        }
    }

    /** The warning shown when deleting a record of this type. */
    public static String deleteMessage(String type) {
        if (type.equals("pets")) {
            return "This also deletes this pet’s photo and routines. Expense records are kept. This cannot be undone.";
        }
        if (type.equals("locations")) {
            return "Routines are retained, but their link to this location is removed. This cannot be undone.";
        }
        return "This saved record will be permanently deleted. This cannot be undone.";
    }

    /** Asks before deleting the record being edited. */
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + record.optString("name") + "?")
                .setMessage(deleteMessage(type))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.delete(type, id);
                    finish();
                })
                .show();
    }

    // ---------------------------------------------------------------------------------------------
    // Form building blocks
    // ---------------------------------------------------------------------------------------------

    /** Connects an XML text field to a record key, for loading and saving. */
    private EditText bindField(String key, int layoutId) {
        TextInputLayout layout = findViewById(layoutId);
        EditText edit = layout.getEditText();
        edit.setText(record.optString(key));
        fields.put(key, edit);
        layouts.put(key, layout);
        return edit;
    }

    /** Fills an XML spinner with options whose labels and saved values match. */
    private void bindChoice(String key, int spinnerId, String[] options) {
        List<String> values = new ArrayList<>(Arrays.asList(options));
        bindChoice(key, spinnerId, values, values);
    }

    /** Fills an XML spinner and restores its saved selection. */
    private void bindChoice(String key, int spinnerId, List<String> labels, List<String> values) {
        Spinner spinner = findViewById(spinnerId);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int selected = values.indexOf(record.optString(key));
        if (selected >= 0) spinner.setSelection(selected);
        choices.put(key, spinner);
        choiceValues.put(key, values);
    }

    /** Adds a drop-down that lists another record type, for example the saved pets or locations. */
    private void bindLink(String key, int spinnerId, String label, String target, boolean optional, int hintId) {
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();

        // The first item is the empty choice.
        labels.add(optional ? "None" : "Choose " + label.replace(" *", "").toLowerCase(Locale.ROOT));
        values.add("");
        for (JSONObject item : store.all(target)) {
            labels.add(item.optString("name"));
            values.add(item.optString("id"));
        }

        bindChoice(key, spinnerId, labels, values);
        if (!optional && values.size() == 1) {
            TextView hint = findViewById(hintId);
            hint.setText("Add a " + singular(target).toLowerCase(Locale.ROOT) + " first using the bottom navigation.");
            hint.setVisibility(View.VISIBLE);
        }
    }

    /** Runs an action whenever a different drop-down item is selected. */
    private static void onSelectionChanged(Spinner spinner, Runnable action) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
                action.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    /** Adds a date field that opens a date picker (the date is never typed by hand). */
    private void bindDate(String key, int layoutId) {
        EditText edit = bindField(key, layoutId);
        if (edit.getText().length() == 0) {
            edit.setText(LocalDate.now().toString());
        }
        edit.setFocusable(false);
        edit.setOnClickListener(v -> showDatePicker(edit));

        TextInputLayout layout = layouts.get(key);
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_calendar);
        layout.setEndIconOnClickListener(v -> showDatePicker(edit));
    }

    private void showDatePicker(EditText edit) {
        LocalDate current;
        try {
            current = LocalDate.parse(edit.getText());
        } catch (RuntimeException e) {
            current = LocalDate.now();
        }
        new DatePickerDialog(this,
                (view, year, month, day) -> edit.setText(LocalDate.of(year, month + 1, day).toString()),
                current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    /** Adds a time field that opens a time picker. */
    private void time() {
        EditText edit = bindField("time", R.id.routineTimeField);
        if (edit.getText().length() == 0) {
            edit.setText(R.string.default_time);
        }
        edit.setFocusable(false);
        edit.setOnClickListener(v -> {
            LocalTime current = LocalTime.parse(edit.getText());
            new TimePickerDialog(this,
                    (view, hour, minute) -> edit.setText(String.format(Locale.ROOT, "%02d:%02d", hour, minute)),
                    current.getHour(), current.getMinute(), true).show();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // The four forms
    // ---------------------------------------------------------------------------------------------

    /** Appearance and field order: res/layout/form_pet.xml. */
    private void petForm() {
        getLayoutInflater().inflate(R.layout.form_pet, content, true);
        photoSection();
        bindField("name", R.id.petNameField);
        bindChoice("species", R.id.petSpeciesSpinner, PET_TYPES);
        bindField("breed", R.id.petBreedField);
        bindField("age", R.id.petAgeField);
        bindField("weight", R.id.petWeightField);
        bindChoice("gender", R.id.petGenderSpinner, new String[]{"Male", "Female"});
        bindField("medical", R.id.petMedicalField);
        bindField("allergies", R.id.petAllergiesField);
        bindField("diet", R.id.petDietField);
        bindField("notes", R.id.petNotesField);
    }

    /** Appearance and field order: res/layout/form_routine.xml. */
    private void routineForm() {
        getLayoutInflater().inflate(R.layout.form_routine, content, true);
        bindField("name", R.id.routineNameField);
        bindLink("petId", R.id.routinePetSpinner, "Pet", "pets", false, R.id.routinePetHint);
        bindChoice("category", R.id.routineCategorySpinner, ROUTINE_CATEGORIES);
        bindChoice("frequency", R.id.routineFrequencySpinner, new String[]{"Daily", "Weekly"});
        bindChoice("day", R.id.routineDaySpinner, WEEKDAYS);

        Spinner repeat = choices.get("frequency");
        Runnable showDayOnlyWhenWeekly = () -> {
            int visibility = "Weekly".equals(repeat.getSelectedItem()) ? View.VISIBLE : View.GONE;
            findViewById(R.id.routineDayLabel).setVisibility(visibility);
            findViewById(R.id.routineDaySpinner).setVisibility(visibility);
        };
        onSelectionChanged(repeat, showDayOnlyWhenWeekly);
        showDayOnlyWhenWeekly.run();

        time();
        bindField("supplies", R.id.routineSuppliesField);
        bindLink("locationId", R.id.routineLocationSpinner, "Location (optional)", "locations", true, 0);
        bindField("notes", R.id.routineNotesField);
    }

    /** Appearance and field order: res/layout/form_expense.xml. */
    private void expenseForm() {
        getLayoutInflater().inflate(R.layout.form_expense, content, true);
        bindLink("petId", R.id.expensePetSpinner, "Pet *", "pets", false, R.id.expensePetHint);
        bindField("name", R.id.expenseNameField);
        bindChoice("category", R.id.expenseCategorySpinner, EXPENSE_CATEGORIES);
        bindField("amount", R.id.expenseAmountField);
        layouts.get("amount").setPrefixText(Money.SYMBOL + " ");
        bindDate("date", R.id.expenseDateField);
        bindField("notes", R.id.expenseNotesField);
    }

    /** XML owns the controls; Java loads the live map and handles its lifecycle. */
    private void locationForm() {
        getLayoutInflater().inflate(R.layout.form_location, content, true);
        bindField("name", R.id.locationNameField);
        mapCaption = findViewById(R.id.locationMapCaption);
        findViewById(R.id.findNearby).setOnClickListener(v -> findNearby(""));   // vets and pet services only
        FrameLayout mapHost = findViewById(R.id.locationMapHost);

        formMap = createMap(false);
        if (formMap == null) {
            mapHost.getLayoutParams().height = dp(160);
            mapHost.addView(mapUnavailable(), new FrameLayout.LayoutParams(-1, -1));
            ((LinearLayout.LayoutParams) mapCaption.getLayoutParams()).bottomMargin = dp(16);
            return;
        }

        int mapHeight = Math.max(280, Math.min(520,
                (int) (getResources().getConfiguration().screenHeightDp * 0.5f)));
        mapHost.getLayoutParams().height = dp(mapHeight);
        MapBox box = new MapBox(this);
        box.setBackground(rounded(R.color.mint, 12));
        box.setClipToOutline(true);
        box.addView(formMap, new FrameLayout.LayoutParams(-1, -1));
        mapHost.addView(box, new FrameLayout.LayoutParams(-1, -1));
        formMap.getMapAsync(this::onFormMapReady);
    }

    /**
     * Gets the phone's position (asking for location permission the first time), then looks up that
     * kind of place around it and shows the results as pins on this screen's own map. Tapping a pin
     * fills in the name and moves the location pin there. An empty category means any pet place.
     */
    private void findNearby(String category) {
        if (formGoogle == null) {
            Toast.makeText(this, "The map is not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Finding places near you…", Toast.LENGTH_SHORT).show();
        int request = ++nearbyRequest;   // lets a newer search replace an older one that is still loading
        myLocation.locate(position -> {
            showMyPositionDot();
            NearbyPlaces.search(this, category, position, places -> {
                if (request != nearbyRequest || isFinishing() || isDestroyed()) {
                    return;
                }
                showNearby(position, places);
            });
        });
    }

    /** Turns on the blue "you are here" dot (only called once location permission is granted). */
    @SuppressLint("MissingPermission")
    private void showMyPositionDot() {
        if (formGoogle != null && myLocation.granted()) {
            formGoogle.setMyLocationEnabled(true);
        }
    }

    /** Replaces any earlier results with pins for these places and zooms the map to show them. */
    private void showNearby(LatLng position, List<NearbyPlaces.Place> places) {
        for (Marker old : nearbyMarkers) {
            old.remove();
        }
        nearbyMarkers.clear();

        if (places == null) {
            String problem = "Could not load places. Check the internet connection and that Places API (New) is enabled for your Maps key.";
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            mapCaption.setText(problem);
            mapCaption.setTextColor(color(R.color.brand_deep));
            formGoogle.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 14f));
            return;
        }
        if (places.isEmpty()) {
            String none = "No matching places were found within 15 km of this position. Tap the map to place the pin by hand.";
            Toast.makeText(this, none, Toast.LENGTH_LONG).show();
            mapCaption.setText(none);
            mapCaption.setTextColor(color(R.color.brand_deep));
            formGoogle.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 14f));
            return;
        }

        List<LatLng> points = new ArrayList<>();
        points.add(position);
        for (NearbyPlaces.Place place : places) {
            Marker marker = formGoogle.addMarker(Maps.markerOptions(this, place.position, place.category)
                    .title(place.name)
                    .snippet(place.category + " · " + distanceText(place.distanceMetres)));
            if (marker != null) {
                marker.setTag(place);
                nearbyMarkers.add(marker);
                points.add(place.position);
            }
        }
        Maps.frame(formMap, formGoogle, points, 15f, dp(48));
        mapCaption.setText(places.size() + " place(s) found. Tap a pin, then tap its name to use it.");
        mapCaption.setTextColor(color(R.color.brand_deep));
    }

    /** "850 m" or "2.3 km", shown under each nearby pin's name. */
    private static String distanceText(float metres) {
        return metres < 1000
                ? Math.round(metres) + " m away"
                : String.format(java.util.Locale.ROOT, "%.1f km away", metres / 1000f);
    }

    /** A tap on a nearby-place pin: select it as this location (name + coordinates). */
    private void useNearbyPlace(NearbyPlaces.Place place) {
        fields.get("name").setText(place.name);
        setPin(place.position);
    }

    /** The map has loaded: let the user place the pin, and show the saved pin when editing. */
    private void onFormMapReady(GoogleMap google) {
        formGoogle = google;
        google.setOnMapClickListener(this::setPin);
        google.setOnPoiClickListener(place -> {
            fields.get("name").setText(place.name);
            setPin(place.latLng);
        });
        google.setOnMapLongClickListener(this::setPin);
        // First tap on a nearby pin shows its name; tapping the pin's name bubble selects it.
        google.setOnInfoWindowClickListener(marker -> {
            if (marker.getTag() instanceof NearbyPlaces.Place) {
                useNearbyPlace((NearbyPlaces.Place) marker.getTag());
                marker.hideInfoWindow();
            }
        });

        LatLng savedPin = latLng(record);
        if (savedPin != null) {
            placePin(savedPin);
            google.moveCamera(CameraUpdateFactory.newLatLngZoom(savedPin, 16f));
        } else {
            google.moveCamera(CameraUpdateFactory.newLatLngZoom(Maps.DEFAULT_CENTER, 6f));
        }
    }

    /** Preserve the pin colour of existing records without a category field. */
    private String currentCategory() {
        return record.optString("category");
    }

    /** Stores the chosen coordinates on the record (6 decimals is about 10 cm) and moves the pin there. */
    private void setPin(LatLng point) {
        Store.set(record, "latitude", String.format(Locale.ROOT, "%.6f", point.latitude));
        Store.set(record, "longitude", String.format(Locale.ROOT, "%.6f", point.longitude));
        placePin(point);
    }

    /** Creates the pin the first time, otherwise moves it, and shows the coordinates under the map. */
    private void placePin(LatLng point) {
        if (formGoogle == null) {
            return;
        }
        if (formMarker == null) {
            formMarker = formGoogle.addMarker(Maps.markerOptions(this, point, currentCategory()));
        } else {
            formMarker.setPosition(point);
        }
        mapCaption.setText(String.format(Locale.ROOT, "Selected: %.5f, %.5f", point.latitude, point.longitude));
        mapCaption.setTextColor(color(R.color.brand_deep));
    }

    /**
     * Holds the map inside the scrolling form. It tells the parent ScrollView not to steal the
     * drag gestures, so the map can be panned instead of scrolling the page.
     */
    private static class MapBox extends FrameLayout {
        MapBox(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            boolean fingerDown = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(fingerDown);
            }
            return super.dispatchTouchEvent(event);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pet photo
    // ---------------------------------------------------------------------------------------------

    /** The photo preview with Choose and Remove buttons. The picture is kept in private app storage. */
    private void photoSection() {
        photoPreview = findViewById(R.id.petPhotoPreview);
        findViewById(R.id.choosePetPhoto).setOnClickListener(v -> photoPicker.launch("image/*"));
        findViewById(R.id.removePetPhoto).setOnClickListener(v -> {
            Store.set(record, "photo", "");
            refreshPhoto();
        });
        refreshPhoto();
    }

    /** Shows the current photo, or the paw placeholder when there is none. */
    private void refreshPhoto() {
        Bitmap bitmap = Images.load(this, record.optString("photo"), 512);
        if (bitmap != null) {
            photoPreview.setPadding(0, 0, 0, 0);
            photoPreview.setImageTintList(null);
            photoPreview.setImageBitmap(bitmap);
            photoPreview.setContentDescription("Selected pet photo");
        } else {
            photoPreview.setPadding(dp(32), dp(32), dp(32), dp(32));
            photoPreview.setImageResource(R.drawable.ic_paw);
            photoPreview.setImageTintList(ColorStateList.valueOf(color(R.color.brand)));
            photoPreview.setContentDescription("No photo selected");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------------------------------

    /** Copies every form control back into the record. */
    private void collect() {
        for (Map.Entry<String, EditText> entry : fields.entrySet()) {
            Store.set(record, entry.getKey(), entry.getValue().getText().toString().trim());
        }
        for (Map.Entry<String, Spinner> entry : choices.entrySet()) {
            int index = entry.getValue().getSelectedItemPosition();
            String value = index < 0 ? "" : choiceValues.get(entry.getKey()).get(index);
            Store.set(record, entry.getKey(), value);
        }
    }

    /** Keeps the unsaved form across rotation. */
    @Override
    protected void onSaveInstanceState(Bundle state) {
        collect();
        state.putString("draft", record.toString());
        state.putStringArrayList("created", new ArrayList<>(createdPhotos));
        super.onSaveInstanceState(state);
    }

    /** Deletes photos picked during an abandoned edit so they do not pile up on the phone. */
    @Override
    protected void onDestroy() {
        if (!saved && isFinishing()) {
            for (String fileName : createdPhotos) {
                Images.delete(this, fileName);
            }
        }
        super.onDestroy();
    }

    /** Validates the form, saves the record, tidies up unused photos and closes the screen. */
    private void save() {
        collect();
        if (!isValid()) {
            return;
        }
        store.save(type, record);

        // Keep only the photo that ended up on the record.
        String finalPhoto = record.optString("photo");
        for (String fileName : createdPhotos) {
            if (!fileName.equals(finalPhoto)) {
                Images.delete(this, fileName);
            }
        }
        if (!originalPhoto.isEmpty() && !originalPhoto.equals(finalPhoto)) {
            Images.delete(this, originalPhoto);
        }
        saved = true;
        finish();
    }

    /** Checks the rules for the record type. Shows the problem and returns false if something is wrong. */
    private boolean isValid() {
        if (!required("name")) {
            return false;
        }
        switch (type) {
            case "pets":
                return required("breed") && number("age", true, 2) && weightInRange();
            case "routines":
                return hasPet();
            case "expenses":
                return hasPet() && number("amount", false, 2);
            case "locations":
                return hasPin();
            default:
                return true;
        }
    }

    /** Shows an error on a field, focuses it, and returns false so callers can return it directly. */
    private boolean fail(String key, String message) {
        EditText field = fields.get(key);
        field.setError(message);
        field.requestFocus();
        return false;
    }

    /** The field must not be empty. */
    private boolean required(String key) {
        return !record.optString(key).isEmpty() || fail(key, "Required");
    }

    /** The field must be a number with a limited number of decimals (and a sane maximum). */
    private boolean number(String key, boolean zeroAllowed, int maxDecimals) {
        BigDecimal value = parseNumber(record.optString(key));
        boolean valid = value != null
                && value.signum() >= 0
                && (zeroAllowed || value.signum() > 0)
                && value.compareTo(MAX_NUMBER) <= 0
                && value.scale() <= maxDecimals;
        if (valid) {
            return true;
        }
        String kind = zeroAllowed ? "non-negative" : "positive";
        return fail(key, "Enter a " + kind + " number with up to " + maxDecimals + " decimals");
    }

    /** Weight can be anything from 0.1 to 150 kg, so small birds and giant breeds are both accepted. */
    private boolean weightInRange() {
        BigDecimal weight = parseNumber(record.optString("weight"));
        boolean valid = weight != null && weight.compareTo(MIN_WEIGHT) >= 0 && weight.compareTo(MAX_WEIGHT) <= 0;
        return valid || fail("weight", "Enter a weight between 0.1 and 150 kg");
    }

    /** Reads a decimal number, or returns null when the text is not one. */
    private static BigDecimal parseNumber(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Routines and expenses belong to a pet, so a saved pet must be chosen. */
    private boolean hasPet() {
        if (store.find("pets", record.optString("petId")) != null) {
            return true;
        }
        message("Select a pet", "A saved pet is required for this record.");
        return false;
    }

    /** A location needs a map pin. */
    private boolean hasPin() {
        if (latLng(record) != null) {
            return true;
        }
        mapCaption.setText("Tap the map to set the location (required)");
        mapCaption.setTextColor(color(R.color.danger));
        return false;
    }
}
