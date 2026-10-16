package org.synyx.urlaubsverwaltung.fork.teams;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;

class TeamsSsoSettingsTest {

    @Test
    void ensureSettingsOfEntraLogin() {
        final TeamsSsoSettings sut = settings("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            "https://login.microsoftonline.com/11111111-2222-3333-4444-555555555555/v2.0");

        assertThat(sut.isAvailable()).isTrue();
        assertThat(sut.tenantId()).hasValue("11111111-2222-3333-4444-555555555555");
        assertThat(sut.applicationIdUri()).hasValue("api://urlaub.example.org/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @Test
    void ensureNotAvailableWithOtherLogin() {
        final TeamsSsoSettings sut = settings("urlaubsverwaltung", "https://keycloak.example.org/realms/uv");

        assertThat(sut.isAvailable()).isFalse();
        assertThat(sut.clientId()).isEmpty();
        assertThat(sut.tenantId()).isEmpty();
    }

    private static TeamsSsoSettings settings(String clientId, String issuerUri) {
        final ClientRegistration registration = ClientRegistration.withRegistrationId("default")
            .clientId(clientId)
            .authorizationGrantType(AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri(issuerUri + "/authorize")
            .tokenUri(issuerUri + "/token")
            .issuerUri(issuerUri)
            .build();
        final ClientRegistrationRepository repository = mock(ClientRegistrationRepository.class);
        when(repository.findByRegistrationId("default")).thenReturn(registration);
        final TeamsAppManifestService manifestService = mock(TeamsAppManifestService.class);
        when(manifestService.applicationUrl()).thenReturn("https://urlaub.example.org");
        return new TeamsSsoSettings(repository, manifestService);
    }
}
