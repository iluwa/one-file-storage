import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import storage.FilePath
import storage.FolderPath
import storage.StorageContainer
import storage.singlefile.SingleFileStorageContainer
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FunctionalTestFromTheTaskTest {
    private lateinit var storageContainer: StorageContainer

    @BeforeEach
    fun setUp() {
        storageContainer = SingleFileStorageContainer()
        storageContainer.start()
    }

    @AfterEach
    fun tearDown() {
        storageContainer.destroy()
    }

    /*
    Include at least one complete functional test: store all project tree,
    erase 70% of all files, compact container, add all files again into new subfolder.
    Close container file, reopen and verify all content.

    Note on the path implementation: the path "." treated as a separate folder. So when the file
    "./file" is added to the one-storage-container, it is added inside the folder "."
     */
    @Test
    fun `Complete functional test`() {
        val allFilesAndFolders = File("./").walk()
            .filter { it.name != "storage" } // exclude writing storage file since it is dynamic
            .toList()

        // create all the files inside the one-file-storage
        allFilesAndFolders.forEach {
            if (it.isDirectory) {
                storageContainer.create(FolderPath(it.toString()))
            } else {
                storageContainer.create(FilePath(it.toString()), it.readBytes())
            }
        }

        // verify files and structure
        allFilesAndFolders.forEach { assertFileOrFolder(it) }

        // delete 70% of the files
        val allFiles = allFilesAndFolders.filter { it.isFile }
        val amountFilesToDelete = (allFiles.size * 0.7).toInt()
        val deletedFiles = mutableListOf<File>()

        for (i in 0..amountFilesToDelete) {
            storageContainer.delete(FilePath(allFiles[i].toString()))
            deletedFiles.add(allFiles[i])
        }

        val filesLeft = allFiles - deletedFiles.toSet()

        // verify files which were deleted
        deletedFiles.forEach { assertFalse(storageContainer.exists(FilePath(it.toString()))) }

        // verify files which were left after deletion
        filesLeft.forEach { assertFileOrFolder(it) }

        // compact storage
        storageContainer.compact()

        // add all files to a new folder
        allFilesAndFolders.forEach {
            if (it.isDirectory) {
                storageContainer.create(FolderPath("./custom-folder/${it}"))
            } else {
                storageContainer.create(FilePath("./custom-folder/${it}"), it.readBytes())
            }
        }

        // Reopen file
        storageContainer.stop()
        storageContainer.start()

        // verify that all files are in the custom-folder
        allFilesAndFolders.forEach { assertFileOrFolder(it, "./custom-folder") }

        // verify files which were deleted
        deletedFiles.forEach { assertFalse(storageContainer.exists(FilePath(it.toString()))) }

        // verify files which were left after deletion
        filesLeft.forEach { assertFileOrFolder(it) }

        // presenting all the paths inside container - as a proof that the test is working
        storageContainer.walk(FolderPath(".")) {
            println(it.value)
        }
    }

    private fun assertFileOrFolder(path: File, parentFolder: String = "") {
        val fullPathInTheStorage = if (parentFolder.isBlank()) path.toString() else "$parentFolder/$path"
        if (path.isDirectory) {
            assertTrue(storageContainer.exists(FolderPath(fullPathInTheStorage)))
        } else {
            val content = storageContainer.read(FilePath(fullPathInTheStorage))
            assertContentEquals(path.readBytes(), content)
        }
    }
}