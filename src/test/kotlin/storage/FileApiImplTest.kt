import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import storage.FileApi
import storage.FileApiImpl
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FileApiImplTest {
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

    @Test
    fun `Basic case of reading the files`() {
        fileApi.create(FileApi.File("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.File("some-file2"), "My string file2".toByteArray())

        val firstFile = fileApi.read(FileApi.File("some-file"))
        assertEquals("My string file", String(firstFile))

        val secondFile = fileApi.read(FileApi.File("some-file2"))
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Read from a file with storage entries should succeed`() {
        fileApi.create(FileApi.File("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.File("some-file2"), "My string file2".toByteArray())

        // A new instance of file api imitating restart of the application
        val newFileApi = FileApiImpl(file)

        val firstFile = newFileApi.read(FileApi.File("some-file"))
        assertEquals("My string file", String(firstFile))

        val secondFile = newFileApi.read(FileApi.File("some-file2"))
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Reading a file after updating should give the latest version`() {
        fileApi.create(FileApi.File("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.File("some-file2"), "My string file2".toByteArray())

        fileApi.write(FileApi.File("some-file"), "New version".toByteArray())

        val firstFile = fileApi.read(FileApi.File("some-file"))
        assertEquals("New version", String(firstFile))
    }

    @Test
    fun `Writing to a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                fileApi.write(FileApi.File("non-existing-file"), "New version".toByteArray())
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Appending to a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                fileApi.append(FileApi.File("non-existing-file"), "New version".toByteArray())
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Reading a file after appending should give the latest version`() {
        fileApi.create(FileApi.File("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.File("some-file2"), "My string file2".toByteArray())

        fileApi.append(FileApi.File("some-file"), " - new version".toByteArray())

        val firstFile = fileApi.read(FileApi.File("some-file"))
        assertEquals("My string file - new version", String(firstFile))
    }

    @Test
    fun `Renaming a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                fileApi.rename(FileApi.File("non-existing-file"), FileApi.File("non-existing-file2"))
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Reading a file after renaming should give the latest version`() {
        fileApi.create(FileApi.File("before"), "My string file".toByteArray())

        fileApi.rename(FileApi.File("before"), FileApi.File("after"))

        val after = fileApi.read(FileApi.File("after"))
        assertEquals("My string file", String(after))

        assertFalse(fileApi.exists(FileApi.File("before")))
    }

    @Test
    fun `Moving a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                fileApi.move(FileApi.File("non-existing-file"), FileApi.File("non-existing-file2"))
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Moving a file after renaming should give the latest version`() {
        fileApi.create(FileApi.File("before"), "My string file".toByteArray())

        fileApi.move(FileApi.File("before"), FileApi.File("after"))

        val after = fileApi.read(FileApi.File("after"))
        assertEquals("My string file", String(after))

        assertFalse(fileApi.exists(FileApi.File("before")))
    }

    @Test
    fun `Deleting a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                fileApi.delete(FileApi.File("non-existing-file"))
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Deleted files should not be loaded on the startup`() {
        fileApi.create(FileApi.File("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.File("some-file2"), "My string file2".toByteArray())
        fileApi.delete(FileApi.File("some-file"))

        // A new instance of file api imitating restart of the application
        val newFileApi = FileApiImpl(file)

        assertFalse(fileApi.exists(FileApi.File("some-file")))

        val secondFile = newFileApi.read(FileApi.File("some-file2"))
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Create and read an empty folder three levels deep`() {
        fileApi.create(FileApi.Folder("level1/level2/level3"))

        val level1 = fileApi.read(FileApi.Folder("level1"))
        assertEquals(1, level1.size)
        assertEquals(FileApi.Folder("level1/level2"), level1[0])

        val level2 = fileApi.read(level1[0] as FileApi.Folder)
        assertEquals(1, level2.size)
        assertEquals(FileApi.Folder("level1/level2/level3"), level2[0])

        val level3 = fileApi.read(level2[0] as FileApi.Folder)
        assertEquals(0, level3.size)
    }

    @Test
    fun `Delete an empty folder`() {
        fileApi.create(FileApi.Folder("level1/level2"))
        fileApi.delete(FileApi.Folder("level1/level2"))

        val level1 = fileApi.read(FileApi.Folder("level1"))
        assertEquals(0, level1.size)
    }

    @Test
    fun `Delete folder with a nested file - the file should be deleted`() {
        fileApi.create(FileApi.Folder("level1/level2"))
        fileApi.create(FileApi.File("level1/level2/myfile"), "Content".toByteArray())
        fileApi.delete(FileApi.Folder("level1/level2"))

        val level1 = fileApi.read(FileApi.Folder("level1"))
        assertEquals(0, level1.size)

        assertFalse(fileApi.exists(FileApi.File("level1/level2/myfile")))
    }

    @Test
    fun `Deleted folder with children objects should not be loaded on the startup`() {
        fileApi.create(FileApi.File("level1/level2/myfile"), "Content".toByteArray())
        fileApi.create(FileApi.Folder("level1/level2-2"))
        fileApi.delete(FileApi.Folder("level1/level2"))

        // A new instance of file api imitating restart of the application
        val newFileApi = FileApiImpl(file)

        val level1 = newFileApi.read(FileApi.Folder("level1"))
        assertEquals(1, level1.size)
        assertEquals(FileApi.Folder("level1/level2-2"), level1[0])

        assertFalse(fileApi.exists(FileApi.File("level1/level2/myfile")))
    }

    @Test
    fun `Multiple level path should be split correctly`() {
        val structure = FileApi.Folder("level1/level2").split()
        assertEquals(2, structure.size)
        assertEquals("level1", structure[0].value)
        assertEquals("level1/level2", structure[1].value)
    }
}