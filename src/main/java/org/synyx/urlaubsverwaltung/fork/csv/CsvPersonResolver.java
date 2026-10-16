package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.util.Optional;

/**
 * Finds the person of a CSV row by the column {@code username}, or by {@code email} if the username is missing.
 */
@Component
class CsvPersonResolver {

    static final String USERNAME = "username";
    static final String EMAIL = "email";

    private final PersonService personService;

    CsvPersonResolver(PersonService personService) {
        this.personService = personService;
    }

    Optional<Person> resolve(CsvTable.Row row) {
        if (row.has(USERNAME)) {
            final Optional<Person> person = personService.getPersonByUsername(row.get(USERNAME));
            if (person.isPresent() && row.has(EMAIL) && !row.get(EMAIL).equalsIgnoreCase(person.get().getEmail())) {
                throw new IllegalArgumentException("fork.csv.error.person.emailMismatch");
            }
            return person;
        }
        if (row.has(EMAIL)) {
            return personService.getPersonByMailAddress(row.get(EMAIL));
        }
        throw new IllegalArgumentException("fork.csv.error.person.missing");
    }

    static String subject(CsvTable.Row row) {
        return row.has(USERNAME) ? row.get(USERNAME) : row.get(EMAIL);
    }
}
