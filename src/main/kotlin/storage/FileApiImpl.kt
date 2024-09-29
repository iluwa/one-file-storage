package storage

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer

private fun ByteArray.toInt(): Int {
    return ByteBuffer.wrap(this).getInt()
}

private fun FileInputStream.readInt(): Int {
    val b = ByteArray(Int.SIZE_BYTES)
    this.read(b)
    return b.toInt()
}

private fun FileInputStream.readUtf8String(size: Int): String {
    val b = ByteArray(size)
    this.read(b)
    return b.toString(Charsets.UTF_8)
}

class FileApiImpl(private val storageFile: File) : FileApi {
    private val entryIndex: MutableMap<FileApi.File, Long> = mutableMapOf()
    private val folderIndex: MutableMap<FileApi.Folder, MutableList<FileApi.Path>> = mutableMapOf()

    init {
        if (storageFile.exists()) {
            val storageLength = storageFile.length()
            storageFile.inputStream().use {
                var pos = 0L
                while (pos < storageLength) {
                    val initialPosition = pos

                    val type = it.readNBytes(1)[0]
                    pos += 1

                    val pathSize = it.readInt()
                    pos += Int.SIZE_BYTES

                    val path = it.readUtf8String(pathSize)
                    pos += pathSize
                    when(type) {
                        0.toByte() -> entryIndex[FileApi.File(path)] = initialPosition
                        1.toByte() -> TODO()
                        else -> error("Unknown type of the storage entry")
                    }

                    val contentSize = it.readInt()
                    pos += Int.SIZE_BYTES
                    if (contentSize == -1) {
                        entryIndex.remove(FileApi.File(path))
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

    override fun create(file: FileApi.File, content: ByteArray) {
        writeInternal(file, content)
    }

    override fun write(file: FileApi.File, content: ByteArray) {
        if (entryIndex[file] == null) {
            throw FileNotFoundException(file.value)
        }
        writeInternal(file, content)
    }

    private fun writeInternal(file: FileApi.File, content: ByteArray) {
        val storageEntry = ExistingEntry.of(file, content)
        val offset = storageEntry.writeToStorage(storageFile)
        entryIndex[file] = offset
    }

    override fun read(file: FileApi.File): ByteArray? = internalRead(file)

    private fun internalRead(path: FileApi.Path): ByteArray? {
        return entryIndex[path]?.let {
            StorageEntry.fromStorage(storageFile, it)
                .content
        }
    }

    override fun append(file: FileApi.File, content: ByteArray) {
        internalRead(file)?.let {
            writeInternal(file, it + content)
        } ?: throw FileNotFoundException(file.value)
    }

    override fun delete(path: FileApi.Path) {
        when (path) {
            is FileApi.File -> {
                entryIndex[path]?.let {
                    val storageEntry = DeletedEntry.of(path)
                    storageEntry.writeToStorage(storageFile)
                    entryIndex.remove(path)
                } ?: throw FileNotFoundException(path.value)
            }
            is FileApi.Folder -> TODO()
        }

    }

    override fun rename(oldPath: FileApi.Path, newPath: FileApi.Path) {
        internalMove(oldPath, newPath)
    }

    override fun move(oldPath: FileApi.Path, newPath: FileApi.Path) {
        internalMove(oldPath, newPath)
    }

    override fun read(folder: FileApi.Folder): List<FileApi.Path> {
        return folderIndex[folder]
            ?: throw FileNotFoundException(folder.value)
    }

    override fun create(folder: FileApi.Folder) {
        val folderPaths = folder.value.split("/")

        val storageEntry = ExistingEntry.of(folder)
        storageEntry.writeToStorage(storageFile)

        if (folderPaths.size == 1) {
            folderIndex[folder] = mutableListOf()
        } else {
            var currentPath = folderPaths[0]
            for (i in 1 until folderPaths.size) {
                val nestedPath = currentPath + "/" + folderPaths[i]
                folderIndex[FileApi.Folder(currentPath)] = mutableListOf(FileApi.Folder(nestedPath))
                currentPath = nestedPath
            }
            folderIndex[folder] = mutableListOf()
        }
    }

    private fun internalMove(oldPath: FileApi.Path, newPath: FileApi.Path) {
        val folders = oldPath is FileApi.Folder && newPath is FileApi.Folder
        val files = oldPath is FileApi.File && newPath is FileApi.File
        require(folders || files)

        when (oldPath) {
            is FileApi.File -> {
                internalRead(oldPath)?.let {
                    writeInternal(newPath as FileApi.File, it)
                    entryIndex.remove(oldPath)
                } ?: throw FileNotFoundException(oldPath.value)
            }
            is FileApi.Folder -> TODO()
        }
    }
}