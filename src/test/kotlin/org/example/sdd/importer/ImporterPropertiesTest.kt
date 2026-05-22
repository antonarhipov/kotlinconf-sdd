package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.BindException
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path

class ImporterPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(ImporterProperties::class)
    open class TestConfig

    @Test
    fun `should bind inputDir when importer input-dir is set`() {
        contextRunner
            .withPropertyValues("importer.input-dir=/tmp/input")
            .run { context ->
                assertThat(context.startupFailure).isNull()
                val properties = context.getBean(ImporterProperties::class.java)
                assertThat(properties.inputDir).isEqualTo(Path.of("/tmp/input"))
            }
    }

    @Test
    fun `should fail context refresh when importer input-dir is missing`() {
        contextRunner
            .run { context ->
                assertThat(context.startupFailure).isNotNull()
                assertThat(context.startupFailure)
                    .hasCauseInstanceOf(BindException::class.java)
            }
    }
}
