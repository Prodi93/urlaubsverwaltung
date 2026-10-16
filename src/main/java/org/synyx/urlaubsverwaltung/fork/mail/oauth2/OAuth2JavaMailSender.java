package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import org.slf4j.Logger;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * {@link JavaMailSenderImpl} that authenticates with an OAuth2 access token (XOAUTH2) instead of a password.
 * The connection is retried once with a new token if the authentication fails, e.g. because the token was revoked.
 */
class OAuth2JavaMailSender extends JavaMailSenderImpl {

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final MailAccessTokenProvider tokenProvider;

    OAuth2JavaMailSender(MailAccessTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected Transport connectTransport() throws MessagingException {
        try {
            return connectWithToken();
        } catch (AuthenticationFailedException e) {
            LOG.info("SMTP authentication with OAuth2 access token failed, retrying with a new token: {}", e.getMessage());
            tokenProvider.invalidate();
            return connectWithToken();
        }
    }

    private Transport connectWithToken() throws MessagingException {
        final String token = obtainToken();
        final Transport transport = getTransport(getSession());
        transport.connect(getHost(), getPort(), getUsername(), token);
        return transport;
    }

    private String obtainToken() throws AuthenticationFailedException {
        try {
            return tokenProvider.getToken();
        } catch (RuntimeException e) {
            throw new AuthenticationFailedException("Could not obtain OAuth2 access token for sending mails: " + e.getMessage(), e);
        }
    }
}
