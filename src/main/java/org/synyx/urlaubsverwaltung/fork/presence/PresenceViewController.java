package org.synyx.urlaubsverwaltung.fork.presence;

import de.focus_shift.launchpad.api.HasLaunchpad;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.UnknownPersonException;
import org.synyx.urlaubsverwaltung.search.HasPersonSearch;
import org.synyx.urlaubsverwaltung.search.PersonSearchUiFragmentSupplier;
import org.synyx.urlaubsverwaltung.search.PersonSuggestionUrlStrategy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@Controller
@RequestMapping("/web/presence")
@PreAuthorize("hasAuthority('USER')")
class PresenceViewController implements HasLaunchpad, HasPersonSearch {

    private static final String DAY_PARAMETER_PREFIX = "day.";

    private final PresenceService presenceService;
    private final PresenceOverviewService presenceOverviewService;
    private final PresencePermissionEvaluator permissionEvaluator;
    private final PersonService personService;
    private final PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier;
    private final Clock clock;

    PresenceViewController(PresenceService presenceService, PresenceOverviewService presenceOverviewService,
                           PresencePermissionEvaluator permissionEvaluator, PersonService personService,
                           PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier, Clock clock) {
        this.presenceService = presenceService;
        this.presenceOverviewService = presenceOverviewService;
        this.permissionEvaluator = permissionEvaluator;
        this.personService = personService;
        this.personSearchUiFragmentSupplier = personSearchUiFragmentSupplier;
        this.clock = clock;
    }

    @Override
    public PersonSuggestionUrlStrategy personSuggestionUrlStrategy() {
        return (suggestion, request) -> "/web/presence/persons/%s".formatted(suggestion.getId());
    }

    @Override
    public PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier() {
        return personSearchUiFragmentSupplier;
    }

    @GetMapping
    String overview(@RequestParam(value = "year", required = false) Integer year,
                    @RequestParam(value = "month", required = false) Integer month,
                    @RequestParam(value = "department", required = false) Long departmentId,
                    Model model, Locale locale) {

        final Person signedInUser = personService.getSignedInUser();
        final YearMonth yearMonth = yearMonth(year, month);
        final DateRange dateRange = new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());

        final List<Department> visibleDepartments = presenceOverviewService.getVisibleDepartments(signedInUser);
        final Optional<Long> selectedDepartmentId = Optional.ofNullable(departmentId)
            .or(() -> defaultDepartment(signedInUser, visibleDepartments));
        final List<Person> persons = presenceOverviewService.getVisiblePersons(signedInUser, visibleDepartments, selectedDepartmentId);
        final Map<Person, Map<LocalDate, PresenceDay>> presenceDays = presenceOverviewService.getPresenceDays(persons, dateRange);
        final Map<LocalDate, Long> inOffice = PresenceOverviewService.countInOffice(presenceDays.values());

        final List<PresenceOverviewRowDto> rows = presenceDays.entrySet().stream()
            .map(entry -> new PresenceOverviewRowDto(entry.getKey(), permissionEvaluator.isAllowedToEdit(signedInUser, entry.getKey()),
                List.copyOf(entry.getValue().values())))
            .toList();

