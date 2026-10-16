package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.application.ApplicationService;
import org.synyx.urlaubsverwaltung.application.application.ApplicationStatus;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendar;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeCalendarService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.TEMPORARY_ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.WAITING;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.activeStatuses;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.PARTTIME_FREEDAY;

/**
 * Calculates the part time free days ("Teilzeit-Freitage") of persons working part time without a fixed day off.
 *
 * <p>The quota of a year is the sum of the working time of every day of the year multiplied with the part time share
 * of that day (e.g. 20% for a part time of 80%). Public holidays, half days on christmas eve and new years eve and
 * changes of the working time or the part time within the year are thereby considered pro rata. The result is rounded
 * to half days. Unused days expire at the end of the year.</p>
 */
@Service
public class PartTimeFreeDaysService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final PartTimeService partTimeService;
    private final PartTimeQuotaRepository partTimeQuotaRepository;
    private final WorkingTimeCalendarService workingTimeCalendarService;
    private final ApplicationService applicationService;

    PartTimeFreeDaysService(PartTimeService partTimeService, PartTimeQuotaRepository partTimeQuotaRepository,
                            WorkingTimeCalendarService workingTimeCalendarService, ApplicationService applicationService) {
        this.partTimeService = partTimeService;
        this.partTimeQuotaRepository = partTimeQuotaRepository;
        this.workingTimeCalendarService = workingTimeCalendarService;
        this.applicationService = applicationService;
    }

    public PartTimeFreeDaysSummary getSummary(Person person, int year) {
        return getSummary(person, year, null);
    }

    /**
     * @param ignoredApplicationId id of an application that should not be counted, e.g. the one that is edited
     */
    public PartTimeFreeDaysSummary getSummary(Person person, int year, Long ignoredApplicationId) {
        final DateRange yearRange = yearRange(year);
        final WorkingTimeCalendar workingTimeCalendar = workingTimeCalendar(person, year);

        final BigDecimal computed = computeQuota(partTimeService.getPartTimes(person), workingTimeCalendar, yearRange);
        final Optional<PartTimeQuotaEntity> quota = partTimeQuotaRepository.findByPersonAndYear(person, year);

        BigDecimal used = ZERO;
        BigDecimal waiting = ZERO;
        final List<Application> applications = applicationService.getApplicationsForACertainPeriodAndPersonAndVacationCategory(
            yearRange.startDate(), yearRange.endDate(), person, activeStatuses(), PARTTIME_FREEDAY);
        for (Application application : applications) {
            if (ignoredApplicationId != null && ignoredApplicationId.equals(application.getId())) {
                continue;
            }
            final BigDecimal days = workingTimeCalendar.workingTimeInDateRage(application, yearRange);
            if (isWaiting(application.getStatus())) {
                waiting = waiting.add(days);
            } else {
                used = used.add(days);
            }
        }

        return new PartTimeFreeDaysSummary(
            year,
            computed,
            quota.map(PartTimeQuotaEntity::getOverrideDays).filter(Objects::nonNull),
            quota.map(PartTimeQuotaEntity::getComment).filter(comment -> !comment.isBlank()),
            used,
            waiting
        );
    }

    /**
     * @return the days the given application needs from the part time free days quota of the given year
     */
    public BigDecimal getDaysOfApplicationInYear(Application application, int year) {
        final WorkingTimeCalendar workingTimeCalendar = workingTimeCalendar(application.getPerson(), year);
        return workingTimeCalendar.workingTimeInDateRage(application, yearRange(year));
    }

    /**
     * Sets a manual correction of the quota. An empty override removes the correction.
     */
    @Transactional
    public void setOverride(Person person, int year, Optional<BigDecimal> overrideDays, Optional<String> comment) {
        final PartTimeQuotaEntity entity = partTimeQuotaRepository.findByPersonAndYear(person, year)
            .orElseGet(() -> new PartTimeQuotaEntity(person, year));
        entity.setOverrideDays(overrideDays.orElse(null));
        entity.setComment(comment.filter(text -> !text.isBlank()).orElse(null));
        partTimeQuotaRepository.save(entity);
    }

    static BigDecimal computeQuota(List<PartTime> orderedPartTimes, WorkingTimeCalendar workingTimeCalendar, DateRange dateRange) {
        if (orderedPartTimes.isEmpty()) {
            return ZERO;
        }

        BigDecimal sum = ZERO;
        for (LocalDate date : dateRange) {
            final Optional<BigDecimal> percentage = PartTimeService.percentageOn(orderedPartTimes, date);
            if (percentage.isPresent()) {
                final BigDecimal workingTime = workingTimeCalendar.workingTime(date).orElse(ZERO);
                final BigDecimal freeShare = HUNDRED.subtract(percentage.get()).divide(HUNDRED, 4, HALF_UP);
                sum = sum.add(workingTime.multiply(freeShare));
            }
        }
        return roundToHalfDays(sum);
    }

    static BigDecimal roundToHalfDays(BigDecimal value) {
        return value.multiply(TWO).setScale(0, HALF_UP).divide(TWO, 1, HALF_UP);
    }

    private WorkingTimeCalendar workingTimeCalendar(Person person, int year) {
        return workingTimeCalendarService.getWorkingTimesByPersons(List.of(person), Year.of(year))
            .getOrDefault(person, new WorkingTimeCalendar(Map.of()));
    }

    private static DateRange yearRange(int year) {
        return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private static boolean isWaiting(ApplicationStatus status) {
        return status == WAITING || status == TEMPORARY_ALLOWED;
    }
}
