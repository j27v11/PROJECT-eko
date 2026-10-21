plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.eko.home"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.eko.home"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes { getByName("debug") { isDebuggable = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true; warningsAsErrors = false }
}
dependencies { testImplementation("junit:junit:4.13.2") }
