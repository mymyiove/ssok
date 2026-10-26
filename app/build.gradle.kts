plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}
android {
    namespace = "app.parkingcoach"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.parkingcoach"
        minSdk = 28            // Pleos 요구: API 28+
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
    }
    // release 서명: 키스토어는 저장소에 두지 않는다(환경변수). 없으면 debug 키로 서명해 '실행 가능한 release' 는 유지
    signingConfigs {
        create("release") {
            val ks = System.getenv("SSOK_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks); storePassword = System.getenv("SSOK_KEYSTORE_PASS"); keyAlias = System.getenv("SSOK_KEY_ALIAS") ?: "ssok"; keyPassword = System.getenv("SSOK_KEY_PASS") ?: System.getenv("SSOK_KEYSTORE_PASS")
            } else { initWith(getByName("debug")) }
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") }
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    useLibrary("android.car")   // AAOS CarPropertyManager: 기어(GEAR_SELECTION) 폴백 — Pleos Gear 도메인은 deprecated
    sourceSets["main"].kotlin.srcDir("src/main/kotlin"); sourceSets["main"].java.srcDir("src/main/java")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")   // Pleos Connect Vehicle SDK (읽기: 조향각·깜빡이·브레이크·기어)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")   // 에뮬레이터 없이 앱을 띄워 프레임 루프·터치·결과까지 돌려 보는 실행 테스트(RunThroughTest)
}
tasks.withType<Test>().configureEach { maxHeapSize = "4g" }   // Robolectric 실행 테스트(수천 프레임)
