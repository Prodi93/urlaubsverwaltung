package org.synyx.urlaubsverwaltung.fork.pwa;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.config.http.SessionCreationPolicy.NEVER;

/**
 * The service worker and the offline page must be available without a session,
 * e.g. when the service worker is updated after the session expired.
 */
@Configuration
class PwaSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain pwaSecurityFilterChain(final HttpSecurity http) {
        return http
            .securityMatchers(configurer -> configurer.requestMatchers(GET, "/sw.js", "/offline"))
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(NEVER))
            .build();
    }
}
