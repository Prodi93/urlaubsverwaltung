package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.department.Department;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;

/**
 * Who may export the data of whom:
 * <ul>
 *     <li>everybody his own data</li>
 *     <li>department heads and second stage authorities the departments they manage</li>
 *     <li>boss and office every department and the whole company</li>
 * </ul>
 * Only the office may import.
 */
@Component
class CsvScopeResolver {

    private final DepartmentService departmentService;
    private final PersonService personService;

    CsvScopeResolver(DepartmentService departmentService, PersonService personService) {
        this.departmentService = departmentService;
        this.personService = personService;
    }

    boolean isAllowedToExportCompany(Person user) {
        return user.hasRole(BOSS) || user.hasRole(OFFICE);
    }

    boolean isAllowedToImport(Person user) {
        return user.hasRole(OFFICE);
    }

    List<Department> getExportableDepartments(Person user) {
        if (isAllowedToExportCompany(user)) {
            return departmentService.getAllDepartments();
        }
        final List<Department> departments = new ArrayList<>();
        if (user.hasRole(DEPARTMENT_HEAD)) {
            departments.addAll(departmentService.getManagedDepartmentsOfDepartmentHead(user));
        }
        if (user.hasRole(SECOND_STAGE_AUTHORITY)) {
            departments.addAll(departmentService.getManagedDepartmentsOfSecondStageAuthority(user));
        }
        final Map<Long, Department> distinctById = new LinkedHashMap<>();
        departments.forEach(department -> distinctById.putIfAbsent(department.getId(), department));
        return distinctById.values().stream().sorted(Comparator.comparing(Department::getName)).toList();
    }

    List<Person> resolvePersons(Person user, CsvScope scope, Long departmentId) {
        return switch (scope) {
            case SELF -> List.of(user);
            case DEPARTMENT -> getExportableDepartments(user).stream()
                .filter(department -> department.getId().equals(departmentId))
                .findFirst()
                .map(department -> department.getMembers().stream().filter(Person::isActive).sorted(Comparator.comparing(Person::getNiceName)).toList())
                .orElseThrow(() -> new AccessDeniedException("not allowed to export department " + departmentId));
            case COMPANY -> {
                if (!isAllowedToExportCompany(user)) {
                    throw new AccessDeniedException("not allowed to export the company");
                }
                yield personService.getActivePersons().stream().sorted(Comparator.comparing(Person::getNiceName)).toList();
            }
        };
    }
}
