package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PresenceDayRepository extends JpaRepository<PresenceDayEntity, Long> {

    List<PresenceDayEntity> findByPersonInAndDateBetween(Collection<Person> persons, LocalDate from, LocalDate to);

    Optional<PresenceDayEntity> findByPersonAndDate(Person person, LocalDate date);

    void deleteByPerson(Person person);
}
