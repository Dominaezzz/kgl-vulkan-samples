package sample.utils

import java.nio.file.Files
import java.nio.file.Paths

actual fun readAllBytes(path: String): ByteArray {
    return Files.readAllBytes(Paths.get(path))
}
