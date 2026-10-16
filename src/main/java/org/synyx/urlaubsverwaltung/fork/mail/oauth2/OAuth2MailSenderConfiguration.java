package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.time.Clock;
import java.util.Properties;

import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST;

/**
 * Replaces the auto configured mail sender of spring boot with one that authenticates via OAuth2.
 * The SMTP connection is still configured with the {@code spring.mail.*} properties, the password is not needed.
 */
@Configuration
@ConditionalOnProperty(prefix = "uv.fork.mail.oauth2", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({OAuth2MailProperties.class, MailProperties.class})
class OAuth2MailSenderConfiguration {

    static final String REGISTRATION_ID = "uv-fork-mail";

    @Bean
    MailAccessTokenProvider mailAccessTokenProvider(OAuth2MailProperties properties, Clock clock) {
        // not registered in the ClientRegistrationRepository to keep the oidc login untouched
        final ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(properties.getClientId())
            .clientSecret(properties.getClientSecret())
            .clientAuthenticationMethod(CLIENT_SECRET_POST)
            .authorizationGrantType(CLIENT_CREDENTIALS)
            .tokenUri(properties.getTokenUri())
            .scope(properties.getScope())
            .build();

        return new MailAccessTokenProvider(clientRegistration, new RestClientClientCredentialsTokenResponseClient(), properties.getRefreshSkew(), clock);
    }

    @Bean
    OAuth2JavaMailSender mailSender(MailProperties mailProperties, MailAccessTokenProvider mailAccessTokenProvider) {
        final OAuth2JavaMailSender sender = new OAuth2JavaMailSender(mailAccessTokenProvider);
        sender.setHost(mailProperties.getHost());
        if (mailProperties.getPort() != null) {
            sender.setPort(mailProperties.getPort());
        }
        sender.setUsername(mailProperties.getUsername());
        sender.setProtocol(mailProperties.getProtocol());
        if (mailProperties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());
        }

        final Properties javaMailProperties = new Properties();
        javaMailProperties.putAll(mailProperties.getProperties());
        javaMailProperties.putIfAbsent("mail.smtp.auth", "true");
        javaMailProperties.putIfAbsent("mail.smtp.auth.mechanisms", "XOAUTH2");
        javaMailProperties.putIfAbsent("mail.smtp.starttls.enable", "true");
        javaMailProperties.putIfAbsent("mail.smtp.starttls.required", "true");
        sender.setJavaMailProperties(javaMailProperties);

        return sender;
    }
}
