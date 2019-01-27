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
    configure(listOf(mingwX64("mingw"), linuxX64("linux"), macosX64("macos"))) {
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
