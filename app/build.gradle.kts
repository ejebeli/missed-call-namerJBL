plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.EhsanJebeli.missedcallnamer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.EhsanJebeli.missedcallnamer"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }


    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            storePassword = "password123"
            keyAlias = "my-key"
            keyPassword = "password123"
        }
    }

    buildTypes {
        release {
            // فعال‌سازی R8 برای کوچک‌سازی و بهینه‌سازی کدها
            isMinifyEnabled = true
            // حذف فایل‌ها و منابع گرافیکی بلااستفاده از کتابخانه‌ها
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
