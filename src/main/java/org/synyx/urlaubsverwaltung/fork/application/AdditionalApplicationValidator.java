package org.synyx.urlaubsverwaltung.fork.application;

import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.springframework.validation.Errors;

/**
 * Additional validation of an application for leave, executed by the
 * {@code ApplicationForLeaveFormValidator} after the upstream checks (working time, overlap, vacation days, overtime).
 */
public interface AdditionalApplicationValidator {

    void validate(Application application, VacationType<?> vacationType, Errors errors);
}
