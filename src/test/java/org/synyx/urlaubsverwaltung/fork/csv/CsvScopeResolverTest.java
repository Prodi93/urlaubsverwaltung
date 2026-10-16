package org.synyx.urlaubsverwaltung.fork.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

@ExtendWith(MockitoExtension.class)
class CsvScopeResolverTest {

    @Mock
    private DepartmentService departmentService;
    @Mock
    private PersonService personService;

    @Test
    void ensureUserMayOnlyExportOwnData() {
        final CsvScopeResolver sut = new CsvScopeResolver(departmentService, personService);
        final Person user = person(1L, USER);

        assertThat(sut.resolvePersons(user, CsvScope.SELF, null)).containsExactly(user);
        assertThat(sut.getExportableDepartments(user)).isEmpty();
        assertThatThrownBy(() -> sut.resolvePersons(user, CsvScope.DEPARTMENT, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> sut.resolvePersons(user, CsvScope.COMPANY, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(sut.isAllowedToImport(user)).isFalse();
    }

    @Test
    void ensureDepartmentHeadMayExportManagedDepartments() {
        final CsvScopeResolver sut = new CsvScopeResolver(departmentService, personService);
        final Person departmentHead = person(1L, USER, DEPARTMENT_HEAD, SECOND_STAGE_AUTHORITY);
        final Person member = person(2L, USER);
        final Department department = department(10L, "Bank", member);
        when(departmentService.getManagedDepartmentsOfDepartmentHead(departmentHead)).thenReturn(List.of(department));
        when(departmentService.getManagedDepartmentsOfSecondStageAuthority(departmentHead)).thenReturn(List.of(department(10L, "Bank", member)));

        assertThat(sut.getExportableDepartments(departmentHead)).hasSize(1);
        assertThat(sut.resolvePersons(departmentHead, CsvScope.DEPARTMENT, 10L)).containsExactly(member);
        assertThatThrownBy(() -> sut.resolvePersons(departmentHead, CsvScope.DEPARTMENT, 11L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> sut.resolvePersons(departmentHead, CsvScope.COMPANY, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureBossAndOfficeMayExportCompanyButOnlyOfficeMayImport() {
        final CsvScopeResolver sut = new CsvScopeResolver(departmentService, personService);
        final Person boss = person(1L, USER, BOSS);
        final Person office = person(2L, USER, OFFICE);
        when(personService.getActivePersons()).thenReturn(List.of(boss, office));

        assertThat(sut.resolvePersons(boss, CsvScope.COMPANY, null)).containsExactlyInAnyOrder(boss, office);
        assertThat(sut.isAllowedToImport(boss)).isFalse();
        assertThat(sut.isAllowedToImport(office)).isTrue();
    }

    private static Person person(Long id, Role... roles) {
        final Person person = new Person("user" + id, "Last" + id, "First", "user" + id + "@example.org");
        person.setId(id);
        person.setPermissions(List.of(roles));
        return person;
    }

    private static Department department(Long id, String name, Person... members) {
        final Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setMembers(List.of(members));
        return department;
    }
}
