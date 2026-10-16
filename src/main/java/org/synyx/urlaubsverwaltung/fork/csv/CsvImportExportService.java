package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export and import of CSV files. An import is first validated without saving anything (preview) and then executed.
 * The execution is all or nothing, unless rows with errors should be skipped.
 */
@Service
public class CsvImportExportService {

    private final Map<CsvDataset, CsvDatasetHandler> handlers = new EnumMap<>(CsvDataset.class);

    CsvImportExportService(List<CsvDatasetHandler> handlers) {
        handlers.forEach(handler -> this.handlers.put(handler.dataset(), handler));
    }

    public byte[] export(CsvDataset dataset, List<Person> persons, DateRange dateRange, Locale locale) {
        final CsvDatasetHandler handler = handler(dataset);
        return ForkCsv.write(handler.header(), handler.export(persons, dateRange, locale));
    }

    /**
     * @return a csv file with the header of the dataset only
     */
    public byte[] template(CsvDataset dataset) {
        return ForkCsv.write(handler(dataset).header(), List.of());
    }

    /**
     * Validates all rows without saving anything.
     */
    @Transactional(readOnly = true)
    public CsvImportResult preview(CsvDataset dataset, String content, Person importer) {
        return run(dataset, content, importer, false);
    }

    /**
     * Saves all rows. If a row has an error, nothing is saved, unless {@code skipErrors} is {@code true}.
     */
    @Transactional(rollbackFor = CsvImportAbortedException.class)
    public CsvImportResult execute(CsvDataset dataset, String content, Person importer, boolean skipErrors) {
        final CsvImportResult result = run(dataset, content, importer, true);
        if (result.hasErrors() && !skipErrors) {
            throw new CsvImportAbortedException(new CsvImportResult(dataset, result.rows(), false));
        }
        return result;
    }

    private CsvImportResult run(CsvDataset dataset, String content, Person importer, boolean execute) {
        final CsvDatasetHandler handler = handler(dataset);
        final CsvTable table = ForkCsv.read(content);

        final boolean hasPersonColumn = table.header().contains(CsvPersonResolver.USERNAME) || table.header().contains(CsvPersonResolver.EMAIL);
        final List<String> missingColumns = new ArrayList<>();
        if (!hasPersonColumn) {
            missingColumns.add(CsvPersonResolver.USERNAME);
        }
        handler.requiredColumns().stream().filter(column -> !table.header().contains(column)).forEach(missingColumns::add);
        if (!missingColumns.isEmpty()) {
            throw new CsvFormatException("fork.csv.error.columns", String.join(", ", missingColumns));
        }

        final List<CsvRowResult> results = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            results.add(handler.importRow(row, importer, execute));
        }
        return new CsvImportResult(dataset, results, execute);
    }

    private CsvDatasetHandler handler(CsvDataset dataset) {
        final CsvDatasetHandler handler = handlers.get(dataset);
        if (handler == null) {
            throw new IllegalArgumentException("no handler for " + dataset);
        }
        return handler;
    }
}
