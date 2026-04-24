plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "xyz.mycompany.friendalert"
    compileSdk = 34

    buildFeatures {
        dataBinding = true
    }

    defaultConfig {
        applicationId = "xyz.mycompany.friendalert"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    implementation("com.github.vestrel00.contacts-android:core:0.3.1")

    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-annotations:1.3.0")
    ksp("io.insert-koin:koin-ksp-compiler:1.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.work:work-runtime:2.8.1")

    implementation("androidx.room:room-runtime:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")

    implementation("com.github.bumptech.glide:glide:4.15.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
}