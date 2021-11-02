plugins {
    kotlin("js") version "1.5.10"
}

group = "venus"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    js(IR) {
        binaries.executable()
        browser {

        }
    }
}