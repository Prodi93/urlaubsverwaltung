package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PresencePatternRepository extends JpaRepository<PresencePatternEntity, Long> {

    List<PresencePatternEntity> findByPersonInOrderByValidFromAsc(Collection<Person> persons);

    Optional<PresencePatternEntity> findByPersonAndValidFrom(Person person, LocalDate validFrom);

    void deleteByPerson(Person person);
}
