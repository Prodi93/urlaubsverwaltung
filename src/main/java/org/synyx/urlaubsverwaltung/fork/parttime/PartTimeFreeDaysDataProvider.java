package org.synyx.urlaubsverwaltung.fork.parttime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.web.DataProviderInterface;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Adds the part time free days of the shown person to the person overview and the application for leave form.
 * The upstream controllers already checked that the signed in user may see the person.
 */
@Component
class PartTimeFreeDaysDataProvider implements DataProviderInterface {

    static final String MODEL_ATTRIBUTE = "forkPartTimeFreeDays";

    private final PartTimeService partTimeService;
    private final PartTimeFreeDaysService partTimeFreeDaysService;
    private final Clock clock;

    PartTimeFreeDaysDataProvider(PartTimeService partTimeService, PartTimeFreeDaysService partTimeFreeDaysService, Clock clock) {
        this.partTimeService = partTimeService;
        this.partTimeFreeDaysService = partTimeFreeDaysService;
        this.clock = clock;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) {
        if (!addDataIf(modelAndView) || !(modelAndView.getModel().get("person") instanceof Person person)) {
            return;
        }

        if (partTimeService.getPartTimes(person).isEmpty()) {
            return;
        }

        final int year = modelAndView.getModel().get("selectedYear") instanceof Integer selectedYear
            ? selectedYear
            : LocalDate.now(clock).getYear();

        final PartTimeFreeDaysSummary summary = partTimeFreeDaysService.getSummary(person, year);
        if (summary.hasQuota()) {
            modelAndView.addObject(MODEL_ATTRIBUTE, summary);
        }
    }
}
