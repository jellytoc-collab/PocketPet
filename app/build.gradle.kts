plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub 비밀 금고에서 꺼낸 도장 파일 위치 (없으면 null)
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.pocketpet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketpet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("upload") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // 도장이 있으면 항상 같은 도장으로 서명한다 → 덮어쓰기 업데이트 가능
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        getByName("release") {
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
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
