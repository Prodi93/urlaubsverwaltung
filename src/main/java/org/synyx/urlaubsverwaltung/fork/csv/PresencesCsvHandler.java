package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.fork.presence.Presence;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceService;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceSlots;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.EMAIL;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.USERNAME;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.CREATE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.DELETE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UNCHANGED;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UPDATE;

/**
 * Planned presences (not the ones of the weekly pattern). An empty type removes the planned presence.
 * Half days are written as {@code <morning>|<noon>}, e.g. {@code OFFICE|HOME_OFFICE} or {@code |OFFICE}.
 */
@Component
class PresencesCsvHandler implements CsvDatasetHandler {

    private static final String DATE = "date";
    private static final String TYPE = "type";

    private final PresenceService presenceService;
    private final CsvPersonResolver personResolver;

    PresencesCsvHandler(PresenceService presenceService, CsvPersonResolver personResolver) {
        this.presenceService = presenceService;
        this.personResolver = personResolver;
    }

    @Override
    public CsvDataset dataset() {
        return CsvDataset.PRESENCES;
    }

    @Override
    public List<String> header() {
        return List.of(USERNAME, EMAIL, DATE, TYPE);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of(DATE, TYPE);
    }

    @Override
    public List<List<String>> export(List<Person> persons, DateRange dateRange, Locale locale) {
        final Map<Person, Map<LocalDate, Presence>> presences = presenceService.getPresences(persons, dateRange);
        final List<List<String>> rows = new ArrayList<>();
        for (Person person : persons) {
            new TreeMap<>(presences.getOrDefault(person, Map.of())).forEach((date, presence) -> {
                if (presence.explicit()) {
                    rows.add(List.of(person.getUsername(), person.getEmail(), CsvValues.format(date), presence.slots().asString()));
                }
            });
        }
        return rows;
    }

    @Override
    public CsvRowResult importRow(CsvTable.Row row, Person importer, boolean execute) {
        final String subject = CsvPersonResolver.subject(row);
        try {
            final Optional<Person> maybePerson = personResolver.resolve(row);
            if (maybePerson.isEmpty()) {
                return CsvRowResult.error(row, subject, "fork.csv.error.person.unknown");
            }
            final Person person = maybePerson.get();
            if (!row.has(DATE)) {
                return CsvRowResult.error(row, subject, "fork.csv.error.mandatory", DATE);
            }
            final LocalDate date = CsvValues.parseDate(row.get(DATE));
            final PresenceSlots type = row.has(TYPE) ? parse(row.get(TYPE)) : null;

            final Optional<PresenceSlots> current = Optional.ofNullable(presenceService.getPresences(person, new DateRange(date, date)).get(date))
                .filter(Presence::explicit)
                .map(Presence::slots);

            final CsvRowResult.Action action;
            if (type == null) {
                action = current.isPresent() ? DELETE : UNCHANGED;
            } else if (current.isEmpty()) {
                action = CREATE;
            } else {
                action = current.get().equals(type) ? UNCHANGED : UPDATE;
            }

            if (execute && action != UNCHANGED) {
                presenceService.setPresence(person, date, type);
            }
            return CsvRowResult.of(row, subject, action);
        } catch (IllegalArgumentException e) {
            return CsvRowResult.error(row, subject, e.getMessage());
        }
    }

    private static PresenceSlots parse(String value) {
        try {
            return PresenceSlots.parse(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("fork.csv.error.value");
        }
    }
}
