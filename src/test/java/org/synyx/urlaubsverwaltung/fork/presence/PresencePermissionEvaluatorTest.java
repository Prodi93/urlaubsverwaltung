package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

@ExtendWith(MockitoExtension.class)
class PresencePermissionEvaluatorTest {

    @Mock
    private DepartmentService departmentService;

    private PresencePermissionEvaluator sut;
    private Person person;

    @BeforeEach
    void setUp() {
        sut = new PresencePermissionEvaluator(departmentService);
        person = person(1L, USER);
    }

    @Test
    void ensurePersonMayEditOwnPresence() {
        assertThat(sut.isAllowedToEdit(person, person)).isTrue();
    }

    @Test
    void ensureColleagueMayNotEdit() {
        assertThat(sut.isAllowedToEdit(person(2L, USER), person)).isFalse();
    }

    @Test
    void ensureOfficeAndBossMayEdit() {
        assertThat(sut.isAllowedToEdit(person(2L, USER, OFFICE), person)).isTrue();
        assertThat(sut.isAllowedToEdit(person(3L, USER, BOSS), person)).isTrue();
    }

    @Test
    void ensureDepartmentHeadMayEditManagedPersonsOnly() {
        final Person departmentHead = person(2L, USER, DEPARTMENT_HEAD);
        final Person other = person(4L, USER);
        when(departmentService.isDepartmentHeadAllowedToManagePerson(departmentHead, person)).thenReturn(true);
        when(departmentService.isDepartmentHeadAllowedToManagePerson(departmentHead, other)).thenReturn(false);

        assertThat(sut.isAllowedToEdit(departmentHead, person)).isTrue();
        assertThat(sut.isAllowedToEdit(departmentHead, other)).isFalse();
    }

    @Test
    void ensureSecondStageAuthorityMayEditManagedPersons() {
        final Person secondStageAuthority = person(2L, USER, SECOND_STAGE_AUTHORITY);
        when(departmentService.isSecondStageAuthorityAllowedToManagePerson(secondStageAuthority, person)).thenReturn(true);

        assertThat(sut.isAllowedToEdit(secondStageAuthority, person)).isTrue();
    }

    private static Person person(Long id, Role... roles) {
        final Person person = new Person("user" + id, "Last", "First", "user" + id + "@example.org");
        person.setId(id);
        person.setPermissions(List.of(roles));
        return person;
    }
}
