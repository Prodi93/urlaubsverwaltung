package org.synyx.urlaubsverwaltung.fork.parttime;

import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Part time percentage of a person. Valid from {@code validFrom} until the next part time of the person.
 * A percentage of 100 ends a previous part time.
 */
public record PartTime(Long id, Person person, LocalDate validFrom, BigDecimal percentage) {

    static PartTime of(PartTimeEntity entity) {
        return new PartTime(entity.getId(), entity.getPerson(), entity.getValidFrom(), entity.getPercentage());
    }
}
