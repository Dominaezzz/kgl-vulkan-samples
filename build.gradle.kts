import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("multiplatform") version "1.3.60" apply false
}

val os = OperatingSystem.current()!!
val kglVersion = "0.1.8-dev-9"
val lwjglVersion = "3.2.1"
val lwjglNatives = when {
    os.isWindows -> "natives-windows"
    os.isLinux -> "natives-linux"
    os.isMacOsX -> "natives-macos"
    else -> TODO()
}

project(":utils") {
    apply(plugin = "kotlin-multiplatform")

    configure<KotlinMultiplatformExtension> {
        sourceSets {
            "commonMain" {
                dependencies {
                    implementation(kotlin("stdlib-common"))
                    api("com.kgl:kgl-glfw:$kglVersion")
                    api("com.kgl:kgl-glfw-vulkan:$kglVersion")
                    api("com.kgl:kgl-vulkan:$kglVersion")
                }
            }
            "commonTest" {
                dependencies {
                    implementation(kotlin("test-common"))
                    implementation(kotlin("test-annotations-common"))
                }
            }
        }

        jvm {
            compilations {
                "main" {
                    defaultSourceSet.dependencies {
                        implementation(kotlin("stdlib-jdk8"))
                        api("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
                        api("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
                    }
                }
                "test" {
                    defaultSourceSet.dependencies {
                        implementation(kotlin("test"))
                        implementation(kotlin("test-junit"))
                    }
                }
            }
        }
        when {
            os.isWindows -> mingwX64()
            os.isLinux -> linuxX64()
            os.isMacOsX -> macosX64()
            else -> TODO()
        }.apply {
            compilations {
                "main" {
                    defaultSourceSet {
                        kotlin.srcDir("src/nativeMain/kotlin")
                        resources.srcDir("src/nativeMain/resources")
                    }
                }
                "test" {
                    defaultSourceSet {
                        kotlin.srcDir("src/nativeTest/kotlin")
                        resources.srcDir("src/nativeTest/resources")
                    }
                }
            }
        }
    }
}

configure(listOf(project(":triangle"), project(":buffers"))) {
    apply(plugin = "kotlin-multiplatform")

    val compileShaders by tasks.registering {
        doLast {
            fileTree("src/main/resources/shaders").matching {
                include("**/*.vert")
                include("**/*.frag")
            }
                .forEach { aFile ->
                    exec {
                        commandLine("glslc", aFile.absolutePath, "-o", "${aFile.absolutePath}.spv")
                    }
                }
        }
    }

    configure<KotlinMultiplatformExtension> {
        sourceSets {
            "commonMain" {
                kotlin.srcDir("src/main/kotlin")
                resources.srcDir("src/main/resources")

                dependencies {
                    implementation(project(":utils"))
                    implementation(kotlin("stdlib-common"))
                }
            }
        }

        jvm {
            compilations {
                "main" {
                    dependencies {
                        implementation(kotlin("stdlib-jdk8"))
                    }
                }
            }
        }

        when {
            os.isWindows -> mingwX64()
            os.isLinux -> linuxX64()
            os.isMacOsX -> macosX64()
            else -> TODO()
        }.apply {
            binaries {
                executable {
                    runTask!!.apply {
                        dependsOn(compileShaders)
                        args("")
                        workingDir("src/main/resources")
                    }
                }
            }
        }

        task<JavaExec>("runProgramJvm") {
            dependsOn(compileShaders)

            main = "MainKt"
            workingDir = project.file("src/main/resources")

            val compilation = jvm().compilations["main"]

            classpath = files(compilation.runtimeDependencyFiles, compilation.output.allOutputs)
        }
    }
}

subprojects {
    repositories {
        maven("https://dl.bintray.com/dominaezzz/kotlin-native")

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
