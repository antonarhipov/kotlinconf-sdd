package org.example.sdd.importer

sealed class ImporterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class StartupError(message: String, cause: Throwable? = null) : ImporterException(message, cause)

sealed class FatalFileError(val reason: String, val inserted: Int = 0, val malformed: Int = 0) {
    class HeaderMissing(reason: String) : FatalFileError(reason, 0, 0)
    class HeaderDuplicate(reason: String) : FatalFileError(reason, 0, 0)
    class FileEncoding(reason: String, inserted: Int, malformed: Int) : FatalFileError(reason, inserted, malformed)
    class Database(reason: String, inserted: Int, malformed: Int) : FatalFileError(reason, inserted, malformed)
}
