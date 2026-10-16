package org.synyx.urlaubsverwaltung.fork.parttime;

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
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * Renders the part time page with the real templates.
 */
@SpringBootTest
class PartTimeViewIT extends SingleTenantTestContainersBase {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PersonService personService;

    private Person person;

    @BeforeEach
    void setUp() {
        person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setId(1L);
        person.setPermissions(List.of(USER, OFFICE));
        when(personService.getSignedInUser()).thenReturn(person);
        when(personService.getActivePersons()).thenReturn(List.of(person));
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));
    }

    @Test
    void ensurePartTimePageIsRendered() throws Exception {
        perform(get("/web/person/1/parttime").param("year", "2026")
            .flashAttr("partTimeSaved", true).flashAttr("partTimeDeleted", true).flashAttr("quotaSaved", true))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"parttime-summary\"")))
            .andExpect(content().string(containsString("data-test-id=\"parttime-form\"")))
            .andExpect(content().string(containsString("data-test-id=\"parttime-quota-form\"")));
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
            .perform(builder.with(oidcLogin().authorities(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("OFFICE"))));
    }
}
