package sample.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.*

actual fun readAllBytes(path: String): ByteArray {
    val file = fopen(path, "rb") ?: throw Exception("File not found.")
    try {
        fseek(file, 0, SEEK_END)
        val fileLength = ftell(file)
        fseek(file, 0, SEEK_SET)

        val buffer = ByteArray(fileLength)
        buffer.usePinned {
            fread(it.addressOf(0), 1u, fileLength.toULong(), file)
        }

        return buffer
    } finally {
        fclose(file)
    }
}
