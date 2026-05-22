package org.example.sdd.importer

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties("importer")
data class ImporterProperties(
    val inputDir: Path
)
