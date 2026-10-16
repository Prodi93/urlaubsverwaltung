package org.synyx.urlaubsverwaltung.fork.parttime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.tenancy.tenant.AbstractTenantAwareEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static jakarta.persistence.GenerationType.SEQUENCE;

/**
 * Part time percentage of a person, valid from the given date until the next entry of the person.
 */
@Entity
@Table(name = "fork_part_time")
public class PartTimeEntity extends AbstractTenantAwareEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    @GeneratedValue(strategy = SEQUENCE, generator = "fork_part_time_generator")
    @SequenceGenerator(name = "fork_part_time_generator", sequenceName = "fork_part_time_id_seq", allocationSize = 10)
    private Long id;

    @ManyToOne(optional = false)
    private Person person;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    protected PartTimeEntity() {
        // OK
    }

    PartTimeEntity(Person person, LocalDate validFrom, BigDecimal percentage) {
        this.person = person;
        this.validFrom = validFrom;
        this.percentage = percentage;
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

    public BigDecimal getPercentage() {
        return percentage;
    }

    void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }
}
