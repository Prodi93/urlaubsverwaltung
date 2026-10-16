package org.synyx.urlaubsverwaltung.fork.parttime;

import org.junit.jupiter.api.Test;
import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartTimeServiceTest {

    private final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

    private final List<PartTime> history = List.of(
        new PartTime(1L, person, LocalDate.of(2025, 1, 1), new BigDecimal("80")),
        new PartTime(2L, person, LocalDate.of(2026, 1, 1), new BigDecimal("60")),
        new PartTime(3L, person, LocalDate.of(2026, 7, 1), new BigDecimal("100"))
    );

    @Test
    void ensureNoPercentageBeforeFirstPartTime() {
        assertThat(PartTimeService.percentageOn(history, LocalDate.of(2024, 12, 31))).isEmpty();
    }

    @Test
    void ensurePercentageOfLatestPartTimeStartedBeforeDate() {
        assertThat(PartTimeService.percentageOn(history, LocalDate.of(2025, 1, 1))).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("80"));
        assertThat(PartTimeService.percentageOn(history, LocalDate.of(2025, 12, 31))).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("80"));
        assertThat(PartTimeService.percentageOn(history, LocalDate.of(2026, 6, 30))).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("60"));
    }

    @Test
    void ensureHundredPercentEndsPartTime() {
        assertThat(PartTimeService.percentageOn(history, LocalDate.of(2026, 7, 1))).isEmpty();
    }
}
