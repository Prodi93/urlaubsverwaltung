package org.synyx.urlaubsverwaltung.fork.teams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.synyx.urlaubsverwaltung.branding.BrandingProperties;
import org.synyx.urlaubsverwaltung.mail.MailProperties;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Creates the app package (manifest.json and icons) to upload in the Teams admin center.
 */
@Service
class TeamsAppManifestService {

    private static final String MANIFEST_VERSION = "1.19";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-li\\.(\\d+))?.*");

    private final MailProperties mailProperties;
    private final BrandingProperties brandingProperties;
    private final MessageSource messageSource;
    private final ResourceLoader resourceLoader;
    private final String applicationVersion;

    TeamsAppManifestService(MailProperties mailProperties, BrandingProperties brandingProperties, MessageSource messageSource,
                            ResourceLoader resourceLoader, @Value("${info.app.version}") String applicationVersion) {
        this.mailProperties = mailProperties;
        this.brandingProperties = brandingProperties;
        this.messageSource = messageSource;
        this.resourceLoader = resourceLoader;
        this.applicationVersion = applicationVersion;
    }

    String applicationUrl() {
        final String url = mailProperties.getApplicationUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * stable id of the app per installation, an uploaded app is updated instead of added again
     */
    String appId() {
        return UUID.nameUUIDFromBytes(("urlaubsverwaltung-teams:" + applicationUrl()).getBytes(UTF_8)).toString();
    }

    /**
     * Teams needs a version x.y.z that increases with every update: 6.17.0-li.4 becomes 6.17.4, 6.17.1-li.2 becomes 6.17.102
     */
    static String appVersion(String applicationVersion) {
        final Matcher matcher = VERSION.matcher(applicationVersion);
        if (!matcher.matches()) {
            return "1.0.0";
        }
        final int patch = Integer.parseInt(matcher.group(3));
        final int fork = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
        return matcher.group(1) + "." + matcher.group(2) + "." + (patch * 100 + fork);
    }

    Map<String, Object> manifest(Locale locale) {
        final String url = applicationUrl();
        final String name = brandingProperties.name();

        final Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("$schema", "https://developer.microsoft.com/en-us/json-schemas/teams/v" + MANIFEST_VERSION + "/MicrosoftTeams.schema.json");
        manifest.put("manifestVersion", MANIFEST_VERSION);
        manifest.put("version", appVersion(applicationVersion));
        manifest.put("id", appId());
        manifest.put("developer", Map.of("name", name, "websiteUrl", url, "privacyUrl", url, "termsOfUseUrl", url));
        manifest.put("name", Map.of("short", truncate(name, 30), "full", name));
        manifest.put("description", Map.of(
            "short", truncate(message("fork.teams.manifest.description.short", locale), 80),
            "full", message("fork.teams.manifest.description.full", locale)));
        manifest.put("icons", Map.of("color", "color.png", "outline", "outline.png"));
        manifest.put("accentColor", "#0EA5E9");
        manifest.put("staticTabs", List.of(
            staticTab("team-calendar", message("fork.teams.tab.teamCalendar", locale), "/web/presence"),
            staticTab("presence-today", message("fork.teams.tab.today", locale), "/web/presence/today"),
            staticTab("overview", message("fork.teams.tab.overview", locale), "/web/overview"),
            staticTab("applications", message("fork.teams.tab.applications", locale), "/web/application")
        ));
        manifest.put("configurableTabs", List.of(Map.of(
            "configurationUrl", url + "/fork/teams/config",
            "canUpdateConfiguration", true,
            "scopes", List.of("team", "groupChat")
        )));
        manifest.put("permissions", List.of("identity"));
        manifest.put("validDomains", List.of(URI.create(url).getAuthority()));
        return manifest;
    }

    byte[] appPackage(Locale locale) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest(locale)));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("color.png"));
            zip.write(colorIcon());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("outline.png"));
            zip.write(outlineIcon());
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return outputStream.toByteArray();
    }

    private Map<String, Object> staticTab(String entityId, String name, String target) {
        final String url = applicationUrl();
        return Map.of(
            "entityId", entityId,
            "name", name,
            "contentUrl", tabUrl(url, target),
            "websiteUrl", url + target,
            "scopes", List.of("personal")
        );
    }

    static String tabUrl(String applicationUrl, String target) {
        return UriComponentsBuilder.fromUriString(applicationUrl + TeamsEmbeddingFilter.TAB_PATH)
            .queryParam("target", target)
            .encode()
            .toUriString();
    }

    /**
     * 192x192, the favicon of the application
     */
    private byte[] colorIcon() throws IOException {
        try (InputStream inputStream = resourceLoader.getResource("classpath:static/favicons/android-chrome-192x192.png").getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    /**
     * 32x32, white on transparent: a calendar
     */
    static byte[] outlineIcon() throws IOException {
        final BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(2.5f));
        graphics.drawRoundRect(4, 6, 24, 22, 4, 4);
        graphics.drawLine(4, 12, 28, 12);
        graphics.drawLine(11, 3, 11, 8);
        graphics.drawLine(21, 3, 21, 8);
        graphics.fillRect(9, 16, 4, 4);
        graphics.fillRect(19, 21, 4, 4);
        graphics.dispose();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, new Object[]{brandingProperties.name()}, locale);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
