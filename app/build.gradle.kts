plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.sift"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.sift"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        create("release") {
            // CI can override with repository secrets; otherwise the bundled personal key is used so
            // every build signs identically and installs over the previous one.
            storeFile = rootProject.file(System.getenv("SIFT_KEYSTORE_PATH") ?: "keystore/sift-release.jks")
            storePassword = System.getenv("SIFT_KEYSTORE_PASSWORD") ?: "siftsift"
            keyAlias = System.getenv("SIFT_KEY_ALIAS") ?: "sift"
            keyPassword = System.getenv("SIFT_KEY_PASSWORD") ?: "siftsift"
        }
    }

    flavorDimensions += "network"
    productFlavors {
        create("offline") {
            dimension = "network"
            buildConfigField("boolean", "SELF_HOSTED", "false")
        }
        create("selfhosted") {
            dimension = "network"
            applicationIdSuffix = ".selfhosted"
            versionNameSuffix = "-selfhosted"
            buildConfigField("boolean", "SELF_HOSTED", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // A lint warning shouldn't block a personal sideload build.
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}
