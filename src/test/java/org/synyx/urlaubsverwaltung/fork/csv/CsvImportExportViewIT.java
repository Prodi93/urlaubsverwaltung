package org.synyx.urlaubsverwaltung.fork.csv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * Renders the import / export page with the real templates.
 */
@SpringBootTest
class CsvImportExportViewIT extends SingleTenantTestContainersBase {

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
    }

    @Test
    void ensureUserSeesExportButNoImport() throws Exception {
        perform(get("/web/import-export"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"csv-export\"")))
            .andExpect(content().string(not(containsString("data-test-id=\"csv-import\""))));
    }

    @Test
    void ensureExportReturnsCsvFile() throws Exception {
        perform(get("/web/import-export/export").param("dataset", "PRESENCES").param("scope", "SELF")
            .param("from", "2026-01-01").param("to", "2026-12-31"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("anwesenheiten_2026-01-01_2026-12-31.csv")))
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(containsString("username;email;date;type")));
    }

    @Test
    void ensureUserIsNotAllowedToImport() throws Exception {
        final MockMultipartFile file = new MockMultipartFile("file", "anwesenheiten.csv", "text/csv", "username;date;type\n".getBytes(UTF_8));
        perform(multipart("/web/import-export/import").file(file).param("dataset", "PRESENCES").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void ensureOfficeSeesPreviewOfImport() throws Exception {
        person.setPermissions(List.of(USER, OFFICE));
        when(personService.getPersonByUsername("muster")).thenReturn(Optional.of(person));

        final MockMultipartFile file = new MockMultipartFile("file", "anwesenheiten.csv", "text/csv",
            "username;date;type\nmuster;2026-10-12;OFFICE\nmuster;2026-13-01;OFFICE\n".getBytes(UTF_8));

        perform(multipart("/web/import-export/import").file(file).param("dataset", "PRESENCES").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"csv-import-result\"")))
            .andExpect(content().string(containsString("data-test-id=\"csv-import-execute\"")))
            .andExpect(content().string(containsString("Ungültiges Datum")));
    }

    @Test
    void ensureOfficeSeesResultOfExecutedImport() throws Exception {
        person.setPermissions(List.of(USER, OFFICE));
        when(personService.getPersonByUsername("muster")).thenReturn(Optional.of(person));

        // removing a presence that does not exist changes nothing
        perform(post("/web/import-export/import/execute").param("dataset", "PRESENCES")
            .param("content", "username;date;type\nmuster;2026-10-12;\n").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"feedback-box\"")))
            .andExpect(content().string(containsString("Import abgeschlossen")));
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> builder) throws Exception {
        final GrantedAuthority[] authorities = person.getPermissions().stream()
            .map(role -> new SimpleGrantedAuthority(role.name()))
            .toArray(GrantedAuthority[]::new);
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
            .perform(builder.locale(Locale.GERMAN).with(oidcLogin().authorities(authorities)));
    }
}
