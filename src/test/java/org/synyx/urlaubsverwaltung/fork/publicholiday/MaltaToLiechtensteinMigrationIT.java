package org.synyx.urlaubsverwaltung.fork.publicholiday;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.settings.Settings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeWriteService;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.synyx.urlaubsverwaltung.workingtime.FederalState.GERMANY_BERLIN;
import static org.synyx.urlaubsverwaltung.workingtime.FederalState.LIECHTENSTEIN_BANK_HOLIDAYS;
import static org.synyx.urlaubsverwaltung.workingtime.FederalState.MALTA;

/**
 * Verifies the SQL of {@code fork-0001-migrate-malta-to-liechtenstein.xml}. The changeset already ran on the empty
 * database when this test starts, so the SQL is read from the changelog and executed again.
 */
@SpringBootTest
@Transactional
class MaltaToLiechtensteinMigrationIT extends SingleTenantTestContainersBase {

    private static final String CHANGELOG = "dbchangelogs/fork/fork-0001-migrate-malta-to-liechtenstein.xml";
    private static final String CHANGESET_ID = "fork-0001-migrate-malta-to-liechtenstein";

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PersonService personService;
    @Autowired
    private WorkingTimeService workingTimeService;
    @Autowired
    private WorkingTimeWriteService workingTimeWriteService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void ensureMaltaIsMigratedToLiechtensteinBankHolidays() throws Exception {
        final Settings settings = settingsService.getSettings();
        settings.getPublicHolidaysSettings().setFederalState(MALTA);
        settingsService.save(settings);

        final Person malta = personService.create("malta", "Malta", "Person", "malta@example.org");
        workingTimeWriteService.touch(List.of(1, 2, 3, 4, 5), LocalDate.of(2026, 1, 1), malta, MALTA);
        final Person berlin = personService.create("berlin", "Berlin", "Person", "berlin@example.org");
        workingTimeWriteService.touch(List.of(1, 2, 3, 4, 5), LocalDate.of(2026, 1, 1), berlin, GERMANY_BERLIN);

        migrate();

        assertThat(settingsService.getSettings().getPublicHolidaysSettings().getFederalState()).isEqualTo(LIECHTENSTEIN_BANK_HOLIDAYS);
        assertThat(workingTimeService.getFederalStateForPerson(malta, LocalDate.of(2026, 6, 1))).isEqualTo(LIECHTENSTEIN_BANK_HOLIDAYS);
        assertThat(workingTimeService.getFederalStateForPerson(berlin, LocalDate.of(2026, 6, 1))).isEqualTo(GERMANY_BERLIN);
    }

    private void migrate() throws Exception {
        entityManager.flush();
        changesetSql().forEach(jdbcTemplate::update);
        entityManager.clear();
    }

    private static List<String> changesetSql() throws Exception {
        try (InputStream changelog = new ClassPathResource(CHANGELOG).getInputStream()) {
            final NodeList changeSets = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(changelog)
                .getElementsByTagName("changeSet");
            for (int i = 0; i < changeSets.getLength(); i++) {
                final Element changeSet = (Element) changeSets.item(i);
                if (CHANGESET_ID.equals(changeSet.getAttribute("id"))) {
                    final NodeList sqls = changeSet.getElementsByTagName("sql");
                    final List<String> statements = new ArrayList<>();
                    for (int j = 0; j < sqls.getLength(); j++) {
                        statements.add(sqls.item(j).getTextContent());
                    }
                    return statements;
                }
            }
        }
        throw new IllegalStateException("changeSet id=%s not found in %s".formatted(CHANGESET_ID, CHANGELOG));
    }
}
