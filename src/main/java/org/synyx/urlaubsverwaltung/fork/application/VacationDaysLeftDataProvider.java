package org.synyx.urlaubsverwaltung.fork.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.synyx.urlaubsverwaltung.account.Account;
import org.synyx.urlaubsverwaltung.account.AccountService;
import org.synyx.urlaubsverwaltung.account.HolidayAccountVacationDays;
import org.synyx.urlaubsverwaltung.account.VacationDaysService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.web.DataProviderInterface;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * Adds the vacation days left of the current year to the application for leave form, like on the person overview.
 */
@Component
class VacationDaysLeftDataProvider implements DataProviderInterface {

    static final String MODEL_ATTRIBUTE = "forkVacationDaysLeft";

    private final AccountService accountService;
    private final VacationDaysService vacationDaysService;
    private final Clock clock;

    VacationDaysLeftDataProvider(AccountService accountService, VacationDaysService vacationDaysService, Clock clock) {
        this.accountService = accountService;
        this.vacationDaysService = vacationDaysService;
        this.clock = clock;
    }

    record VacationDaysLeftDto(int year, BigDecimal days) {
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) {
        if (!addDataIf(modelAndView) || !(modelAndView.getModel().get("person") instanceof Person person)) {
            return;
        }
        getVacationDaysLeft(person).ifPresent(dto -> modelAndView.addObject(MODEL_ATTRIBUTE, dto));
    }

    Optional<VacationDaysLeftDto> getVacationDaysLeft(Person person) {
        final LocalDate today = LocalDate.now(clock);
        final int year = today.getYear();
        return accountService.getHolidaysAccount(year, person).flatMap(account -> {
            final List<Account> nextYear = accountService.getHolidaysAccount(year + 1, person).stream().toList();
            return Optional.ofNullable(vacationDaysService.getVacationDaysLeft(List.of(account), Year.of(year), nextYear).get(account))
                .map(HolidayAccountVacationDays::vacationDaysYear)
                .map(left -> new VacationDaysLeftDto(year, left.getLeftVacationDays(today, account.doRemainingVacationDaysExpire(), account.getExpiryDate())));
        });
    }
}
