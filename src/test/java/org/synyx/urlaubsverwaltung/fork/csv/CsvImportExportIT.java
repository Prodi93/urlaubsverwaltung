package org.synyx.urlaubsverwaltung.fork.csv;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.account.AccountInteractionService;
import org.synyx.urlaubsverwaltung.application.application.ApplicationService;
import org.synyx.urlaubsverwaltung.fork.parttime.PartTimeService;
import org.synyx.urlaubsverwaltung.fork.presence.PresenceService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeWriteService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.CREATE;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.ERROR;
import static org.synyx.urlaubsverwaltung.fork.csv.CsvRowResult.Action.UNCHANGED;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * Not transactional on purpose: the import must commit or roll back on its own.
 * Therefore all created persons are deleted after each test, the database is shared with the other tests.
 */
@SpringBootTest
class CsvImportExportIT extends SingleTenantTestContainersBase {

    private static final DateRange YEAR_2026 = new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    @Autowired
    private CsvImportExportService sut;
    @Autowired
    private PersonService personService;
    @Autowired
    private PresenceService presenceService;
    @Autowired
    private PartTimeService partTimeService;
    @Autowired
    private WorkingTimeWriteService workingTimeWriteService;
    @Autowired
    private AccountInteractionService accountInteractionService;
    @Autowired
    private ApplicationService applicationService;

    private final List<Person> createdPersons = new ArrayList<>();
    private Person office;

    @AfterEach
    void deleteCreatedPersons() {
        createdPersons.forEach(person -> personService.delete(person.getIdAsPersonId(), office.getIdAsPersonId()));
        personService.delete(office.getIdAsPersonId(), office.getIdAsPersonId());
        createdPersons.clear();
    }

    @Test
    void ensureExportedFilesCanBeImportedWithoutChanges() {
        final Person office = office();
        final Person person = person();
        workingTimeWriteService.touch(List.of(1, 2, 3, 4, 5), LocalDate.of(2026, 1, 1), person, null);
        partTimeService.save(person, LocalDate.of(2026, 1, 1), new BigDecimal("80"));
        presenceService.setPresence(person, LocalDate.of(2026, 10, 12), OFFICE);
        presenceService.setPresence(person, LocalDate.of(2026, 10, 13), HOME_OFFICE);
        accountInteractionService.updateOrCreateHolidaysAccount(person, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
            new BigDecimal("25"), new BigDecimal("25"), new BigDecimal("3.5"), BigDecimal.ZERO, "Kommentar; mit \"Zeichen\"");
        sut.execute(CsvDataset.APPLICATIONS, csv("username;vacation_type;start_date;end_date",
            person.getUsername() + ";Erholungsurlaub;2026-08-03;2026-08-07"), office, false);

        for (CsvDataset dataset : CsvDataset.values()) {
            final String exported = new String(sut.export(dataset, List.of(person), YEAR_2026, Locale.GERMAN), UTF_8);
            assertThat(exported.lines().count()).as(dataset.name()).isGreaterThan(1);

            final CsvImportResult result = sut.execute(dataset, exported, office, false);

            assertThat(result.rows()).as(dataset + ":\n" + exported).isNotEmpty().allSatisfy(row -> assertThat(row.action()).isEqualTo(UNCHANGED));
        }
    }

    @Test
    void ensureApplicationsAreCreatedWithoutWorkflow() {
        final Person office = office();
        final Person person = person();
        workingTimeWriteService.touch(List.of(1, 2, 3, 4, 5), LocalDate.of(2026, 1, 1), person, null);

        final String file = csv("username;vacation_type;start_date;end_date;day_length;status",
            person.getUsername() + ";Erholungsurlaub;03.08.2026;07.08.2026;FULL;ALLOWED",
            person.getUsername() + ";Erholungsurlaub;2026-09-01;2026-09-01;MORNING;");

        final CsvImportResult preview = sut.preview(CsvDataset.APPLICATIONS, file, office);
        assertThat(preview.rows()).extracting(CsvRowResult::action).containsExactly(CREATE, CREATE);
        assertThat(applicationService.getApplicationsForACertainPeriodAndPerson(YEAR_2026.startDate(), YEAR_2026.endDate(), person)).isEmpty();

        final CsvImportResult result = sut.execute(CsvDataset.APPLICATIONS, file, office, false);
        assertThat(result.created()).isEqualTo(2);
        assertThat(applicationService.getApplicationsForACertainPeriodAndPerson(YEAR_2026.startDate(), YEAR_2026.endDate(), person)).hasSize(2);

        assertThat(sut.preview(CsvDataset.APPLICATIONS, file, office).rows()).extracting(CsvRowResult::action).containsExactly(UNCHANGED, UNCHANGED);
    }

    @Test
    void ensureImportWithErrorsIsRolledBack() {
        final Person office = office();
        final Person person = person();

        final String file = csv("username;date;type",
            person.getUsername() + ";2026-10-12;OFFICE",
            "unknown-" + UUID.randomUUID() + ";2026-10-12;OFFICE");

        assertThatThrownBy(() -> sut.execute(CsvDataset.PRESENCES, file, office, false))
            .isInstanceOf(CsvImportAbortedException.class)
            .satisfies(e -> assertThat(((CsvImportAbortedException) e).getResult().rows()).extracting(CsvRowResult::action).containsExactly(CREATE, ERROR));
        assertThat(presenceService.getPresences(person, YEAR_2026)).isEmpty();

        final CsvImportResult skipped = sut.execute(CsvDataset.PRESENCES, file, office, true);
        assertThat(skipped.created()).isOne();
        assertThat(presenceService.getPresences(person, YEAR_2026)).hasSize(1);
    }

    @Test
    void ensurePersonsAreCreatedWithWorkingTimeAndPartTime() {
        final Person office = office();
        final String username = "new-" + UUID.randomUUID();

        final CsvImportResult result = sut.execute(CsvDataset.PERSONS_WORKING_TIMES, csv(
            "username;email;first_name;last_name;valid_from;working_days;part_time_percentage",
            username + ";" + username + "@example.org;Neu;Person;2026-01-01;MO,DI,MI,DO,FR;80"), office, false);

        assertThat(result.created()).isOne();
        final Person person = personService.getPersonByUsername(username).orElseThrow();
        createdPersons.add(person);
        assertThat(partTimeService.getPercentage(person, LocalDate.of(2026, 6, 1))).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("80"));
    }

    @Test
    void ensureMissingColumnsAreRejected() {
        assertThatThrownBy(() -> sut.preview(CsvDataset.ACCOUNTS, "username;year\nmuster;2026", office()))
            .isInstanceOf(CsvFormatException.class)
            .extracting("messageKey").isEqualTo("fork.csv.error.columns");
    }

    private Person person() {
        final String username = "csv-" + UUID.randomUUID();
        final Person person = personService.create(username, "Marlene", "Muster", username + "@example.org");
        createdPersons.add(person);
        return person;
    }

    private Person office() {
        final String username = "office-" + UUID.randomUUID();
        office = personService.create(username, "Olaf", "Office", username + "@example.org", List.of(),
            List.of(USER, org.synyx.urlaubsverwaltung.person.Role.OFFICE));
        return office;
    }

    private static String csv(String... lines) {
        return String.join("\n", lines) + "\n";
    }
}
