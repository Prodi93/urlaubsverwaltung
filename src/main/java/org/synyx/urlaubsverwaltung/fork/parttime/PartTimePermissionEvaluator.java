package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;

import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;

@Component
class PartTimePermissionEvaluator {

    private final DepartmentService departmentService;

    PartTimePermissionEvaluator(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * the person itself, boss, office and department heads / second stage authorities of the person
     */
    boolean isAllowedToView(Person signedInUser, Person person) {
        return departmentService.isSignedInUserAllowedToAccessPersonData(signedInUser, person);
    }

    boolean isAllowedToEdit(Person signedInUser) {
        return signedInUser.hasRole(OFFICE);
    }
}
