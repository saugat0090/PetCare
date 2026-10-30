# 🐾 PetCare

New to the project? Start with [PROJECT_GUIDE.md](PROJECT_GUIDE.md) for a screen-to-file map, XML examples, and common edits.

An Android app that helps pet owners keep their pets' care organised: pet profiles, daily and weekly routines,
saved places such as the vet, nearby place search, SMS delegation to a caregiver, and an expense tracker.

Built with **Java**, **XML** and **Material 3**. Data is stored with **Firebase** (Authentication and Cloud Firestore)
and maps use the **Google Maps SDK for Android**.

| | |
| --- | --- |
| Package | `com.example.assignmenttask` |
| Min / target SDK | 26 / 36 |
| Java / JDK | Java 11 source level, JDK 21 to build |
| Desirable feature attempted | **Geotagging** (saving places with a map pin, and finding places nearby) |

> **Setup note:** `firebase_config.xml` is not in this repository because it holds private project values. Copy `app/src/main/res/values/firebase_config.xml.example` to `firebase_config.xml` and fill in your own Firebase values. Put `MAPS_API_KEY=...` in `~/.gradle/gradle.properties`.

---

## Features

- **Accounts**: sign up, log in, and a "Forgot password?" email (Firebase Authentication). Each user's data is private to their own account.
- **Pets**: add, view, edit and delete pets with an optional photo, breed, age, weight, medical notes, diet and favourite toys.
- **Routines**: daily or weekly care routines with a time, supplies and instructions. Tap *Pending* / *Completed* to tick a routine off. Completed routines disappear from Home after three seconds but remain in Routines with their last completion date. Completion resets for the next scheduled day.
- **Delegate by SMS**: pick routines, choose a caregiver (typed or from your contacts), preview the message, and open it as a draft in your own messaging app. You can include a Google Maps link to the routine's saved place.
- **Locations (geotagging)**
  - Save vet clinics, grooming salons, dog parks, pet supply stores and animal shelters with a coloured map pin.
  - Set the pin by tapping the expanded map; tapping a named map place also fills its name. Add Location has no category field.
  - A map preview on the Locations tab opens a full-screen map with a details panel and a **My location** button.
  - Link routines to a place (**Linked Care**).
  - **Find nearby**: search for real places around you (see below).
- **Expenses**: record spending per pet (category, amount, date, notes) and see the total.
- **Phone and tablet layouts**: a bottom bar on phones, a navigation rail and a centred content column on screens 600dp and wider. The location editor map scales with screen height.

### Find nearby

Open **Locations → Add location** to use **Find nearby**, below the expanded map.

1. Tap **Find nearby** and choose *All pet places* or one category (vet, groomer, dog park, pet store, shelter).
2. The first time, the app asks for location permission.
3. The app finds your position and opens your maps app (Google Maps or any app that handles `geo:` links) already searching for that kind of place around you.

This needs no extra Google API, billing or key, because the search itself runs in the maps app. The links are built in `MapsConfig.java`
and the button is in `EditorActivity.locationForm()`. Return to the editor and tap the place on the map to save its pin.
*Grooming Salon* is searched as "pet grooming" so you do not get hairdressers.

---

## Getting started

### 1. Firebase

1. Create a Firebase project and register an Android app with package `com.example.assignmenttask`. Copy the project values from the downloaded `google-services.json` (project number, API key, app ID, storage bucket, project ID) into `app/src/main/res/values/firebase_config.xml`. Firebase reads them at start-up, so no Google Services Gradle plugin is needed.
2. **Authentication → Sign-in method**: enable **Email/Password**.
3. **Firestore Database**: create a database, then paste the contents of `firestore.rules` into the **Rules** tab and publish.
   The rules let each signed-in user read and write only their own data.

Firestore layout:

```
users/{uid}                                          profile (name, email, photo file name)
users/{uid}/pets | routines | locations | expenses   one document per record
```

Photos are kept on the phone only (they are not uploaded).

### 2. Google Maps key

1. In Google Cloud (the Firebase project works), enable **Maps SDK for Android** and attach billing. Google requires it; the free monthly credit normally covers a coursework app.
2. Add the key to `gradle.properties`:
   ```properties
   MAPS_API_KEY=your_key_here
   ```
   It reaches the app through the `com.google.android.geo.API_KEY` meta-data in `AndroidManifest.xml` (filled in by a manifest placeholder in `app/build.gradle.kts`).
3. If the key is restricted to Android apps, add the package name and the **SHA-1** of the keystore you run with (`./gradlew signingReport` prints the debug one).
   A wrong or missing SHA-1 is the usual cause of a grey map with a Google logo.

