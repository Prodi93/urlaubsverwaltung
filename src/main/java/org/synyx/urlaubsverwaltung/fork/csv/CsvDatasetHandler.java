package org.synyx.urlaubsverwaltung.fork.csv;

import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;

import java.util.List;
import java.util.Locale;

/**
 * Export and import of one {@link CsvDataset}. Exported files can be imported again without changes.
 */
interface CsvDatasetHandler {

    CsvDataset dataset();

    /**
     * @return column names of the CSV file
     */
    List<String> header();

    /**
     * @return columns that must be present in an imported file
     */
    List<String> requiredColumns();

    List<List<String>> export(List<Person> persons, DateRange dateRange, Locale locale);

    /**
     * Validates a row and, if {@code execute} is {@code true}, saves it.
     * Must not save anything if the result is an error.
     */
    CsvRowResult importRow(CsvTable.Row row, Person importer, boolean execute);
}
