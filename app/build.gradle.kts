plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.parkingcoach"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.parkingcoach"
        minSdk = 28            // Pleos 요구: API 28+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].kotlin.srcDir("src/main/kotlin"); sourceSets["main"].java.srcDir("src/main/java")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")   // Pleos Connect Vehicle SDK (읽기: 조향각·깜빡이·브레이크·기어)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
