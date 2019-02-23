import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    val os = org.gradle.internal.os.OperatingSystem.current()

    val kglVersion = "0.1.5-dev-3"
    val lwjglVersion = "3.2.1"
    val lwjglNatives = when {
        os.isWindows -> "natives-windows"
        os.isLinux -> "natives-linux"
        os.isMacOsX -> "natives-macos"
        else -> TODO()
    }

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
    if (os.isWindows || System.getProperty("idea.active") != "true") mingwX64("mingw")
    if (os.isLinux || System.getProperty("idea.active") != "true") linuxX64("linux")
    if (os.isMacOsX || System.getProperty("idea.active") != "true") macosX64("macos")

    targets.filterIsInstance<KotlinNativeTarget>().forEach {
        it.compilations {
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
