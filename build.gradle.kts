import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("multiplatform") version "1.3.30" apply false
}

repositories {
    maven(url = "https://dl.bintray.com/dominaezzz/kotlin-native")

    mavenLocal()
    jcenter()
    mavenCentral()
}

val os = OperatingSystem.current()!!
val kglVersion = "0.1.5"
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
            val commonMain by getting {
                dependencies {
                    implementation(kotlin("stdlib-common"))
                    api("com.kgl:kgl-glfw:$kglVersion")
                    api("com.kgl:kgl-glfw-vulkan:$kglVersion")
                    api("com.kgl:kgl-vulkan:$kglVersion")
                }
            }
            val commonTest by getting {
                dependencies {
                    implementation(kotlin("test-common"))
                    implementation(kotlin("test-annotations-common"))
                }
            }
        }

        jvm {
            compilations {
                val main by getting {
                    defaultSourceSet.dependencies {
                        implementation(kotlin("stdlib-jdk8"))
                        api("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
                        api("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
                    }
                }
                val test by getting {
                    defaultSourceSet.dependencies {
                        implementation(kotlin("test"))
                        implementation(kotlin("test-junit"))
                    }
                }
            }
        }
        when {
            os.isWindows -> mingwX64("mingw")
            os.isLinux -> linuxX64("linux")
            os.isMacOsX -> macosX64("macos")
            else -> TODO()
        }.apply {
            compilations {
                val main by getting {
                    defaultSourceSet {
                        kotlin.srcDir("src/nativeMain/kotlin")
                        resources.srcDir("src/nativeMain/resources")
                    }
                }
                val test by getting {
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
            fileTree("src/commonMain/resources/shaders").matching {
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

    configure<KotlinMultiplatformExtension>  {
        sourceSets {
            val commonMain by getting {
                dependencies {
                    implementation(project(":utils"))
                    implementation(kotlin("stdlib-common"))
                }
            }
        }

        jvm {
            val main by compilations.getting {
                defaultSourceSet {
                    dependencies {
                        implementation(kotlin("stdlib-jdk8"))
                    }
                }
            }
        }

        when {
            os.isWindows -> mingwX64("mingw")
            os.isLinux -> linuxX64("linux")
            os.isMacOsX -> macosX64("macos")
            else -> TODO()
        }.apply {
            binaries {
                executable {
                    runTask!!.apply {
                        dependsOn(compileShaders)
                        args("")
                        workingDir("src/commonMain/resources")
                    }
                }
            }
        }

        task<JavaExec>("runProgramJvm") {
            dependsOn(compileShaders)

            main = "MainKt"
            workingDir = project.file("src/commonMain/resources")

            val compilation = jvm().compilations["main"]

            classpath = files(compilation.runtimeDependencyFiles, compilation.output.allOutputs)
        }
    }
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
