import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import storage.FilePath
import storage.FolderPath
import storage.StorageContainer
import storage.singlefile.SingleFileStorageContainer
import java.io.FileNotFoundException
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SingleFileStorageContainerTest {
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

    @Test
    fun `Basic case of reading the files`() {
        storageContainer.create(FilePath("some-file"), "My string file".toByteArray())
        storageContainer.create(FilePath("some-file2"), "My string file2".toByteArray())

        val firstFile = storageContainer.read(FilePath("some-file"))
        assertEquals("My string file", String(firstFile))

        val secondFile = storageContainer.read(FilePath("some-file2"))
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Read from a file with storage entries should succeed`() {
        storageContainer.create(FilePath("some-file"), "My string file".toByteArray())
        storageContainer.create(FilePath("some-file2"), "My string file2".toByteArray())

        storageContainer.stop()
        storageContainer.start()

        val firstFile = storageContainer.read(FilePath("some-file"))
        assertEquals("My string file", String(firstFile))

        val secondFile = storageContainer.read(FilePath("some-file2"))
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Reading a file after updating should give the latest version`() {
        storageContainer.create(FilePath("some-file"), "My string file".toByteArray())
        storageContainer.create(FilePath("some-file2"), "My string file2".toByteArray())

        storageContainer.write(FilePath("some-file"), "New version".toByteArray())

        val firstFile = storageContainer.read(FilePath("some-file"))
        assertEquals("New version", String(firstFile))
    }

    @Test
    fun `Writing to a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                storageContainer.write(FilePath("non-existing-file"), "New version".toByteArray())
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Appending to a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                storageContainer.append(FilePath("non-existing-file"), "New version".toByteArray())
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Reading a file after appending should give the latest version`() {
        storageContainer.create(FilePath("some-file"), "My string file".toByteArray())
        storageContainer.create(FilePath("some-file2"), "My string file2".toByteArray())

        storageContainer.append(FilePath("some-file"), " - new version".toByteArray())

        val firstFile = storageContainer.read(FilePath("some-file"))
        assertEquals("My string file - new version", String(firstFile))
    }

    @Test
    fun `Renaming a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                storageContainer.rename(FilePath("non-existing-file"), FilePath("non-existing-file2"))
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Reading a file after renaming should give the latest version`() {
        storageContainer.create(FilePath("before"), "My string file".toByteArray())

        storageContainer.rename(FilePath("before"), FilePath("after"))

        val after = storageContainer.read(FilePath("after"))
        assertEquals("My string file", String(after))

        assertFalse(storageContainer.exists(FilePath("before")))
    }

    @Test
    fun `Moving a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                storageContainer.move(FilePath("non-existing-file"), FilePath("non-existing-file2"))
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Moving a file after renaming should give the latest version`() {
        storageContainer.create(FilePath("before"), "My string file".toByteArray())

        storageContainer.move(FilePath("before"), FilePath("after"))

        val after = storageContainer.read(FilePath("after"))
        assertEquals("My string file", String(after))

        assertFalse(storageContainer.exists(FilePath("before")))
    }

    @Test
    fun `Deleting a non-existing file should resolve in exception`() {
        val ex = assertFailsWith<FileNotFoundException>(
            message = "No FileNotFoundException was thrown",
            block = {
                storageContainer.delete(FilePath("non-existing-file"))
            }
        )
        assertEquals("non-existing-file", ex.message)
    }

    @Test
    fun `Deleted files should not be loaded on the startup`() {
        storageContainer.create(FilePath("some-file"), "My string file".toByteArray())
        storageContainer.create(FilePath("some-file2"), "My string file2".toByteArray())
        storageContainer.delete(FilePath("some-file"))

        storageContainer.stop()
        storageContainer.start()

        assertFalse(storageContainer.exists(FilePath("some-file")))

        val secondFile = storageContainer.read(FilePath("some-file2"))
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Create and read an empty folder three levels deep`() {
        storageContainer.create(FolderPath("level1/level2/level3"))

        val level1 = storageContainer.read(FolderPath("level1"))
        assertEquals(1, level1.size)
        assertEquals(FolderPath("level1/level2"), level1[0])

        val level2 = storageContainer.read(level1[0] as FolderPath)
        assertEquals(1, level2.size)
        assertEquals(FolderPath("level1/level2/level3"), level2[0])

        val level3 = storageContainer.read(level2[0] as FolderPath)
        assertEquals(0, level3.size)
    }

    @Test
    fun `Delete an empty folder`() {
        storageContainer.create(FolderPath("level1/level2"))
        storageContainer.delete(FolderPath("level1/level2"))

        val level1 = storageContainer.read(FolderPath("level1"))
        assertEquals(0, level1.size)
    }

    @Test
    fun `Delete folder with a nested file - the file should be deleted`() {
        storageContainer.create(FolderPath("level1/level2"))
        storageContainer.create(FilePath("level1/level2/myfile"), "Content".toByteArray())
        storageContainer.delete(FolderPath("level1/level2"))

        val level1 = storageContainer.read(FolderPath("level1"))
        assertEquals(0, level1.size)

        assertFalse(storageContainer.exists(FilePath("level1/level2/myfile")))
    }

    @Test
    fun `Deleted folder with children objects should not be loaded on the startup`() {
        storageContainer.create(FilePath("level1/level2/myfile"), "Content".toByteArray())
        storageContainer.create(FolderPath("level1/level2-2"))
        storageContainer.delete(FolderPath("level1/level2"))

        storageContainer.stop()
        storageContainer.start()

        val level1 = storageContainer.read(FolderPath("level1"))
        assertEquals(1, level1.size)
        assertEquals(FolderPath("level1/level2-2"), level1[0])

        assertFalse(storageContainer.exists(FilePath("level1/level2/myfile")))
    }

    @Test
    fun `After folder renaming all nested objects should be under the renamed one`() {
        storageContainer.create(FilePath("level1/myfile"), "Content".toByteArray())
        storageContainer.create(FolderPath("level1/level2"))
        storageContainer.rename(FolderPath("level1"), FolderPath("level1-1"))

        assertFalse(storageContainer.exists(FolderPath("level1")))

        val level11 = storageContainer.read(FolderPath("level1-1"))
        assertEquals(2, level11.size)

        assertTrue(storageContainer.exists(FilePath("level1-1/myfile")))
        assertTrue(storageContainer.exists(FolderPath("level1-1/level2")))
    }

    @Test
    fun `Test moving from root folder to a nested one`() {
        storageContainer.create(FilePath("myfile"), "Content".toByteArray())
        storageContainer.create(FolderPath("level1"))
        storageContainer.move(FilePath("myfile"), FilePath("level1/myfile"))

        val movedFileContent = storageContainer.read(FilePath("level1/myfile"))
        assertEquals("Content", String(movedFileContent))
    }

    @Test
    fun `Test that compact file should keep all of the files`() {
        storageContainer.create(FilePath("myfile"), "Content".toByteArray())
        storageContainer.create(FolderPath("level1"))
        storageContainer.create(FilePath("level2/nested-file"), "Nested content".toByteArray())
        storageContainer.move(FilePath("myfile"), FilePath("level1/myfile"))

        storageContainer.compact()

        assertTrue(storageContainer.exists(FilePath("level1/myfile")))
        assertTrue(storageContainer.exists(FilePath("level2/nested-file")))
    }
}