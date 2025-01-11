plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.turtlepaw.smartbattery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.turtlepaw.scheduler"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.api)

// Add this line if you want to support Shizuku
    implementation(libs.provider)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.material)

    compileOnly(project(":hidden-api"))

    annotationProcessor("dev.rikka.tools.refine:annotation-processor:4.4.0")
    implementation("dev.rikka.tools.refine:runtime:4.4.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    implementation("com.anggrayudi:android-hidden-api:30.0")

    // Kotlin + coroutines
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("io.github.raamcosta.compose-destinations:core:2.1.0-beta14")
    ksp("io.github.raamcosta.compose-destinations:ksp:2.1.0-beta14")

    // for bottom sheet destination support, also add
    implementation("io.github.raamcosta.compose-destinations:bottom-sheet:2.1.0-beta14")

    // Icons
    implementation(libs.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.gson)
}

ksp {
    arg("compose-destinations.generateNavGraphs", "true") // Enable NavGraphs generation
    arg("compose-destinations.navGraph.visibility", "internal") // Set visibility (optional)
    arg("compose-destinations.navGraph.moduleName", "app") // Set module name (optional)
    arg("room.schemaLocation", "$projectDir/schemas")
}