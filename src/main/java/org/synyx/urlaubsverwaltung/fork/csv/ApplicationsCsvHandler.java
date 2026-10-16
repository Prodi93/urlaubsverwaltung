package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.application.ApplicationService;
import org.synyx.urlaubsverwaltung.application.application.ApplicationStatus;
import org.synyx.urlaubsverwaltung.application.comment.ApplicationCommentAction;
import org.synyx.urlaubsverwaltung.application.comment.ApplicationCommentService;
import org.synyx.urlaubsverwaltung.application.vacationtype.ProvidedVacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeService;
import org.synyx.urlaubsverwaltung.overlap.OverlapCase;
import org.synyx.urlaubsverwaltung.overlap.OverlapService;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.math.RoundingMode.HALF_UP;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.ALLOWED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.CANCELLED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.REJECTED;
import static org.synyx.urlaubsverwaltung.application.application.ApplicationStatus.WAITING;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.OVERTIME;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.EMAIL;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.USERNAME;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.CREATE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UNCHANGED;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;

/**
 * Applications for leave. Imported applications are saved directly, without the approval workflow, so no mails are
 * sent and no calendars are synchronized. Existing applications are not changed.
 */
@Component
class ApplicationsCsvHandler implements CsvDatasetHandler {

    private static final String VACATION_TYPE = "vacation_type";
    private static final String START_DATE = "start_date";
    private static final String END_DATE = "end_date";
    private static final String DAY_LENGTH = "day_length";
    private static final String STATUS = "status";
    private static final String HOURS = "hours";
    private static final String REASON = "reason";

    private static final Set<ApplicationStatus> IMPORTABLE_STATUSES = Set.of(ALLOWED, WAITING, CANCELLED, REJECTED);
    private static final Map<ApplicationStatus, ApplicationCommentAction> COMMENT_ACTIONS = Map.of(
        ALLOWED, ApplicationCommentAction.ALLOWED_DIRECTLY,
        WAITING, ApplicationCommentAction.APPLIED,
        CANCELLED, ApplicationCommentAction.CANCELLED_DIRECTLY,
        REJECTED, ApplicationCommentAction.REJECTED
    );
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final ApplicationService applicationService;
    private final ApplicationCommentService applicationCommentService;
    private final VacationTypeService vacationTypeService;
    private final OverlapService overlapService;
    private final CsvPersonResolver personResolver;
    private final Clock clock;

    ApplicationsCsvHandler(ApplicationService applicationService, ApplicationCommentService applicationCommentService,
                           VacationTypeService vacationTypeService, OverlapService overlapService,
                           CsvPersonResolver personResolver, Clock clock) {
        this.applicationService = applicationService;
        this.applicationCommentService = applicationCommentService;
        this.vacationTypeService = vacationTypeService;
        this.overlapService = overlapService;
        this.personResolver = personResolver;
        this.clock = clock;
    }

    @Override
    public CsvDataset dataset() {
        return CsvDataset.APPLICATIONS;
    }

    @Override
    public List<String> header() {
        return List.of(USERNAME, EMAIL, VACATION_TYPE, START_DATE, END_DATE, DAY_LENGTH, STATUS, HOURS, REASON);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of(VACATION_TYPE, START_DATE, END_DATE);
    }

    @Override
    public List<List<String>> export(List<Person> persons, DateRange dateRange, Locale locale) {
        return applicationService.getForStatesAndPerson(Arrays.asList(ApplicationStatus.values()), persons, dateRange.startDate(), dateRange.endDate())
            .stream()
            .sorted(Comparator.comparing((Application application) -> application.getPerson().getNiceName()).thenComparing(Application::getStartDate))
            .map(application -> List.of(
                application.getPerson().getUsername(),
                application.getPerson().getEmail(),
                application.getVacationType().getLabel(locale),
                CsvValues.format(application.getStartDate()),
                CsvValues.format(application.getEndDate()),
                application.getDayLength().name(),
                application.getStatus().name(),
                application.getHours() == null ? "" : CsvValues.format(hours(application.getHours())),
                Objects.requireNonNullElse(application.getReason(), "")
            ))
            .toList();
    }

