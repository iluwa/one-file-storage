package storage

import java.io.FileNotFoundException

internal class StorageIndex {
    private val entryIndex: MutableMap<FileApi.Path, Long> = mutableMapOf()
    private val folderIndex: MutableMap<FileApi.Folder, MutableSet<FileApi.Path>> = mutableMapOf()

    fun add(path: FileApi.Path, offset: Long) {
        entryIndex[path] = offset

        val structure = path.split()

        if (structure.size == 1 && path is FileApi.Folder) {
            folderIndex[path] = mutableSetOf()
        }

        for (i in 0..structure.size - 2) {
            if (structure[i + 1] is FileApi.Folder && !folderIndex.containsKey(structure[i + 1])) {
                folderIndex[structure[i + 1] as FileApi.Folder] = mutableSetOf()
            }

            if (folderIndex.containsKey(structure[i])) {
                folderIndex[structure[i]]?.add(structure[i + 1])
            } else {
                folderIndex[structure[i] as FileApi.Folder] = mutableSetOf(structure[i + 1])
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
            folderIndex[path.parent()]?.remove(path)
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