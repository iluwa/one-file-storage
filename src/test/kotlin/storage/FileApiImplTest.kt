import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import storage.FileApi
import storage.FileApiImpl
import java.io.File

class FileApiImplTest {
    lateinit var file: File
    lateinit var fileApi: FileApiImpl

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
}