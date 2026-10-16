package org.synyx.urlaubsverwaltung.fork.parttime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class PartTimeWebConfiguration implements WebMvcConfigurer {

    private final PartTimeFreeDaysDataProvider partTimeFreeDaysDataProvider;

    PartTimeWebConfiguration(PartTimeFreeDaysDataProvider partTimeFreeDaysDataProvider) {
        this.partTimeFreeDaysDataProvider = partTimeFreeDaysDataProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(partTimeFreeDaysDataProvider)
            .addPathPatterns(
                "/web/overview",
                "/web/person/*/overview",
                "/web/application",
                "/web/application/new",
                "/web/application/*/edit"
            );
    }
}
