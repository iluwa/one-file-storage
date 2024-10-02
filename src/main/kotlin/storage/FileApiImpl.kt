package storage

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files

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

class FileApiImpl(private var storageFile: File) : FileApi {
    private var storageIndex = StorageIndex()

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

                    val contentSize = it.readInt()
                    pos += Int.SIZE_BYTES

                    when {
                        contentSize > -1 && type == 0.toByte() -> storageIndex.add(FileApi.File(path), initialPosition)
                        contentSize == -1 && type == 0.toByte() -> storageIndex.remove(FileApi.File(path))
                        contentSize > -1 && type == 1.toByte() -> storageIndex.add(FileApi.Folder(path), initialPosition)
                        contentSize == -1 && type == 1.toByte() ->  storageIndex.remove(FileApi.Folder(path))
                        else -> error("Unknown type of the storage entry")
                    }

                    if (contentSize > -1) {
                        it.skip(contentSize.toLong())
                        pos += contentSize
                    }
                }
            }
        } else {
            storageFile.createNewFile()
        }
    }

    override fun compact() {
        val newStorage = File("tmp")
        newStorage.createNewFile()

        val entries: Map<FileApi.Path, Long> = storageIndex.getAllEntries()
        val newEntries: MutableMap<FileApi.Path, Long> = mutableMapOf()

        entries.forEach { (path, _) ->
            val storageEntry = when (path) {
                is FileApi.File -> {
                    val content = internalRead(path)
                    ExistingEntry.of(path, content)

                }
                is FileApi.Folder -> ExistingEntry.of(path)
            }
            val offset = storageEntry.writeToStorage(newStorage)
            newEntries[path] = offset
        }

        val prevStorageFile = storageFile
        val bkp = File(prevStorageFile.name + "_bkp")
        if (prevStorageFile.renameTo(bkp)) {
            val renamed = Files.move(newStorage.toPath(), prevStorageFile.toPath())
            storageFile = renamed.toFile()
            storageIndex = storageIndex.copyWithStructure(newEntries)
            bkp.delete()
        } else {
            throw IOException("Error during the compact procedure: storage file cannot be renamed")
        }
    }

    override fun create(file: FileApi.File, content: ByteArray) {
        writeInternal(file, content)
    }

    override fun write(file: FileApi.File, content: ByteArray) {
        storageIndex.ensurePathExists(file)
        writeInternal(file, content)
    }

    private fun writeInternal(file: FileApi.File, content: ByteArray) {
        val storageEntry = ExistingEntry.of(file, content)
        val offset = storageEntry.writeToStorage(storageFile)
        storageIndex.add(file, offset)
    }

    override fun read(file: FileApi.File): ByteArray = internalRead(file)

    override fun exists(path: FileApi.Path): Boolean {
        return storageIndex.checkPathExists(path)
    }

    private fun internalRead(file: FileApi.File): ByteArray {
        return storageIndex.getOffsetOrThrow(file).let {
            StorageEntry.fromStorage(storageFile, it)
                .content
        }
    }

    override fun append(file: FileApi.File, content: ByteArray) {
        storageIndex.ensurePathExists(file)
        writeInternal(file, internalRead(file) + content)
    }

    override fun delete(path: FileApi.Path) {
        storageIndex.ensurePathExists(path)
        deleteInternal(path)
    }

    private fun deleteInternal(path: FileApi.Path) {
        val storageEntry = DeletedEntry.of(path)
        storageEntry.writeToStorage(storageFile)
        storageIndex.remove(path)
    }

    override fun rename(oldPath: FileApi.Path, newPath: FileApi.Path) {
        storageIndex.ensurePathExists(oldPath)
        internalMove(oldPath, newPath)
    }

    override fun move(oldPath: FileApi.Path, newPath: FileApi.Path) {
        storageIndex.ensurePathExists(oldPath)
        internalMove(oldPath, newPath)
    }

    override fun read(folder: FileApi.Folder): List<FileApi.Path> {
        return storageIndex.getChildrenOrThrow(folder).toList()
    }

    override fun create(folder: FileApi.Folder) {
        createFolderInternal(folder)
    }

    fun createFolderInternal(folder: FileApi.Folder) {
        val storageEntry = ExistingEntry.of(folder)
        val position = storageEntry.writeToStorage(storageFile)
        storageIndex.add(folder, position)
    }

    private fun internalMove(oldPath: FileApi.Path, newPath: FileApi.Path) {
        val folders = oldPath is FileApi.Folder && newPath is FileApi.Folder
        val files = oldPath is FileApi.File && newPath is FileApi.File
        require(folders || files)

        when (oldPath) {
            is FileApi.File -> {
                internalRead(oldPath).let {
                    writeInternal(newPath as FileApi.File, it)
                    deleteInternal(oldPath)
                }
            }
            is FileApi.Folder -> {
                createFolderInternal(newPath as FileApi.Folder)
                storageIndex.getChildrenOrThrow(oldPath).forEach {
                    internalMove(it, newPath.append(it.last()))
                }
                deleteInternal(oldPath)
            }
        }
    }
}