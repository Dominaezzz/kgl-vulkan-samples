import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") version "1.3.30" apply false
}

repositories {
    maven(url = "https://dl.bintray.com/dominaezzz/kotlin-native")

    mavenLocal()
    jcenter()
    mavenCentral()
}

subprojects {
    repositories {
        maven(url = "https://dl.bintray.com/dominaezzz/kotlin-native")

        mavenLocal()
        jcenter()
        mavenCentral()
    }

    afterEvaluate {
        configure<KotlinMultiplatformExtension> {
            sourceSets.all {
                languageSettings.apply {
                    enableLanguageFeature("InlineClasses")
                    useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
                }
            }
        }
    }
}