    @Override
    public CsvRowResult importRow(CsvTable.Row row, Person importer, boolean execute) {
        final String subject = CsvPersonResolver.subject(row);
        try {
            final Optional<Person> maybePerson = personResolver.resolve(row);
            if (maybePerson.isEmpty()) {
                return CsvRowResult.error(row, subject, "fork.csv.error.person.unknown");
            }
            final Person person = maybePerson.get();
            for (String column : requiredColumns()) {
                if (!row.has(column)) {
                    return CsvRowResult.error(row, subject, "fork.csv.error.mandatory", column);
                }
            }

            final Optional<VacationType<?>> maybeVacationType = vacationType(row.get(VACATION_TYPE));
            if (maybeVacationType.isEmpty()) {
                return CsvRowResult.error(row, subject, "fork.csv.error.application.vacationType", row.get(VACATION_TYPE));
            }
            final VacationType<?> vacationType = maybeVacationType.get();

            final LocalDate startDate = CsvValues.parseDate(row.get(START_DATE));
            final LocalDate endDate = CsvValues.parseDate(row.get(END_DATE));
            final DayLength dayLength = row.has(DAY_LENGTH) ? CsvValues.parseEnum(DayLength.class, row.get(DAY_LENGTH)) : FULL;
            final ApplicationStatus status = row.has(STATUS) ? CsvValues.parseEnum(ApplicationStatus.class, row.get(STATUS)) : ALLOWED;
            if (startDate.isAfter(endDate) || dayLength == DayLength.ZERO || (dayLength.isHalfDay() && !startDate.equals(endDate))) {
                return CsvRowResult.error(row, subject, "fork.csv.error.application.period");
            }
            if (!IMPORTABLE_STATUSES.contains(status)) {
                return CsvRowResult.error(row, subject, "fork.csv.error.application.status", status.name());
            }

            Duration hours = null;
            if (row.has(HOURS)) {
                hours = Duration.ofMinutes(CsvValues.parseDecimal(row.get(HOURS)).multiply(MINUTES_PER_HOUR).setScale(0, HALF_UP).longValueExact());
            } else if (vacationType.isOfCategory(OVERTIME)) {
                return CsvRowResult.error(row, subject, "fork.csv.error.mandatory", HOURS);
            }

            final boolean exists = applicationService.getApplicationsForACertainPeriodAndPerson(startDate, endDate, person).stream()
                .anyMatch(application -> application.getStartDate().equals(startDate)
                    && application.getEndDate().equals(endDate)
                    && application.getDayLength() == dayLength
                    && application.getVacationType().getId().equals(vacationType.getId())
                    && application.getStatus() == status);
            if (exists) {
                return CsvRowResult.of(row, subject, UNCHANGED);
            }

            final Application application = new Application();
            application.setPerson(person);
            application.setApplier(importer);
            application.setVacationType(vacationType);
            application.setStartDate(startDate);
            application.setEndDate(endDate);
            application.setDayLength(dayLength);
            application.setStatus(status);
            application.setHours(hours);
            application.setReason(row.get(REASON));
            application.setApplicationDate(LocalDate.now(clock));
            if (status == ALLOWED) {
                application.setBoss(importer);
            }
            if (status == CANCELLED) {
                application.setCancelDate(LocalDate.now(clock));
            }

            if (status == ALLOWED || status == WAITING) {
                final OverlapCase overlap = overlapService.checkOverlap(application);
                if (overlap == OverlapCase.FULLY_OVERLAPPING || overlap == OverlapCase.PARTLY_OVERLAPPING) {
                    return CsvRowResult.error(row, subject, "fork.csv.error.application.overlap");
                }
            }

            if (execute) {
                final Application saved = applicationService.save(application);
                applicationCommentService.create(saved, COMMENT_ACTIONS.get(status), Optional.of("CSV-Import"), importer);
            }
            return CsvRowResult.of(row, subject, CREATE);
        } catch (ArithmeticException e) {
            return CsvRowResult.error(row, subject, "fork.csv.error.number");
        } catch (IllegalArgumentException e) {
            return CsvRowResult.error(row, subject, e.getMessage());
        }
    }

    /**
     * by id, message key of a provided type or label (german or english, ignoring case)
     */
    private Optional<VacationType<?>> vacationType(String value) {
        final List<VacationType<?>> vacationTypes = vacationTypeService.getAllVacationTypes();
        return vacationTypes.stream()
            .filter(vacationType -> value.equals(String.valueOf(vacationType.getId()))
                || (vacationType instanceof ProvidedVacationType provided && value.equals(provided.getMessageKey()))
                || value.equalsIgnoreCase(vacationType.getLabel(Locale.GERMAN))
                || value.equalsIgnoreCase(vacationType.getLabel(Locale.ENGLISH)))
            .findFirst();
    }

    private static BigDecimal hours(Duration duration) {
        return BigDecimal.valueOf(duration.toMinutes()).divide(MINUTES_PER_HOUR, 2, HALF_UP);
    }
}
