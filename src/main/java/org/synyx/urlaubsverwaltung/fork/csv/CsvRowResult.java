package org.synyx.urlaubsverwaltung.fork.csv;

/**
 * Result of the import of a row.
 *
 * @param line      line number in the file
 * @param subject   who / what the row is about, e.g. the username
 * @param action    what happens with the row
 * @param message   message key with details, optional
 * @param arguments arguments of the message
 */
public record CsvRowResult(int line, String subject, Action action, String message, Object... arguments) {

    public enum Action {
        CREATE,
        UPDATE,
        DELETE,
        UNCHANGED,
        ERROR
    }

    public static CsvRowResult of(CsvTable.Row row, String subject, Action action) {
        return new CsvRowResult(row.line(), subject, action, null);
    }

    public static CsvRowResult error(CsvTable.Row row, String subject, String message, Object... arguments) {
        return new CsvRowResult(row.line(), subject, Action.ERROR, message, arguments);
    }

    public boolean isError() {
        return action == Action.ERROR;
    }
}
