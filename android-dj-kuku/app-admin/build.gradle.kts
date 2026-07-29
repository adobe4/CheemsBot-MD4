plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("com.google.gms.google-services") }

android { namespace = "com.djkuku.admin"; compileSdk = 35
    defaultConfig { applicationId = "com.djkuku.admin"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("com.google.firebase:firebase-auth-ktx")
}
