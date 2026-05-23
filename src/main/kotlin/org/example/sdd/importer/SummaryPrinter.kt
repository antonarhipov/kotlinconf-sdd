package org.example.sdd.importer

import org.springframework.stereotype.Component
import java.io.PrintStream

@Component
class SummaryPrinter {
    fun printFileSummary(out: PrintStream, fileName: String, inserted: Int, duplicates: Int, malformed: Int) {
        out.println("$fileName: inserted=$inserted, duplicates=$duplicates, malformed=$malformed")
        out.flush()
    }

    fun printFileFailed(out: PrintStream, reason: String) {
        out.println("FAILED: $reason")
        out.flush()
    }

    fun printEmptyTotal(out: PrintStream) {
        out.println("TOTAL: files=0, inserted=0, duplicates=0, malformed=0, failed_files=0")
        out.flush()
    }

    fun printTotal(out: PrintStream, files: Int, inserted: Int, duplicates: Int, malformed: Int, failedFiles: Int) {
        out.println("TOTAL: files=$files, inserted=$inserted, duplicates=$duplicates, malformed=$malformed, failed_files=$failedFiles")
        out.flush()
    }
}
