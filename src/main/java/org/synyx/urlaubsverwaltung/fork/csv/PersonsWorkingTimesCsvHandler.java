package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.fork.parttime.PartTimeService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.workingtime.FederalState;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTime;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeWriteService;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.joining;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.EMAIL;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.USERNAME;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.CREATE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UNCHANGED;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UPDATE;

/**
 * Persons with their working time and part time valid at the end of the export period.
 *
 * <p>Import: unknown persons are created (username, email, first and last name required). A working time and a part
 * time are saved valid from {@code valid_from}. {@code working_days} are full days, e.g. {@code MON,TUE,WED,THU,FRI},
 * an empty {@code federal_state} uses the default of the settings, an empty {@code part_time_percentage} leaves the
 * part time unchanged and 100 ends it. {@code departments} is only exported.</p>
 */
@Component
class PersonsWorkingTimesCsvHandler implements CsvDatasetHandler {

    private static final String FIRST_NAME = "first_name";
    private static final String LAST_NAME = "last_name";
    private static final String DEPARTMENTS = "departments";
    private static final String VALID_FROM = "valid_from";
    private static final String WORKING_DAYS = "working_days";
    private static final String FEDERAL_STATE = "federal_state";
    private static final String PART_TIME_PERCENTAGE = "part_time_percentage";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PersonService personService;
    private final DepartmentService departmentService;
    private final WorkingTimeService workingTimeService;
    private final WorkingTimeWriteService workingTimeWriteService;
    private final PartTimeService partTimeService;
    private final CsvPersonResolver personResolver;

    PersonsWorkingTimesCsvHandler(PersonService personService, DepartmentService departmentService,
                                  WorkingTimeService workingTimeService, WorkingTimeWriteService workingTimeWriteService,
                                  PartTimeService partTimeService, CsvPersonResolver personResolver) {
        this.personService = personService;
        this.departmentService = departmentService;
        this.workingTimeService = workingTimeService;
        this.workingTimeWriteService = workingTimeWriteService;
        this.partTimeService = partTimeService;
        this.personResolver = personResolver;
    }

    @Override
    public CsvDataset dataset() {
        return CsvDataset.PERSONS_WORKING_TIMES;
    }

    @Override
    public List<String> header() {
        return List.of(USERNAME, EMAIL, FIRST_NAME, LAST_NAME, DEPARTMENTS, VALID_FROM, WORKING_DAYS, FEDERAL_STATE, PART_TIME_PERCENTAGE);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of(VALID_FROM, WORKING_DAYS);
    }

    @Override
    public List<List<String>> export(List<Person> persons, DateRange dateRange, Locale locale) {
        final LocalDate date = dateRange.endDate();
        final List<List<String>> rows = new ArrayList<>();
        for (Person person : persons) {
            final Optional<WorkingTime> workingTime = workingTimeService.getWorkingTime(person, date);
            final String departments = departmentService.getAssignedDepartmentsOfMember(person).stream()
                .map(Department::getName).sorted().collect(joining(", "));
            rows.add(List.of(
                person.getUsername(),
                Objects.requireNonNullElse(person.getEmail(), ""),
                Objects.requireNonNullElse(person.getFirstName(), ""),
                Objects.requireNonNullElse(person.getLastName(), ""),
                departments,
                workingTime.map(WorkingTime::getValidFrom).map(CsvValues::format).orElse(""),
                workingTime.map(PersonsWorkingTimesCsvHandler::workingDays).orElse(""),
                workingTime.filter(time -> !time.isDefaultFederalState()).map(time -> time.getFederalState().name()).orElse(""),
                partTimeService.getPercentage(person, date).map(CsvValues::format).orElse("")
            ));
        }
        return rows;
    }

