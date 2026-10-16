package org.synyx.urlaubsverwaltung.fork.teams;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsEmbeddingFilterTest {

    private final TeamsEmbeddingFilter sut = new TeamsEmbeddingFilter(List.of("https://teams.microsoft.com"));

    @Test
    void ensureFrameAncestorsReplaceFrameOptions() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        sut.doFilter(new MockHttpServletRequest("GET", "/web/presence"), response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse servletResponse) {
                servletResponse.setHeader("X-Frame-Options", "DENY");
                servletResponse.addHeader("X-Frame-Options", "DENY");
                servletResponse.setHeader("X-Content-Type-Options", "nosniff");
            }
        }));

        assertThat(response.getHeader("X-Frame-Options")).isNull();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("frame-ancestors 'self' https://teams.microsoft.com");
    }

    @Test
    void ensureLoginInFrameIsRedirectedToTab() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/web/presence");
        request.setQueryString("year=2026&month=10");
        request.addHeader("Sec-Fetch-Dest", "iframe");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        sut.doFilter(request, response, redirectingTo("/oauth2/authorization/default"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/fork/teams/tab?target=/web/presence?year%3D2026%26month%3D10");
    }

    @Test
    void ensureLoginOutsideOfFrameIsUnchanged() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        sut.doFilter(new MockHttpServletRequest("GET", "/web/presence"), response, redirectingTo("/oauth2/authorization/default"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/default");
    }

    private static MockFilterChain redirectingTo(String location) {
        return new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
                response.sendRedirect(location);
            }
        });
    }
}
