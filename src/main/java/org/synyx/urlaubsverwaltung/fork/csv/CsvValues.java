package org.synyx.urlaubsverwaltung.fork.csv;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Formats and parses values of CSV files. Dates are written as ISO (yyyy-MM-dd), dd.MM.yyyy is accepted too.
 * Decimals are written with '.', ',' is accepted too.
 */
final class CsvValues {

    private static final DateTimeFormatter GERMAN_DATE = DateTimeFormatter.ofPattern("d.M.uuuu");

    private CsvValues() {
    }

    static String format(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    static String format(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    static String format(Boolean value) {
        return value == null ? "" : value.toString();
    }

    static LocalDate parseDate(String value) {
        try {
            return value.contains(".") ? LocalDate.parse(value, GERMAN_DATE) : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("fork.csv.error.date");
        }
    }

    static BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("fork.csv.error.number");
        }
    }

    static Boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "ja", "yes", "1", "x" -> true;
            case "false", "nein", "no", "0" -> false;
            default -> throw new IllegalArgumentException("fork.csv.error.boolean");
        };
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("fork.csv.error.value");
        }
    }
}
