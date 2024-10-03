package storage

/**
 * Represents the path in the file api.
 * The format is "folder1/folder2/file-or-folder"
 */
sealed class Path {
    abstract val value: String

    fun parent(): FolderPath? {
        val paths = value.split("/")
        return if (paths.size == 1) null
        else {
            val prevPath = (0..paths.size - 2).joinToString("/") { paths[it] }
            FolderPath(prevPath)
        }
    }

    fun split(): List<Path> {
        val paths = value.split("/")
        var cumulativePath = ""
        val res = mutableListOf<Path>()
        for (i in paths.indices) {
            cumulativePath += if (i == 0) paths[i] else "/" + paths[i]
            if (i == paths.size - 1) res.add(this)
            else res.add(FolderPath(cumulativePath))
        }
        return res
    }

    fun last(): Path {
        val last = value.split("/").last()
        return when (this) {
            is FolderPath -> FolderPath(last)
            is FilePath -> FilePath(last)
        }
    }

    fun append(path: Path): Path {
        val fullPath = this.value + "/" + path.value
        return when (path) {
            is FolderPath -> FolderPath(fullPath)
            is FilePath -> FilePath(fullPath)
        }
    }
}

data class FilePath(override val value: String) : Path()
data class FolderPath(override val value: String) : Path()