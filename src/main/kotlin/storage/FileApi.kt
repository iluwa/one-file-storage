package storage

interface FileApi {
    sealed class Path {
        abstract val value: String

        fun parent(): Folder? {
            val paths = value.split("/")
            return if (paths.size == 1) null
            else {
                val prevPath = (0..paths.size - 2).joinToString("/") { paths[it] }
                Folder(prevPath)
            }
        }

        fun split(): List<Path> {
            val paths = value.split("/")
            var cumulativePath = ""
            val res = mutableListOf<Path>()
            for (i in paths.indices) {
                cumulativePath += if (i == 0) paths[i] else "/" + paths[i]
                if (i == paths.size - 1) res.add(this)
                else res.add(Folder(cumulativePath))
            }
            return res
        }
    }
    data class File(override val value: String) : Path()
    data class Folder(override val value: String) : Path()

    fun create(file: File, content: ByteArray)
    fun write(file: File, content: ByteArray)
    fun read(file: File): ByteArray
    fun exists(path: Path): Boolean
    fun append(file: File, content: ByteArray)
    fun delete(path: Path)
    fun rename(oldPath: Path, newPath: Path)
    fun move(oldPath: Path, newPath: Path)

    fun read(folder: Folder): List<Path>
    fun create(folder: Folder)
}