package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class PresenceWebConfiguration implements WebMvcConfigurer {

    private final PresenceWeekDataProvider presenceWeekDataProvider;
    private final AbsenceOverviewRedirectInterceptor absenceOverviewRedirectInterceptor;

    PresenceWebConfiguration(PresenceWeekDataProvider presenceWeekDataProvider,
                             AbsenceOverviewRedirectInterceptor absenceOverviewRedirectInterceptor) {
        this.presenceWeekDataProvider = presenceWeekDataProvider;
        this.absenceOverviewRedirectInterceptor = absenceOverviewRedirectInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(presenceWeekDataProvider)
            .addPathPatterns("/web/overview", "/web/person/*/overview");
        registry.addInterceptor(absenceOverviewRedirectInterceptor)
            .addPathPatterns("/web/absences");
    }
}
