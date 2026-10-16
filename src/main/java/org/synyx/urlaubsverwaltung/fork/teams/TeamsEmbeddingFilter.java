package org.synyx.urlaubsverwaltung.fork.teams;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

/**
 * Allows Microsoft Teams to embed the pages:
 * <ul>
 *     <li>replaces {@code X-Frame-Options} of spring security with {@code Content-Security-Policy: frame-ancestors}</li>
 *     <li>the login of Microsoft Entra ID cannot be shown in a frame: a redirect to the login within a frame is
 *     redirected to the Teams tab page, which signs in with a popup of Teams</li>
 * </ul>
 */
class TeamsEmbeddingFilter extends OncePerRequestFilter {

    static final String TAB_PATH = "/fork/teams/tab";
    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String LOGIN_PATH = "/oauth2/authorization/";

    private final String contentSecurityPolicy;

    TeamsEmbeddingFilter(List<String> frameAncestors) {
        this.contentSecurityPolicy = "frame-ancestors 'self' " + String.join(" ", frameAncestors);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", contentSecurityPolicy);
        filterChain.doFilter(request, new EmbeddableResponse(request, response));
    }

    static boolean isFrameNavigation(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "iframe".equals(request.getHeader("Sec-Fetch-Dest"));
    }

    private static final class EmbeddableResponse extends HttpServletResponseWrapper {

        private final HttpServletRequest request;

        private EmbeddableResponse(HttpServletRequest request, HttpServletResponse response) {
            super(response);
            this.request = request;
        }

        @Override
        public void setHeader(String name, String value) {
            if (!X_FRAME_OPTIONS.equalsIgnoreCase(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!X_FRAME_OPTIONS.equalsIgnoreCase(name)) {
                super.addHeader(name, value);
            }
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            if (location != null && location.contains(LOGIN_PATH) && isFrameNavigation(request)) {
                final String target = request.getRequestURI().substring(request.getContextPath().length())
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
                final String tab = UriComponentsBuilder.fromPath(request.getContextPath() + TAB_PATH)
                    .queryParam("target", target)
                    .encode()
                    .toUriString();
                super.sendRedirect(tab);
                return;
            }
            super.sendRedirect(location);
        }
    }
}
