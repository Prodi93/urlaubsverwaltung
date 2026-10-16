package org.synyx.urlaubsverwaltung.fork.presence;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The team calendar of the fork replaces the absence overview: requests of the absence overview are redirected to it.
 * Can be switched off with {@code uv.fork.presence.replace-absence-overview=false}.
 */
@Component
class AbsenceOverviewRedirectInterceptor implements HandlerInterceptor {

    private final boolean enabled;

    AbsenceOverviewRedirectInterceptor(@Value("${uv.fork.presence.replace-absence-overview:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!enabled || !"GET".equals(request.getMethod())) {
            return true;
        }

        final UriComponentsBuilder target = UriComponentsBuilder.fromPath(request.getContextPath() + "/web/presence");
        final String year = request.getParameter("year");
        if (year != null && year.matches("\\d{4}")) {
            target.queryParam("year", year);
        }
        final String month = request.getParameter("month");
        if (month != null && month.matches("\\d{1,2}")) {
            target.queryParam("month", month);
        }
        response.sendRedirect(target.build().toUriString());
        return false;
    }

    boolean isEnabled() {
        return enabled;
    }
}
