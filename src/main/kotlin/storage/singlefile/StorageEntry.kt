package storage.singlefile

import storage.FilePath
import storage.FolderPath
import storage.Path
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

private fun File.outputStream(appendMode: Boolean = false): FileOutputStream {
    return FileOutputStream(this, appendMode)
}

private fun Int.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
    buffer.putInt(this)
    return buffer.array()
}

private val EMPTY_BYTE_ARRAY = ByteArray(0)

/**
 * An entry that is written to a file, almost like a database row.
 * (type - file or folder)(pathSize)(path)(contentSize)(content)
 * An example of written entry translated to string:
 * - file: 010folder/file100somecontent
 * - folder: 110folder/nested_folder0
 *
 * @property pathSize - size of the path in bytes
 * @property path - path to a folder or file
 * @property contentSize - size of the content in bytes. If it is -1, then the entry is deleted
 * @property content - the content of the file
 */
internal sealed class StorageEntry(
    val pathSize: Int,
    val path: Path,
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
                    0.toByte() -> FilePath(decodedPath)
                    1.toByte() -> FolderPath(decodedPath)
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
                is FilePath -> it.write(0)
                is FolderPath -> it.write(1)
            }
            it.write(pathSize.toByteArray())
            it.write(path.value.toByteArray())
            it.write(contentSize.toByteArray())
            it.write(content)
        }
        return offset
    }
}

internal class ExistingEntry(
    pathSize: Int,
    path: Path,
    contentSize: Int,
    content: ByteArray
) : StorageEntry(pathSize, path, contentSize, content) {
    companion object {
        fun of(folder: FolderPath): ExistingEntry = ExistingEntry(
            folder.value.toByteArray().size,
            folder,
            0,
            EMPTY_BYTE_ARRAY
        )

        fun of(file: FilePath, content: ByteArray): ExistingEntry = ExistingEntry(
            file.value.toByteArray().size,
            file,
            content.size,
            content
        )
    }
}

internal class DeletedEntry(pathSize: Int, path: Path) : StorageEntry(
    pathSize,
    path,
    -1,
    EMPTY_BYTE_ARRAY
) {
    companion object {
        fun of(path: Path): DeletedEntry = DeletedEntry(path.value.toByteArray().size, path)
    }
}

