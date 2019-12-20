plugins {
    java
    application
    id("com.google.protobuf") version "0.8.10" apply false
}

group = "ru.ifmo.java"
version = "1.0-SNAPSHOT"

subprojects {
    apply {
        plugin("java")
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("com.google.protobuf:protobuf-java:3.10.0")
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
}
