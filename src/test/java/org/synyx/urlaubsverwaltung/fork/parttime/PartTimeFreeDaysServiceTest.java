package org.synyx.urlaubsverwaltung.fork.parttime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.application.ApplicationService;
import org.synyx.urlaubsverwaltung.application.application.ApplicationStatus;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendar;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendar.WorkingDayInformation;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendarService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED_CANCELLATION_REQUESTED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.TEMPORARY_ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.WAITING;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.activeStatuses;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.PARTTIME_FREEDAY;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.MORNING;
import static org.synyx.urlaubsverwaltung.period.DayLength.NOON;
import static org.synyx.urlaubsverwaltung.period.DayLength.ZERO;
import static org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendar.WorkingDayInformation.WorkingTimeCalendarEntryType.NO_WORKDAY;
import static org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendar.WorkingDayInformation.WorkingTimeCalendarEntryType.PUBLIC_HOLIDAY;
import static org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendar.WorkingDayInformation.WorkingTimeCalendarEntryType.WORKDAY;

@ExtendWith(MockitoExtension.class)
class PartTimeFreeDaysServiceTest {

    private static final DateRange YEAR_2026 = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    @Mock
    private PartTimeService partTimeService;
    @Mock
    private PartTimeQuotaRepository partTimeQuotaRepository;
    @Mock
    private WorkingTimeCalendarService workingTimeCalendarService;
    @Mock
    private ApplicationService applicationService;

    private PartTimeFreeDaysService sut;
    private Person person;

    @BeforeEach
    void setUp() {
        sut = new PartTimeFreeDaysService(partTimeService, partTimeQuotaRepository, workingTimeCalendarService, applicationService);
        person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setId(1L);
    }

    @Test
    void ensureNoQuotaWithoutPartTime() {
        assertThat(PartTimeFreeDaysService.computeQuota(List.of(), calendar(Set.of()), YEAR_2026)).isEqualByComparingTo("0");
    }

    @Test
    void ensureQuotaForWholeYearIsTwentyPercentOfWorkingDays() {
        // 2026 has 261 weekdays -> 52.2 days -> rounded to 52.0
        final List<PartTime> partTimes = List.of(partTime(LocalDate.of(2026, 1, 1), "80"));
        assertThat(PartTimeFreeDaysService.computeQuota(partTimes, calendar(Set.of()), YEAR_2026)).isEqualByComparingTo("52.0");
    }

