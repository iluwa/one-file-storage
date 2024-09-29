package storage

interface FileApi {
    sealed class Path {
        abstract val value: String
    }
    data class File(override val value: String) : Path()
    data class Folder(override val value: String) : Path()

    fun create(file: File, content: ByteArray)
    fun write(file: File, content: ByteArray)
    fun read(file: File): ByteArray?
    fun append(file: File, content: ByteArray)
    fun delete(path: Path)
    fun rename(oldPath: Path, newPath: Path)
    fun move(oldPath: Path, newPath: Path)

    fun read(folder: Folder): List<Path>
    fun create(folder: Folder)
}