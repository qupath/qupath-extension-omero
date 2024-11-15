pluginManagement {
    plugins {
        kotlin("org.bytedeco.gradle-javacpp-platform") version "1.5.9"
    }
}

rootProject.name = "qupath-extension-omero"

extra["qupathVersion"] = "0.6.0-SNAPSHOT"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from("io.github.qupath:qupath-catalog:${extra["qupathVersion"]}")
        }
    }
    
    repositories {
        mavenCentral()

        maven {
            url = uri("https://maven.scijava.org/content/repositories/releases")
        }

        maven {
            url = uri("https://maven.scijava.org/content/repositories/snapshots")
        }

    }
}
