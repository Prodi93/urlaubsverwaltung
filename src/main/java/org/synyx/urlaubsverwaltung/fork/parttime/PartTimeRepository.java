package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PartTimeRepository extends JpaRepository<PartTimeEntity, Long> {

    List<PartTimeEntity> findByPersonOrderByValidFromAsc(Person person);

    List<PartTimeEntity> findByPersonInOrderByValidFromAsc(Collection<Person> persons);

    Optional<PartTimeEntity> findByPersonAndValidFrom(Person person, LocalDate validFrom);

    void deleteByPerson(Person person);
}
