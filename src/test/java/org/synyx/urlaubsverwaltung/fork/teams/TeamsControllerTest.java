package org.synyx.urlaubsverwaltung.fork.teams;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsControllerTest {

    @ParameterizedTest
    @CsvSource({
        "/web/overview, /web/overview",
        "/web/presence?year=2026, /web/presence?year=2026",
        "https://evil.example.org, /web/presence",
        "//evil.example.org/web, /web/presence",
        "/web//evil.example.org, /web/presence",
        "/api/persons, /web/presence",
    })
    void ensureOnlyPagesOfTheApplicationAreTargets(String target, String expected) {
        assertThat(TeamsController.safeTarget(target)).isEqualTo(expected);
    }
}
