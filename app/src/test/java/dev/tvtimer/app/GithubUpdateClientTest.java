package dev.tvtimer.app;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class GithubUpdateClientTest {
    @Test
    public void usesBrandedReleaseAssetName() {
        assertEquals(
                "AndroidScreenTimer-1.3.1.apk",
                GithubUpdateClient.expectedAssetName("1.3.1")
        );
    }

    @Test
    public void acceptsOnlyCanonicalRepositoryReleaseUrls() throws IOException {
        GithubUpdateClient.validateDownloadUrl(
                "https://github.com/alexaistudio/parental-control-android-screen-timer/"
                        + "releases/download/v1.3.1/AndroidScreenTimer-1.3.1.apk",
                "v1.3.1"
        );
        assertThrows(IOException.class, () -> GithubUpdateClient.validateDownloadUrl(
                "https://github.com/alexaistudio/tvtimer/"
                        + "releases/download/v1.3.1/TVTimer-1.3.1.apk",
                "v1.3.1"
        ));
        assertThrows(IOException.class, () -> GithubUpdateClient.validateDownloadUrl(
                "https://example.com/alexaistudio/parental-control-android-screen-timer/"
                        + "releases/download/v1.3.1/AndroidScreenTimer-1.3.1.apk",
                "v1.3.1"
        ));
    }
}
