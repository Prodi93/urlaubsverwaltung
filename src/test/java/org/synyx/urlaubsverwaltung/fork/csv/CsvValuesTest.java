package org.synyx.urlaubsverwaltung.fork.csv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvValuesTest {

    @Test
    void ensureDatesInIsoAndGermanFormat() {
        assertThat(CsvValues.parseDate("2026-10-12")).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(CsvValues.parseDate("12.10.2026")).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(CsvValues.parseDate("1.2.2026")).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThatThrownBy(() -> CsvValues.parseDate("2026/10/12")).hasMessage("fork.csv.error.date");
    }

    @Test
    void ensureDecimalsWithPointAndComma() {
        assertThat(CsvValues.parseDecimal("12,5")).isEqualByComparingTo("12.5");
        assertThat(CsvValues.format(new BigDecimal("30.00"))).isEqualTo("30");
        assertThat(CsvValues.format(new BigDecimal("2.50"))).isEqualTo("2.5");
    }

    @Test
    void ensureBooleans() {
        assertThat(CsvValues.parseBoolean("ja")).isTrue();
        assertThat(CsvValues.parseBoolean("FALSE")).isFalse();
        assertThatThrownBy(() -> CsvValues.parseBoolean("vielleicht")).hasMessage("fork.csv.error.boolean");
    }

    @Test
    void ensureWorkingDaysInEnglishAndGerman() {
        assertThat(PersonsWorkingTimesCsvHandler.parseWorkingDays("MON,TUE,WED,THU,FRI"))
            .containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
        assertThat(PersonsWorkingTimesCsvHandler.parseWorkingDays("fr, mo di mi do"))
            .containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
        assertThat(PersonsWorkingTimesCsvHandler.parseWorkingDays("MONDAY")).containsExactly(DayOfWeek.MONDAY);
        assertThatThrownBy(() -> PersonsWorkingTimesCsvHandler.parseWorkingDays("X")).hasMessage("fork.csv.error.workingDays");
    }
}
