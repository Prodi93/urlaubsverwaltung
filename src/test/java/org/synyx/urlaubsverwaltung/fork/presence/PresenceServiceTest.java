package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.MOBILE_WORK;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private PresenceDayRepository presenceDayRepository;
    @Mock
    private PresencePatternRepository presencePatternRepository;

    private PresenceService sut;
    private Person person;

    @BeforeEach
    void setUp() {
        sut = new PresenceService(presenceDayRepository, presencePatternRepository);
        person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setId(1L);
    }

    @Test
    void ensurePlannedDayWinsOverPatternAndPatternHistoryIsRespected() {
        // monday 2026-10-12 until sunday 2026-10-18
        final DateRange week = new DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));

        final PresencePatternEntity oldPattern = new PresencePatternEntity(person, LocalDate.of(2026, 1, 1));
        oldPattern.setDays(Map.of(DayOfWeek.MONDAY, OFFICE, DayOfWeek.TUESDAY, OFFICE, DayOfWeek.WEDNESDAY, OFFICE));
        final PresencePatternEntity newPattern = new PresencePatternEntity(person, LocalDate.of(2026, 10, 14));
        newPattern.setDays(Map.of(DayOfWeek.WEDNESDAY, HOME_OFFICE, DayOfWeek.THURSDAY, HOME_OFFICE));

        when(presencePatternRepository.findByPersonInOrderByValidFromAsc(List.of(person))).thenReturn(List.of(oldPattern, newPattern));
        when(presenceDayRepository.findByPersonInAndDateBetween(List.of(person), week.startDate(), week.endDate()))
            .thenReturn(List.of(new PresenceDayEntity(person, LocalDate.of(2026, 10, 13), MOBILE_WORK)));

        final Map<LocalDate, Presence> presences = sut.getPresences(person, week);

        assertThat(presences)
            .containsEntry(LocalDate.of(2026, 10, 12), new Presence(OFFICE, false))
            .containsEntry(LocalDate.of(2026, 10, 13), new Presence(MOBILE_WORK, true))
            .containsEntry(LocalDate.of(2026, 10, 14), new Presence(HOME_OFFICE, false))
            .containsEntry(LocalDate.of(2026, 10, 15), new Presence(HOME_OFFICE, false))
            .doesNotContainKeys(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 17));
    }

    @Test
    void ensureSettingNullRemovesPlannedDay() {
        final PresenceDayEntity existing = new PresenceDayEntity(person, LocalDate.of(2026, 10, 13), OFFICE);
        when(presenceDayRepository.findByPersonAndDate(person, LocalDate.of(2026, 10, 13))).thenReturn(Optional.of(existing));

        sut.setPresence(person, LocalDate.of(2026, 10, 13), null);

        verify(presenceDayRepository).delete(existing);
        verify(presenceDayRepository, never()).save(any());
    }

    @Test
    void ensureSettingTypeUpdatesPlannedDay() {
        final PresenceDayEntity existing = new PresenceDayEntity(person, LocalDate.of(2026, 10, 13), OFFICE);
        when(presenceDayRepository.findByPersonAndDate(person, LocalDate.of(2026, 10, 13))).thenReturn(Optional.of(existing));

        sut.setPresence(person, LocalDate.of(2026, 10, 13), HOME_OFFICE);

        assertThat(existing.getType()).isEqualTo(HOME_OFFICE);
        verify(presenceDayRepository).save(existing);
    }

    @Test
    void ensureNoPresencesForNoPersons() {
        assertThat(sut.getPresences(List.of(), new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))).isEmpty();
    }
}