    @Override
    public CsvRowResult importRow(CsvTable.Row row, Person importer, boolean execute) {
        final String subject = CsvPersonResolver.subject(row);
        try {
            for (String column : requiredColumns()) {
                if (!row.has(column)) {
                    return CsvRowResult.error(row, subject, "fork.csv.error.mandatory", column);
                }
            }
            final LocalDate validFrom = CsvValues.parseDate(row.get(VALID_FROM));
            final List<DayOfWeek> workingDays = parseWorkingDays(row.get(WORKING_DAYS));
            final FederalState federalState = row.has(FEDERAL_STATE) ? CsvValues.parseEnum(FederalState.class, row.get(FEDERAL_STATE)) : null;
            final Optional<BigDecimal> percentage = row.has(PART_TIME_PERCENTAGE)
                ? Optional.of(CsvValues.parseDecimal(row.get(PART_TIME_PERCENTAGE)))
                : Optional.empty();
            if (percentage.isPresent() && (percentage.get().signum() <= 0 || percentage.get().compareTo(HUNDRED) > 0)) {
                return CsvRowResult.error(row, subject, "fork.parttime.error.percentage");
            }

            final Optional<Person> existing = personResolver.resolve(row);
            if (existing.isEmpty()) {
                for (String column : List.of(USERNAME, EMAIL, FIRST_NAME, LAST_NAME)) {
                    if (!row.has(column)) {
                        return CsvRowResult.error(row, subject, "fork.csv.error.person.create", column);
                    }
                }
                if (execute) {
                    final Person person = personService.create(row.get(USERNAME), row.get(FIRST_NAME), row.get(LAST_NAME), row.get(EMAIL));
                    saveWorkingTime(person, validFrom, workingDays, federalState);
                    percentage.ifPresent(value -> partTimeService.save(person, validFrom, value));
                }
                return CsvRowResult.of(row, subject, CREATE);
            }

            final Person person = existing.get();
            final boolean workingTimeChanged = isWorkingTimeChanged(person, validFrom, workingDays, federalState);
            final boolean partTimeChanged = percentage.isPresent() && isPartTimeChanged(person, validFrom, percentage.get());
            if (!workingTimeChanged && !partTimeChanged) {
                return CsvRowResult.of(row, subject, UNCHANGED);
            }
            if (execute) {
                if (workingTimeChanged) {
                    saveWorkingTime(person, validFrom, workingDays, federalState);
                }
                if (partTimeChanged) {
                    partTimeService.save(person, validFrom, percentage.get());
                }
            }
            return CsvRowResult.of(row, subject, UPDATE);
        } catch (IllegalArgumentException e) {
            return CsvRowResult.error(row, subject, e.getMessage());
        }
    }

    private boolean isWorkingTimeChanged(Person person, LocalDate validFrom, List<DayOfWeek> workingDays, FederalState federalState) {
        return workingTimeService.getWorkingTime(person, validFrom)
            .map(current -> !current.getValidFrom().equals(validFrom)
                || !current.getWorkingDays().equals(workingDays)
                || (federalState == null ? !current.isDefaultFederalState() : current.isDefaultFederalState() || current.getFederalState() != federalState))
            .orElse(true);
    }

    private boolean isPartTimeChanged(Person person, LocalDate validFrom, BigDecimal percentage) {
        final BigDecimal current = partTimeService.getPercentage(person, validFrom).orElse(HUNDRED);
        return current.compareTo(percentage) != 0;
    }

    private void saveWorkingTime(Person person, LocalDate validFrom, List<DayOfWeek> workingDays, FederalState federalState) {
        workingTimeWriteService.touch(workingDays.stream().map(DayOfWeek::getValue).toList(), validFrom, person, federalState);
    }

    private static String workingDays(WorkingTime workingTime) {
        return workingTime.getWorkingDays().stream()
            .map(day -> day.name().substring(0, 3))
            .collect(joining(","));
    }

    static List<DayOfWeek> parseWorkingDays(String value) {
        final List<DayOfWeek> days = new ArrayList<>();
        for (String part : value.split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            final String day = part.strip().toUpperCase(Locale.ROOT);
            final DayOfWeek dayOfWeek = Arrays.stream(DayOfWeek.values())
                .filter(candidate -> candidate.name().startsWith(day.length() >= 2 ? day : "?") || germanAbbreviation(candidate).equals(day))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fork.csv.error.workingDays"));
            if (!days.contains(dayOfWeek)) {
                days.add(dayOfWeek);
            }
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException("fork.csv.error.workingDays");
        }
        days.sort(null);
        return days;
    }

    private static String germanAbbreviation(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "MO";
            case TUESDAY -> "DI";
            case WEDNESDAY -> "MI";
            case THURSDAY -> "DO";
            case FRIDAY -> "FR";
            case SATURDAY -> "SA";
            case SUNDAY -> "SO";
        };
    }
}
