package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.stereotype.Service;
import org.synyx.urlaubsverwaltung.absence.AbsencePeriod;
import org.synyx.urlaubsverwaltung.absence.AbsenceService;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Kind;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Comparator.comparing;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.NO_WORKDAY;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.PUBLIC_HOLIDAY;

/**
 * Combines presences with absences, public holidays and non working days. Per day the first matching entry wins:
 * full day public holiday, full day absence, non working day, presence. Absences of other persons are shown
 * without their type.
 */
@Service
public class PresenceOverviewService {

    private final PresenceService presenceService;
    private final AbsenceService absenceService;
    private final DepartmentService departmentService;
    private final PersonService personService;

    PresenceOverviewService(PresenceService presenceService, AbsenceService absenceService,
                            DepartmentService departmentService, PersonService personService) {
        this.presenceService = presenceService;
        this.absenceService = absenceService;
        this.departmentService = departmentService;
        this.personService = personService;
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

    public Map<Person, Map<LocalDate, PresenceDay>> getPresenceDays(List<Person> persons, DateRange dateRange) {
        final Map<Person, Map<LocalDate, Presence>> presences = presenceService.getPresences(persons, dateRange);
        final Map<Person, Map<LocalDate, HalfDays>> absences = absences(persons, dateRange);

        final Map<Person, Map<LocalDate, PresenceDay>> result = new LinkedHashMap<>();
        for (Person person : persons) {
            final Map<LocalDate, Presence> personPresences = presences.getOrDefault(person, Map.of());
            final Map<LocalDate, HalfDays> personAbsences = absences.getOrDefault(person, Map.of());
            final Map<LocalDate, PresenceDay> days = new LinkedHashMap<>();
            for (LocalDate date : dateRange) {
                days.put(date, day(personPresences.get(date), personAbsences.get(date)));
            }
            result.put(person, days);
        }
        return result;
    }

    /**
     * @return number of persons in the office per day
     */
    public static Map<LocalDate, Long> countInOffice(Collection<Map<LocalDate, PresenceDay>> days) {
        final Map<LocalDate, Long> count = new HashMap<>();
        days.forEach(personDays -> personDays.forEach((date, day) -> {
            if (day.kind() == Kind.PRESENCE && day.type() == PresenceType.OFFICE) {
                count.merge(date, 1L, Long::sum);
            }
        }));
        return count;
    }

    static PresenceDay day(Presence presence, HalfDays halfDays) {
        if (halfDays != null && halfDays.isFullDay()) {
            if (halfDays.contains(PUBLIC_HOLIDAY)) {
                return PresenceDay.of(Kind.PUBLIC_HOLIDAY);
            }
            if (halfDays.isAbsence()) {
                return PresenceDay.of(Kind.ABSENT);
            }
            return PresenceDay.of(Kind.NO_WORKDAY);
        }

        final boolean halfDayOff = halfDays != null;
        if (presence == null) {
            return new PresenceDay(Kind.UNPLANNED, null, false, halfDayOff);
        }
        return new PresenceDay(Kind.PRESENCE, presence.type(), presence.explicit(), halfDayOff);
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
                absenceRecord.getMorning().ifPresent(info -> halfDays.morning = info.getAbsenceType());
                absenceRecord.getNoon().ifPresent(info -> halfDays.noon = info.getAbsenceType());
            }
        }
        return result;
    }

    static final class HalfDays {

        AbsencePeriod.AbsenceType morning;
        AbsencePeriod.AbsenceType noon;

        boolean isFullDay() {
            return morning != null && noon != null;
        }

        boolean contains(AbsencePeriod.AbsenceType type) {
            return morning == type || noon == type;
        }

        boolean isAbsence() {
            return isAbsence(morning) || isAbsence(noon);
        }

        private static boolean isAbsence(AbsencePeriod.AbsenceType type) {
            return type != null && type != PUBLIC_HOLIDAY && type != NO_WORKDAY;
        }
    }
}
