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
    // 광고 동의 창(UMP): 유럽 등에서 광고 전에 동의를 받는 구글 라이브러리 (광고 예외에 포함)
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    // 구글 Play 결제(구독): 광고와 함께 허용된 예외
    implementation("com.android.billingclient:billing:8.0.0")
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
            // 시험용 버전: 구글이 주는 테스트 광고 ID (눌러도 괜찮다)
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            resValue("string", "admob_banner_id", "ca-app-pub-3940256099942544/9214589741")
            resValue("string", "admob_app_open_id", "ca-app-pub-3940256099942544/9257395921")
            // 도장이 있으면 항상 같은 도장으로 서명한다 → 덮어쓰기 업데이트 가능
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        getByName("release") {
            // 플레이스토어 버전: 내 진짜 AdMob 광고 ID
            // 🚨 진짜 광고는 절대 직접 누르지 않는다 (계정 정지 위험)
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6732124200971826~3609405963"
            resValue("string", "admob_banner_id", "ca-app-pub-6732124200971826/6840919016")
            resValue("string", "admob_app_open_id", "ca-app-pub-6732124200971826/3429121761")
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
