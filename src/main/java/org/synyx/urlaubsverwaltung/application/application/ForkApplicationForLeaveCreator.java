package org.synyx.urlaubsverwaltung.application.application;

import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * LI fork: applies for leave from other pages than the application form (e.g. the presence planning) with the same
 * validation and workflow as the application form: approval, mails, calendar sync.
 */
@Service
public class ForkApplicationForLeaveCreator {

    private final ApplicationForLeaveFormValidator validator;
    private final ApplicationMapper applicationMapper;
    private final ApplicationInteractionService applicationInteractionService;
    private final ApplicationForLeavePermissionEvaluator permissionEvaluator;
    private final MessageSource messageSource;

    ForkApplicationForLeaveCreator(ApplicationForLeaveFormValidator validator, ApplicationMapper applicationMapper,
                                   ApplicationInteractionService applicationInteractionService,
                                   ApplicationForLeavePermissionEvaluator permissionEvaluator, MessageSource messageSource) {
        this.validator = validator;
        this.applicationMapper = applicationMapper;
        this.applicationInteractionService = applicationInteractionService;
        this.permissionEvaluator = permissionEvaluator;
        this.messageSource = messageSource;
    }

    /**
     * @param errorMessages resolved validation errors, empty if the application has been saved
     */
    public record Result(Optional<Application> application, List<String> errorMessages) {
        public boolean isSuccess() {
            return application.isPresent();
        }
    }

    public boolean isAllowedToApplyForPerson(Person applier, Person person) {
        return permissionEvaluator.isAllowedToApplyForPerson(applier, person);
    }

    public Result apply(Person applier, Person person, Long vacationTypeId, LocalDate startDate, LocalDate endDate,
                        DayLength dayLength, Locale locale) {

        if (!isAllowedToApplyForPerson(applier, person)) {
            throw new AccessDeniedException("User '%s' is not allowed to apply for leave for user '%s'".formatted(applier.getId(), person.getId()));
        }

        final ApplicationForLeaveFormVacationTypeDto vacationType = new ApplicationForLeaveFormVacationTypeDto();
        vacationType.setId(vacationTypeId);

        final ApplicationForLeaveForm form = new ApplicationForLeaveForm();
        form.setPerson(person);
        form.setVacationType(vacationType);
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        form.setDayLength(dayLength);

        final Errors errors = new BeanPropertyBindingResult(form, "applicationForLeaveForm");
        validator.validate(form, errors);
        if (errors.hasErrors()) {
            final List<String> messages = errors.getAllErrors().stream()
                .map(error -> messageSource.getMessage(error, locale))
                .distinct()
                .toList();
            return new Result(Optional.empty(), messages);
        }

        final Application application = applicationMapper.mapToApplication(form);
        final Application saved = application.getVacationType().isRequiresApprovalToApply()
            ? applicationInteractionService.apply(application, applier, Optional.empty())
            : applicationInteractionService.directAllow(application, applier, Optional.empty());
        return new Result(Optional.of(saved), List.of());
    }
}
