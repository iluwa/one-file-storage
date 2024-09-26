package storage

import java.io.File

class FileApiImpl(private val storageFile: File) : FileApi {
    private val entryIndex: MutableMap<FileApi.Path, Long> = mutableMapOf()

    override fun create(path: FileApi.Path, content: ByteArray) {
        val storageEntry = StorageEntry.of(path, content)
        val offset = storageEntry.writeToStorage(storageFile)
        entryIndex[path] = offset
    }

    override fun write(path: FileApi.Path, content: ByteArray) {
        TODO("Not yet implemented")
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