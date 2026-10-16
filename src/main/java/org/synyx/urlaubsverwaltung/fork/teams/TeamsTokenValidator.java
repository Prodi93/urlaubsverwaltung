package org.synyx.urlaubsverwaltung.fork.teams;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates the token Teams issues for the application ({@code authentication.getAuthToken()}): signed by Entra ID,
 * issued by the tenant of the oidc login, for this application (audience client id or application id uri).
 */
@Component
class TeamsTokenValidator {

    private final TeamsSsoSettings ssoSettings;
    private final Function<String, JwtDecoder> decoderFactory;
    private JwtDecoder decoder;

    @Autowired
    TeamsTokenValidator(TeamsSsoSettings ssoSettings) {
        this(ssoSettings, tenantId -> NimbusJwtDecoder.withJwkSetUri("https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys").build());
    }

    TeamsTokenValidator(TeamsSsoSettings ssoSettings, Function<String, JwtDecoder> decoderFactory) {
        this.ssoSettings = ssoSettings;
        this.decoderFactory = decoderFactory;
    }

    /**
     * @throws org.springframework.security.oauth2.jwt.JwtException if the token is not valid
     * @throws IllegalStateException if the single sign on is not available
     */
    Jwt validate(String token) {
        return decoder().decode(token);
    }

    private synchronized JwtDecoder decoder() {
        if (decoder == null) {
            final String tenantId = ssoSettings.tenantId().orElseThrow(() -> new IllegalStateException("teams sso needs the login with entra id"));
            final String clientId = ssoSettings.clientId().orElseThrow(() -> new IllegalStateException("teams sso needs the login with entra id"));
            final JwtDecoder jwtDecoder = decoderFactory.apply(tenantId);
            if (jwtDecoder instanceof NimbusJwtDecoder nimbusJwtDecoder) {
                nimbusJwtDecoder.setJwtValidator(validator(tenantId, clientId, ssoSettings.applicationIdUri().orElse(clientId)));
            }
            decoder = jwtDecoder;
        }
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String tenantId, String clientId, String applicationIdUri) {
        final Set<String> issuers = Set.of(
            "https://login.microsoftonline.com/" + tenantId + "/v2.0",
            "https://sts.windows.net/" + tenantId + "/"
        );
        final Set<String> audiences = Set.of(clientId, applicationIdUri);

        final OAuth2TokenValidator<Jwt> issuer = jwt -> issuers.contains(jwt.getClaimAsString("iss"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "unexpected issuer", null));
        final OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "unexpected audience", null));
        final OAuth2TokenValidator<Jwt> tenant = jwt -> tenantId.equalsIgnoreCase(jwt.getClaimAsString("tid"))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "unexpected tenant", null));

        return new DelegatingOAuth2TokenValidator<>(List.of(new JwtTimestampValidator(), issuer, audience, tenant));
    }
}
