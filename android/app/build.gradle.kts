plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wifivision.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wifivision.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"http://10.0.2.2:5000\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT_DEV_URL", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"\"")
            buildConfigField("boolean", "ALLOW_CLEARTEXT_DEV_URL", "false")
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
    testImplementation("junit:junit:4.13.2")
}
