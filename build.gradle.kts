import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.0"
    id("com.ncorti.ktfmt.gradle") version "0.24.0"
}

group = "io.github.philkes"

version = "1.0.4"

repositories { mavenCentral() }

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
    // uses commons-compress:1.12, higher version incompatible with some android projects
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("com.deepl.api:deepl-java:1.10.3")
    implementation("com.google.cloud:google-cloud-translate:2.77.0")
    implementation("com.azure:azure-ai-translation-text:1.1.6")
    implementation("com.azure:azure-core-http-okhttp:1.13.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.8")
    implementation("com.openai:openai-java:4.6.1")

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
    testImplementation("io.mockk:mockk:1.13.5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test { useJUnitPlatform() }

kotlin {
    jvmToolchain(8)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
}

java { withSourcesJar() }

ktfmt { kotlinLangStyle() }

gradlePlugin {
    website = "https://github.com/PhilKes/auto-translation-gradle-plugin"
    vcsUrl = "https://github.com/PhilKes/auto-translation-gradle-plugin"
    description = "Automatically translate your strings.xml via external APIs"
    plugins {
        create("autoTranslateStrings") {
            id = "io.github.philkes.auto-translation"
            displayName = "Auto Translations"
            description =
                "Auto. translate your Android project (strings.xml + Fastlane) into any language using Translation providers like DeepL, Google, Azure AI, OpenAI, LibreTranslate"
            tags =
                listOf(
                    "android",
                    "translation",
                    "fastlane",
                    "strings.xml",
                    "ai",
                    "google",
                    "azure",
                    "deepl",
                    "libretranslate",
                    "openai",
                )
            implementationClass = "io.github.philkes.auto.translation.plugin.AutoTranslatePlugin"
        }
    }
}
