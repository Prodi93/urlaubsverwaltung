package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;

@ExtendWith(MockitoExtension.class)
class MailAccessTokenProviderTest {

    @Mock
    private OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient;

    private MutableClock clock;
    private MailAccessTokenProvider sut;

    @BeforeEach
    void setUp() {
        final ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("mail")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(CLIENT_CREDENTIALS)
            .tokenUri("https://login.example.org/token")
            .scope("https://outlook.office365.com/.default")
            .build();

        clock = new MutableClock(Instant.now());
        sut = new MailAccessTokenProvider(clientRegistration, tokenResponseClient, Duration.ofMinutes(5), clock);
    }

    @Test
    void ensureTokenIsCachedWhileValid() {
        when(tokenResponseClient.getTokenResponse(any())).thenReturn(tokenResponse("token-1", 3600));

        assertThat(sut.getToken()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(30));
        assertThat(sut.getToken()).isEqualTo("token-1");

        verify(tokenResponseClient, times(1)).getTokenResponse(any());
    }

    @Test
    void ensureTokenIsRenewedShortlyBeforeExpiry() {
        when(tokenResponseClient.getTokenResponse(any()))
            .thenReturn(tokenResponse("token-1", 3600))
            .thenReturn(tokenResponse("token-2", 3600));

        assertThat(sut.getToken()).isEqualTo("token-1");
        clock.advance(Duration.ofMinutes(56));
        assertThat(sut.getToken()).isEqualTo("token-2");
    }

    @Test
    void ensureInvalidateForcesNewToken() {
        when(tokenResponseClient.getTokenResponse(any()))
            .thenReturn(tokenResponse("token-1", 3600))
            .thenReturn(tokenResponse("token-2", 3600));

        assertThat(sut.getToken()).isEqualTo("token-1");
        sut.invalidate();
        assertThat(sut.getToken()).isEqualTo("token-2");
    }

    @Test
    void ensureTokenWithoutExpiryIsNotCached() {
        when(tokenResponseClient.getTokenResponse(any()))
            .thenReturn(OAuth2AccessTokenResponse.withToken("token-1").tokenType(OAuth2AccessToken.TokenType.BEARER).build())
            .thenReturn(OAuth2AccessTokenResponse.withToken("token-2").tokenType(OAuth2AccessToken.TokenType.BEARER).build());

        assertThat(sut.getToken()).isEqualTo("token-1");
        assertThat(sut.getToken()).isEqualTo("token-2");
    }

    private static OAuth2AccessTokenResponse tokenResponse(String token, long expiresInSeconds) {
        return OAuth2AccessTokenResponse.withToken(token)
            .tokenType(OAuth2AccessToken.TokenType.BEARER)
            .expiresIn(expiresInSeconds)
            .build();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
