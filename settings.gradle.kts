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

rootProject.name = "aosp_poc"
include(":app")
// Add the submodule as a library
include(":keyboard-autofill-library")
project(":keyboard-autofill-library").projectDir = file("keyboard-autofill/app")