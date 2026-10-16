package org.synyx.urlaubsverwaltung.fork.parttime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PartTimeServiceIT extends SingleTenantTestContainersBase {

    @Autowired
    private PartTimeService partTimeService;
    @Autowired
    private PartTimeFreeDaysService partTimeFreeDaysService;
    @Autowired
    private PersonService personService;

    @Test
    void ensurePartTimeHistoryIsPersistedAndUpdatedByValidFrom() {
        final Person person = personService.create("parttime", "Marlene", "Muster", "parttime@example.org");

        partTimeService.save(person, LocalDate.of(2026, 1, 1), new BigDecimal("80"));
        partTimeService.save(person, LocalDate.of(2026, 7, 1), new BigDecimal("60"));
        partTimeService.save(person, LocalDate.of(2026, 7, 1), new BigDecimal("50"));

        assertThat(partTimeService.getPartTimes(person))
            .extracting(PartTime::validFrom, partTime -> partTime.percentage().stripTrailingZeros().toPlainString())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 1, 1), "80"),
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 7, 1), "50")
            );
        assertThat(partTimeService.getPercentage(person, LocalDate.of(2026, 3, 1)))
            .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("80"));
    }

    @Test
    void ensureQuotaOverrideIsPersisted() {
        final Person person = personService.create("quota", "Max", "Muster", "quota@example.org");
        partTimeService.save(person, LocalDate.of(2026, 1, 1), new BigDecimal("80"));

        partTimeFreeDaysService.setOverride(person, 2026, Optional.of(new BigDecimal("12.5")), Optional.of("Korrektur"));

        final PartTimeFreeDaysSummary summary = partTimeFreeDaysService.getSummary(person, 2026);
        assertThat(summary.override()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("12.5"));
        assertThat(summary.comment()).hasValue("Korrektur");

        partTimeFreeDaysService.setOverride(person, 2026, Optional.empty(), Optional.empty());
        assertThat(partTimeFreeDaysService.getSummary(person, 2026).override()).isEmpty();
    }
}
