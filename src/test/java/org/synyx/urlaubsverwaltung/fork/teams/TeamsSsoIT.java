package org.synyx.urlaubsverwaltung.fork.teams;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * The Teams single sign on creates a session, with which the pages are available like after the oidc login.
 */
@SpringBootTest(properties = "uv.fork.teams.enabled=true")
class TeamsSsoIT extends SingleTenantTestContainersBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PersonService personService;

    @MockitoBean
    private TeamsTokenValidator tokenValidator;

    @Test
    void ensureSignInWithTeamsTokenCreatesASession() throws Exception {
        final String username = "teams-" + UUID.randomUUID();
        final Person person = personService.create(username, "Tessa", "Teams", username + "@example.org", List.of(), List.of(USER));
        when(tokenValidator.validate("teams-token")).thenReturn(jwt(username, "someone@example.org"));

        final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        final MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/fork/teams/sso").header("Authorization", "Bearer teams-token"))
            .andExpect(status().isNoContent())
            .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/web/presence/today").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(person.getNiceName())));

        personService.delete(person.getIdAsPersonId(), person.getIdAsPersonId());
    }

    @Test
    void ensureSignInByEmailIfSubjectIsUnknown() throws Exception {
        final String username = "teams-mail-" + UUID.randomUUID();
        final Person person = personService.create(username, "Mia", "Mail", username + "@example.org", List.of(), List.of(USER));
        when(tokenValidator.validate("teams-token")).thenReturn(jwt("pairwise-subject", person.getEmail()));

        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
            .perform(post("/fork/teams/sso").header("Authorization", "Bearer teams-token"))
            .andExpect(status().isNoContent());

        personService.delete(person.getIdAsPersonId(), person.getIdAsPersonId());
    }

    @Test
    void ensureRejectsInvalidTokensAndUnknownPersons() throws Exception {
        final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(tokenValidator.validate("invalid")).thenThrow(new BadJwtException("bad"));
        when(tokenValidator.validate("unknown")).thenReturn(jwt("unknown-subject", "unknown@example.org"));

        mockMvc.perform(post("/fork/teams/sso")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/fork/teams/sso").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_token"));
        mockMvc.perform(post("/fork/teams/sso").header("Authorization", "Bearer unknown"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("unknown_person"));
    }

    private static Jwt jwt(String subject, String email) {
        return Jwt.withTokenValue("teams-token").header("alg", "RS256")
            .issuer("https://login.microsoftonline.com/tenant/v2.0").subject(subject).claim("preferred_username", email)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
