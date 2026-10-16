package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import org.slf4j.Logger;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Obtains and caches the access token used as SMTP password for XOAUTH2.
 */
class MailAccessTokenProvider {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final ClientRegistration clientRegistration;
    private final OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient;
    private final Duration refreshSkew;
    private final Clock clock;

    private OAuth2AccessToken accessToken;

    MailAccessTokenProvider(ClientRegistration clientRegistration,
                            OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient,
                            Duration refreshSkew, Clock clock) {
        this.clientRegistration = clientRegistration;
        this.tokenResponseClient = tokenResponseClient;
        this.refreshSkew = refreshSkew;
        this.clock = clock;
    }

    synchronized String getToken() {
        if (accessToken == null || isExpired(accessToken)) {
            LOG.debug("Requesting new access token for sending mails");
            accessToken = tokenResponseClient.getTokenResponse(new OAuth2ClientCredentialsGrantRequest(clientRegistration)).getAccessToken();
        }
        return accessToken.getTokenValue();
    }

    synchronized void invalidate() {
        accessToken = null;
    }

    private boolean isExpired(OAuth2AccessToken token) {
        final Instant expiresAt = token.getExpiresAt();
        return expiresAt == null || !Instant.now(clock).plus(refreshSkew).isBefore(expiresAt);
    }
}
