package org.synyx.urlaubsverwaltung.fork.publicholiday;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EasterSundayTest {

    @ParameterizedTest
    @CsvSource({
        "2000, 2000-04-23",
        "2008, 2008-03-23",
        "2011, 2011-04-24",
        "2019, 2019-04-21",
        "2024, 2024-03-31",
        "2025, 2025-04-20",
        "2026, 2026-04-05",
        "2027, 2027-03-28",
        "2028, 2028-04-16",
        "2029, 2029-04-01",
        "2030, 2030-04-21",
        "2035, 2035-03-25",
        "2038, 2038-04-25",
    })
    void ensureCorrectEasterSunday(int year, LocalDate expected) {
        assertThat(EasterSunday.of(year)).isEqualTo(expected);
    }
}
