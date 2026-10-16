package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;

@SpringBootTest
@Transactional
class PresenceServiceIT extends SingleTenantTestContainersBase {

    @Autowired
    private PresenceService presenceService;
    @Autowired
    private PersonService personService;

    @Test
    void ensurePresencesArePersisted() {
        final Person person = personService.create("presence", "Marlene", "Muster", "presence@example.org");

        presenceService.savePattern(person, LocalDate.of(2026, 1, 1), Map.of(DayOfWeek.MONDAY, OFFICE, DayOfWeek.FRIDAY, HOME_OFFICE));
        presenceService.setPresence(person, LocalDate.of(2026, 10, 12), HOME_OFFICE);
        presenceService.setPresence(person, LocalDate.of(2026, 10, 13), OFFICE);
        presenceService.setPresence(person, LocalDate.of(2026, 10, 13), null);

        final Map<LocalDate, Presence> presences = presenceService.getPresences(person,
            new DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16)));

        assertThat(presences)
            .containsEntry(LocalDate.of(2026, 10, 12), new Presence(HOME_OFFICE, true))
            .containsEntry(LocalDate.of(2026, 10, 16), new Presence(HOME_OFFICE, false))
            .doesNotContainKey(LocalDate.of(2026, 10, 13))
            .hasSize(2);
    }
}
