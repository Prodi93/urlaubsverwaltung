package org.synyx.urlaubsverwaltung.fork.pwa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.synyx.urlaubsverwaltung.branding.BrandingProperties;
import org.synyx.urlaubsverwaltung.web.AssetManifestService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Serves the service worker and the offline page of the progressive web app.
 * Both are rendered without the frame of the application, because they have to work without a signed in user.
 */
@RestController
class PwaController {

    private static final MediaType JAVASCRIPT = new MediaType("text", "javascript", UTF_8);
    private static final MediaType HTML = new MediaType("text", "html", UTF_8);

    private final ResourceLoader resourceLoader;
    private final AssetManifestService assetManifestService;
    private final MessageSource messageSource;
    private final BrandingProperties brandingProperties;
    private final String applicationVersion;

    PwaController(ResourceLoader resourceLoader, AssetManifestService assetManifestService, MessageSource messageSource,
                  BrandingProperties brandingProperties, @Value("${info.app.version}") String applicationVersion) {
        this.resourceLoader = resourceLoader;
        this.assetManifestService = assetManifestService;
        this.messageSource = messageSource;
        this.brandingProperties = brandingProperties;
        this.applicationVersion = applicationVersion;
    }

    @GetMapping("/sw.js")
    ResponseEntity<String> serviceWorker() {
        final String script = readResource("classpath:fork/pwa/sw.js")
            .replace("__UV_CACHE_VERSION__", cacheVersion());

        return ResponseEntity.ok()
            .contentType(JAVASCRIPT)
            .cacheControl(CacheControl.noCache())
            .header("Service-Worker-Allowed", "/")
            .body(script);
    }

    @GetMapping("/offline")
    ResponseEntity<String> offlinePage() {
        final Locale locale = LocaleContextHolder.getLocale();
        final String applicationName = brandingProperties.name();

        final String html = readResource("classpath:fork/pwa/offline.html")
            .replace("__LANG__", escape(locale.getLanguage()))
            .replace("__TITLE__", escape(message("fork.pwa.offline.title", locale, applicationName)))
            .replace("__HEADLINE__", escape(message("fork.pwa.offline.headline", locale, applicationName)))
            .replace("__TEXT__", escape(message("fork.pwa.offline.text", locale, applicationName)))
            .replace("__RETRY__", escape(message("fork.pwa.offline.retry", locale, applicationName)))
            .replace("__ICON__", "favicons/android-chrome-192x192.png");

        return ResponseEntity.ok()
            .contentType(HTML)
            .cacheControl(CacheControl.noCache())
            .body(html);
    }

    private String cacheVersion() {
        // a new frontend build results in a new service worker, which drops the cached assets of the old build
        final int assetsHash = assetManifestService.getAssets("").hashCode();
        return applicationVersion + "-" + Integer.toHexString(assetsHash);
    }

    private static String escape(String text) {
        return htmlEscape(text, UTF_8.name());
    }

    private String message(String key, Locale locale, String applicationName) {
        return messageSource.getMessage(key, new Object[]{applicationName}, locale);
    }

    private String readResource(String location) {
        try (InputStream inputStream = resourceLoader.getResource(location).getInputStream()) {
            return new String(inputStream.readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + location, e);
        }
    }
}
