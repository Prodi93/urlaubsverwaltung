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

import static jakarta.persistence.GenerationType.SEQUENCE;

/**
 * Manual correction of the computed part time free days of a person for a year.
 */
@Entity
@Table(name = "fork_part_time_quota")
public class PartTimeQuotaEntity extends AbstractTenantAwareEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    @GeneratedValue(strategy = SEQUENCE, generator = "fork_part_time_quota_generator")
    @SequenceGenerator(name = "fork_part_time_quota_generator", sequenceName = "fork_part_time_quota_id_seq", allocationSize = 10)
    private Long id;

    @ManyToOne(optional = false)
    private Person person;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "override_days", precision = 5, scale = 1)
    private BigDecimal overrideDays;

    @Column(name = "comment")
    private String comment;

    protected PartTimeQuotaEntity() {
        // OK
    }

    PartTimeQuotaEntity(Person person, int year) {
        this.person = person;
        this.year = year;
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getOverrideDays() {
        return overrideDays;
    }

    void setOverrideDays(BigDecimal overrideDays) {
        this.overrideDays = overrideDays;
    }

    public String getComment() {
        return comment;
    }

    void setComment(String comment) {
        this.comment = comment;
    }
}
