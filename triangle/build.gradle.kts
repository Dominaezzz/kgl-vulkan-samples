import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("multiplatform")
}

kotlin {
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

    val os = OperatingSystem.current()!!

    when {
        os.isWindows -> mingwX64("mingw")
        os.isLinux -> linuxX64("linux")
        os.isMacOsX -> macosX64("macos")
        else -> TODO()
    }.apply {
        binaries {
            executable {
                runTask!!.apply {
                    dependsOn("compileShaders")
                    args("")
                    workingDir("src/commonMain/resources")
                }
            }
        }
    }
}

task("compileShaders") {
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

task<JavaExec>("runProgramJvm") {
    dependsOn("compileShaders")

    main = "MainKt"
    workingDir = project.file("src/commonMain/resources")

    val target = kotlin.jvm()
    val compilation = target.compilations["main"]

    classpath = files(compilation.runtimeDependencyFiles, compilation.output.allOutputs)
}
