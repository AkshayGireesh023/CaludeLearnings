import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kharcha.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kharcha.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0.1"
    }

    // Sign with your own fixed key when keystore.properties exists (kept out of git), so every
    // build can update the installed app. Without it, the standard debug key is used.
    val keyProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keyProps != null) {
            create("kharcha") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }
    val signing = signingConfigs.findByName("kharcha") ?: signingConfigs.getByName("debug")

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
        debug {
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module", "META-INF/versions/9/previous-compilation-data.bin",
            )
        }
    }
}

// Room schema JSONs for the migration test (debug builds only; not shipped in release).
android.sourceSets["debug"].assets.srcDir("$projectDir/schemas")
android.testOptions.unitTests.isIncludeAndroidResources = true

// Robolectric's Android runtime, fetched by Gradle so tests also work behind proxies / offline.
val robolectricRuntime: Configuration by configurations.creating
dependencies { robolectricRuntime("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6") }
val robolectricDir = layout.buildDirectory.dir("robolectric-jars")
val copyRobolectric by tasks.registering(Copy::class) {
    from(robolectricRuntime)
    into(robolectricDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectric)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Claude: the AI brain (chat, daily brief, transaction clean-up).
    implementation("com.anthropic:anthropic-java:3.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
