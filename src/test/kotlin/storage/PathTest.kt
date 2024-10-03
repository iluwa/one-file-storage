import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import storage.FolderPath

class PathTest {
    @Test
    fun `Multiple level path should be split correctly`() {
        val structure = FolderPath("level1/level2").split()
        assertEquals(2, structure.size)
        assertEquals("level1", structure[0].value)
        assertEquals("level1/level2", structure[1].value)
    }
}