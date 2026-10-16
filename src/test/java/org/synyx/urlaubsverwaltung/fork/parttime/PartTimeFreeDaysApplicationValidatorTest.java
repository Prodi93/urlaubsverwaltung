package org.synyx.urlaubsverwaltung.fork.parttime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.Errors;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.HOLIDAY;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.PARTTIME_FREEDAY;

@ExtendWith(MockitoExtension.class)
class PartTimeFreeDaysApplicationValidatorTest {

    @Mock
    private PartTimeService partTimeService;
    @Mock
    private PartTimeFreeDaysService partTimeFreeDaysService;
    @Mock
    private Errors errors;

    private PartTimeFreeDaysApplicationValidator sut;
    private Person person;
    private Application application;

    @BeforeEach
    void setUp() {
        sut = new PartTimeFreeDaysApplicationValidator(partTimeService, partTimeFreeDaysService);
        person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setId(1L);
        application = new Application();
        application.setId(42L);
        application.setPerson(person);
        application.setStartDate(LocalDate.of(2026, 3, 2));
        application.setEndDate(LocalDate.of(2026, 3, 3));
    }

    @Test
    void ensureOtherCategoriesAreIgnored() {
        sut.validate(application, vacationType(HOLIDAY), errors);

        verifyNoInteractions(partTimeService, partTimeFreeDaysService, errors);
    }

    @Test
    void ensureRejectsWithoutPartTime() {
        when(partTimeService.getPercentage(person, application.getStartDate())).thenReturn(Optional.empty());

        sut.validate(application, vacationType(PARTTIME_FREEDAY), errors);

        verify(errors).reject(PartTimeFreeDaysApplicationValidator.ERROR_NO_PART_TIME);
    }

    @Test
    void ensureRejectsIfNotEnoughFreeDaysLeft() {
        when(partTimeService.getPercentage(person, application.getStartDate())).thenReturn(Optional.of(new BigDecimal("80")));
        when(partTimeFreeDaysService.getDaysOfApplicationInYear(application, 2026)).thenReturn(new BigDecimal("2"));
        when(partTimeFreeDaysService.getSummary(person, 2026, 42L)).thenReturn(summary("1.5"));

        sut.validate(application, vacationType(PARTTIME_FREEDAY), errors);

        verify(errors).reject(eq(PartTimeFreeDaysApplicationValidator.ERROR_NOT_ENOUGH_FREE_DAYS), any(Object[].class), eq(null));
    }

    @Test
    void ensureAcceptsIfEnoughFreeDaysLeft() {
        when(partTimeService.getPercentage(person, application.getStartDate())).thenReturn(Optional.of(new BigDecimal("80")));
        when(partTimeFreeDaysService.getDaysOfApplicationInYear(any(), anyInt())).thenReturn(new BigDecimal("2"));
        when(partTimeFreeDaysService.getSummary(person, 2026, 42L)).thenReturn(summary("2"));

        sut.validate(application, vacationType(PARTTIME_FREEDAY), errors);

        verifyNoInteractions(errors);
    }

    private static PartTimeFreeDaysSummary summary(String left) {
        return new PartTimeFreeDaysSummary(2026, new BigDecimal(left), Optional.empty(), Optional.empty(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static VacationType<?> vacationType(org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory category) {
        final VacationType<?> vacationType = mock(VacationType.class);
        when(vacationType.isOfCategory(any())).thenAnswer(invocation -> invocation.getArgument(0) == category);
        return vacationType;
    }
}
