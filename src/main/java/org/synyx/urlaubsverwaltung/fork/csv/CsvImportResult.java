package org.synyx.urlaubsverwaltung.fork.csv;

import java.util.List;

import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.CREATE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.DELETE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.ERROR;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UNCHANGED;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UPDATE;

/**
 * @param dataset  imported dataset
 * @param rows     result per row
 * @param executed {@code true} if the changes are saved, {@code false} for a preview
 */
public record CsvImportResult(CsvDataset dataset, List<CsvRowResult> rows, boolean executed) {

    public long created() {
        return count(CREATE);
    }

    public long updated() {
        return count(UPDATE);
    }

    public long deleted() {
        return count(DELETE);
    }

    public long unchanged() {
        return count(UNCHANGED);
    }

    public long errors() {
        return count(ERROR);
    }

    public boolean hasErrors() {
        return errors() > 0;
    }

    public boolean hasChanges() {
        return created() + updated() + deleted() > 0;
    }

    private long count(CsvRowResult.Action action) {
        return rows.stream().filter(row -> row.action() == action).count();
    }
}
