package storage.singlefile

import storage.*
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

internal enum class ContainerStatus {
    CREATED,
    READY_TO_USE,
    STOPPED,
    DESTROYED
}

class SingleFileStorageContainer : StorageContainer {
    private var status: ContainerStatus = ContainerStatus.CREATED
    private var storageIndex = StorageIndex()
    private var storageFile: File = File("storage")

    private fun requireStatus(statuses: List<ContainerStatus>) {
        check(statuses.contains(this.status)) {
            "The operation is only allowed in statuses: $statuses. Current status: ${this.status}"
        }
    }

    private fun requireStatus(status: ContainerStatus) {
        check(status == this.status) {
            "The operation is only allowed in status: $status. Current status: ${this.status}"
        }
    }

    override fun start() {
        requireStatus(listOf(ContainerStatus.CREATED, ContainerStatus.STOPPED))
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
                        contentSize > -1 && type == 0.toByte() -> storageIndex.add(FilePath(path), initialPosition)
                        contentSize == -1 && type == 0.toByte() -> storageIndex.remove(FilePath(path))
                        contentSize > -1 && type == 1.toByte() -> storageIndex.add(FolderPath(path), initialPosition)
                        contentSize == -1 && type == 1.toByte() -> storageIndex.remove(FolderPath(path))
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
        status = ContainerStatus.READY_TO_USE
    }

    override fun stop() {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex = StorageIndex()
        status = ContainerStatus.STOPPED
    }

    override fun destroy() {
        requireStatus(listOf(ContainerStatus.CREATED, ContainerStatus.STOPPED, ContainerStatus.READY_TO_USE))
        storageIndex = StorageIndex()
        storageFile.delete()
        status = ContainerStatus.DESTROYED
    }

    override fun compact() {
        requireStatus(ContainerStatus.READY_TO_USE)
        val newStorage = File("tmp")
        newStorage.createNewFile()

        val entries: Map<Path, Long> = storageIndex.getAllEntries()
        val newEntries: MutableMap<Path, Long> = mutableMapOf()

        entries.forEach { (path, _) ->
            val storageEntry = when (path) {
                is FilePath -> {
                    val content = internalRead(path)
                    ExistingEntry.of(path, content)

                }

                is FolderPath -> ExistingEntry.of(path)
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

    override fun create(filePath: FilePath, content: ByteArray) {
        requireStatus(ContainerStatus.READY_TO_USE)
        writeInternal(filePath, content)
    }

    override fun write(filePath: FilePath, content: ByteArray) {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex.ensurePathExists(filePath)
        writeInternal(filePath, content)
    }

    override fun read(filePath: FilePath): ByteArray {
        requireStatus(ContainerStatus.READY_TO_USE)
        return internalRead(filePath)
    }

    override fun exists(path: Path): Boolean {
        requireStatus(ContainerStatus.READY_TO_USE)
        return storageIndex.checkPathExists(path)
    }

    override fun append(filePath: FilePath, content: ByteArray) {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex.ensurePathExists(filePath)
        writeInternal(filePath, internalRead(filePath) + content)
    }

    override fun delete(path: Path) {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex.ensurePathExists(path)
        deleteInternal(path)
    }


    override fun rename(oldPath: Path, newPath: Path) {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex.ensurePathExists(oldPath)
        internalMove(oldPath, newPath)
    }

    override fun move(oldPath: Path, newPath: Path) {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex.ensurePathExists(oldPath)
        internalMove(oldPath, newPath)
    }

    override fun read(folderPath: FolderPath): List<Path> {
        requireStatus(ContainerStatus.READY_TO_USE)
        return storageIndex.getChildrenOrThrow(folderPath).toList()
    }

    override fun create(folderPath: FolderPath) {
        requireStatus(ContainerStatus.READY_TO_USE)
        createFolderInternal(folderPath)
    }

    override fun walk(folderPath: FolderPath, consumer: (Path) -> Unit) {
        requireStatus(ContainerStatus.READY_TO_USE)
        storageIndex.getChildrenOrThrow(folderPath).forEach {
            when (it) {
                is FilePath -> consumer(it)
                is FolderPath -> {
                    consumer(it)
                    walk(it, consumer)
                }
            }
        }
    }

    private fun createFolderInternal(folder: FolderPath) {
        val storageEntry = ExistingEntry.of(folder)
        val position = storageEntry.writeToStorage(storageFile)
        storageIndex.add(folder, position)
    }

    private fun internalMove(oldPath: Path, newPath: Path) {
        val folders = oldPath is FolderPath && newPath is FolderPath
        val files = oldPath is FilePath && newPath is FilePath
        require(folders || files)

        when (oldPath) {
            is FilePath -> {
                internalRead(oldPath).let {
                    writeInternal(newPath as FilePath, it)
                    deleteInternal(oldPath)
                }
            }

            is FolderPath -> {
                createFolderInternal(newPath as FolderPath)
                storageIndex.getChildrenOrThrow(oldPath).forEach {
                    internalMove(it, newPath.append(it.last()))
                }
                deleteInternal(oldPath)
            }
        }
    }

    private fun writeInternal(filePath: FilePath, content: ByteArray) {
        val storageEntry = ExistingEntry.of(filePath, content)
        val offset = storageEntry.writeToStorage(storageFile)
        storageIndex.add(filePath, offset)
    }

    private fun internalRead(filePath: FilePath): ByteArray {
        return storageIndex.getOffsetOrThrow(filePath).let {
            StorageEntry.fromStorage(storageFile, it)
                .content
        }
    }

    private fun deleteInternal(path: Path) {
        val storageEntry = DeletedEntry.of(path)
        storageEntry.writeToStorage(storageFile)
        storageIndex.remove(path)
    }
}