> **Keep your key private.** Do not publish a real key in a public repository. Restrict it to your app (step 3), or keep it in your personal `~/.gradle/gradle.properties` instead of the project's.

### 3. Run it

Open the folder in Android Studio (JDK 21), install SDK 36.1, sync Gradle and run the `app` configuration.
Use a device or emulator image that includes **Google Play services** (a "Google APIs / Google Play" image). Without it the app shows "map unavailable" instead of a map.

Command line (use `gradlew.bat` on Windows):

```bash
./gradlew assembleDebug testDebugUnitTest      # build and run the unit tests
./gradlew connectedDebugAndroidTest            # Espresso test, needs a device or emulator
```

---

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Firebase and map tiles |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Requested **only** when you tap **My location** or **Find nearby**. Never at app start. |

No SMS permission is needed because messages are sent from your own messaging app, and no contacts permission is needed because the contact picker returns only the one contact you choose.

---

## Validation

Email format, password of at least 6 characters with a matching confirmation, required fields, pet age (not negative, up to 2 decimals),
pet weight 0.1 to 150 kg, expense amount (above 0, up to 2 decimals), phone number format before the SMS app opens, and a map pin to save a location.

Deleting a pet also removes its routines and photo (expense records are kept). Deleting a location keeps its routines and clears their link.

---

## Project structure

```
app/src/main/java/com/example/assignmenttask/
├── Screens
│   ├── WelcomeActivity      Log in / Sign up entry screen
│   ├── AuthActivity         Sign up, log in and forgot password
│   ├── Dashboard            Home, Pets, Routines, Locations, More (and Expenses)
│   ├── EditorActivity       Add / edit form for every record type, photo and map pin picking
│   ├── DetailActivity       Read-only details for a pet, routine, location or expense
│   ├── DelegateActivity     Three-step SMS delegation
│   └── MapActivity          Full-screen map with a details panel
├── Shared UI
│   ├── BaseActivity         Shared layout, navigation and helpers for building views and map previews
│   └── Ui                   Window-inset helper for screens that do not use BaseActivity
├── Data
│   ├── Store                The API the screens use (records, routines, profile)
│   ├── Repo                 In-memory copy of the data, synced with Firestore
│   ├── Accounts             Firebase Authentication
│   ├── Images               Saving and loading photos on the phone
│   └── Money                Currency formatting (change the symbol here)
└── Maps and location
    ├── Maps                 Pins, camera framing, Google Play services check
    ├── MapsConfig           Google Maps links (pin links and Find nearby searches)
    └── MyLocation           The phone's position (Fused Location Provider)
```

Records are plain JSON objects grouped by type (`pets`, `routines`, `locations`, `expenses`). `Repo` keeps them in memory so screens read instantly,
and writes go to Firestore, whose offline cache queues them until the phone is online again.

---

## Tests

| Test | What it checks |
| --- | --- |
| `MoneyTest` | Amount parsing and formatting |
| `StoreTest` | Saving and updating records, cascade delete, location unlinking, routines due today, per-day completion |
| `MapsTest` | Coordinates, the shared pin link, the **Find nearby** search links, the no-Play-services fallback |
| `ScreenTest` | Every screen builds (Robolectric), nearby search in the location editor, delayed completion removal and undo |
| `NavigationTest` | Espresso: add and edit a pet and visit every tab (needs a device) |

---

## Assignment coverage

| Requirement | Where |
| --- | --- |
| Home screen with Login and Signup | `WelcomeActivity` → `AuthActivity` |
| Register and log in | Firebase Authentication. "Forgot password?" calls `Accounts.sendReset`. Under **More** you can edit your name and picture and log out. |
| Create / view / edit / delete pets | `Dashboard` (Pets), `EditorActivity`, `DetailActivity` |
| Optional pet image | Photo picker on the pet form, copied to private storage |
| Daily and weekly routines | Routine form (category, repeat, day, time, supplies, instructions) |
| Edit, delete, mark completed | Routines screen, Home, and Routine Details |
| SMS delegation | `DelegateActivity` (typed number or **Choose from contacts**, optional location pin) |
| **Desirable feature: Geotagging** | Locations tab, `MapActivity`, location details and linked routines, **Find nearby** |
| Material Design, phone and tablet | Material 3 theme, bottom bar on phones and navigation rail on `sw600dp+` |
| Data storage | Cloud Firestore with its offline cache, plus private image files on the phone |

---

## Not implemented

Syncing photos to the cloud, scheduled reminders and notifications, offline map tiles, and the other two optional features (app integration and gestures).
