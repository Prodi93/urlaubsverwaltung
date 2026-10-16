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
import org.synyx.urlaubsverwaltung.application.application.ForkApplicationForLeaveCreator;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationTypeService;
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
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.OVERTIME;

@Controller
@RequestMapping("/web/presence")
@PreAuthorize("hasAuthority('USER')")
class PresenceViewController implements HasLaunchpad, HasPersonSearch {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.");
    private static final long MAX_RANGE_DAYS = 62;

    private final PresenceService presenceService;
    private final PresenceOverviewService presenceOverviewService;
    private final PresencePermissionEvaluator permissionEvaluator;
    private final PersonService personService;
    private final VacationTypeService vacationTypeService;
    private final ForkApplicationForLeaveCreator applicationCreator;
    private final PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier;
    private final Clock clock;

    PresenceViewController(PresenceService presenceService, PresenceOverviewService presenceOverviewService,
                           PresencePermissionEvaluator permissionEvaluator, PersonService personService,
                           VacationTypeService vacationTypeService, ForkApplicationForLeaveCreator applicationCreator,
                           PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier, Clock clock) {
        this.presenceService = presenceService;
        this.presenceOverviewService = presenceOverviewService;
        this.permissionEvaluator = permissionEvaluator;
        this.personService = personService;
        this.vacationTypeService = vacationTypeService;
        this.applicationCreator = applicationCreator;
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

    // ------------------------------------------------------------------------------------------------- team calendar

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
        final Map<Person, Map<LocalDate, PresenceDay>> presenceDays = presenceOverviewService.getPresenceDays(signedInUser, persons, dateRange, locale);
        final Map<LocalDate, Double> inOffice = PresenceOverviewService.countInOffice(presenceDays.values());

        final List<PresenceOverviewRowDto> rows = presenceDays.entrySet().stream()
            .map(entry -> new PresenceOverviewRowDto(entry.getKey(), permissionEvaluator.isAllowedToEdit(signedInUser, entry.getKey()),
                List.copyOf(entry.getValue().values())))
            .toList();

        final List<PresenceDayHeaderDto> days = days(dateRange, locale);
        model.addAttribute("days", days);
        model.addAttribute("rows", rows);
        model.addAttribute("inOffice", days.stream().map(day -> formatCount(inOffice.getOrDefault(day.date(), 0d))).toList());
        model.addAttribute("visibleDepartments", visibleDepartments);
        model.addAttribute("selectedDepartmentId", selectedDepartmentId.orElse(null));
        model.addAttribute("vacationTypes", vacationTypeService.getActiveVacationTypes());
        addMonthNavigation(model, yearMonth, locale);
        return "fork/presence/overview";
    }

    // ------------------------------------------------------------------------------------------------- today

    @GetMapping("/today")
    String today(Model model, Locale locale) {
        final Person signedInUser = personService.getSignedInUser();
        final LocalDate today = LocalDate.now(clock);
        final DateRange dateRange = new DateRange(today, today.plusDays(6));
        final Map<LocalDate, PresenceDay> days = presenceDays(signedInUser, signedInUser, dateRange, locale);

        model.addAttribute("person", signedInUser);
        model.addAttribute("today", today);
        model.addAttribute("days", toPlanningDays(days, locale));
        model.addAttribute("presenceTypes", PresenceType.values());
        return "fork/presence/today";
    }

    @PostMapping("/today")
    String setToday(@RequestParam("date") @DateTimeFormat(iso = DATE) LocalDate date,
                    @RequestParam(value = "type", required = false) PresenceType type,
                    RedirectAttributes redirectAttributes, Locale locale) {
        final Person signedInUser = personService.getSignedInUser();
        setPresenceInRange(signedInUser, signedInUser, date, date, type, locale);
        redirectAttributes.addFlashAttribute("presenceSaved", true);
        return "redirect:/web/presence/today";
    }

