package org.synyx.urlaubsverwaltung.fork.parttime;

import de.focus_shift.launchpad.api.HasLaunchpad;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.UnknownPersonException;
import org.synyx.urlaubsverwaltung.search.HasPersonSearch;
import org.synyx.urlaubsverwaltung.search.PersonSearchUiFragmentSupplier;
import org.synyx.urlaubsverwaltung.search.PersonSuggestionUrlStrategy;
import org.synyx.urlaubsverwaltung.web.DecimalNumberPropertyEditor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import static org.synyx.urlaubsverwaltung.security.SecurityRules.IS_OFFICE;

@Controller
@RequestMapping("/web/person/{personId}/parttime")
class PartTimeViewController implements HasLaunchpad, HasPersonSearch {

    private static final String VIEW = "fork/parttime/parttime";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_OVERRIDE_DAYS = BigDecimal.valueOf(366);

    private final PersonService personService;
    private final PartTimeService partTimeService;
    private final PartTimeFreeDaysService partTimeFreeDaysService;
    private final PartTimePermissionEvaluator permissionEvaluator;
    private final PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier;
    private final Clock clock;

    PartTimeViewController(PersonService personService, PartTimeService partTimeService,
                           PartTimeFreeDaysService partTimeFreeDaysService, PartTimePermissionEvaluator permissionEvaluator,
                           PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier, Clock clock) {
        this.personService = personService;
        this.partTimeService = partTimeService;
        this.partTimeFreeDaysService = partTimeFreeDaysService;
        this.permissionEvaluator = permissionEvaluator;
        this.personSearchUiFragmentSupplier = personSearchUiFragmentSupplier;
        this.clock = clock;
    }

    @InitBinder
    public void initBinder(DataBinder binder, Locale locale) {
        binder.registerCustomEditor(BigDecimal.class, new DecimalNumberPropertyEditor(locale));
    }

    @Override
    public PersonSuggestionUrlStrategy personSuggestionUrlStrategy() {
        return (suggestion, request) -> "/web/person/%s/parttime".formatted(suggestion.getId());
    }

    @Override
    public PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier() {
        return personSearchUiFragmentSupplier;
    }

    @GetMapping
    String partTime(@PathVariable("personId") Long personId, @RequestParam(value = "year", required = false) Integer year,
                    Model model) throws UnknownPersonException {

        final Person person = getPerson(personId);
        final Person signedInUser = personService.getSignedInUser();
        if (!permissionEvaluator.isAllowedToView(signedInUser, person)) {
            throw new AccessDeniedException("User '%s' has not the correct permissions to see the part time of user '%s'"
                .formatted(signedInUser.getId(), person.getId()));
        }

        final int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        fillModel(model, person, signedInUser, selectedYear, new PartTimeForm(), quotaForm(person, selectedYear));
        return VIEW;
    }

    @PreAuthorize(IS_OFFICE)
    @PostMapping
    String savePartTime(@PathVariable("personId") Long personId,
                        @ModelAttribute("partTimeForm") PartTimeForm partTimeForm, Errors errors,
                        Model model, RedirectAttributes redirectAttributes) throws UnknownPersonException {

        final Person person = getPerson(personId);
        validate(partTimeForm, errors);
        if (errors.hasErrors()) {
            final int year = LocalDate.now(clock).getYear();
            fillModel(model, person, personService.getSignedInUser(), year, partTimeForm, quotaForm(person, year));
            return VIEW;
        }

        partTimeService.save(person, partTimeForm.getValidFrom(), partTimeForm.getPercentage());
        redirectAttributes.addFlashAttribute("partTimeSaved", true);
        return "redirect:/web/person/%s/parttime?year=%s".formatted(personId, partTimeForm.getValidFrom().getYear());
    }

    @PreAuthorize(IS_OFFICE)
    @PostMapping("/{partTimeId}/delete")
    String deletePartTime(@PathVariable("personId") Long personId, @PathVariable("partTimeId") Long partTimeId,
                          RedirectAttributes redirectAttributes) throws UnknownPersonException {

        partTimeService.delete(getPerson(personId), partTimeId);
        redirectAttributes.addFlashAttribute("partTimeDeleted", true);
        return "redirect:/web/person/%s/parttime".formatted(personId);
    }

    @PreAuthorize(IS_OFFICE)
    @PostMapping("/quota/{year}")
    String saveQuota(@PathVariable("personId") Long personId, @PathVariable("year") int year,
                     @ModelAttribute("quotaForm") PartTimeQuotaForm quotaForm, Errors errors,
                     Model model, RedirectAttributes redirectAttributes) throws UnknownPersonException {

        final Person person = getPerson(personId);
        final BigDecimal overrideDays = quotaForm.getOverrideDays();
        if (overrideDays != null && (overrideDays.signum() < 0 || overrideDays.compareTo(MAX_OVERRIDE_DAYS) > 0
            || overrideDays.remainder(new BigDecimal("0.5")).signum() != 0)) {
            errors.rejectValue("overrideDays", "fork.parttime.quota.error.overrideDays");
        }
        if (errors.hasErrors()) {
            fillModel(model, person, personService.getSignedInUser(), year, new PartTimeForm(), quotaForm);
            return VIEW;
        }

        partTimeFreeDaysService.setOverride(person, year, Optional.ofNullable(overrideDays), Optional.ofNullable(quotaForm.getComment()));
        redirectAttributes.addFlashAttribute("quotaSaved", true);
        return "redirect:/web/person/%s/parttime?year=%s".formatted(personId, year);
    }

    private void validate(PartTimeForm form, Errors errors) {
        if (form.getValidFrom() == null) {
            errors.rejectValue("validFrom", "error.entry.mandatory");
        }
        final BigDecimal percentage = form.getPercentage();
        if (percentage == null) {
            errors.rejectValue("percentage", "error.entry.mandatory");
        } else if (percentage.signum() <= 0 || percentage.compareTo(HUNDRED) > 0 || percentage.scale() > 2) {
            errors.rejectValue("percentage", "fork.parttime.error.percentage");
        }
    }

    private void fillModel(Model model, Person person, Person signedInUser, int year, PartTimeForm partTimeForm, PartTimeQuotaForm quotaForm) {
        model.addAttribute("person", person);
        model.addAttribute("canEdit", permissionEvaluator.isAllowedToEdit(signedInUser));
        model.addAttribute("partTimes", partTimeService.getPartTimes(person).reversed());
        model.addAttribute("summary", partTimeFreeDaysService.getSummary(person, year));
        model.addAttribute("selectedYear", year);
        model.addAttribute("currentYear", LocalDate.now(clock).getYear());
        model.addAttribute("partTimeForm", partTimeForm);
        model.addAttribute("quotaForm", quotaForm);
    }

    private PartTimeQuotaForm quotaForm(Person person, int year) {
        final PartTimeFreeDaysSummary summary = partTimeFreeDaysService.getSummary(person, year);
        final PartTimeQuotaForm form = new PartTimeQuotaForm();
        form.setOverrideDays(summary.override().orElse(null));
        form.setComment(summary.comment().orElse(null));
        return form;
    }

    private Person getPerson(Long personId) throws UnknownPersonException {
        return personService.getPersonByID(personId).orElseThrow(() -> new UnknownPersonException(personId));
    }
}
