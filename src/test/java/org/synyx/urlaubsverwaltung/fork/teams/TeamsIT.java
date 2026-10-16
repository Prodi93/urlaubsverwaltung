package org.synyx.urlaubsverwaltung.fork.teams;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

@SpringBootTest(properties = "uv.fork.teams.enabled=true")
class TeamsIT extends SingleTenantTestContainersBase {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PersonService personService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("teamsEmbeddingFilter", org.springframework.boot.web.servlet.FilterRegistrationBean.class).getFilter())
            .apply(springSecurity())
            .build();
    }

    @Test
    void ensureTabIsAvailableWithoutLoginAndEmbeddableInTeams() throws Exception {
        mockMvc().perform(get("/fork/teams/tab").param("target", "/web/presence"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-Frame-Options"))
            .andExpect(header().string("Content-Security-Policy", containsString("https://teams.microsoft.com")))
            .andExpect(content().string(containsString("data-teams-page=\"tab\"")))
            .andExpect(content().string(containsString("data-target=\"/web/presence\"")));
    }

    @Test
    void ensureConfigIsAvailableWithoutLogin() throws Exception {
        mockMvc().perform(get("/fork/teams/config"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-teams-page=\"config\"")));
    }

    @Test
    void ensureLoginInTeamsFrameIsRedirectedToTab() throws Exception {
        mockMvc().perform(get("/web/presence").header("Sec-Fetch-Dest", "iframe"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fork/teams/tab?target=/web/presence"));
    }

    @Test
    void ensureAuthEndRequiresLogin() throws Exception {
        mockMvc().perform(get("/fork/teams/auth-end"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void ensureOfficeGetsSettingsAndAppPackage() throws Exception {
        final Person office = new Person("office", "Office", "Olaf", "office@example.org");
        office.setId(1L);
        office.setPermissions(List.of(USER, OFFICE));
        when(personService.getSignedInUser()).thenReturn(office);

        mockMvc().perform(get("/web/settings/teams").with(oidcLogin().authorities(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("OFFICE"))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-test-id=\"teams-app-package\"")));

        final byte[] zip = mockMvc().perform(get("/web/settings/teams/app-package")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("OFFICE"))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andReturn().getResponse().getContentAsByteArray();

        final List<String> entries = new ArrayList<>();
        String manifest = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().equals("manifest.json")) {
                    manifest = new String(zipInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(entries).containsExactly("manifest.json", "color.png", "outline.png");
        assertThat(manifest)
            .contains("\"manifestVersion\" : \"1.19\"")
            .contains("https://localhost:8080/fork/teams/tab?target=/web/presence")
            .contains("\"validDomains\" : [ \"localhost:8080\" ]");
    }
}
