package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonDeletedEvent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;

/**
 * Presence planning (office / home office) of persons. A day explicitly planned wins over the weekly pattern.
 */
@Service
public class PresenceService {

    private final PresenceDayRepository presenceDayRepository;
    private final PresencePatternRepository presencePatternRepository;

    PresenceService(PresenceDayRepository presenceDayRepository, PresencePatternRepository presencePatternRepository) {
        this.presenceDayRepository = presenceDayRepository;
        this.presencePatternRepository = presencePatternRepository;
    }

    /**
     * @return presences of the persons in the date range (inclusive). Persons and days without presence are missing.
     */
    public Map<Person, Map<LocalDate, Presence>> getPresences(Collection<Person> persons, DateRange dateRange) {
        if (persons.isEmpty()) {
            return Map.of();
        }

        final Map<Person, List<PresencePattern>> patternsByPerson = getPatterns(persons);
        final Map<Person, List<PresenceDayEntity>> daysByPerson = presenceDayRepository
            .findByPersonInAndDateBetween(persons, dateRange.startDate(), dateRange.endDate()).stream()
            .collect(groupingBy(PresenceDayEntity::getPerson));

        final Map<Person, Map<LocalDate, Presence>> result = new HashMap<>();
        for (Person person : persons) {
            final Map<LocalDate, Presence> presences = new HashMap<>();
            final List<PresencePattern> patterns = patternsByPerson.getOrDefault(person, List.of());
            if (!patterns.isEmpty()) {
                for (LocalDate date : dateRange) {
                    patternOn(patterns, date)
                        .map(pattern -> pattern.days().get(date.getDayOfWeek()))
                        .ifPresent(type -> presences.put(date, new Presence(type, false)));
                }
            }
            for (PresenceDayEntity day : daysByPerson.getOrDefault(person, List.of())) {
                presences.put(day.getDate(), new Presence(day.getType(), true));
            }
            if (!presences.isEmpty()) {
                result.put(person, presences);
            }
        }
        return result;
    }

    public Map<LocalDate, Presence> getPresences(Person person, DateRange dateRange) {
        return getPresences(List.of(person), dateRange).getOrDefault(person, Map.of());
    }

    /**
     * Plans the presence of the person on the given date. {@code null} removes the planned presence, so the weekly
     * pattern applies again.
     */
    @Transactional
    public void setPresence(Person person, LocalDate date, PresenceType type) {
        final Optional<PresenceDayEntity> existing = presenceDayRepository.findByPersonAndDate(person, date);
        if (type == null) {
            existing.ifPresent(presenceDayRepository::delete);
            return;
        }
        final PresenceDayEntity entity = existing.orElseGet(() -> new PresenceDayEntity(person, date, type));
        entity.setType(type);
        presenceDayRepository.save(entity);
    }

    @Transactional
    public void setPresences(Person person, Map<LocalDate, PresenceType> presences) {
        presences.forEach((date, type) -> setPresence(person, date, type));
    }

    public List<PresencePattern> getPatterns(Person person) {
        return getPatterns(List.of(person)).getOrDefault(person, List.of());
    }

    /**
     * @return the weekly pattern valid on the given date
     */
    public Optional<PresencePattern> getPattern(Person person, LocalDate date) {
        return patternOn(getPatterns(person), date);
    }

    @Transactional
    public PresencePattern savePattern(Person person, LocalDate validFrom, Map<DayOfWeek, PresenceType> days) {
        final PresencePatternEntity entity = presencePatternRepository.findByPersonAndValidFrom(person, validFrom)
            .orElseGet(() -> new PresencePatternEntity(person, validFrom));
        entity.setDays(days);
        return PresencePattern.of(presencePatternRepository.save(entity));
    }

    @EventListener
    @Transactional
    void deleteAll(PersonDeletedEvent event) {
        presenceDayRepository.deleteByPerson(event.person());
        presencePatternRepository.deleteByPerson(event.person());
    }

    private Map<Person, List<PresencePattern>> getPatterns(Collection<Person> persons) {
        return presencePatternRepository.findByPersonInOrderByValidFromAsc(persons).stream()
            .map(PresencePattern::of)
            .collect(groupingBy(PresencePattern::person));
    }

    static Optional<PresencePattern> patternOn(List<PresencePattern> orderedPatterns, LocalDate date) {
        PresencePattern current = null;
        for (PresencePattern pattern : orderedPatterns) {
            if (pattern.validFrom().isAfter(date)) {
                break;
            }
            current = pattern;
        }
        return Optional.ofNullable(current);
    }
}
