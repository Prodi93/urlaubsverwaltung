package org.synyx.urlaubsverwaltung.application.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.account.AccountInteractionService;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeWriteService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.HOLIDAY;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.MORNING;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

@SpringBootTest
@Transactional
class ForkApplicationForLeaveCreatorIT extends SingleTenantTestContainersBase {

    @Autowired
    private ForkApplicationForLeaveCreator sut;
    @Autowired
    private PersonService personService;
    @Autowired
    private WorkingTimeWriteService workingTimeWriteService;
    @Autowired
    private AccountInteractionService accountInteractionService;
    @Autowired
    private VacationTypeService vacationTypeService;
    @Autowired
    private ApplicationService applicationService;

    @Test
    void ensureAppliesWithTheValidationOfTheApplicationForm() {
        final Person person = personService.create("creator", "Marlene", "Muster", "creator@example.org", List.of(), List.of(USER, OFFICE));
        final int year = LocalDate.now().getYear() + 1;
        workingTimeWriteService.touch(List.of(1, 2, 3, 4, 5), LocalDate.of(year, 1, 1), person);
        accountInteractionService.updateOrCreateHolidaysAccount(person, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), null, null,
            new BigDecimal("2"), new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO, null);
        final VacationType<?> holiday = vacationTypeService.getActiveVacationTypes().stream().filter(type -> type.isOfCategory(HOLIDAY)).findFirst().orElseThrow();

        // a monday and half a tuesday in march
        final LocalDate monday = LocalDate.of(year, 3, 1).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        final ForkApplicationForLeaveCreator.Result fullDay = sut.apply(person, person, holiday.getId(), monday, monday, FULL, Locale.GERMAN);
        final ForkApplicationForLeaveCreator.Result halfDay = sut.apply(person, person, holiday.getId(), monday.plusDays(1), monday.plusDays(1), MORNING, Locale.GERMAN);

        assertThat(fullDay.isSuccess()).isTrue();
        assertThat(halfDay.isSuccess()).isTrue();
        assertThat(applicationService.getApplicationsForACertainPeriodAndPerson(monday, monday.plusDays(1), person)).hasSize(2);

        // only half a day left
        final ForkApplicationForLeaveCreator.Result tooMuch = sut.apply(person, person, holiday.getId(), monday.plusDays(7), monday.plusDays(8), FULL, Locale.GERMAN);
        assertThat(tooMuch.isSuccess()).isFalse();
        assertThat(tooMuch.errorMessages()).isNotEmpty();

        // overlapping
        final ForkApplicationForLeaveCreator.Result overlap = sut.apply(person, person, holiday.getId(), monday, monday, FULL, Locale.GERMAN);
        assertThat(overlap.isSuccess()).isFalse();
    }
}
