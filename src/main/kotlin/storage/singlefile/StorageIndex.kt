package storage.singlefile

import storage.FilePath
import storage.FolderPath
import storage.Path
import java.io.FileNotFoundException

/**
 * An index that remembers the folder structure and the address of the files.
 *
 * @property entryIndex - stores an offset for every created folder or file
 * @property structureIndex - stores the structure of the file system. Every nested
 * path is stored twice - as a children of its parent, and as a key itself.
 * Example: "level1/level2/level3" is stored like:
 * (level1 : level2)
 * (level1/level2 : level3)
 * (level1/level2/level3 : _)
 * That allows to have quick access for deep nested folders.
 */
internal class StorageIndex {
    private val entryIndex: MutableMap<Path, Long>
    private val structureIndex: MutableMap<Path, MutableSet<Path>>

    constructor() {
        entryIndex = mutableMapOf()
        structureIndex = mutableMapOf()
    }

    private constructor(
        entryIndex: MutableMap<Path, Long>,
        folderIndex: MutableMap<Path, MutableSet<Path>>
    ) {
        this.entryIndex = entryIndex
        this.structureIndex = folderIndex
    }

    fun copyWithStructure(newEntryIndex: MutableMap<Path, Long>): StorageIndex {
        return StorageIndex(
            newEntryIndex,
            this.structureIndex
        )
    }

    fun getAllEntries(): Map<Path, Long> {
        return entryIndex
    }

    fun add(path: Path, offset: Long) {
        entryIndex[path] = offset

        val structure = path.split()

        if (structure.size == 1) {
            structureIndex[path] = mutableSetOf()
        }

        for (i in 0..structure.size - 2) {
            if (!structureIndex.containsKey(structure[i + 1])) {
                structureIndex[structure[i + 1]] = mutableSetOf()
            }

            if (structureIndex.containsKey(structure[i])) {
                structureIndex[structure[i]]?.add(structure[i + 1])
            } else {
                structureIndex[structure[i]] = mutableSetOf(structure[i + 1])
            }
        }
    }

    fun remove(path: Path) {
        when (path) {
            is FilePath -> {}
            is FolderPath -> {
                val children = structureIndex[path]
                children?.forEach { remove(it) }
            }
        }

        entryIndex.remove(path)
        if (path.parent() != null) {
            structureIndex[path.parent() as Path]?.remove(path)
        }
        structureIndex.remove(path)
    }

    fun ensurePathExists(path: Path) {
        if (!checkPathExistsInternal(path)) throw FileNotFoundException(path.value)
    }

    fun checkPathExists(path: Path): Boolean {
        return checkPathExistsInternal(path)
    }

    private fun checkPathExistsInternal(path: Path): Boolean {
        return when (path) {
            is FilePath -> entryIndex.containsKey(path)
            is FolderPath -> structureIndex.containsKey(path)
        }
    }

    fun getOffsetOrThrow(file: FilePath): Long {
        return entryIndex[file] ?: throw FileNotFoundException(file.value)
    }

    fun getChildrenOrThrow(folder: FolderPath): Set<Path> {
        return structureIndex[folder]?.toSet() ?: throw FileNotFoundException(folder.value)
    }
}