    /**
     * Sets a full day presence for all working halves in the range, used by the calendar and the week box of the
     * person overview. An empty type removes the planned presences.
     */
    @PostMapping("/persons/{personId}/range")
    String setRange(@PathVariable("personId") Long personId,
                    @RequestParam("from") @DateTimeFormat(iso = DATE) LocalDate from,
                    @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DATE) LocalDate to,
                    @RequestParam(value = "type", required = false) PresenceType type,
                    @RequestParam(value = "returnTo", defaultValue = "overview") String returnTo,
                    @RequestParam(value = "week", required = false) @DateTimeFormat(iso = DATE) LocalDate week,
                    RedirectAttributes redirectAttributes, Locale locale) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final LocalDate end = to == null || to.isBefore(from) ? from : to;
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new IllegalArgumentException("range too long");
        }
        setPresenceInRange(personService.getSignedInUser(), person, from, end, type, locale);

        redirectAttributes.addFlashAttribute("presenceSaved", true);
        return switch (returnTo) {
            case "planning" -> "redirect:/web/presence/persons/%s?year=%s&month=%s".formatted(personId, from.getYear(), from.getMonthValue());
            case "presence" -> "redirect:/web/presence?year=%s&month=%s".formatted(from.getYear(), from.getMonthValue());
            default -> week == null
                ? "redirect:/web/person/%s/overview?year=%s".formatted(personId, from.getYear())
                : "redirect:/web/person/%s/overview?year=%s&%s=%s#%s".formatted(personId, from.getYear(),
                    PresenceWeekDataProvider.WEEK_PARAMETER, week, PresenceWeekDataProvider.BOX_ANCHOR);
        };
    }

    // ------------------------------------------------------------------------------------------------- planning

    @GetMapping("/persons/{personId}")
    String planning(@PathVariable("personId") Long personId,
                    @RequestParam(value = "year", required = false) Integer year,
                    @RequestParam(value = "month", required = false) Integer month,
                    Model model, Locale locale) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final Person signedInUser = personService.getSignedInUser();
        final YearMonth yearMonth = yearMonth(year, month);
        final DateRange dateRange = new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        final Map<LocalDate, PresenceDay> days = presenceDays(signedInUser, person, dateRange, locale);

        model.addAttribute("person", person);
        model.addAttribute("days", toPlanningDays(days, locale));
        model.addAttribute("presenceTypes", PresenceType.values());
        model.addAttribute("absenceTypes", applicationCreator.isAllowedToApplyForPerson(signedInUser, person) ? absenceTypes(locale) : List.of());
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
                        RedirectAttributes redirectAttributes, Locale locale) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final Person signedInUser = personService.getSignedInUser();
        final YearMonth yearMonth = YearMonth.of(year, month);
        final DateRange dateRange = new DateRange(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        final Map<LocalDate, PresenceDay> days = presenceDays(signedInUser, person, dateRange, locale);

        final PlanningForm form = PlanningForm.parse(parameters, days);
        presenceService.setPresences(person, form.presences());

        final List<String> errors = new ArrayList<>();
        int applied = 0;
        if (!form.absenceRequests().isEmpty() && !applicationCreator.isAllowedToApplyForPerson(signedInUser, person)) {
            throw new AccessDeniedException("User '%s' is not allowed to apply for leave for user '%s'".formatted(signedInUser.getId(), person.getId()));
        }
        for (PlanningForm.AbsenceRequest request : form.absenceRequests()) {
            final ForkApplicationForLeaveCreator.Result result = applicationCreator.apply(signedInUser, person,
                request.vacationTypeId(), request.from(), request.to(), request.dayLength(), locale);
            if (result.isSuccess()) {
                applied++;
            } else {
                final String period = request.from().equals(request.to())
                    ? request.from().format(SHORT_DATE)
                    : request.from().format(SHORT_DATE) + "–" + request.to().format(SHORT_DATE);
                result.errorMessages().forEach(message -> errors.add(period + ": " + message));
            }
        }

        redirectAttributes.addFlashAttribute("presenceSaved", true);
        redirectAttributes.addFlashAttribute("applicationsCreated", applied);
        redirectAttributes.addFlashAttribute("applicationErrors", errors);
        return "redirect:/web/presence/persons/%s?year=%s&month=%s".formatted(personId, year, month);
    }

    @PostMapping("/persons/{personId}/pattern")
    String savePattern(@PathVariable("personId") Long personId,
                       @RequestParam("validFrom") @DateTimeFormat(iso = DATE) LocalDate validFrom,
                       @RequestParam Map<String, String> parameters,
                       RedirectAttributes redirectAttributes) throws UnknownPersonException {

        final Person person = getEditablePerson(personId);
        final Map<DayOfWeek, PresenceSlots> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            final PresenceType morning = presenceType(parameters.get(dayOfWeek.name() + ".morning"));
            final PresenceType noon = presenceType(parameters.get(dayOfWeek.name() + ".noon"));
            PresenceSlots.of(morning, noon).ifPresent(slots -> days.put(dayOfWeek, slots));
        }
        presenceService.savePattern(person, validFrom, days);

        redirectAttributes.addFlashAttribute("patternSaved", true);
        return "redirect:/web/presence/persons/%s".formatted(personId);
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private void setPresenceInRange(Person signedInUser, Person person, LocalDate from, LocalDate to, PresenceType type, Locale locale) {
        final Map<LocalDate, PresenceDay> days = presenceDays(signedInUser, person, new DateRange(from, to), locale);
        days.forEach((date, day) -> {
            if (!day.isWorkday()) {
                return;
            }
            if (type == null) {
                presenceService.setPresence(person, date, (PresenceSlots) null);
            } else {
                final PresenceType morning = day.morning().isWorking() ? type : null;
                final PresenceType noon = day.noon().isWorking() ? type : null;
                PresenceSlots.of(morning, noon).ifPresent(slots -> presenceService.setPresence(person, date, slots));
            }
        });
    }

    private Map<LocalDate, PresenceDay> presenceDays(Person viewer, Person person, DateRange dateRange, Locale locale) {
        return presenceOverviewService.getPresenceDays(viewer, List.of(person), dateRange, locale).get(person);
    }

    private List<AbsenceTypeOptionDto> absenceTypes(Locale locale) {
        return vacationTypeService.getActiveVacationTypes().stream()
            .filter(vacationType -> !vacationType.isOfCategory(OVERTIME))
            .map(vacationType -> new AbsenceTypeOptionDto(PlanningForm.absenceValue(vacationType), vacationType.getLabel(locale)))
            .toList();
    }

    private static PresenceType presenceType(String value) {
        return value == null || value.isBlank() ? null : PresenceType.valueOf(value);
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
            .map(entry -> PresencePlanningDayDto.of(entry.getKey(),
                entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                entry.getKey().equals(today), entry.getValue()))
            .toList();
    }

    private static String formatCount(double count) {
        if (count == 0) {
            return "";
        }
        return count == Math.floor(count) ? String.valueOf((long) count) : String.valueOf(count).replace('.', ',');
    }

    record PresenceDayHeaderDto(LocalDate date, int dayOfMonth, String dayOfWeek, boolean today, boolean weekend) {
    }

    record PresenceOverviewRowDto(Person person, boolean editable, List<PresenceDay> days) {
    }

    record AbsenceTypeOptionDto(String value, String label) {
    }

    /**
     * @param fullValue    value of the full day select, see {@link PlanningForm}
     * @param morningValue value of the morning select
     * @param noonValue    value of the noon select
     */
    record PresencePlanningDayDto(LocalDate date, String dayOfWeek, boolean today, PresenceDay day,
                                  String fullValue, String morningValue, String noonValue) {

        static PresencePlanningDayDto of(LocalDate date, String dayOfWeek, boolean today, PresenceDay day) {
            final String morning = day.morning().kind() == PresenceDay.Kind.PRESENCE && day.morning().explicit()
                ? PlanningForm.presenceValue(day.morning().type()) : "";
            final String noon = day.noon().kind() == PresenceDay.Kind.PRESENCE && day.noon().explicit()
                ? PlanningForm.presenceValue(day.noon().type()) : "";
            final String full = morning.equals(noon) ? morning : PlanningForm.SPLIT;
            return new PresencePlanningDayDto(date, dayOfWeek, today, day, full, morning, noon);
        }

        public boolean isSplit() {
            return PlanningForm.SPLIT.equals(fullValue);
        }
    }
}
