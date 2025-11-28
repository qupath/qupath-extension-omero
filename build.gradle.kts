plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
    `maven-publish`
}

qupathExtension {
    name = "qupath-extension-omero"
    group = "io.github.qupath"
    version = "0.2.1"
    description = "QuPath extension to support image reading using OMERO APIs"
    automaticModule = "io.github.qupath.extension.omero"
}

dependencies {
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    shadow(libs.qupath.ext.bioformats)
    shadow(libs.guava)
    shadow("org.openmicroscopy:omero-gateway:5.10.3")

    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
    testImplementation(libs.junit.platform)
    testImplementation(libs.qupath.ext.bioformats)
    testImplementation("org.openjfx:javafx-base:${libs.versions.javafx.get()}")
    testImplementation("org.openmicroscopy:omero-gateway:5.10.3")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

repositories {
    maven {
        name = "ome.maven"
        url = uri("https://artifacts.openmicroscopy.org/artifactory/maven")
    }

    maven {
        name = "unidata.releases<"
        url = uri("https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases")
    }

    // Required to find cisd:jhdf5:19.04.1
    maven {
        name = "SciJava Public"
        url = uri("https://maven.scijava.org/content/repositories/public/")
    }
}

publishing {
    repositories {
        maven {
            name = "SciJava"
            val releasesRepoUrl = uri("https://maven.scijava.org/content/repositories/releases")
            val snapshotsRepoUrl = uri("https://maven.scijava.org/content/repositories/snapshots")
            // Use gradle -Prelease publish
            url = if (project.hasProperty("release")) releasesRepoUrl else snapshotsRepoUrl
            credentials {
                username = System.getenv("MAVEN_USER")
                password = System.getenv("MAVEN_PASS")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name = "Apache License v2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
            }
        }
    }
}
