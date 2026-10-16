package org.synyx.urlaubsverwaltung.fork.presence;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.web.DataProviderInterface;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/**
 * Adds the presence of the current week to the person overview, if the signed in user may plan the presence of the
 * shown person. The box is also the form of the presence menu of the calendar.
 */
@Component
class PresenceWeekDataProvider implements DataProviderInterface {

    static final String WEEK_PARAMETER = "presenceWeek";
    static final String BOX_ANCHOR = "presence-week";

    private final PresenceOverviewService presenceOverviewService;
    private final PresencePermissionEvaluator permissionEvaluator;
    private final PersonService personService;
    private final Clock clock;

    PresenceWeekDataProvider(PresenceOverviewService presenceOverviewService, PresencePermissionEvaluator permissionEvaluator,
                             PersonService personService, Clock clock) {
        this.presenceOverviewService = presenceOverviewService;
        this.permissionEvaluator = permissionEvaluator;
        this.personService = personService;
        this.clock = clock;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) {
        if (!addDataIf(modelAndView) || !(modelAndView.getModel().get("person") instanceof Person person)) {
            return;
        }

        final Person signedInUser = personService.getSignedInUser();
        if (!permissionEvaluator.isAllowedToEdit(signedInUser, person)) {
            return;
        }

        final Locale locale = LocaleContextHolder.getLocale();
        final LocalDate today = LocalDate.now(clock);
        final LocalDate currentMonday = today.with(previousOrSame(DayOfWeek.MONDAY));
        final LocalDate monday = selectedWeek(request).orElse(currentMonday);
        final Map<LocalDate, PresenceDay> days = presenceOverviewService
            .getPresenceDays(signedInUser, List.of(person), new DateRange(monday, monday.plusDays(6)), locale)
            .get(person);

        final List<PresenceViewController.PresencePlanningDayDto> week = days.entrySet().stream()
            // weekends only if they are working days
            .filter(entry -> entry.getKey().getDayOfWeek().getValue() <= 5 || entry.getValue().isWorkday())
            .map(entry -> PresenceViewController.PresencePlanningDayDto.of(entry.getKey(),
                entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                entry.getKey().equals(today), entry.getValue()))
            .toList();

        modelAndView.addObject("forkPresenceWeek", week);
        modelAndView.addObject("forkPresenceTypes", PresenceType.values());
        modelAndView.addObject("forkPresenceWeekNavigation", new WeekNavigationDto(
            monday,
            monday.get(WeekFields.ISO.weekOfWeekBasedYear()),
            monday.equals(currentMonday),
            weekUrl(request, monday.minusWeeks(1)),
            weekUrl(request, monday.plusWeeks(1)),
            weekUrl(request, currentMonday)
        ));
    }

    /**
     * @param monday     first day of the shown week
     * @param weekNumber ISO week number
     * @param current    {@code true} if the shown week is the current week
     */
    record WeekNavigationDto(LocalDate monday, int weekNumber, boolean current, String previousUrl, String nextUrl, String currentUrl) {

        public LocalDate sunday() {
            return monday.plusDays(6);
        }
    }

    /**
     * the week of the request parameter {@value #WEEK_PARAMETER}, any day of the week is accepted
     */
    static Optional<LocalDate> selectedWeek(HttpServletRequest request) {
        final String value = request.getParameter(WEEK_PARAMETER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value).with(previousOrSame(DayOfWeek.MONDAY)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * link to the same overview page (keeping the year of the calendar) with the given week, scrolled to the box
     */
    private static String weekUrl(HttpServletRequest request, LocalDate monday) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromPath(request.getRequestURI());
        final String year = request.getParameter("year");
        if (year != null && year.matches("\\d{4}")) {
            builder.queryParam("year", year);
        }
        return builder.queryParam(WEEK_PARAMETER, monday).fragment(BOX_ANCHOR).build().toUriString();
    }
}
