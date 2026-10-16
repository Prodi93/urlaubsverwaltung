package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.synyx.urlaubsverwaltung.absence.AbsencePeriod;
import org.synyx.urlaubsverwaltung.absence.AbsenceService;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeService;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.AbsenceMark;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Half;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Kind;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.NO_WORKDAY;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.PUBLIC_HOLIDAY;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.SICK;
import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;

/**
 * Team calendar: combines presences with absences, public holidays and non working days, separately for the morning
 * and the noon. Absences, public holidays and non working days take precedence over presences.
 *
 * <p>Like in the absence overview, the type of an absence is only shown to the person itself, to boss and office, to
 * department heads and second stage authorities of the person, or if the vacation type is visible to everyone.
 * Everybody else sees "absent".</p>
 */
@Service
public class PresenceOverviewService {

    static final String ANONYMIZED_COLOR = "var(--absence-color-YELLOW)";
    static final String SICK_NOTE_COLOR = "var(--sick-note-color)";

    private final PresenceService presenceService;
    private final AbsenceService absenceService;
    private final DepartmentService departmentService;
    private final PersonService personService;
    private final VacationTypeService vacationTypeService;
    private final MessageSource messageSource;

    PresenceOverviewService(PresenceService presenceService, AbsenceService absenceService,
                            DepartmentService departmentService, PersonService personService,
                            VacationTypeService vacationTypeService, MessageSource messageSource) {
        this.presenceService = presenceService;
        this.absenceService = absenceService;
        this.departmentService = departmentService;
        this.personService = personService;
        this.vacationTypeService = vacationTypeService;
        this.messageSource = messageSource;
    }

    /**
     * @return departments the signed in user may select, empty if there are no departments or the user only sees himself
     */
    public List<Department> getVisibleDepartments(Person signedInUser) {
        if (departmentService.getNumberOfDepartments() == 0) {
            return List.of();
        }
        return departmentService.getDepartmentsPersonHasAccessTo(signedInUser);
    }

    /**
     * Same visibility as the absence overview: members of the selected departments, the user himself without
     * accessible departments, or everybody if there are no departments at all.
     */
    public List<Person> getVisiblePersons(Person signedInUser, List<Department> visibleDepartments, Optional<Long> selectedDepartmentId) {
        if (departmentService.getNumberOfDepartments() == 0) {
            return personService.getActivePersons().stream().sorted(comparing(Person::getNiceName)).toList();
        }
        if (visibleDepartments.isEmpty()) {
            return List.of(signedInUser);
        }
        final List<Department> selected = selectedDepartmentId
            .flatMap(id -> visibleDepartments.stream().filter(department -> department.getId().equals(id)).findFirst())
            .map(List::of)
            .orElse(visibleDepartments);
        return selected.stream()
            .map(Department::getMembers)
            .flatMap(List::stream)
            .filter(Person::isActive)
            .distinct()
            .sorted(comparing(Person::getNiceName))
            .toList();
    }

    /**
     * @param viewer the signed in user, decides whether the type of absences is shown
     */
    public Map<Person, Map<LocalDate, PresenceDay>> getPresenceDays(Person viewer, List<Person> persons, DateRange dateRange, Locale locale) {
        final Map<Person, Map<LocalDate, Presence>> presences = presenceService.getPresences(persons, dateRange);
        final Map<Person, Map<LocalDate, HalfDays>> absences = absences(persons, dateRange);
        final AbsenceMarks absenceMarks = new AbsenceMarks(viewer, locale);

        final Map<Person, Map<LocalDate, PresenceDay>> result = new LinkedHashMap<>();
        for (Person person : persons) {
            final Map<LocalDate, Presence> personPresences = presences.getOrDefault(person, Map.of());
            final Map<LocalDate, HalfDays> personAbsences = absences.getOrDefault(person, Map.of());
            final Map<LocalDate, PresenceDay> days = new LinkedHashMap<>();
            for (LocalDate date : dateRange) {
                final HalfDays halfDays = personAbsences.getOrDefault(date, new HalfDays());
                final Presence presence = personPresences.get(date);
                days.put(date, new PresenceDay(
                    half(halfDays.morning, presence == null ? null : presence.slots().morning(), presence, absenceMarks),
                    half(halfDays.noon, presence == null ? null : presence.slots().noon(), presence, absenceMarks)
                ));
            }
            result.put(person, days);
        }
        return result;
    }

    /**
     * @return number of persons in the office per day, half days count 0.5
     */
    public static Map<LocalDate, Double> countInOffice(Collection<Map<LocalDate, PresenceDay>> days) {
        final Map<LocalDate, Double> count = new HashMap<>();
        days.forEach(personDays -> personDays.forEach((date, day) -> {
            if (day.inOffice() > 0) {
                count.merge(date, day.inOffice(), Double::sum);
            }
        }));
        return count;
    }

