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
                val pathSize = file.readInt()
                val path = FileApi.Path(ByteArray(pathSize).also { file.readFully(it) }.decodeToString())
                val contentSize = file.readInt()
                val content = ByteArray(contentSize).also { file.readFully(it) }
                ExistingEntry(pathSize, path, contentSize, content)
            }
        }
    }

    abstract fun size(): Int

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
}

class ExistingEntry(
    pathSize: Int,
    path: FileApi.Path,
    contentSize: Int,
    content: ByteArray
) : StorageEntry(pathSize, path, contentSize, content) {
    companion object {
        fun of(path: FileApi.Path, content: ByteArray): ExistingEntry = ExistingEntry(
            path.value.toByteArray().size,
            path,
            content.size,
            content
        )
    }

    override fun size(): Int {
        return Int.SIZE_BYTES + pathSize + Int.SIZE_BYTES + contentSize
    }
}

class DeletedEntry(pathSize: Int, path: FileApi.Path) : StorageEntry(
    pathSize,
    path,
    -1,
    ByteArray(0)
) {
    companion object {
        fun of(path: FileApi.Path): DeletedEntry = DeletedEntry(path.value.toByteArray().size, path)
    }

    override fun size(): Int {
        return Int.SIZE_BYTES + pathSize + Int.SIZE_BYTES
    }
}

