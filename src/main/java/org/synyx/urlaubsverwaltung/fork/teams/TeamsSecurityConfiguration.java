package org.synyx.urlaubsverwaltung.fork.teams;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.config.http.SessionCreationPolicy.NEVER;

/**
 * The tab and the configuration page of the Teams app must be available without a session: they start the sign in.
 * They contain no data. The end of the sign in ({@code /fork/teams/auth-end}) is protected by the web security.
 */
@Configuration
class TeamsSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain teamsSecurityFilterChain(final HttpSecurity http) {
        return http
            .securityMatchers(configurer -> configurer.requestMatchers(GET, TeamsEmbeddingFilter.TAB_PATH, "/fork/teams/config"))
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(NEVER))
            .build();
    }

    /**
     * Teams single sign on: authenticated by the Entra token in the request, which creates the session.
     * No csrf token, the request needs a valid bearer token.
     */
    @Bean
    @Order(1)
    SecurityFilterChain teamsSsoSecurityFilterChain(final HttpSecurity http) {
        return http
            .securityMatchers(configurer -> configurer.requestMatchers(POST, "/fork/teams/sso"))
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .build();
    }
}
