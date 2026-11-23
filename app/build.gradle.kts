plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub 비밀 금고에서 꺼낸 도장 파일 위치 (없으면 null)
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

// GitHub 빌드 번호를 버전 번호로 쓴다 (플레이스토어는 매번 더 큰 번호가 필요)
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// 외부 라이브러리 예외: 구글 광고(AdMob). 결제 라이브러리와 함께 허용된 유일한 예외
dependencies {
    implementation("com.google.android.gms:play-services-ads:24.4.0")
}

android {
    namespace = "com.pocketpet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jellytoc.pocketpet"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
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
            // 플레이스토어에 올릴 버전
            isMinifyEnabled = false
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
