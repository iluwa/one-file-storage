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

val EMPTY_BYTE_ARRAY = ByteArray(0)

sealed class StorageEntry(
    val pathSize: Int,
    val path: FileApi.Path,
    val contentSize: Int,
    val content: ByteArray
) {
    companion object {
        fun fromStorage(storage: File, offset: Long): StorageEntry {
            return RandomAccessFile(storage, "r").use { file ->
                file.seek(offset)
                val type = file.readByte()
                val pathSize = file.readInt()
                val decodedPath = ByteArray(pathSize).also { file.readFully(it) }.decodeToString()
                val path = when (type) {
                    0.toByte() -> FileApi.File(decodedPath)
                    1.toByte() -> FileApi.Folder(decodedPath)
                    else -> error("Unknown type of the storage entry")
                }
                val contentSize = file.readInt()
                val content = ByteArray(contentSize).also { file.readFully(it) }
                ExistingEntry(pathSize, path, contentSize, content)
            }
        }
    }

    fun writeToStorage(storage: File): Long {
        val offset = storage.length()
        storage.outputStream(true).use {
            when (path) {
                is FileApi.File -> it.write(0)
                is FileApi.Folder -> it.write(1)
            }
            it.write(pathSize.toByteArray())
            it.write(path.value.toByteArray())
            it.write(contentSize.toByteArray())
            it.write(content)
        }
        return offset
    }
}

class ExistingEntry(
    pathSize: Int,
    path: FileApi.Path,
    contentSize: Int,
    content: ByteArray
) : StorageEntry(pathSize, path, contentSize, content) {
    companion object {
        fun of(folder: FileApi.Folder): ExistingEntry = ExistingEntry(
            folder.value.toByteArray().size,
            folder,
            0,
            EMPTY_BYTE_ARRAY
        )

        fun of(file: FileApi.File, content: ByteArray): ExistingEntry = ExistingEntry(
            file.value.toByteArray().size,
            file,
            content.size,
            content
        )
    }
}

class DeletedEntry(pathSize: Int, path: FileApi.Path) : StorageEntry(
    pathSize,
    path,
    -1,
    EMPTY_BYTE_ARRAY
) {
    companion object {
        fun of(path: FileApi.Path): DeletedEntry = DeletedEntry(path.value.toByteArray().size, path)
    }
}

