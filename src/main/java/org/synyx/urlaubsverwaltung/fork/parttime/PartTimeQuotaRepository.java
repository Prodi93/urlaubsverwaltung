package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.synyx.urlaubsverwaltung.person.Person;

import java.util.Optional;

interface PartTimeQuotaRepository extends JpaRepository<PartTimeQuotaEntity, Long> {

    Optional<PartTimeQuotaEntity> findByPersonAndYear(Person person, int year);

    void deleteByPerson(Person person);
}
