package org.synyx.urlaubsverwaltung.fork.teams;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings of the Teams single sign on, derived from the oidc login with Microsoft Entra ID
 * ({@code spring.security.oauth2.client.registration.default}).
 */
@Component
class TeamsSsoSettings {

    static final String REGISTRATION_ID = "default";
    private static final Pattern GUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern ENTRA_ISSUER = Pattern.compile("^https://login\\.microsoftonline\\.com/([0-9a-fA-F-]{36})(/v2\\.0)?/?$");

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final TeamsAppManifestService manifestService;

    TeamsSsoSettings(ClientRegistrationRepository clientRegistrationRepository, TeamsAppManifestService manifestService) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.manifestService = manifestService;
    }

    /**
     * @return the client id of the Entra app registration, empty if the login does not use Entra ID
     */
    Optional<String> clientId() {
        return registration()
            .map(ClientRegistration::getClientId)
            .filter(clientId -> GUID.matcher(clientId).matches());
    }

    /**
     * @return the tenant of the Entra issuer, e.g. https://login.microsoftonline.com/{tenant}/v2.0
     */
    Optional<String> tenantId() {
        return registration()
            .map(registration -> registration.getProviderDetails().getIssuerUri())
            .map(ENTRA_ISSUER::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1));
    }

    /**
     * @return the application id uri, which has to be set in "Expose an API" of the app registration
     */
    Optional<String> applicationIdUri() {
        return clientId().map(clientId -> "api://" + URI.create(manifestService.applicationUrl()).getAuthority() + "/" + clientId);
    }

    boolean isAvailable() {
        return clientId().isPresent() && tenantId().isPresent();
    }

    private Optional<ClientRegistration> registration() {
        return Optional.ofNullable(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID));
    }
}
