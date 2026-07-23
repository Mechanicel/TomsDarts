plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mechanicel.tomsdarts"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mechanicel.tomsdarts"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // Robolectric benoetigt Zugriff auf Android-Ressourcen im JVM-Unit-Test.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Schema-Export-Verzeichnis als Asset registrieren, damit Room-Migrationstests
    // auf die exportierten Schemas zugreifen koennen:
    // - androidTest: fuer Instrumented-Tests (liest aus den androidTest-Assets).
    // - debug: die host-seitigen Robolectric-Tests (test) lesen ueber den
    //   Instrumentation-Context die *gemergten Debug-Assets* (mergeDebugAssets).
    //   Reine test-SourceSet-Assets werden dort nicht gemergt, daher landen die
    //   Schemas ueber den debug-BuildType in den fuer Robolectric sichtbaren
    //   Assets; das Release-APK bleibt davon unberuehrt.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
        getByName("debug") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

// Room: exportiert das DB-Schema als JSON nach app/schemas (versioniert im Repo).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}