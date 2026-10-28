if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    throw GradleException(
        "AudienceLab Android SDK requires JDK 17 or newer. " +
            "Current JVM: ${System.getProperty("java.version")}"
    )
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "audiencelab-android-native-sdk"
include(":audiencelab-sdk")
include(":sample-app")
