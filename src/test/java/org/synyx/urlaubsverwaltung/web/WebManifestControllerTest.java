package org.synyx.urlaubsverwaltung.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.synyx.urlaubsverwaltung.branding.BrandingProperties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.GERMAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WebManifestControllerTest {

    @Test
    void ensureManifestUsesConfiguredApplicationName() throws Exception {

        final WebManifestController sut = new WebManifestController(new BrandingProperties("Abwesenheiten"), messageSource());

        standaloneSetup(sut).build()
            .perform(get("/site.webmanifest").locale(GERMAN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
            .andExpect(jsonPath("$.name", is("Abwesenheiten")))
            .andExpect(jsonPath("$.short_name", is("Abwesenheiten")))
            .andExpect(jsonPath("$.start_url", is(".")))
            .andExpect(jsonPath("$.display", is("standalone")))
            .andExpect(jsonPath("$.icons.length()", is(2)))
            .andExpect(jsonPath("$.icons[0].src", is("favicons/android-chrome-192x192.png")))
            .andExpect(jsonPath("$.scope", is(".")))
            .andExpect(jsonPath("$.shortcuts.length()", is(2)))
            .andExpect(jsonPath("$.shortcuts[0].name", is("Abwesenheit beantragen")))
            .andExpect(jsonPath("$.shortcuts[0].url", is("web/application/new")));
    }

    @Test
    void ensureManifestIsUtf8Encoded() throws Exception {

        final WebManifestController sut = new WebManifestController(new BrandingProperties("Ürlaub & Co"), messageSource());

        final byte[] body = standaloneSetup(sut).build()
            .perform(get("/site.webmanifest"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(body, UTF_8)).contains("\"name\":\"Ürlaub & Co\"");
    }

    private static StaticMessageSource messageSource() {
        final StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.addMessage("fork.pwa.shortcut.application.new", GERMAN, "Abwesenheit beantragen");
        messageSource.addMessage("fork.pwa.shortcut.overview", GERMAN, "Übersicht");
        return messageSource;
    }
}
