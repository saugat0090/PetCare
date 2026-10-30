plugins {
    alias(libs.plugins.android.application)
}

// Google Maps API key. Set MAPS_API_KEY in gradle.properties (the app still builds without one, but maps stay blank).
val mapsApiKey: String = (project.findProperty("MAPS_API_KEY") as String?) ?: ""

android {
    namespace = "com.example.assignmenttask"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.assignmenttask"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Makes the key available to AndroidManifest.xml (${MAPS_API_KEY}).
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildFeatures {
        buildConfig = false
    }

    testOptions {
        // Lets Robolectric unit tests use the app's resources.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // User interface
    implementation(libs.appcompat)
    implementation(libs.material)

    // Google Maps (map views and pins) and the Fused Location Provider ("Use my location", "Find nearby")
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Firebase: sign-in and the Firestore database
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