    @Test
    void ensurePublicHolidaysReduceTheQuota() {
        // 261 weekdays - 11 public holidays on weekdays = 250 -> 50.0
        final Set<LocalDate> publicHolidays = Set.of(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 4, 6), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 25),
            LocalDate.of(2026, 6, 4), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 12, 8)
        );
        final List<PartTime> partTimes = List.of(partTime(LocalDate.of(2026, 1, 1), "80"));
        assertThat(PartTimeFreeDaysService.computeQuota(partTimes, calendar(publicHolidays), YEAR_2026)).isEqualByComparingTo("50.0");
    }

    @Test
    void ensurePartTimeEndingWithinTheYearIsProRata() {
        // 129 weekdays from january to june -> 25.8 -> 26.0
        final List<PartTime> partTimes = List.of(
            partTime(LocalDate.of(2026, 1, 1), "80"),
            partTime(LocalDate.of(2026, 7, 1), "100")
        );
        assertThat(PartTimeFreeDaysService.computeQuota(partTimes, calendar(Set.of()), YEAR_2026)).isEqualByComparingTo("26.0");
    }

    @Test
    void ensurePartTimeStartingWithinTheYearIsProRata() {
        // 132 weekdays from july to december at 50% -> 66.0
        final List<PartTime> partTimes = List.of(partTime(LocalDate.of(2026, 7, 1), "50"));
        assertThat(PartTimeFreeDaysService.computeQuota(partTimes, calendar(Set.of()), YEAR_2026)).isEqualByComparingTo("66.0");
    }

    @Test
    void ensureHalfWorkingDayCountsHalf() {
        final Map<LocalDate, WorkingDayInformation> days = new HashMap<>();
        days.put(LocalDate.of(2026, 12, 24), new WorkingDayInformation(MORNING, WORKDAY, PUBLIC_HOLIDAY));
        days.put(LocalDate.of(2026, 12, 23), new WorkingDayInformation(FULL, WORKDAY, WORKDAY));
        final List<PartTime> partTimes = List.of(partTime(LocalDate.of(2026, 1, 1), "50"));

        final BigDecimal quota = PartTimeFreeDaysService.computeQuota(partTimes, new WorkingTimeCalendar(days),
            new DateRange(LocalDate.of(2026, 12, 23), LocalDate.of(2026, 12, 24)));

        // (1 + 0.5) * 50% = 0.75 -> 1.0
        assertThat(quota).isEqualByComparingTo("1.0");
    }

    @ParameterizedTest
    @CsvSource({"10.24, 10.0", "10.25, 10.5", "10.74, 10.5", "10.75, 11.0", "0, 0.0"})
    void ensureRoundingToHalfDays(String value, String expected) {
        assertThat(PartTimeFreeDaysService.roundToHalfDays(new BigDecimal(value))).isEqualByComparingTo(expected);
    }

    @Test
    void ensureSummaryCountsUsedAndWaitingApplications() {
        when(partTimeService.getPartTimes(person)).thenReturn(List.of(partTime(LocalDate.of(2026, 1, 1), "80")));
        when(workingTimeCalendarService.getWorkingTimesByPersons(List.of(person), Year.of(2026))).thenReturn(Map.of(person, calendar(Set.of())));
        when(partTimeQuotaRepository.findByPersonAndYear(person, 2026)).thenReturn(Optional.empty());
        when(applicationService.getApplicationsForACertainPeriodAndPersonAndVacationCategory(
            eq(YEAR_2026.startDate()), eq(YEAR_2026.endDate()), eq(person), eq(activeStatuses()), eq(PARTTIME_FREEDAY)))
            .thenReturn(List.of(
                // monday + tuesday
                application(1L, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), FULL, ALLOWED),
                application(2L, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), FULL, ALLOWED_CANCELLATION_REQUESTED),
                application(3L, LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 16), MORNING, WAITING),
                application(4L, LocalDate.of(2026, 3, 17), LocalDate.of(2026, 3, 17), FULL, TEMPORARY_ALLOWED)
            ));

        final PartTimeFreeDaysSummary summary = sut.getSummary(person, 2026);

        assertThat(summary.computed()).isEqualByComparingTo("52.0");
        assertThat(summary.quota()).isEqualByComparingTo("52.0");
        assertThat(summary.used()).isEqualByComparingTo("3");
        assertThat(summary.waiting()).isEqualByComparingTo("1.5");
        assertThat(summary.left()).isEqualByComparingTo("47.5");
        assertThat(summary.isOverridden()).isFalse();
    }

    @Test
    void ensureSummaryIgnoresGivenApplication() {
        when(partTimeService.getPartTimes(person)).thenReturn(List.of(partTime(LocalDate.of(2026, 1, 1), "80")));
        when(workingTimeCalendarService.getWorkingTimesByPersons(List.of(person), Year.of(2026))).thenReturn(Map.of(person, calendar(Set.of())));
        when(partTimeQuotaRepository.findByPersonAndYear(person, 2026)).thenReturn(Optional.empty());
        when(applicationService.getApplicationsForACertainPeriodAndPersonAndVacationCategory(any(), any(), any(), any(), any()))
            .thenReturn(List.of(application(1L, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), FULL, ALLOWED)));

        assertThat(sut.getSummary(person, 2026, 1L).used()).isEqualByComparingTo("0");
    }

    @Test
    void ensureOverrideReplacesComputedQuota() {
        final PartTimeQuotaEntity quota = new PartTimeQuotaEntity(person, 2026);
        quota.setOverrideDays(new BigDecimal("40.5"));
        quota.setComment("Eintritt 1.3.");

        when(partTimeService.getPartTimes(person)).thenReturn(List.of(partTime(LocalDate.of(2026, 1, 1), "80")));
        when(workingTimeCalendarService.getWorkingTimesByPersons(List.of(person), Year.of(2026))).thenReturn(Map.of(person, calendar(Set.of())));
        when(partTimeQuotaRepository.findByPersonAndYear(person, 2026)).thenReturn(Optional.of(quota));
        when(applicationService.getApplicationsForACertainPeriodAndPersonAndVacationCategory(any(), any(), any(), any(), any())).thenReturn(List.of());

        final PartTimeFreeDaysSummary summary = sut.getSummary(person, 2026);

        assertThat(summary.computed()).isEqualByComparingTo("52.0");
        assertThat(summary.quota()).isEqualByComparingTo("40.5");
        assertThat(summary.isOverridden()).isTrue();
        assertThat(summary.comment()).hasValue("Eintritt 1.3.");
    }

    @Test
    void ensureApplicationAcrossYearsOnlyCountsDaysOfTheYear() {
        final Map<LocalDate, WorkingDayInformation> days2026 = new HashMap<>(calendar(Set.of()).workingDays());
        when(workingTimeCalendarService.getWorkingTimesByPersons(List.of(person), Year.of(2026))).thenReturn(Map.of(person, new WorkingTimeCalendar(days2026)));

        // tuesday 2026-12-29 until friday 2027-01-08
        final Application application = application(1L, LocalDate.of(2026, 12, 29), LocalDate.of(2027, 1, 8), FULL, WAITING);

        assertThat(sut.getDaysOfApplicationInYear(application, 2026)).isEqualByComparingTo("3");
    }

    private Application application(Long id, LocalDate start, LocalDate end, DayLength dayLength, ApplicationStatus status) {
        final Application application = new Application();
        application.setId(id);
        application.setPerson(person);
        application.setStartDate(start);
        application.setEndDate(end);
        application.setDayLength(dayLength);
        application.setStatus(status);
        return application;
    }

    private PartTime partTime(LocalDate validFrom, String percentage) {
        return new PartTime(null, person, validFrom, new BigDecimal(percentage));
    }

    /**
     * monday to friday full working days in 2026, except the given public holidays
     */
    private static WorkingTimeCalendar calendar(Set<LocalDate> publicHolidays) {
        final Map<LocalDate, WorkingDayInformation> days = new HashMap<>();
        for (LocalDate date : YEAR_2026) {
            if (date.getDayOfWeek() == SATURDAY || date.getDayOfWeek() == SUNDAY) {
                days.put(date, new WorkingDayInformation(ZERO, NO_WORKDAY, NO_WORKDAY));
            } else if (publicHolidays.contains(date)) {
                days.put(date, new WorkingDayInformation(ZERO, PUBLIC_HOLIDAY, PUBLIC_HOLIDAY));
            } else {
                days.put(date, new WorkingDayInformation(FULL, WORKDAY, WORKDAY));
            }
        }
        return new WorkingTimeCalendar(days);
    }
}
