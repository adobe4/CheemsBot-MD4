pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform's runtime classpath pulls androidx.lifecycle artifacts, which are
        // published to Google's Maven rather than Maven Central.
        google()
    }
}

rootProject.name = "VinPlayDesktop"
