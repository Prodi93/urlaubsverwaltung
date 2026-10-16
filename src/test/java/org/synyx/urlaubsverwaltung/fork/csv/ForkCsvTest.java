package org.synyx.urlaubsverwaltung.fork.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForkCsvTest {

    @Test
    void ensureWrittenFileCanBeReadAgain() {
        final byte[] csv = ForkCsv.write(List.of("username", "Reason"), List.of(
            List.of("müller", "Urlaub; \"Strand\"\nzweite Zeile"),
            List.of("o'brien", "C:\\temp")
        ));

        final String content = new String(csv, UTF_8);
        assertThat(content).startsWith("\uFEFFusername;Reason\r\n");

        final CsvTable table = ForkCsv.read(content);
        assertThat(table.header()).containsExactly("username", "reason");
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(0).get("username")).isEqualTo("müller");
        assertThat(table.rows().get(0).get("reason")).isEqualTo("Urlaub; \"Strand\"\nzweite Zeile");
        assertThat(table.rows().get(1).get("reason")).isEqualTo("C:\\temp");
        // row number as shown in a spreadsheet, a line break within a value does not count
        assertThat(table.rows().get(1).line()).isEqualTo(3);
    }

    @Test
    void ensureCommaSeparatedFilesAreDetected() {
        final CsvTable table = ForkCsv.read("username,date,type\nmuster,2026-10-12,OFFICE\n\n");
        assertThat(table.rows()).singleElement().satisfies(row -> {
            assertThat(row.get("date")).isEqualTo("2026-10-12");
            assertThat(row.get("type")).isEqualTo("OFFICE");
            assertThat(row.get("missing")).isEmpty();
        });
    }

    @Test
    void ensureEmptyFileIsRejected() {
        assertThatThrownBy(() -> ForkCsv.read("\uFEFF  "))
            .isInstanceOf(CsvFormatException.class)
            .extracting("messageKey").isEqualTo("fork.csv.error.empty");
    }
}
