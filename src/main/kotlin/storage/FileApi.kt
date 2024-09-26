package storage

interface FileApi {
    data class Path(val value: String)

    fun create(path: Path, content: ByteArray)
    fun write(path: Path, content: ByteArray)
    fun read(path: Path): ByteArray?
    fun append(path: Path, content: ByteArray)
    fun delete(path: Path)
    fun rename(oldPath: Path, newPath: String)
    fun move(oldPath: Path, newPath: String)
}