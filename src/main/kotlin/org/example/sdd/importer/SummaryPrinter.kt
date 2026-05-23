package org.example.sdd.importer

import org.springframework.stereotype.Component
import java.io.PrintStream

@Component
class SummaryPrinter {
    fun printEmptyTotal(out: PrintStream) {
        out.println("TOTAL: files=0, inserted=0, duplicates=0, malformed=0, failed_files=0")
    }
}
