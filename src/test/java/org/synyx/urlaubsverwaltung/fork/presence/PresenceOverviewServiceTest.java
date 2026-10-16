package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;
import org.synyx.urlaubsverwaltung.absence.AbsencePeriod;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.AbsenceMark;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Half;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceDay.Kind;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.NO_WORKDAY;
import static org.synyx.urlaubsverwaltung.absence.AbsencePeriod.AbsenceType.PUBLIC_HOLIDAY;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;

class PresenceOverviewServiceTest {

    private static final Presence OFFICE_PLANNED = new Presence(PresenceSlots.fullDay(OFFICE), true);
    private static final AbsenceMark VACATION = new AbsenceMark("Erholungsurlaub", "var(--absence-color-YELLOW)", false, 1L);

    @Test
    void ensurePresenceWithoutAbsence() {
        assertThat(PresenceOverviewService.half(null, OFFICE, OFFICE_PLANNED, null)).isEqualTo(Half.presence(OFFICE, true));
    }

    @Test
    void ensureUnplannedWorkingHalf() {
        assertThat(PresenceOverviewService.half(null, null, null, null).kind()).isEqualTo(Kind.UNPLANNED);
    }

    @Test
    void ensurePublicHolidayAndNoWorkdayWinOverPresence() {
        assertThat(PresenceOverviewService.half(info(PUBLIC_HOLIDAY), OFFICE, OFFICE_PLANNED, null).kind()).isEqualTo(Kind.PUBLIC_HOLIDAY);
        assertThat(PresenceOverviewService.half(info(NO_WORKDAY), OFFICE, OFFICE_PLANNED, null).kind()).isEqualTo(Kind.NO_WORKDAY);
    }

    @Test
    void ensureDayIsSplitIfHalvesDiffer() {
        final PresenceDay sameDay = new PresenceDay(Half.presence(OFFICE, true), Half.presence(OFFICE, true));
        final PresenceDay splitDay = new PresenceDay(Half.absent(VACATION), Half.presence(HOME_OFFICE, true));

        assertThat(sameDay.isSplit()).isFalse();
        assertThat(sameDay.presence()).contains(PresenceSlots.fullDay(OFFICE));
        assertThat(splitDay.isSplit()).isTrue();
        assertThat(splitDay.isWorkday()).isTrue();
        assertThat(splitDay.isFullWorkday()).isFalse();
        assertThat(splitDay.presence()).contains(new PresenceSlots(null, HOME_OFFICE));
    }

    @Test
    void ensureCountsHalfDaysInOffice() {
        final LocalDate monday = LocalDate.of(2026, 10, 12);
        final LocalDate tuesday = monday.plusDays(1);
        final Map<LocalDate, Double> count = PresenceOverviewService.countInOffice(List.of(
            Map.of(monday, new PresenceDay(Half.presence(OFFICE, true), Half.presence(OFFICE, true)),
                tuesday, new PresenceDay(Half.presence(HOME_OFFICE, true), Half.presence(HOME_OFFICE, true))),
            Map.of(monday, new PresenceDay(Half.presence(OFFICE, false), Half.absent(VACATION)),
                tuesday, new PresenceDay(Half.absent(VACATION), Half.absent(VACATION)))
        ));

        assertThat(count).containsEntry(monday, 1.5).doesNotContainKey(tuesday);
    }

    private static AbsencePeriod.RecordInfo info(AbsencePeriod.AbsenceType type) {
        final AbsencePeriod.RecordInfo info = mock(AbsencePeriod.RecordInfo.class);
        when(info.getAbsenceType()).thenReturn(type);
        return info;
    }
}
