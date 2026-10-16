package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * Renders the presence pages with the real templates.
 */
@SpringBootTest
class PresenceViewIT extends SingleTenantTestContainersBase {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PersonService personService;

    private Person person;

    @BeforeEach
    void setUp() {
        person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setId(1L);
        person.setPermissions(List.of(USER));
        when(personService.getSignedInUser()).thenReturn(person);
        when(personService.getActivePersons()).thenReturn(List.of(person));
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));
    }

    @Test
    void ensureOverviewIsRendered() throws Exception {
        perform(get("/web/presence").param("year", "2026").param("month", "10"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"presence-overview\"")))
            .andExpect(content().string(containsString("Marlene Muster")))
            .andExpect(content().string(containsString("basic-presence-link")));
    }

    @Test
    void ensureTodayIsRendered() throws Exception {
        perform(get("/web/presence/today"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"presence-today\"")));
    }

    @Test
    void ensurePlanningIsRendered() throws Exception {
        perform(get("/web/presence/persons/1").param("year", "2026").param("month", "10"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"presence-planning-form\"")))
            .andExpect(content().string(containsString("data-test-id=\"presence-pattern-form\"")));
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
            .perform(builder.with(oidcLogin().authorities(new SimpleGrantedAuthority("USER"))));
    }
}
