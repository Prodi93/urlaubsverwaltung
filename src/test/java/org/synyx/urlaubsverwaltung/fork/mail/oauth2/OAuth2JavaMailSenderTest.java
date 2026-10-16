package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2JavaMailSenderTest {

    @Mock
    private MailAccessTokenProvider tokenProvider;
    @Mock
    private Transport transport;

    private OAuth2JavaMailSender sut;

    @BeforeEach
    void setUp() {
        sut = new OAuth2JavaMailSender(tokenProvider) {
            @Override
            protected Transport getTransport(Session session) {
                return transport;
            }
        };
        sut.setHost("smtp.office365.com");
        sut.setPort(587);
        sut.setUsername("urlaub@example.org");
    }

    @Test
    void ensureConnectsWithAccessTokenAsPassword() throws Exception {
        when(tokenProvider.getToken()).thenReturn("token");

        final Transport connected = sut.connectTransport();

        assertThat(connected).isSameAs(transport);
        verify(transport).connect("smtp.office365.com", 587, "urlaub@example.org", "token");
    }

    @Test
    void ensureRetriesOnceWithNewTokenIfAuthenticationFails() throws Exception {
        when(tokenProvider.getToken()).thenReturn("expired", "fresh");
        doThrow(new AuthenticationFailedException("535 5.7.3 Authentication unsuccessful"))
            .when(transport).connect("smtp.office365.com", 587, "urlaub@example.org", "expired");

        sut.connectTransport();

        verify(tokenProvider).invalidate();
        verify(transport).connect("smtp.office365.com", 587, "urlaub@example.org", "fresh");
    }

    @Test
    void ensureTokenErrorIsReportedAsAuthenticationFailure() {
        when(tokenProvider.getToken()).thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_client")));

        assertThatThrownBy(() -> sut.connectTransport())
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessageContaining("Could not obtain OAuth2 access token");
    }
}
