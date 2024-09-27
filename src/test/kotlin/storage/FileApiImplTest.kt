import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import storage.FileApi
import storage.FileApiImpl
import java.io.File

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
        fileApi.create(FileApi.Path("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.Path("some-file2"), "My string file2".toByteArray())

        val firstFile = fileApi.read(FileApi.Path("some-file"))!!
        assertEquals("My string file", String(firstFile))

        val secondFile = fileApi.read(FileApi.Path("some-file2"))!!
        assertEquals("My string file2", String(secondFile))
    }

    @Test
    fun `Read from a file with storage entries should succeed`() {
        fileApi.create(FileApi.Path("some-file"), "My string file".toByteArray())
        fileApi.create(FileApi.Path("some-file2"), "My string file2".toByteArray())

        // A new instance of file api imitating restart of the application
        val newFileApi = FileApiImpl(file)

        val firstFile = newFileApi.read(FileApi.Path("some-file"))!!
        assertEquals("My string file", String(firstFile))

        val secondFile = newFileApi.read(FileApi.Path("some-file2"))!!
        assertEquals("My string file2", String(secondFile))
    }
}