        final List<PresenceDayHeaderDto> days = days(dateRange, locale);
        model.addAttribute("days", days);
        model.addAttribute("rows", rows);
        model.addAttribute("inOffice", days.stream().map(day -> inOffice.getOrDefault(day.date(), 0L)).toList());
        model.addAttribute("visibleDepartments", visibleDepartments);
        model.addAttribute("selectedDepartmentId", selectedDepartmentId.orElse(null));
        addMonthNavigation(model, yearMonth, locale);
        return "fork/presence/overview";
    }

    @GetMapping("/today")
    String today(Model model, Locale locale) {
        final Person signedInUser = personService.getSignedInUser();
        final LocalDate today = LocalDate.now(clock);
        final DateRange dateRange = new DateRange(today, today.plusDays(6));
        final Map<LocalDate, PresenceDay> days = presenceOverviewService.getPresenceDays(List.of(signedInUser), dateRange).get(signedInUser);

        model.addAttribute("person", signedInUser);
        model.addAttribute("today", today);
        model.addAttribute("days", toPlanningDays(days, locale));
        model.addAttribute("presenceTypes", PresenceType.values());
        return "fork/presence/today";
    }

    @PostMapping("/today")
    String setToday(@RequestParam("date") @DateTimeFormat(iso = DATE) LocalDate date,
                    @RequestParam(value = "type", required = false) PresenceType type,
                    RedirectAttributes redirectAttributes) {
        final Person signedInUser = personService.getSignedInUser();
        presenceService.setPresence(signedInUser, date, type);
        redirectAttributes.addFlashAttribute("presenceSaved", true);
        return "redirect:/web/presence/today";
    }

    @GetMapping("/persons/{personId}")
    String planning(@PathVariable("personId") Long personId,
                    @RequestParam(value = "year", required = false) Integer year,
                    @RequestParam(value = "month", required = false) Integer month,
                    Model model, Locale locale) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final YearMonth yearMonth = yearMonth(year, month);
        final DateRange dateRange = new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        final Map<LocalDate, PresenceDay> days = presenceOverviewService.getPresenceDays(List.of(person), dateRange).get(person);

        model.addAttribute("person", person);
        model.addAttribute("days", toPlanningDays(days, locale));
        model.addAttribute("presenceTypes", PresenceType.values());
        model.addAttribute("weekDays", DayOfWeek.values());
        model.addAttribute("pattern", presenceService.getPattern(person, LocalDate.now(clock)).map(PresencePattern::days).orElse(Map.of()));
        model.addAttribute("patternValidFrom", LocalDate.now(clock));
        addMonthNavigation(model, yearMonth, locale);
        return "fork/presence/planning";
    }

    @PostMapping("/persons/{personId}")
    String savePlanning(@PathVariable("personId") Long personId,
                        @RequestParam("year") int year, @RequestParam("month") int month,
                        @RequestParam Map<String, String> parameters,
                        RedirectAttributes redirectAttributes) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final Map<LocalDate, PresenceType> presences = new LinkedHashMap<>();
        parameters.forEach((name, value) -> {
            if (name.startsWith(DAY_PARAMETER_PREFIX)) {
                final LocalDate date = LocalDate.parse(name.substring(DAY_PARAMETER_PREFIX.length()));
                presences.put(date, value == null || value.isBlank() ? null : PresenceType.valueOf(value));
            }
        });
        presenceService.setPresences(person, presences);

        redirectAttributes.addFlashAttribute("presenceSaved", true);
        return "redirect:/web/presence/persons/%s?year=%s&month=%s".formatted(personId, year, month);
    }

    @PostMapping("/persons/{personId}/pattern")
    String savePattern(@PathVariable("personId") Long personId,
                       @RequestParam("validFrom") @DateTimeFormat(iso = DATE) LocalDate validFrom,
                       @RequestParam Map<String, String> parameters,
                       RedirectAttributes redirectAttributes) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final Map<DayOfWeek, PresenceType> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            final String value = parameters.get(dayOfWeek.name());
            if (value != null && !value.isBlank()) {
                days.put(dayOfWeek, PresenceType.valueOf(value));
            }
        }
        presenceService.savePattern(person, validFrom, days);

        redirectAttributes.addFlashAttribute("patternSaved", true);
        return "redirect:/web/presence/persons/%s".formatted(personId);
    }

    private Person getEditablePerson(Long personId) throws UnknownPersonException {
        final Person person = personService.getPersonByID(personId).orElseThrow(() -> new UnknownPersonException(personId));
        final Person signedInUser = personService.getSignedInUser();
        if (!permissionEvaluator.isAllowedToEdit(signedInUser, person)) {
            throw new AccessDeniedException("User '%s' is not allowed to plan the presence of user '%s'"
                .formatted(signedInUser.getId(), person.getId()));
        }
        return person;
    }

    private Optional<Long> defaultDepartment(Person signedInUser, List<Department> visibleDepartments) {
        return visibleDepartments.stream()
            .filter(department -> department.getMembers().contains(signedInUser))
            .map(Department::getId)
            .findFirst();
    }

    private YearMonth yearMonth(Integer year, Integer month) {
        final YearMonth now = YearMonth.now(clock);
        return YearMonth.of(year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month);
    }

    private void addMonthNavigation(Model model, YearMonth yearMonth, Locale locale) {
        model.addAttribute("year", yearMonth.getYear());
        model.addAttribute("month", yearMonth.getMonthValue());
        model.addAttribute("monthName", yearMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + yearMonth.getYear());
        model.addAttribute("previousMonth", yearMonth.minusMonths(1));
        model.addAttribute("nextMonth", yearMonth.plusMonths(1));
    }

    private List<PresenceDayHeaderDto> days(DateRange dateRange, Locale locale) {
        final LocalDate today = LocalDate.now(clock);
        return dateRange.stream()
            .map(date -> new PresenceDayHeaderDto(date, date.getDayOfMonth(),
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                date.equals(today), date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY))
            .toList();
    }

    private List<PresencePlanningDayDto> toPlanningDays(Map<LocalDate, PresenceDay> days, Locale locale) {
        final LocalDate today = LocalDate.now(clock);
        return days.entrySet().stream()
            .map(entry -> new PresencePlanningDayDto(entry.getKey(),
                entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                entry.getKey().equals(today), entry.getValue()))
            .toList();
    }

    record PresenceDayHeaderDto(LocalDate date, int dayOfMonth, String dayOfWeek, boolean today, boolean weekend) {
    }

    record PresenceOverviewRowDto(Person person, boolean editable, List<PresenceDay> days) {
    }

    record PresencePlanningDayDto(LocalDate date, String dayOfWeek, boolean today, PresenceDay day) {
    }
}
