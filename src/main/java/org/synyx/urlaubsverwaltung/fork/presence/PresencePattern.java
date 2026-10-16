package org.synyx.urlaubsverwaltung.fork.presence;

import org.synyx.urlaubsverwaltung.person.Person;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Default presence per weekday, valid from {@code validFrom} until the next pattern of the person.
 */
public record PresencePattern(Long id, Person person, LocalDate validFrom, Map<DayOfWeek, PresenceSlots> days) {

    static PresencePattern of(PresencePatternEntity entity) {
        return new PresencePattern(entity.getId(), entity.getPerson(), entity.getValidFrom(), entity.getDays());
    }
}
