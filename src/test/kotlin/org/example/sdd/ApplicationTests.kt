package org.example.sdd

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["importer.input-dir=src/test/resources/input"])
class ApplicationTests {

    @Test
    fun contextLoads() {
    }

}
