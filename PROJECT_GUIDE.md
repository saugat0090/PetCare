# Editing PetCare: start here

The app keeps its existing appearance. This guide explains where to edit the
interface and where to edit its behavior.

## Find the right XML file

All paths in this table are inside `app/src/main/res/`.

| What you want to edit | File |
| --- | --- |
| Welcome screen | `layout/activity_welcome.xml` |
| Log in, sign up, and password reset controls | `layout/activity_auth.xml` |
| Add or edit a pet | `layout/form_pet.xml` |
| Add or edit a routine | `layout/form_routine.xml` |
| Add or edit a location | `layout/form_location.xml` |
| Add or edit an expense | `layout/form_expense.xml` |
| Full-screen map | `layout/activity_map.xml` |
| Shared toolbar, scrolling area, and phone navigation | `layout/activity_base.xml` |
| Shared layout for tablets | `layout-sw600dp/activity_base.xml` |
| Reusable card | `layout/item_card.xml` |
| Reusable input used outside the four forms | `layout/item_field.xml` |
| Bottom navigation labels and icons | `menu/bottom_nav_menu.xml` |
| Add/edit form wording | `values/form_strings.xml` |
| App and navigation wording | `values/strings.xml` |
| Colors | `values/colors.xml` |
| Shared field style and app theme | `values/themes.xml` |
| Screen margins | `values/dimens.xml` and the `values-sw…dp` versions |

Android Studio groups tablet and phone versions under the same filename. Expand
`activity_base (2)` to see both. Do not move the tablet file into the ordinary
layout folder: Android chooses the correct version automatically.

## Read a layout

Each control starts with its name, followed by one attribute per line:

```xml
<TextView
    android:id="@+id/exampleLabel"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/app_name"
    android:textSize="18sp" />
```

- `android:id`: the name Java uses to find the control.
- `layout_width` and `layout_height`: how much space it occupies.
- `layout_margin…`: space outside a control; `padding…`: space inside it.
- `android:text` or `android:hint`: visible wording. `@string/…` points to a value file.
- `dp`: sizes and spacing. `sp`: text sizes, respecting the phone's font settings.
- `tools:…`: preview-only information; it does not change the running app.

Controls in a vertical `LinearLayout` appear in the order written. Comments label
the groups. A `TextInputLayout` wraps its `TextInputEditText`: keep these together
when moving a field.

The four `form_*.xml` files use `<merge>` because their controls are inserted into
the shared screen's existing vertical content column. This avoids a redundant
container. `tools:parentTag` supplies the preview container. Open **Split** to see
both the readable XML and Android Studio's preview.

## Java handles the actions

Java source lives in `app/src/main/java/com/example/assignmenttask/`.

| File | Responsibility |
| --- | --- |
| `EditorActivity.java` | Loads one `form_*.xml`, fills fields, validates input, and saves records |
| `Dashboard.java` | Home, Pets, Routines, Locations, More, and Expenses sections |
| `DetailActivity.java` | Displays a saved pet, routine, location, or expense |
| `DelegateActivity.java` | Routine selection, recipient details, and SMS preview |
| `BaseActivity.java` | Shared navigation, screen helpers, and map lifecycle |
| `AuthActivity.java`, `WelcomeActivity.java`, `MapActivity.java` | Behavior for their matching activity XML files |
| `Store.java`, `Repo.java` | Record operations and Firestore synchronization |
| `Accounts.java` | Authentication |
| `Images.java`, `Maps.java`, `MapsConfig.java`, `MyLocation.java`, `Money.java`, `Ui.java` | Reusable helpers |
| `PetCareApp.java` | App startup |

In `EditorActivity`, `petForm()`, `routineForm()`, `locationForm()`, and
`expenseForm()` name the XML they load. A line such as:

```java
bindField("name", R.id.petNameField);
```

connects a stored record value to its XML field. Change the field's appearance in
XML; change validation and actions in Java. If you rename an XML ID, use Android
Studio's **Refactor → Rename** so Java references are updated too.

Dashboard sections, detail screens, delegation steps, dialogs, and the conditional
Save/Delete buttons still create content in Java. They are documented here rather
than represented by empty XML files. This is not a complete migration of every
screen to XML. Helper classes such as `Money` do not need a layout.

## Common edits

1. **Rename the Location name label:** edit `label_locationName` in `values/form_strings.xml`.
2. **Change field spacing:** edit `layout_marginBottom` in the relevant `form_*.xml`.
3. **Move a pet field:** move its entire `TextInputLayout` block in `form_pet.xml`.
4. **Change routine options:** edit the option arrays or `bindChoice` call in `EditorActivity.java`.
5. **Change the location map height:** its preview height is in `form_location.xml`;
   its adaptive runtime height is calculated in `EditorActivity.locationForm()`.

The map, saved records, and spinner options are populated when the app runs.
A layout preview does not show live Google Maps or your saved data.

## Keep the formatting tidy

XML uses four-space indentation, one attribute per line, and blank lines between
controls. `.editorconfig` defines indentation for the source files. Android Studio's
**Code → Reformat Code** helps while editing. To apply the project's XML formatting
consistently from the project folder:

```sh
python3 scripts/format_xml.py
```

The script formats source XML only and checks that XML values and structure remain
unchanged. It never touches generated files under `build/`.

After functional edits, run the app and check the relevant screen. Automated checks:

```sh
bash gradlew testDebugUnitTest assembleDebug lintDebug
```