    static Half half(AbsencePeriod.RecordInfo absence, PresenceType presenceType, Presence presence, AbsenceMarks absenceMarks) {
        if (absence != null) {
            if (absence.getAbsenceType() == PUBLIC_HOLIDAY) {
                return Half.of(Kind.PUBLIC_HOLIDAY);
            }
            if (absence.getAbsenceType() == NO_WORKDAY) {
                return Half.of(Kind.NO_WORKDAY);
            }
            return Half.absent(absenceMarks.of(absence));
        }
        if (presenceType == null) {
            return Half.of(Kind.UNPLANNED);
        }
        return Half.presence(presenceType, presence.explicit());
    }

    private Map<Person, Map<LocalDate, HalfDays>> absences(List<Person> persons, DateRange dateRange) {
        final Map<Person, Map<LocalDate, HalfDays>> result = new HashMap<>();
        if (persons.isEmpty()) {
            return result;
        }
        for (AbsencePeriod period : absenceService.getOpenAbsences(persons, dateRange.startDate(), dateRange.endDate())) {
            for (AbsencePeriod.Record absenceRecord : period.absenceRecords()) {
                final HalfDays halfDays = result.computeIfAbsent(absenceRecord.getPerson(), person -> new HashMap<>())
                    .computeIfAbsent(absenceRecord.getDate(), date -> new HalfDays());
                absenceRecord.getMorning().ifPresent(info -> halfDays.morning = preferred(halfDays.morning, info));
                absenceRecord.getNoon().ifPresent(info -> halfDays.noon = preferred(halfDays.noon, info));
            }
        }
        return result;
    }

    /**
     * a real absence wins over a non working day or a public holiday of the same half
     */
    private static AbsencePeriod.RecordInfo preferred(AbsencePeriod.RecordInfo current, AbsencePeriod.RecordInfo candidate) {
        if (current == null) {
            return candidate;
        }
        final boolean currentIsAbsence = current.getAbsenceType() != PUBLIC_HOLIDAY && current.getAbsenceType() != NO_WORKDAY;
        return currentIsAbsence ? current : candidate;
    }

    static final class HalfDays {
        AbsencePeriod.RecordInfo morning;
        AbsencePeriod.RecordInfo noon;
    }

    /**
     * Creates the absences as the viewer may see them.
     */
    final class AbsenceMarks {

        private final Person viewer;
        private final Locale locale;
        private final boolean seesAll;
        private final Set<Long> managedPersonIds = new HashSet<>();
        private final Map<Long, VacationType<?>> vacationTypes;

        AbsenceMarks(Person viewer, Locale locale) {
            this.viewer = viewer;
            this.locale = locale;
            this.seesAll = viewer.hasRole(BOSS) || viewer.hasRole(OFFICE);
            if (!seesAll) {
                if (viewer.hasRole(DEPARTMENT_HEAD)) {
                    departmentService.getMembersForDepartmentHead(viewer).forEach(person -> managedPersonIds.add(person.getId()));
                }
                if (viewer.hasRole(SECOND_STAGE_AUTHORITY)) {
                    departmentService.getMembersForSecondStageAuthority(viewer).forEach(person -> managedPersonIds.add(person.getId()));
                }
            }
            this.vacationTypes = vacationTypeService.getAllVacationTypes().stream()
                .collect(toMap(VacationType::getId, identity(), (first, second) -> first));
        }

        AbsenceMark of(AbsencePeriod.RecordInfo info) {
            final boolean waiting = info.hasStatusWaiting() || info.hasStatusTemporaryAllowed();
            final Person person = info.getPerson();
            final boolean mayShowDetails = seesAll || person.getId().equals(viewer.getId()) || managedPersonIds.contains(person.getId())
                || (info.getAbsenceType() != SICK && info.isVisibleToEveryone());
            if (!mayShowDetails) {
                return new AbsenceMark(message("fork.presence.absent"), ANONYMIZED_COLOR, false, null);
            }
            if (info.getAbsenceType() == SICK) {
                return new AbsenceMark(message("fork.presence.sick"), SICK_NOTE_COLOR, waiting, null);
            }
            final Optional<VacationType<?>> vacationType = info.getTypeId().map(vacationTypes::get);
            final String label = vacationType.map(type -> type.getLabel(locale)).orElseGet(() -> message("fork.presence.absent"));
            final String color = vacationType.map(type -> "var(--absence-color-" + type.getColor().name() + ")").orElse(ANONYMIZED_COLOR);
            return new AbsenceMark(label, color, waiting, info.getId().orElse(null));
        }

        private String message(String key) {
            return messageSource.getMessage(key, null, key, locale);
        }
    }
}
