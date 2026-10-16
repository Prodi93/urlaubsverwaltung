package org.synyx.urlaubsverwaltung.fork.application;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ApplicationFormWebConfiguration implements WebMvcConfigurer {

    private final VacationDaysLeftDataProvider vacationDaysLeftDataProvider;

    ApplicationFormWebConfiguration(VacationDaysLeftDataProvider vacationDaysLeftDataProvider) {
        this.vacationDaysLeftDataProvider = vacationDaysLeftDataProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(vacationDaysLeftDataProvider)
            .addPathPatterns("/web/application", "/web/application/new", "/web/application/*/edit");
    }
}
