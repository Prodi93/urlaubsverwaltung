package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonDeletedEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;

/**
 * Manages the part time history of persons ("Teilzeit ohne festen freien Tag").
 */
@Service
public class PartTimeService {

    private final PartTimeRepository partTimeRepository;
    private final PartTimeQuotaRepository partTimeQuotaRepository;

    PartTimeService(PartTimeRepository partTimeRepository, PartTimeQuotaRepository partTimeQuotaRepository) {
        this.partTimeRepository = partTimeRepository;
        this.partTimeQuotaRepository = partTimeQuotaRepository;
    }

    /**
     * @return part time history of the person, ordered by valid from ascending
     */
    public List<PartTime> getPartTimes(Person person) {
        return partTimeRepository.findByPersonOrderByValidFromAsc(person).stream()
            .map(PartTime::of)
            .toList();
    }

    /**
     * @return part time histories of the persons, ordered by valid from ascending
     */
    public Map<Person, List<PartTime>> getPartTimes(Collection<Person> persons) {
        if (persons.isEmpty()) {
            return Map.of();
        }
        return partTimeRepository.findByPersonInOrderByValidFromAsc(persons).stream()
            .map(PartTime::of)
            .collect(groupingBy(PartTime::person));
    }

    /**
     * @return the part time percentage of the person on the given date, empty if the person has no part time
     * (or 100 percent) on that date
     */
    public Optional<BigDecimal> getPercentage(Person person, LocalDate date) {
        return percentageOn(getPartTimes(person), date);
    }

    /**
     * Creates or updates the part time of the person starting at the given date.
     */
    @Transactional
    public PartTime save(Person person, LocalDate validFrom, BigDecimal percentage) {
        final PartTimeEntity entity = partTimeRepository.findByPersonAndValidFrom(person, validFrom)
            .orElseGet(() -> new PartTimeEntity(person, validFrom, percentage));
        entity.setPercentage(percentage);
        return PartTime.of(partTimeRepository.save(entity));
    }

    @Transactional
    public void delete(Person person, Long partTimeId) {
        partTimeRepository.findById(partTimeId)
            .filter(entity -> entity.getPerson().getId().equals(person.getId()))
            .ifPresent(partTimeRepository::delete);
    }

    @EventListener
    @Transactional
    void deleteAll(PersonDeletedEvent event) {
        partTimeRepository.deleteByPerson(event.person());
        partTimeQuotaRepository.deleteByPerson(event.person());
    }

    static Optional<BigDecimal> percentageOn(List<PartTime> orderedPartTimes, LocalDate date) {
        PartTime current = null;
        for (PartTime partTime : orderedPartTimes) {
            if (partTime.validFrom().isAfter(date)) {
                break;
            }
            current = partTime;
        }
        return Optional.ofNullable(current)
            .map(PartTime::percentage)
            .filter(percentage -> percentage.compareTo(BigDecimal.valueOf(100)) < 0);
    }
}
