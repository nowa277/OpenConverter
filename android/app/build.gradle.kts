plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openconverter.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.openconverter.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("openconverter-release.jks")
            storePassword = "openconverter2024"
            keyAlias = "openconverter"
            keyPassword = "openconverter2024"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    // ffmpeg-kit-full-gpl 6.0-2.LTS — fetched by android/scripts/fetch-ffmpeg-kit.sh, gitignored.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // ffmpeg-kit AAR has a compile-time dep on smart-exception-java (referenced by
    // FFmpegKitConfig.<clinit>). fileTree(*.aar) skips POM resolution, so we must
    // declare it explicitly — otherwise every ffmpeg call dies with
    // NoClassDefFoundError: com/arthenica/smartexception/java/Exceptions.
    implementation("com.arthenica:smart-exception-java:0.2.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Real JSON for JVM unit tests (the Android stub `org.json` is throw-on-call on JVM)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
