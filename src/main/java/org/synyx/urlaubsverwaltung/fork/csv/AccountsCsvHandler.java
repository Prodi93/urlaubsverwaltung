package org.synyx.urlaubsverwaltung.fork.csv;

import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.account.Account;
import org.synyx.urlaubsverwaltung.account.AccountInteractionService;
import org.synyx.urlaubsverwaltung.account.AccountService;
import org.synyx.urlaubsverwaltung.person.Person;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.EMAIL;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvPersonResolver.USERNAME;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.CREATE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UNCHANGED;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UPDATE;

/**
 * Vacation accounts per person and year. An empty {@code do_remaining_vacation_days_expire} uses the global setting.
 */
@Component
class AccountsCsvHandler implements CsvDatasetHandler {

    private static final String YEAR = "year";
    private static final String VALID_FROM = "valid_from";
    private static final String VALID_TO = "valid_to";
    private static final String ANNUAL = "annual_vacation_days";
    private static final String ACTUAL = "actual_vacation_days";
    private static final String REMAINING = "remaining_vacation_days";
    private static final String REMAINING_NOT_EXPIRING = "remaining_vacation_days_not_expiring";
    private static final String EXPIRE = "do_remaining_vacation_days_expire";
    private static final String EXPIRY_DATE = "expiry_date";
    private static final String COMMENT = "comment";

    private final AccountService accountService;
    private final AccountInteractionService accountInteractionService;
    private final CsvPersonResolver personResolver;

    AccountsCsvHandler(AccountService accountService, AccountInteractionService accountInteractionService, CsvPersonResolver personResolver) {
        this.accountService = accountService;
        this.accountInteractionService = accountInteractionService;
        this.personResolver = personResolver;
    }

    @Override
    public CsvDataset dataset() {
        return CsvDataset.ACCOUNTS;
    }

    @Override
    public List<String> header() {
        return List.of(USERNAME, EMAIL, YEAR, VALID_FROM, VALID_TO, ANNUAL, ACTUAL, REMAINING, REMAINING_NOT_EXPIRING, EXPIRE, EXPIRY_DATE, COMMENT);
    }

    @Override
    public List<String> requiredColumns() {
        return List.of(YEAR, ANNUAL, ACTUAL, REMAINING);
    }

    @Override
    public List<List<String>> export(List<Person> persons, DateRange dateRange, Locale locale) {
        final List<List<String>> rows = new ArrayList<>();
        for (int year = dateRange.startDate().getYear(); year <= dateRange.endDate().getYear(); year++) {
            final List<Account> accounts = new ArrayList<>(accountService.getHolidaysAccount(year, persons));
            accounts.sort(Comparator.comparing(account -> account.getPerson().getNiceName()));
            for (Account account : accounts) {
                final Person person = account.getPerson();
                rows.add(List.of(
                    person.getUsername(), person.getEmail(), String.valueOf(account.getYear()),
                    CsvValues.format(account.getValidFrom()), CsvValues.format(account.getValidTo()),
                    CsvValues.format(account.getAnnualVacationDays()), CsvValues.format(account.getActualVacationDays()),
                    CsvValues.format(account.getRemainingVacationDays()), CsvValues.format(account.getRemainingVacationDaysNotExpiring()),
                    CsvValues.format(account.isDoRemainingVacationDaysExpireLocally()), CsvValues.format(account.getExpiryDateLocally()),
                    Objects.requireNonNullElse(account.getComment(), "")
                ));
            }
        }
        return rows;
    }

    @Override
    public CsvRowResult importRow(CsvTable.Row row, Person importer, boolean execute) {
        final String subject = CsvPersonResolver.subject(row);
        try {
            final Optional<Person> maybePerson = personResolver.resolve(row);
            if (maybePerson.isEmpty()) {
                return CsvRowResult.error(row, subject, "fork.csv.error.person.unknown");
            }
            final Person person = maybePerson.get();
            for (String column : requiredColumns()) {
                if (!row.has(column)) {
                    return CsvRowResult.error(row, subject, "fork.csv.error.mandatory", column);
                }
            }

            final int year = Integer.parseInt(row.get(YEAR));
            final LocalDate validFrom = row.has(VALID_FROM) ? CsvValues.parseDate(row.get(VALID_FROM)) : LocalDate.of(year, 1, 1);
            final LocalDate validTo = row.has(VALID_TO) ? CsvValues.parseDate(row.get(VALID_TO)) : LocalDate.of(year, 12, 31);
            if (validFrom.getYear() != year || validTo.getYear() != year || validFrom.isAfter(validTo)) {
                return CsvRowResult.error(row, subject, "fork.csv.error.account.period");
            }
            final BigDecimal annual = CsvValues.parseDecimal(row.get(ANNUAL));
            final BigDecimal actual = CsvValues.parseDecimal(row.get(ACTUAL));
            final BigDecimal remaining = CsvValues.parseDecimal(row.get(REMAINING));
            final BigDecimal remainingNotExpiring = row.has(REMAINING_NOT_EXPIRING) ? CsvValues.parseDecimal(row.get(REMAINING_NOT_EXPIRING)) : BigDecimal.ZERO;
            final Boolean expire = row.has(EXPIRE) ? CsvValues.parseBoolean(row.get(EXPIRE)) : null;
            final LocalDate expiryDate = row.has(EXPIRY_DATE) ? CsvValues.parseDate(row.get(EXPIRY_DATE)) : null;
            final String comment = row.get(COMMENT);
            if (annual.signum() < 0 || actual.signum() < 0 || remaining.signum() < 0 || remainingNotExpiring.signum() < 0) {
                return CsvRowResult.error(row, subject, "fork.csv.error.number");
            }

            final Optional<Account> existing = accountService.getHolidaysAccount(year, person);
            final CsvRowResult.Action action = existing
                .map(account -> isUnchanged(account, validFrom, validTo, annual, actual, remaining, remainingNotExpiring, expire, expiryDate, comment) ? UNCHANGED : UPDATE)
                .orElse(CREATE);

            if (execute && action != UNCHANGED) {
                accountInteractionService.updateOrCreateHolidaysAccount(person, validFrom, validTo, expire, expiryDate,
                    annual, actual, remaining, remainingNotExpiring, comment);
            }
            return CsvRowResult.of(row, subject, action);
        } catch (NumberFormatException e) {
            return CsvRowResult.error(row, subject, "fork.csv.error.number");
        } catch (IllegalArgumentException e) {
            return CsvRowResult.error(row, subject, e.getMessage());
        }
    }

    private static boolean isUnchanged(Account account, LocalDate validFrom, LocalDate validTo, BigDecimal annual, BigDecimal actual,
                                       BigDecimal remaining, BigDecimal remainingNotExpiring, Boolean expire, LocalDate expiryDate, String comment) {
        return account.getValidFrom().equals(validFrom)
            && account.getValidTo().equals(validTo)
            && equal(account.getAnnualVacationDays(), annual)
            && equal(account.getActualVacationDays(), actual)
            && equal(account.getRemainingVacationDays(), remaining)
            && equal(account.getRemainingVacationDaysNotExpiring(), remainingNotExpiring)
            && Objects.equals(account.isDoRemainingVacationDaysExpireLocally(), expire)
            && Objects.equals(account.getExpiryDateLocally(), expiryDate)
            && Objects.equals(Objects.requireNonNullElse(account.getComment(), ""), comment);
    }

    private static boolean equal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null || b.signum() == 0 : b != null && a.compareTo(b) == 0;
    }
}
