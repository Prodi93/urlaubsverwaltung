package org.synyx.urlaubsverwaltung.fork.teams;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Integration into Microsoft Teams as app with tabs ({@code uv.fork.teams.*}).
 *
 * @param enabled        allows embedding the pages into Teams (frame-ancestors, SameSite=None session cookie)
 * @param frameAncestors origins allowed to embed the pages, Teams web, desktop and mobile and the other Microsoft 365 hosts
 */
@ConfigurationProperties("uv.fork.teams")
public record TeamsProperties(boolean enabled, List<String> frameAncestors) {

    static final List<String> DEFAULT_FRAME_ANCESTORS = List.of(
        "https://teams.microsoft.com",
        "https://*.teams.microsoft.com",
        "https://teams.cloud.microsoft",
        "https://*.cloud.microsoft",
        "https://*.office.com",
        "https://*.microsoft365.com",
        "https://outlook.office365.com"
    );

    public TeamsProperties {
        frameAncestors = frameAncestors == null || frameAncestors.isEmpty() ? DEFAULT_FRAME_ANCESTORS : List.copyOf(frameAncestors);
    }
}
