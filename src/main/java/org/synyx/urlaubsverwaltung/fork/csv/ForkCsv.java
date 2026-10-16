package org.synyx.urlaubsverwaltung.fork.csv;

import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;
import com.opencsv.exceptions.CsvException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Writes and reads CSV files: separator ';' (',' is detected when reading), values quoted if needed (RFC 4180),
 * UTF-8 with byte order mark, so Excel opens umlauts correctly.
 */
public final class ForkCsv {

    private static final char SEPARATOR = ';';
    private static final String BOM = "﻿";

    private ForkCsv() {
    }

    public static byte[] write(List<String> header, List<List<String>> rows) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(outputStream, UTF_8);
             ICSVWriter csvWriter = new CSVWriter(writer, SEPARATOR, ICSVWriter.DEFAULT_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER, "\r\n")) {
            writer.write(BOM);
            csvWriter.writeNext(header.toArray(String[]::new), false);
            for (List<String> row : rows) {
                csvWriter.writeNext(row.stream().map(value -> value == null ? "" : value).toArray(String[]::new), false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return outputStream.toByteArray();
    }

    public static CsvTable read(String content) {
        String text = content == null ? "" : content;
        if (text.startsWith(BOM)) {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw new CsvFormatException("fork.csv.error.empty");
        }

        final char separator = detectSeparator(text);
        final List<String[]> lines;
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
            .withCSVParser(new RFC4180ParserBuilder().withSeparator(separator).build())
            .build()) {
            lines = reader.readAll();
        } catch (IOException | CsvException e) {
            throw new CsvFormatException("fork.csv.error.unreadable", e.getMessage());
        }

        if (lines.isEmpty()) {
            throw new CsvFormatException("fork.csv.error.empty");
        }

        final List<String> header = new ArrayList<>();
        for (String column : lines.getFirst()) {
            header.add(column.strip().toLowerCase(Locale.ROOT));
        }

        final List<CsvTable.Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            final String[] values = lines.get(i);
            if (isEmptyLine(values)) {
                continue;
            }
            final Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                row.put(header.get(column), column < values.length ? values[column] : "");
            }
            rows.add(new CsvTable.Row(i + 1, row));
        }
        return new CsvTable(header, rows);
    }

    private static char detectSeparator(String text) {
        final int lineEnd = text.indexOf('\n');
        final String firstLine = lineEnd < 0 ? text : text.substring(0, lineEnd);
        final long semicolons = firstLine.chars().filter(c -> c == ';').count();
        final long commas = firstLine.chars().filter(c -> c == ',').count();
        return commas > semicolons ? ',' : ';';
    }

    private static boolean isEmptyLine(String[] values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
