package org.synyx.urlaubsverwaltung.fork.csv;

/**
 * Thrown to roll back an import with errors.
 */
class CsvImportAbortedException extends RuntimeException {

    private final transient CsvImportResult result;

    CsvImportAbortedException(CsvImportResult result) {
        super("import aborted because of errors");
        this.result = result;
    }

    CsvImportResult getResult() {
        return result;
    }
}
