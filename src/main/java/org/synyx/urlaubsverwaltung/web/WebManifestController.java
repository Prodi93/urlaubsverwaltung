package org.synyx.urlaubsverwaltung.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.synyx.urlaubsverwaltung.branding.BrandingProperties;

import java.util.List;
import java.util.Locale;

/**
 * Serves the web app manifest. It is rendered instead of being a static resource,
 * because its name depends on the configured application name.
 */
@RestController
class WebManifestController {

    private static final String MANIFEST_JSON = "application/manifest+json";

    private final BrandingProperties brandingProperties;
    private final MessageSource messageSource;

    WebManifestController(BrandingProperties brandingProperties, MessageSource messageSource) {
        this.brandingProperties = brandingProperties;
        this.messageSource = messageSource;
    }

    @GetMapping(value = "/site.webmanifest", produces = MANIFEST_JSON)
    WebManifestDto webManifest() {
        final Locale locale = LocaleContextHolder.getLocale();
        final List<IconDto> shortcutIcons = List.of(new IconDto("favicons/android-chrome-192x192.png", "192x192", "image/png"));
        return new WebManifestDto(
            ".",
            brandingProperties.name(),
            brandingProperties.name(),
            ".",
            ".",
            List.of(
                new IconDto("favicons/android-chrome-192x192.png", "192x192", "image/png"),
                new IconDto("favicons/android-chrome-512x512.png", "512x512", "image/png")
            ),
            "#ffffff",
            "#ffffff",
            "standalone",
            List.of(
                new ShortcutDto(messageSource.getMessage("fork.pwa.shortcut.application.new", null, locale), "web/application/new", shortcutIcons),
                new ShortcutDto(messageSource.getMessage("fork.pwa.shortcut.presence.today", null, locale), "web/presence/today", shortcutIcons),
                new ShortcutDto(messageSource.getMessage("fork.pwa.shortcut.overview", null, locale), "web/overview", shortcutIcons)
            ),
            List.of(new RelatedApplicationDto("play", "https://play.google.com/store/apps/details?id=cloud.urlaubsverwaltung.mobile.urlaubsverwaltung"))
        );
    }

    record WebManifestDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("short_name") String shortName,
        @JsonProperty("start_url") String startUrl,
        @JsonProperty("scope") String scope,
        @JsonProperty("icons") List<IconDto> icons,
        @JsonProperty("theme_color") String themeColor,
        @JsonProperty("background_color") String backgroundColor,
        @JsonProperty("display") String display,
        @JsonProperty("shortcuts") List<ShortcutDto> shortcuts,
        @JsonProperty("related_applications") List<RelatedApplicationDto> relatedApplications
    ) {
    }

    record ShortcutDto(
        @JsonProperty("name") String name,
        @JsonProperty("url") String url,
        @JsonProperty("icons") List<IconDto> icons
    ) {
    }

    record IconDto(
        @JsonProperty("src") String src,
        @JsonProperty("sizes") String sizes,
        @JsonProperty("type") String type
    ) {
    }

    record RelatedApplicationDto(
        @JsonProperty("platform") String platform,
        @JsonProperty("url") String url
    ) {
    }
}
