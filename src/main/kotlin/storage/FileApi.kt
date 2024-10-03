package storage

interface StorageContainer : FileOperations, HasContainerLifecycle

interface FileOperations {
    /**
     * Add [filePath] with the [content] to the storage.
     * If parent folders do not exist, they are created
     */
    fun create(filePath: FilePath, content: ByteArray)

    /**
     * Write [content] to the [filePath]
     *
     * @throws java.io.FileNotFoundException when there is no file in the [filePath]
     */
    fun write(filePath: FilePath, content: ByteArray)

    /**
     * Read [filePath] and return its content
     *
     * @throws java.io.FileNotFoundException when there is no file in the [filePath]
     */
    fun read(filePath: FilePath): ByteArray

    /**
     * Check if a file or a folder exists in the storage container
     */
    fun exists(path: Path): Boolean

    /**
     * Append [content] to the [filePath]
     *
     * @throws java.io.FileNotFoundException when there is no file in the [filePath]
     */
    fun append(filePath: FilePath, content: ByteArray)

    /**
     * Delete file or folder in the [path]. If it is a folder then
     * delete every nested object
     *
     * @throws java.io.FileNotFoundException when there is no file in the [path]
     */
    fun delete(path: Path)

    /**
     * Rename [oldPath] to the [newPath].
     *
     * @throws IllegalArgumentException if the type of the input arguments differs
     * They should be either both [FilePath] or both [FolderPath]
     */
    fun rename(oldPath: Path, newPath: Path)

    /**
     * Move [oldPath] to the [newPath].
     *
     * @throws IllegalArgumentException if the type of the input arguments differs
     * They should be either both [FilePath] or both [FolderPath]
     */
    fun move(oldPath: Path, newPath: Path)

    /**
     * Read [folderPath] and return its children
     *
     * @throws java.io.FileNotFoundException when there is no folder in the [folderPath]
     */
    fun read(folderPath: FolderPath): List<Path>

    /**
     * Create an empty folder in [folderPath]
     * If parent folders do not exist, they are created
     */
    fun create(folderPath: FolderPath)
}

interface HasContainerLifecycle {
    /**
     * Start the container and make ready to use
     */
    fun start()

    /**
     * Stop the container, no operations are allowed except start and destroy
     */
    fun stop()

    /**
     * DANGER! Destroy the storage file inside the container
     */
    fun destroy()

    /**
     * Maintenance procedure for a storage container - the call frequency
     * depends on the implementation of the storage container
     */
    fun compact()
}