package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2MailSenderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
        .withUserConfiguration(OAuth2MailSenderConfiguration.class)
        .withBean(Clock.class, Clock::systemUTC)
        .withPropertyValues(
            "spring.mail.host=smtp.office365.com",
            "spring.mail.port=587",
            "spring.mail.username=urlaub@example.org"
        );

    @Test
    void ensureSpringBootMailSenderIsUsedIfOAuth2IsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JavaMailSender.class);
            assertThat(context).doesNotHaveBean(OAuth2JavaMailSender.class);
            assertThat(context).doesNotHaveBean(MailAccessTokenProvider.class);
        });
    }

    @Test
    void ensureOAuth2MailSenderReplacesSpringBootMailSender() {
        contextRunner
            .withPropertyValues(
                "uv.fork.mail.oauth2.enabled=true",
                "uv.fork.mail.oauth2.token-uri=https://login.microsoftonline.com/tenant/oauth2/v2.0/token",
                "uv.fork.mail.oauth2.client-id=client",
                "uv.fork.mail.oauth2.client-secret=secret",
                "spring.mail.properties.mail.smtp.connectiontimeout=5000"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(JavaMailSender.class);
                final JavaMailSenderImpl mailSender = context.getBean(OAuth2JavaMailSender.class);
                assertThat(mailSender.getHost()).isEqualTo("smtp.office365.com");
                assertThat(mailSender.getPort()).isEqualTo(587);
                assertThat(mailSender.getUsername()).isEqualTo("urlaub@example.org");
                assertThat(mailSender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.auth.mechanisms", "XOAUTH2")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsEntry("mail.smtp.connectiontimeout", "5000");
            });
    }

    @Test
    void ensureStartupFailsIfOAuth2IsEnabledWithoutClientConfiguration() {
        contextRunner
            .withPropertyValues("uv.fork.mail.oauth2.enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }
}
