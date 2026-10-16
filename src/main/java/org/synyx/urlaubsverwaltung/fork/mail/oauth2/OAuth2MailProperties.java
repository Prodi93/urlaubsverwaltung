package org.synyx.urlaubsverwaltung.fork.mail.oauth2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration to send mails via SMTP with OAuth2 (XOAUTH2) and the client credentials grant,
 * e.g. for Microsoft 365 / Exchange Online.
 */
@Validated
@ConfigurationProperties("uv.fork.mail.oauth2")
public class OAuth2MailProperties {

    private boolean enabled;

    /**
     * Token endpoint, e.g. https://login.microsoftonline.com/{tenant-id}/oauth2/v2.0/token
     */
    @NotBlank
    private String tokenUri;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String scope = "https://outlook.office365.com/.default";

    /**
     * An access token is renewed this duration before it expires.
     */
    @NotNull
    private Duration refreshSkew = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Duration getRefreshSkew() {
        return refreshSkew;
    }

    public void setRefreshSkew(Duration refreshSkew) {
        this.refreshSkew = refreshSkew;
    }
}
