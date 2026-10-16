package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.AbsenceMark;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Half;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Kind;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.MORNING;
import static org.synyx.urlaubsverwaltung.period.DayLength.NOON;

class PlanningFormTest {

    // thursday 2026-10-15 until tuesday 2026-10-20, weekend is no workday
    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 15);

    @Test
    void ensurePresencesOfFullAndSplitDays() {
        final Map<LocalDate, PresenceDay> days = week();
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("day." + THURSDAY, "P:OFFICE");
        parameters.put("day." + THURSDAY.plusDays(1), "SPLIT");
        parameters.put("day." + THURSDAY.plusDays(1) + ".morning", "P:OFFICE");
        parameters.put("day." + THURSDAY.plusDays(1) + ".noon", "P:HOME_OFFICE");
        parameters.put("day." + THURSDAY.plusDays(4), "");

        final PlanningForm form = PlanningForm.parse(parameters, days);

        assertThat(form.presences())
            .containsEntry(THURSDAY, PresenceSlots.fullDay(OFFICE))
            .containsEntry(THURSDAY.plusDays(1), new PresenceSlots(OFFICE, HOME_OFFICE))
            .containsEntry(THURSDAY.plusDays(4), null)
            .doesNotContainKeys(THURSDAY.plusDays(2), THURSDAY.plusDays(5));
        assertThat(form.absenceRequests()).isEmpty();
    }

    @Test
    void ensureConsecutiveFullDaysAcrossTheWeekendBecomeOneApplication() {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("day." + THURSDAY, "A:1");
        parameters.put("day." + THURSDAY.plusDays(1), "A:1");
        parameters.put("day." + THURSDAY.plusDays(4), "A:1");
        parameters.put("day." + THURSDAY.plusDays(5), "A:2");

        final PlanningForm form = PlanningForm.parse(parameters, week());

        assertThat(form.absenceRequests()).containsExactly(
            new PlanningForm.AbsenceRequest(1L, THURSDAY, THURSDAY.plusDays(4), FULL),
            new PlanningForm.AbsenceRequest(2L, THURSDAY.plusDays(5), THURSDAY.plusDays(5), FULL)
        );
        assertThat(form.presences()).containsEntry(THURSDAY, null);
    }

    @Test
    void ensureHalfDayAbsenceAndPresence() {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("day." + THURSDAY, "SPLIT");
        parameters.put("day." + THURSDAY + ".morning", "A:1");
        parameters.put("day." + THURSDAY + ".noon", "P:OFFICE");

        final PlanningForm form = PlanningForm.parse(parameters, week());

        assertThat(form.absenceRequests()).containsExactly(new PlanningForm.AbsenceRequest(1L, THURSDAY, THURSDAY, MORNING));
        assertThat(form.presences()).containsEntry(THURSDAY, new PresenceSlots(null, OFFICE));
    }

    @Test
    void ensureOnlyTheWorkingHalfOfAHalfAbsentDayIsTaken() {
        final Map<LocalDate, PresenceDay> days = week();
        days.put(THURSDAY, new PresenceDay(Half.absent(new AbsenceMark("Urlaub", "red", false, 1L)), Half.of(Kind.UNPLANNED)));
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("day." + THURSDAY + ".morning", "P:OFFICE");
        parameters.put("day." + THURSDAY + ".noon", "A:3");

        final PlanningForm form = PlanningForm.parse(parameters, days);

        assertThat(form.presences()).containsEntry(THURSDAY, null);
        assertThat(form.absenceRequests()).containsExactly(new PlanningForm.AbsenceRequest(3L, THURSDAY, THURSDAY, NOON));
    }

    private static Map<LocalDate, PresenceDay> week() {
        final Map<LocalDate, PresenceDay> days = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            final LocalDate date = THURSDAY.plusDays(i);
            final boolean weekend = date.getDayOfWeek().getValue() > 5;
            days.put(date, PresenceDay.fullDay(Half.of(weekend ? Kind.NO_WORKDAY : Kind.UNPLANNED)));
        }
        return days;
    }
}
