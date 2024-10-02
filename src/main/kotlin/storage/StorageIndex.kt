package storage

import java.io.FileNotFoundException

internal class StorageIndex {
    private val entryIndex: MutableMap<FileApi.Path, Long>
    private val folderIndex: MutableMap<FileApi.Path, MutableSet<FileApi.Path>>

    constructor() {
        entryIndex = mutableMapOf()
        folderIndex = mutableMapOf()
    }

    private constructor(entryIndex: MutableMap<FileApi.Path, Long>,
                        folderIndex: MutableMap<FileApi.Path, MutableSet<FileApi.Path>>) {
        this.entryIndex = entryIndex
        this.folderIndex = folderIndex
    }

    fun copyWithStructure(newEntryIndex: MutableMap<FileApi.Path, Long>): StorageIndex {
        return StorageIndex(
            newEntryIndex,
            this.folderIndex
        )
    }

    fun getAllEntries(): Map<FileApi.Path, Long> {
        return entryIndex
    }

    fun add(path: FileApi.Path, offset: Long) {
        entryIndex[path] = offset

        val structure = path.split()

        if (structure.size == 1) {
            folderIndex[path] = mutableSetOf()
        }

        for (i in 0..structure.size - 2) {
            if (!folderIndex.containsKey(structure[i + 1])) {
                folderIndex[structure[i + 1]] = mutableSetOf()
            }

            if (folderIndex.containsKey(structure[i])) {
                folderIndex[structure[i]]?.add(structure[i + 1])
            } else {
                folderIndex[structure[i]] = mutableSetOf(structure[i + 1])
            }
        }
    }

    fun remove(path: FileApi.Path) {
        when (path) {
            is FileApi.File -> {}
            is FileApi.Folder -> {
                val children = folderIndex[path]
                children?.forEach { remove(it) }
            }
        }

        entryIndex.remove(path)
        if (path.parent() != null) {
            folderIndex[path.parent() as FileApi.Path]?.remove(path)
        }
        folderIndex.remove(path)
    }

    fun ensurePathExists(path : FileApi.Path) {
        if (!checkPathExistsInternal(path)) throw FileNotFoundException(path.value)
    }

    fun checkPathExists(path : FileApi.Path): Boolean {
        return checkPathExistsInternal(path)
    }

    private fun checkPathExistsInternal(path : FileApi.Path): Boolean {
        return when (path) {
            is FileApi.File -> entryIndex.containsKey(path)
            is FileApi.Folder -> folderIndex.containsKey(path)
        }
    }

    fun getOffsetOrThrow(file : FileApi.File): Long {
        return entryIndex[file] ?: throw FileNotFoundException(file.value)
    }

    fun getChildrenOrThrow(folder: FileApi.Folder): Set<FileApi.Path> {
        return folderIndex[folder]?.toSet() ?: throw FileNotFoundException(folder.value)
    }
}