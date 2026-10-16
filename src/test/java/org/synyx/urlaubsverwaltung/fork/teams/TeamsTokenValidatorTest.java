package org.synyx.urlaubsverwaltung.fork.teams;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsTokenValidatorTest {

    private static final String TENANT = "11111111-2222-3333-4444-555555555555";
    private static final String CLIENT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String APPLICATION_ID_URI = "api://urlaub.example.org/" + CLIENT_ID;

    private final OAuth2TokenValidator<Jwt> sut = TeamsTokenValidator.validator(TENANT, CLIENT_ID, APPLICATION_ID_URI);

    @Test
    void ensureAcceptsV1AndV2TokensOfTheTenantForTheApplication() {
        assertThat(sut.validate(jwt("https://sts.windows.net/" + TENANT + "/", APPLICATION_ID_URI, TENANT)).hasErrors()).isFalse();
        assertThat(sut.validate(jwt("https://login.microsoftonline.com/" + TENANT + "/v2.0", CLIENT_ID, TENANT)).hasErrors()).isFalse();
    }

    @Test
    void ensureRejectsOtherIssuersAudiencesAndTenants() {
        assertThat(sut.validate(jwt("https://login.microsoftonline.com/other/v2.0", CLIENT_ID, TENANT)).hasErrors()).isTrue();
        assertThat(sut.validate(jwt("https://login.microsoftonline.com/" + TENANT + "/v2.0", "api://other", TENANT)).hasErrors()).isTrue();
        assertThat(sut.validate(jwt("https://login.microsoftonline.com/" + TENANT + "/v2.0", CLIENT_ID, "other")).hasErrors()).isTrue();
    }

    @Test
    void ensureRejectsExpiredTokens() {
        final Jwt expired = Jwt.withTokenValue("token").header("alg", "RS256")
            .issuer("https://login.microsoftonline.com/" + TENANT + "/v2.0").audience(List.of(CLIENT_ID)).claim("tid", TENANT)
            .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build();
        assertThat(sut.validate(expired).hasErrors()).isTrue();
    }

    private static Jwt jwt(String issuer, String audience, String tenant) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
            .issuer(issuer).audience(List.of(audience)).claim("tid", tenant).subject("subject")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
