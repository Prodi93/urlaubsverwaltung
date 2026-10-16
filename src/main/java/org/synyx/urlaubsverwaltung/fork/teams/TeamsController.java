package org.synyx.urlaubsverwaltung.fork.teams;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.synyx.urlaubsverwaltung.branding.BrandingProperties;
import org.synyx.urlaubsverwaltung.web.AssetManifestService;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Pages used by the Teams app: the tab (signs in with a Teams popup if needed and opens the page), the configuration
 * of a channel tab and the end of the sign in popup.
 *
 * <p>They are rendered without the frame of the application and without the interceptors of the web pages, because
 * the tab and the configuration are shown before the user is signed in.</p>
 */
@RestController
class TeamsController {

    static final List<String> CHANNEL_TARGETS = List.of("/web/presence", "/web/presence/today", "/web/overview", "/web/application");
    private static final MediaType HTML = new MediaType("text", "html", UTF_8);

    private final ITemplateEngine templateEngine;
    private final AssetManifestService assetManifestService;
    private final BrandingProperties brandingProperties;

    TeamsController(@Qualifier("templateEngine") ITemplateEngine templateEngine, AssetManifestService assetManifestService, BrandingProperties brandingProperties) {
        this.templateEngine = templateEngine;
        this.assetManifestService = assetManifestService;
        this.brandingProperties = brandingProperties;
    }

    @GetMapping(TeamsEmbeddingFilter.TAB_PATH)
    ResponseEntity<String> tab(@RequestParam(value = "target", defaultValue = "/web/presence") String target, HttpServletRequest request) {
        return render("fork/teams/tab", request, Map.of("target", request.getContextPath() + safeTarget(target)));
    }

    @GetMapping("/fork/teams/config")
    ResponseEntity<String> config(HttpServletRequest request) {
        return render("fork/teams/config", request, Map.of("targets", CHANNEL_TARGETS));
    }

    /**
     * Requires a signed in user: opened in the popup, it signs in with OIDC and then tells Teams the sign in succeeded.
     */
    @GetMapping("/fork/teams/auth-end")
    ResponseEntity<String> authEnd(HttpServletRequest request) {
        return render("fork/teams/auth-end", request, Map.of());
    }

    private ResponseEntity<String> render(String template, HttpServletRequest request, Map<String, Object> variables) {
        final Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariables(variables);
        context.setVariable("contextPath", request.getContextPath());
        context.setVariable("applicationName", brandingProperties.name());
        context.setVariable("teamsScript", assetManifestService.getHashedAssetFilename("fork_teams.js", request.getContextPath()));
        context.setVariable("commonCss", request.getContextPath() + "/css/common.css");
        return ResponseEntity.ok()
            .contentType(HTML)
            .cacheControl(CacheControl.noStore())
            .body(templateEngine.process(template, context));
    }

    /**
     * only pages of this application, no open redirect
     */
    static String safeTarget(String target) {
        if (target == null || !target.startsWith("/web/") || target.contains("//") || target.contains("\\") || target.contains("://")) {
            return "/web/presence";
        }
        return target;
    }
}
