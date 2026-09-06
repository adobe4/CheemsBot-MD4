import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
}

group = "com.vinplay.desktop"
version = "1.0.1"

// Target 17 bytecode using whatever JDK (17+) runs the build, rather than pinning a toolchain
// that must be installed locally.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.vinplay.desktop.MainKt"

        nativeDistributions {
            // The bundled runtime is trimmed by jlink to the modules jdeps can see. JDBC drivers are
            // loaded reflectively via ServiceLoader, so java.sql was dropped and the app died with
            // "java/sql/DriverManager — Failed to launch JVM". Bundling every module also keeps
            // HTTPS working (jdk.crypto.ec) and covers anything else loaded reflectively; it costs
            // some size, which matters far less here than the app actually starting.
            includeAllModules = true

            // MSI needs the WiX toolset; the portable app image (createDistributable) always works,
            // so CI ships that as a zip and the installer only when the toolchain is available.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VinPlay Manager"
            packageVersion = "1.0.1"
            description = "Manage, search and test very large IPTV playlists"
            vendor = "VinPlay"

            windows {
                menu = true
                shortcut = true
                // Stable UUID so upgrades replace the previous install instead of stacking.
                upgradeUuid = "9F5B2C41-7E3A-4D18-9C77-2A6E4B0D51C3"
            }
        }
    }
}
