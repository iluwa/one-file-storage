import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import storage.FileApi
import storage.FileApiImpl
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FunctionalTestFromTheTaskTest {
    private lateinit var file: File
    private lateinit var fileApi: FileApiImpl

    @BeforeEach
    fun setUp() {
        file = File("storage")
        file.createNewFile()
        fileApi = FileApiImpl(file)
    }

    @AfterEach
    fun tearDown() {
        file.delete()
    }

    /*
    Include at least one complete functional test: store all project tree,
    erase 70% of all files, compact container, add all files again into new subfolder.
    Close container file, reopen and verify all content.
     */
    @Test
    fun `Complete functional test`() {
        val allFilesAndFolders = File("./").walk()
            .filter { it.name != file.name } // exclude writing storage file since it is dynamic
            .toList()

        // create all the files inside the one-file-storage
        allFilesAndFolders.forEach {
            if (it.isDirectory) {
                fileApi.create(FileApi.Folder(it.toString()))
            } else {
                fileApi.create(FileApi.File(it.toString()), it.readBytes())
            }
        }

        // verify files and structure
        allFilesAndFolders.forEach { assertFileOrFolder(it) }

        // delete 70% of the files
        val allFiles = allFilesAndFolders.filter { it.isFile }
        val amountFilesToDelete = (allFiles.size * 0.7).toInt()
        val deletedFiles = mutableListOf<File>()

        for (i in 0..amountFilesToDelete) {
            fileApi.delete(FileApi.File(allFiles[i].toString()))
            deletedFiles.add(allFiles[i])
        }

        val filesLeft = allFiles - deletedFiles.toSet()

        // verify files which were deleted
        deletedFiles.forEach { assertFalse(fileApi.exists(FileApi.File(it.toString()))) }

        // verify files which were left after deletion
        filesLeft.forEach { assertFileOrFolder(it) }

        // compact storage
        fileApi.compact()

        // add all files to a new folder
        allFilesAndFolders.forEach {
            if (it.isDirectory) {
                fileApi.create(FileApi.Folder("./custom-folder/${it}"))
            } else {
                fileApi.create(FileApi.File("./custom-folder/${it}"), it.readBytes())
            }
        }

        // Reopen file
        fileApi = FileApiImpl(file)

        // verify that all files are in the custom-folder
        allFilesAndFolders.forEach { assertFileOrFolder(it, "./custom-folder") }

        // verify files which were deleted
        deletedFiles.forEach { assertFalse(fileApi.exists(FileApi.File(it.toString()))) }

        // verify files which were left after deletion
        filesLeft.forEach { assertFileOrFolder(it) }
    }

    private fun assertFileOrFolder(path: File, parentFolder: String = "") {
        val fullPathInTheStorage = if (parentFolder.isBlank()) path.toString() else "$parentFolder/$path"
        if (path.isDirectory) {
            assertTrue(fileApi.exists(FileApi.Folder(fullPathInTheStorage)))
        } else {
            val content = fileApi.read(FileApi.File(fullPathInTheStorage))
            assertContentEquals(path.readBytes(), content)
        }
    }
}