package storage

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

fun File.outputStream(appendMode: Boolean = false): FileOutputStream {
    return FileOutputStream(this, appendMode)
}

fun Int.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
    buffer.putInt(this)
    return buffer.array()
}

data class StorageEntry(val pathSize: Int, val path: FileApi.Path, val contentSize: Int, val content: ByteArray) {
    companion object {
        fun of(path: FileApi.Path, content: ByteArray): StorageEntry {
            return StorageEntry(
                path.value.toByteArray().size,
                path,
                content.size,
                content
            )
        }

        fun fromStorage(storage: File, offset: Long): StorageEntry {
            return RandomAccessFile(storage, "r").use { file ->
                file.seek(offset)
                val pathSize = file.readInt()
                val path = FileApi.Path(ByteArray(pathSize).also { file.readFully(it) }.decodeToString())
                val contentSize = file.readInt()
                val content = ByteArray(contentSize).also { file.readFully(it) }
                StorageEntry(pathSize, path, contentSize, content)
            }
        }
    }

    fun writeToStorage(storage: File): Long {
        val offset = storage.length()
        storage.outputStream(true).use {
            it.write(pathSize.toByteArray())
            it.write(path.value.toByteArray())
            it.write(contentSize.toByteArray())
            it.write(content)
        }
        return offset
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StorageEntry

        if (pathSize != other.pathSize) return false
        if (path != other.path) return false
        if (contentSize != other.contentSize) return false
        return content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = pathSize
        result = 31 * result + path.hashCode()
        result = 31 * result + contentSize
        result = 31 * result + content.contentHashCode()
        return result
    }
}