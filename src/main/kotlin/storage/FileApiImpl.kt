package storage

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer

fun ByteArray.toInt(): Int {
    return ByteBuffer.wrap(this).getInt()
}

fun FileInputStream.readInt(): Int {
    val b = ByteArray(Int.SIZE_BYTES)
    this.read(b)
    return b.toInt()
}

fun FileInputStream.readUtf8String(size: Int): String {
    val b = ByteArray(size)
    this.read(b)
    return b.toString(Charsets.UTF_8)
}

class FileApiImpl(private val storageFile: File) : FileApi {
    private val entryIndex: MutableMap<FileApi.Path, Long> = mutableMapOf()

    init {
        if (storageFile.exists()) {
            val storageLength = storageFile.length()
            storageFile.inputStream().use {
                var pos = 0L
                while (pos < storageLength) {
                    val initialPosition = pos

                    val pathSize = it.readInt()
                    pos += Int.SIZE_BYTES

                    val path = it.readUtf8String(pathSize)
                    pos += pathSize
                    entryIndex[FileApi.Path(path)] = initialPosition

                    val contentSize = it.readInt()
                    pos += Int.SIZE_BYTES

                    it.skip(contentSize.toLong())
                    pos += contentSize
                }
            }
        } else {
            storageFile.createNewFile()
        }
    }

    override fun create(path: FileApi.Path, content: ByteArray) {
        appendInternal(path, content)
    }

    override fun write(path: FileApi.Path, content: ByteArray) {
        if (entryIndex[path] == null) {
            throw FileNotFoundException(path.value)
        }
        appendInternal(path, content)
    }

    private fun appendInternal(path: FileApi.Path, content: ByteArray) {
        val storageEntry = StorageEntry.of(path, content)
        val offset = storageEntry.writeToStorage(storageFile)
        entryIndex[path] = offset
    }

    override fun read(path: FileApi.Path): ByteArray? {
        return entryIndex[path]?.let {
            StorageEntry.fromStorage(storageFile, it)
                .content
        }
    }

    override fun append(path: FileApi.Path, content: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun delete(path: FileApi.Path) {
        TODO("Not yet implemented")
    }

    override fun rename(oldPath: FileApi.Path, newPath: String) {
        TODO("Not yet implemented")
    }

    override fun move(oldPath: FileApi.Path, newPath: String) {
        TODO("Not yet implemented")
    }
}