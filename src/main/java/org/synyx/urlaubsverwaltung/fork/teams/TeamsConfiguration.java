package org.synyx.urlaubsverwaltung.fork.teams;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Embedding into Microsoft Teams, only with {@code uv.fork.teams.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(TeamsProperties.class)
class TeamsConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "uv.fork.teams", name = "enabled", havingValue = "true")
    FilterRegistrationBean<TeamsEmbeddingFilter> teamsEmbeddingFilter(TeamsProperties properties) {
        final FilterRegistrationBean<TeamsEmbeddingFilter> registration = new FilterRegistrationBean<>(new TeamsEmbeddingFilter(properties.frameAncestors()));
        // before spring session and spring security, so it wraps the responses written by them
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Teams embeds the pages in a frame of another site, the session cookie is only sent with SameSite=None.
     */
    @Bean
    @ConditionalOnProperty(prefix = "uv.fork.teams", name = "enabled", havingValue = "true")
    DefaultCookieSerializerCustomizer teamsSessionCookieCustomizer() {
        return serializer -> {
            serializer.setSameSite("None");
            serializer.setUseSecureCookie(true);
        };
    }
}
