package org.synyx.urlaubsverwaltung.fork.csv;

import java.util.List;
import java.util.Map;

/**
 * Parsed CSV file.
 *
 * @param header column names in lower case
 * @param rows   the rows with their line number in the file
 */
public record CsvTable(List<String> header, List<Row> rows) {

    public record Row(int line, Map<String, String> values) {

        /**
         * @return the trimmed value of the column, empty string if the column is missing
         */
        public String get(String column) {
            final String value = values.get(column);
            return value == null ? "" : value.strip();
        }

        public boolean has(String column) {
            return !get(column).isEmpty();
        }
    }
}
