plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.example.codelabapp"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.commonmark:commonmark:0.22.0")
}

intellij {
    localPath.set("C:\\Users\\zalmek\\Android Studio1")
    plugins.set(listOf())
}

kotlin {
    jvmToolchain(21)
}

tasks {
    patchPluginXml {
        sinceBuild.set("253")
        untilBuild.set("253.*")
    }
    buildSearchableOptions {
        enabled = false
    }
}
