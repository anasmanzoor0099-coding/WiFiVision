import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigning = if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
        !keystoreProperties.getProperty(it).isNullOrBlank()
    }
} else {
    false
}

android {
    namespace = "com.wifivision.app"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.wifivision.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.1"
        buildConfigField(
            "String",
            "RELEASE_METADATA_URL",
            "\"https://wi-fi-vision-d6kt-theta.vercel.app/downloads/latest.json\""
        )
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"https://wi-fi-vision-backend.vercel.app\"")
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

gradle.taskGraph.whenReady {
    val releasePackagingTasks = setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "packageReleaseBundle",
        "signReleaseBundle"
    )
    val runningReleasePackaging = allTasks.any { it.name in releasePackagingTasks }
    if (runningReleasePackaging) {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release signing credentials missing: copy android/keystore.properties.example to android/keystore.properties and configure storeFile, storePassword, keyAlias, and keyPassword before building a release APK or AAB."
            )
        }
        val configuredStoreFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
        if (!configuredStoreFile.exists()) {
            throw GradleException(
                "Release keystore file not found at configured storeFile path (${configuredStoreFile.name}). Provide a valid upload keystore before building a release APK or AAB."
            )
        }
    }
}
