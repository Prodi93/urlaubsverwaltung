package org.synyx.urlaubsverwaltung.fork.pwa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.synyx.urlaubsverwaltung.branding.BrandingProperties;
import org.synyx.urlaubsverwaltung.web.AssetManifestService;

import java.util.Map;

import static java.util.Locale.GERMAN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class PwaControllerTest {

    @Mock
    private AssetManifestService assetManifestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        final StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("fork.pwa.offline.title", GERMAN, "Offline – {0}");
        messageSource.addMessage("fork.pwa.offline.headline", GERMAN, "Keine Verbindung");
        messageSource.addMessage("fork.pwa.offline.text", GERMAN, "Die {0} ist nicht erreichbar.");
        messageSource.addMessage("fork.pwa.offline.retry", GERMAN, "Erneut versuchen");

        final PwaController sut = new PwaController(new DefaultResourceLoader(), assetManifestService, messageSource,
            new BrandingProperties("Urlaub <LI>"), "6.17.0-li.1");
        mockMvc = standaloneSetup(sut).build();
    }

    @Test
    void ensureServiceWorkerIsServedWithVersionedCacheAndWithoutHttpCaching() throws Exception {
        when(assetManifestService.getAssets("")).thenReturn(Map.of());

        mockMvc.perform(get("/sw.js"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/javascript"))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(header().string("Service-Worker-Allowed", "/"))
            .andExpect(content().string(containsString("const CACHE_VERSION = \"6.17.0-li.1-")))
            .andExpect(content().string(not(containsString("__UV_CACHE_VERSION__"))));
    }

    @Test
    void ensureOfflinePageIsLocalizedAndEscaped() throws Exception {
        mockMvc.perform(get("/offline").locale(GERMAN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(header().string("Cache-Control", "no-cache"))
            .andExpect(content().string(containsString("<html lang=\"de\">")))
            .andExpect(content().string(containsString("<title>Offline – Urlaub &lt;LI&gt;</title>")))
            .andExpect(content().string(containsString("<h1>Keine Verbindung</h1>")))
            .andExpect(content().string(containsString("Erneut versuchen")))
            .andExpect(content().string(not(containsString("__"))));
    }
}
