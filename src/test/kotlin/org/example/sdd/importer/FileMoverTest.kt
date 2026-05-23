package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileMoverTest {

    @TempDir
    lateinit var tempDir: Path

    private val mover = FileMover()

    @Test
    fun `should successfully move file when no collision exists`() {
        val srcDir = Files.createDirectory(tempDir.resolve("src"))
        val dstDir = Files.createDirectory(tempDir.resolve("dst"))
        val file = Files.createFile(srcDir.resolve("test.csv"))

        val result = mover.move(file, dstDir)

        assertThat(result).isNotNull()
        assertThat(result!!.fileName.toString()).isEqualTo("test.csv")
        assertThat(Files.exists(dstDir.resolve("test.csv"))).isTrue()
        assertThat(Files.exists(file)).isFalse()
    }

    @Test
    fun `should retry with timestamp suffix on name collision`() {
        val srcDir = Files.createDirectory(tempDir.resolve("src"))
        val dstDir = Files.createDirectory(tempDir.resolve("dst"))
        
        val file = Files.createFile(srcDir.resolve("test.csv"))
        // Create a colliding file in dstDir
        val collidingFile = Files.createFile(dstDir.resolve("test.csv"))

        val beforeMove = System.currentTimeMillis()
        val result = mover.move(file, dstDir)
        val afterMove = System.currentTimeMillis()

        assertThat(result).isNotNull()
        val resultName = result!!.fileName.toString()
        assertThat(resultName).startsWith("test-")
        assertThat(resultName).endsWith(".csv")
        
        // Extract timestamp from test-<timestamp>.csv
        val timestampStr = resultName.substringAfter("test-").substringBefore(".csv")
        val timestamp = timestampStr.toLong()
        assertThat(timestamp).isBetween(beforeMove - 5000, afterMove + 5000)

        assertThat(Files.exists(dstDir.resolve(resultName))).isTrue()
        assertThat(Files.exists(collidingFile)).isTrue()
        assertThat(Files.exists(file)).isFalse()
    }

    @Test
    fun `should return null and leave source in place on other IOException`() {
        val srcDir = Files.createDirectory(tempDir.resolve("src"))
        val dstDir = Files.createDirectory(tempDir.resolve("dst"))
        
        // Move a non-existent file to trigger IOException
        val nonExistentFile = srcDir.resolve("doesnotexist.csv")
        val result = mover.move(nonExistentFile, dstDir)

        assertThat(result).isNull()
        assertThat(Files.exists(nonExistentFile)).isFalse() // Should not exist as it didn't exist in the first place
    }
}
