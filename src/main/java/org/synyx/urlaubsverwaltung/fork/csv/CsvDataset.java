package org.synyx.urlaubsverwaltung.fork.csv;

/**
 * Data that can be exported and imported as CSV.
 */
public enum CsvDataset {

    APPLICATIONS("abwesenheiten"),
    ACCOUNTS("urlaubskonten"),
    PRESENCES("anwesenheiten"),
    PERSONS_WORKING_TIMES("personen-arbeitszeiten");

    private final String fileName;

    CsvDataset(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
