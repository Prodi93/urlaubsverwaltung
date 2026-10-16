package org.synyx.urlaubsverwaltung.fork.teams;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsAppManifestServiceTest {

    @ParameterizedTest
    @CsvSource({
        "6.17.0-li.4, 6.17.4",
        "6.17.1-li.2, 6.17.102",
        "6.18.0-li.1, 6.18.1",
        "6.17.0, 6.17.0",
        "6.18.0-SNAPSHOT, 6.18.0",
        "unknown, 1.0.0",
    })
    void ensureAppVersionIncreasesWithEveryRelease(String applicationVersion, String expected) {
        assertThat(TeamsAppManifestService.appVersion(applicationVersion)).isEqualTo(expected);
    }

    @Test
    void ensureTabUrlEncodesTheTarget() {
        assertThat(TeamsAppManifestService.tabUrl("https://urlaub.example.org", "/web/presence/today"))
            .isEqualTo("https://urlaub.example.org/fork/teams/tab?target=/web/presence/today");
    }

    @Test
    void ensureOutlineIconIsTransparent32x32() throws Exception {
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(TeamsAppManifestService.outlineIcon()));
        assertThat(image.getWidth()).isEqualTo(32);
        assertThat(image.getHeight()).isEqualTo(32);
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(0, 0) >>> 24).isZero();
    }
}
