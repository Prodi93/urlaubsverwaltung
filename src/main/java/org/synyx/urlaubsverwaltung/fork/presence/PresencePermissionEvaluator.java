package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.person.Person;

import static org.synyx.urlaubsverwaltung.person.Role.BOSS;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;

@Component
class PresencePermissionEvaluator {

    private final DepartmentService departmentService;

    PresencePermissionEvaluator(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * the person itself, boss, office and department heads / second stage authorities of the person
     */
    boolean isAllowedToEdit(Person signedInUser, Person person) {
        if (signedInUser.getId().equals(person.getId())) {
            return true;
        }
        if (signedInUser.hasRole(OFFICE) || signedInUser.hasRole(BOSS)) {
            return true;
        }
        if (signedInUser.hasRole(DEPARTMENT_HEAD) && departmentService.isDepartmentHeadAllowedToManagePerson(signedInUser, person)) {
            return true;
        }
        return signedInUser.hasRole(SECOND_STAGE_AUTHORITY) && departmentService.isSecondStageAuthorityAllowedToManagePerson(signedInUser, person);
    }
}
