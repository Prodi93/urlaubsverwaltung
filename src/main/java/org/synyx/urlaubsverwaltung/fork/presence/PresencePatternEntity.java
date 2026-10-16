package org.synyx.urlaubsverwaltung.fork.presence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.tenancy.tenant.AbstractTenantAwareEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.SEQUENCE;

/**
 * Default presence of a person per weekday, valid from the given date until the next pattern of the person.
 */
@Entity
@Table(name = "fork_presence_pattern")
public class PresencePatternEntity extends AbstractTenantAwareEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    @GeneratedValue(strategy = SEQUENCE, generator = "fork_presence_pattern_generator")
    @SequenceGenerator(name = "fork_presence_pattern_generator", sequenceName = "fork_presence_pattern_id_seq", allocationSize = 10)
    private Long id;

    @ManyToOne(optional = false)
    private Person person;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Enumerated(STRING)
    private PresenceType monday;
    @Enumerated(STRING)
    private PresenceType tuesday;
    @Enumerated(STRING)
    private PresenceType wednesday;
    @Enumerated(STRING)
    private PresenceType thursday;
    @Enumerated(STRING)
    private PresenceType friday;
    @Enumerated(STRING)
    private PresenceType saturday;
    @Enumerated(STRING)
    private PresenceType sunday;

    protected PresencePatternEntity() {
        // OK
    }

    PresencePatternEntity(Person person, LocalDate validFrom) {
        this.person = person;
        this.validFrom = validFrom;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    Map<DayOfWeek, PresenceType> getDays() {
        final Map<DayOfWeek, PresenceType> days = new EnumMap<>(DayOfWeek.class);
        putIfPresent(days, DayOfWeek.MONDAY, monday);
        putIfPresent(days, DayOfWeek.TUESDAY, tuesday);
        putIfPresent(days, DayOfWeek.WEDNESDAY, wednesday);
        putIfPresent(days, DayOfWeek.THURSDAY, thursday);
        putIfPresent(days, DayOfWeek.FRIDAY, friday);
        putIfPresent(days, DayOfWeek.SATURDAY, saturday);
        putIfPresent(days, DayOfWeek.SUNDAY, sunday);
        return days;
    }

    void setDays(Map<DayOfWeek, PresenceType> days) {
        monday = days.get(DayOfWeek.MONDAY);
        tuesday = days.get(DayOfWeek.TUESDAY);
        wednesday = days.get(DayOfWeek.WEDNESDAY);
        thursday = days.get(DayOfWeek.THURSDAY);
        friday = days.get(DayOfWeek.FRIDAY);
        saturday = days.get(DayOfWeek.SATURDAY);
        sunday = days.get(DayOfWeek.SUNDAY);
    }

    private static void putIfPresent(Map<DayOfWeek, PresenceType> days, DayOfWeek dayOfWeek, PresenceType type) {
        if (type != null) {
            days.put(dayOfWeek, type);
        }
    }
}
