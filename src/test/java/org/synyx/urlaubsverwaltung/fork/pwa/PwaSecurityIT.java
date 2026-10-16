package org.synyx.urlaubsverwaltung.fork.pwa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.web.AssetManifestService;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PwaSecurityIT extends SingleTenantTestContainersBase {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AssetManifestService assetManifestService;

    @Test
    void ensureServiceWorkerIsAvailableWithoutLogin() throws Exception {
        when(assetManifestService.getAssets("")).thenReturn(Map.of());

        perform(get("/sw.js"))
            .andExpect(status().isOk())
            .andExpect(header().string("Service-Worker-Allowed", "/"));
    }

    @Test
    void ensureOfflinePageIsAvailableWithoutLogin() throws Exception {
        perform(get("/offline"))
            .andExpect(status().isOk());
    }

    @Test
    void ensureOtherPagesStillRequireLogin() throws Exception {
        perform(get("/web/overview"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void ensureOnlyGetIsPermitted() throws Exception {
        perform(post("/offline"))
            .andExpect(status().isForbidden());
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build().perform(builder);
    }
}
