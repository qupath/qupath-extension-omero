plugins {
    // Main gradle plugin for building a Java library
    id("java-library")
    id("maven-publish")
    // To create a shadow/fat jar that bundle up all dependencies
    id("com.github.johnrengelman.shadow") version "8.1.1"
    // Add JavaFX dependencies
    alias(libs.plugins.javafx)
    // Version in settings.gradle
    id("org.bytedeco.gradle-javacpp-platform")
}

val moduleName = "qupath.extension.omero"
version = "0.1.1-rc1"
description = "QuPath extension to support image reading using OMERO APIs."
group = "io.github.qupath"
val qupathVersion = "0.6.0-rc1"
extra["qupathJavaVersion"] = libs.versions.jdk.get()

dependencies {
    shadow("io.github.qupath:qupath-gui-fx:${qupathVersion}")
    shadow("io.github.qupath:qupath-extension-bioformats:${qupathVersion}")
    shadow(libs.qupath.fxtras)
    shadow(libs.guava)

    shadow(libs.slf4j)

    // Auto-discover if OMERO-Gateway is available
    shadow("org.openmicroscopy:omero-gateway:5.8.2")

    testImplementation("io.github.qupath:qupath-gui-fx:${qupathVersion}")
    testImplementation("io.github.qupath:qupath-extension-bioformats:${qupathVersion}")
    testImplementation("org.openmicroscopy:omero-gateway:5.8.2")
    testImplementation(libs.junit)
    testImplementation("org.testcontainers:testcontainers:1.19.1")
    testImplementation("org.testcontainers:junit-jupiter:1.19.1")
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion,
                "Automatic-Module-Name" to moduleName
        ))
    }
}

/**
 * Copy necessary attributes, see
 * - https://github.com/qupath/qupath-extension-template/issues/9
 * - https://github.com/openjfx/javafx-gradle-plugin#variants
 */
configurations.shadow {
    val runtimeAttributes = configurations.runtimeClasspath.get().attributes
    runtimeAttributes.keySet().forEach { key ->
        attributes.attribute(key as Attribute<Any>, runtimeAttributes.getAttribute(key))
    }
}

tasks.processResources {
    from ("${projectDir}/LICENSE") {
        into("licenses/")
    }
}

tasks.register<Copy>("copyDependencies") {
    description = "Copy dependencies into the build directory for use elsewhere"
    group = "QuPath"

    from(configurations.default)
    into("build/libs")
}

java {
    toolchain {
        //languageVersion.set(JavaLanguageVersion.of(extra["qupathJavaVersion"] as String))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    if (findProperty("strictJavadoc") == null) {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://maven.scijava.org/content/repositories/releases")
    }

    maven {
        url = uri("https://maven.scijava.org/content/repositories/snapshots")
    }

    maven {
        name = "ome.maven"
        url = uri("https://artifacts.openmicroscopy.org/artifactory/maven")
    }

    maven {
        name = "unidata.releases<"
        url = uri("https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases")
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

