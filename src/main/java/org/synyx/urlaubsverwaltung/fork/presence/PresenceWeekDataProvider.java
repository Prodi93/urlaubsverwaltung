package org.synyx.urlaubsverwaltung.fork.presence;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.web.DataProviderInterface;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/**
 * Adds the presence of the current week to the person overview, if the signed in user may plan the presence of the
 * shown person. The box is also the form of the presence menu of the calendar.
 */
@Component
class PresenceWeekDataProvider implements DataProviderInterface {

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
        final LocalDate monday = today.with(previousOrSame(DayOfWeek.MONDAY));
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
    }
}
