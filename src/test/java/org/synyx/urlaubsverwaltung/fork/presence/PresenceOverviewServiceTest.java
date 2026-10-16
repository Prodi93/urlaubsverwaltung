package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Kind;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceOverviewService.HalfDays;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.NO_WORKDAY;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.PUBLIC_HOLIDAY;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.SICK;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.VACATION;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;

class PresenceOverviewServiceTest {

    private static final Presence OFFICE_PLANNED = new Presence(OFFICE, true);

    @Test
    void ensurePresenceWithoutAbsence() {
        assertThat(PresenceOverviewService.day(OFFICE_PLANNED, null))
            .isEqualTo(new PresenceDay(Kind.PRESENCE, OFFICE, true, false));
    }

    @Test
    void ensureUnplannedWorkday() {
        assertThat(PresenceOverviewService.day(null, null).kind()).isEqualTo(Kind.UNPLANNED);
    }

    @Test
    void ensurePublicHolidayWinsOverPresence() {
        assertThat(PresenceOverviewService.day(OFFICE_PLANNED, halfDays(PUBLIC_HOLIDAY, PUBLIC_HOLIDAY)).kind()).isEqualTo(Kind.PUBLIC_HOLIDAY);
    }

    @Test
    void ensureFullDayAbsenceWinsOverPresence() {
        assertThat(PresenceOverviewService.day(OFFICE_PLANNED, halfDays(VACATION, VACATION)).kind()).isEqualTo(Kind.ABSENT);
        assertThat(PresenceOverviewService.day(OFFICE_PLANNED, halfDays(SICK, VACATION)).kind()).isEqualTo(Kind.ABSENT);
    }

    @Test
    void ensureNoWorkdayWinsOverPatternPresence() {
        assertThat(PresenceOverviewService.day(new Presence(OFFICE, false), halfDays(NO_WORKDAY, NO_WORKDAY)).kind()).isEqualTo(Kind.NO_WORKDAY);
    }

    @Test
    void ensureHalfDayAbsenceKeepsPresenceWithMarker() {
        assertThat(PresenceOverviewService.day(OFFICE_PLANNED, halfDays(VACATION, null)))
            .isEqualTo(new PresenceDay(Kind.PRESENCE, OFFICE, true, true));
    }

    @Test
    void ensureHalfPublicHolidayKeepsPresenceWithMarker() {
        // e.g. christmas eve with working time in the morning
        assertThat(PresenceOverviewService.day(OFFICE_PLANNED, halfDays(null, PUBLIC_HOLIDAY)).halfDayOff()).isTrue();
    }

    @Test
    void ensureCountsPersonsInOfficePerDay() {
        final LocalDate monday = LocalDate.of(2026, 10, 12);
        final LocalDate tuesday = monday.plusDays(1);
        final Map<LocalDate, Long> count = PresenceOverviewService.countInOffice(List.of(
            Map.of(monday, new PresenceDay(Kind.PRESENCE, OFFICE, true, false), tuesday, new PresenceDay(Kind.PRESENCE, HOME_OFFICE, true, false)),
            Map.of(monday, new PresenceDay(Kind.PRESENCE, OFFICE, false, true), tuesday, PresenceDay.of(Kind.ABSENT))
        ));

        assertThat(count).containsEntry(monday, 2L).doesNotContainKey(tuesday);
    }

    private static HalfDays halfDays(org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType morning,
                                     org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType noon) {
        final HalfDays halfDays = new HalfDays();
        halfDays.morning = morning;
        halfDays.noon = noon;
        return halfDays;
    }
}
