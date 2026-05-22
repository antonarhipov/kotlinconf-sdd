package org.example.sdd.importer

sealed class ImporterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class StartupError(message: String, cause: Throwable? = null) : ImporterException(message, cause)
