pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Maven Central's CDN sometimes answers GitHub-hosted runner IPs with a miss for
        // artifacts that do exist (403/429 throttling, or 404s during a CDN blip). Gradle
        // reports that as "Plugin [...] was not found in any of the following sources" and
        // configuration dies before a single task runs, as happened to
        // org.jetbrains.kotlin.plugin.compose:2.3.20. Google mirrors Central byte for byte on
        // GCS, so list it as a fall-through: it is only queried when the repositories above
        // do not have the module, and Google's own artifacts still come from google().
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Same Maven Central fall-through as above, for libraries rather than build plugins.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
    }
}

rootProject.name = "ImanCore"
include(":app")
include(":shared")
