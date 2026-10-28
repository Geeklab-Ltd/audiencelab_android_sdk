plugins {
    id("com.android.library")
    kotlin("android")
    id("maven-publish")
}

group = providers.gradleProperty("POM_GROUP_ID").get()
version = providers.gradleProperty("SDK_VERSION").get()

val sdkVersion = version.toString()
val versionedReleaseAarName = "audiencelab-sdk-release-$sdkVersion.aar"
val packageRepositoryForPublishing = System.getenv("AUDIENCELAB_PACKAGE_REPOSITORY")?.trim()?.lowercase()
val packageUsernameForPublishing = System.getenv("AUDIENCELAB_PACKAGE_USERNAME")?.trim()
val packageTokenForPublishing = System.getenv("AUDIENCELAB_PACKAGE_TOKEN")?.trim()

android {
    namespace = "app.geeklab.audiencelab.sdk"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.register<Copy>("packageVersionedReleaseAar") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/aar/audiencelab-sdk-release.aar"))
    into(layout.buildDirectory.dir("outputs/versioned-aar"))
    rename { versionedReleaseAarName }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("packageVersionedReleaseAar")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = providers.gradleProperty("POM_GROUP_ID").get()
                artifactId = providers.gradleProperty("POM_ARTIFACT_ID").get()
                version = providers.gradleProperty("SDK_VERSION").get()

                pom {
                    name.set(providers.gradleProperty("POM_NAME"))
                    description.set(providers.gradleProperty("POM_DESCRIPTION"))
                    url.set(providers.gradleProperty("POM_URL"))

                    licenses {
                        license {
                            name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                            url.set(providers.gradleProperty("POM_LICENSE_URL"))
                        }
                    }

                    developers {
                        developer {
                            id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                            name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                        }
                    }

                    scm {
                        url.set(providers.gradleProperty("POM_SCM_URL"))
                    }

                    if (!packageRepositoryForPublishing.isNullOrBlank()) {
                        withXml {
                            val node = asNode()
                            val distributionManagement = node.appendNode("distributionManagement")
                            val repository = distributionManagement.appendNode("repository")
                            repository.appendNode("id", "github")
                            repository.appendNode("url", "https://maven.pkg.github.com/$packageRepositoryForPublishing")
                        }
                    }
                }
            }
        }

        repositories {
            mavenLocal()

            if (!packageRepositoryForPublishing.isNullOrBlank() &&
                !packageUsernameForPublishing.isNullOrBlank() &&
                !packageTokenForPublishing.isNullOrBlank()
            ) {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/$packageRepositoryForPublishing")
                    credentials {
                        username = packageUsernameForPublishing
                        password = packageTokenForPublishing
                    }
                }
            }
        }
    }
}
