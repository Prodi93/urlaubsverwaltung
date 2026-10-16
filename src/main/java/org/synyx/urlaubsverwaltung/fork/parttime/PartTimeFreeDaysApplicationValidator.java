package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.synyx.urlaubsverwaltung.application.application.Application;
import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.fork.application.AdditionalApplicationValidator;
import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;

import static org.synyx.urlaubsverwaltung.application.vacationtype.VacationCategory.PARTTIME_FREEDAY;

/**
 * Ensures that an application for a part time free day does not exceed the part time free days quota.
 */
@Component
class PartTimeFreeDaysApplicationValidator implements AdditionalApplicationValidator {

    static final String ERROR_NO_PART_TIME = "fork.parttime.error.noPartTime";
    static final String ERROR_NOT_ENOUGH_FREE_DAYS = "fork.parttime.error.notEnoughFreeDays";

    private final PartTimeService partTimeService;
    private final PartTimeFreeDaysService partTimeFreeDaysService;

    PartTimeFreeDaysApplicationValidator(PartTimeService partTimeService, PartTimeFreeDaysService partTimeFreeDaysService) {
        this.partTimeService = partTimeService;
        this.partTimeFreeDaysService = partTimeFreeDaysService;
    }

    @Override
    public void validate(Application application, VacationType<?> vacationType, Errors errors) {
        if (vacationType == null || !vacationType.isOfCategory(PARTTIME_FREEDAY)) {
            return;
        }

        final Person person = application.getPerson();
        if (partTimeService.getPercentage(person, application.getStartDate()).isEmpty()) {
            errors.reject(ERROR_NO_PART_TIME);
            return;
        }

        for (int year = application.getStartDate().getYear(); year <= application.getEndDate().getYear(); year++) {
            final BigDecimal needed = partTimeFreeDaysService.getDaysOfApplicationInYear(application, year);
            if (needed.signum() > 0) {
                final BigDecimal left = partTimeFreeDaysService.getSummary(person, year, application.getId()).left();
                if (needed.compareTo(left) > 0) {
                    errors.reject(ERROR_NOT_ENOUGH_FREE_DAYS, new Object[]{left.max(BigDecimal.ZERO), year}, null);
                    return;
                }
            }
        }
    }
}
