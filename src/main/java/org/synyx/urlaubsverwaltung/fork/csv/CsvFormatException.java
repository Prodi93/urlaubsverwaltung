package org.synyx.urlaubsverwaltung.fork.csv;

/**
 * The CSV file cannot be read, e.g. because it is empty or columns are missing.
 */
public class CsvFormatException extends RuntimeException {

    private final String messageKey;
    private final Object[] arguments;

    public CsvFormatException(String messageKey, Object... arguments) {
        super(messageKey);
        this.messageKey = messageKey;
        this.arguments = arguments;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArguments() {
        return arguments;
    }
}
