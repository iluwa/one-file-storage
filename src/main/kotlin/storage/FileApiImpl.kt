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
                    if (contentSize == -1) {
                        entryIndex.remove(FileApi.Path(path))
                    } else {
                        it.skip(contentSize.toLong())
                        pos += contentSize
                    }
                }
            }
        } else {
            storageFile.createNewFile()
        }
    }

    override fun create(path: FileApi.Path, content: ByteArray) {
        writeInternal(path, content)
    }

    override fun write(path: FileApi.Path, content: ByteArray) {
        if (entryIndex[path] == null) {
            throw FileNotFoundException(path.value)
        }
        writeInternal(path, content)
    }

    private fun writeInternal(path: FileApi.Path, content: ByteArray) {
        val storageEntry = ExistingEntry.of(path, content)
        val offset = storageEntry.writeToStorage(storageFile)
        entryIndex[path] = offset
    }

    override fun read(path: FileApi.Path): ByteArray? = internalRead(path)

    private fun internalRead(path: FileApi.Path): ByteArray? {
        return entryIndex[path]?.let {
            StorageEntry.fromStorage(storageFile, it)
                .content
        }
    }

    override fun append(path: FileApi.Path, content: ByteArray) {
        internalRead(path)?.let {
            writeInternal(path, it + content)
        } ?: throw FileNotFoundException(path.value)
    }

    override fun delete(path: FileApi.Path) {
        entryIndex[path]?.let {
            val storageEntry = DeletedEntry.of(path)
            storageEntry.writeToStorage(storageFile)
            entryIndex.remove(path)
        } ?: throw FileNotFoundException(path.value)
    }

    override fun rename(oldPath: FileApi.Path, newPath: FileApi.Path) {
        internalMove(oldPath, newPath)
    }

    override fun move(oldPath: FileApi.Path, newPath: FileApi.Path) {
        internalMove(oldPath, newPath)
    }

    private fun internalMove(oldPath: FileApi.Path, newPath: FileApi.Path) {
        internalRead(oldPath)?.let {
            writeInternal(newPath, it)
            entryIndex.remove(oldPath)
        } ?: throw FileNotFoundException(oldPath.value)
    }
}