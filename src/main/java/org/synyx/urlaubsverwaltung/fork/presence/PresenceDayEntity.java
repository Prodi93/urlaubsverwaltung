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

import java.time.LocalDate;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.SEQUENCE;

/**
 * Explicitly planned presence of a person on a day, overrides the weekly pattern.
 */
@Entity
@Table(name = "fork_presence_day")
public class PresenceDayEntity extends AbstractTenantAwareEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    @GeneratedValue(strategy = SEQUENCE, generator = "fork_presence_day_generator")
    @SequenceGenerator(name = "fork_presence_day_generator", sequenceName = "fork_presence_day_id_seq", allocationSize = 10)
    private Long id;

    @ManyToOne(optional = false)
    private Person person;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(STRING)
    @Column(name = "type", nullable = false)
    private PresenceType type;

    protected PresenceDayEntity() {
        // OK
    }

    PresenceDayEntity(Person person, LocalDate date, PresenceType type) {
        this.person = person;
        this.date = date;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public LocalDate getDate() {
        return date;
    }

    public PresenceType getType() {
        return type;
    }

    void setType(PresenceType type) {
        this.type = type;
    }
}
