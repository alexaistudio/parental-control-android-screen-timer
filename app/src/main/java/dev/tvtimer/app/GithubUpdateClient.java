package dev.tvtimer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class GithubUpdateClient {
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/alexaistudio/tvtimer/releases/latest";
    private static final String DOWNLOAD_PATH_PREFIX =
            "/alexaistudio/tvtimer/releases/download/";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    ReleaseInfo fetchLatest() throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "TVTimer/" + BuildConfig.VERSION_NAME);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned HTTP " + status);
            }
            JSONObject release = new JSONObject(readLimited(connection.getInputStream()));
            String tag = release.optString("tag_name", "");
            String normalizedVersion = VersionComparator.normalized(tag);
            if (normalizedVersion.isEmpty()) {
                throw new IOException("Release tag is not a supported version");
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                throw new IOException("Release has no assets");
            }
            String expectedName = "TVTimer-" + normalizedVersion + ".apk";
            JSONObject selected = null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                if (expectedName.equals(asset.optString("name"))) {
                    selected = asset;
                    break;
                }
            }
            if (selected == null) {
                throw new IOException("Release APK was not found");
            }
            String downloadUrl = selected.optString("browser_download_url", "");
            validateDownloadUrl(downloadUrl, tag);
            String digest = selected.optString("digest", "");
            if (!digest.matches("(?i)^sha256:[0-9a-f]{64}$")) {
                throw new IOException("Release does not contain a SHA-256 digest");
            }
            return new ReleaseInfo(
                    tag,
                    normalizedVersion,
                    selected.optString("name"),
                    downloadUrl,
                    digest.toLowerCase()
            );
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("GitHub response is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void validateDownloadUrl(String value, String tag) throws IOException {
        try {
            URI uri = new URI(value);
            String requiredPrefix = DOWNLOAD_PATH_PREFIX + tag + "/";
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getPath() == null
                    || !uri.getPath().startsWith(requiredPrefix)) {
                throw new IOException("Release download URL is outside the configured repository");
            }
        } catch (URISyntaxException exception) {
            throw new IOException("Release download URL is invalid", exception);
        }
    }

    static final class ReleaseInfo {
        final String tag;
        final String version;
        final String assetName;
        final String downloadUrl;
        final String digest;

        ReleaseInfo(String tag, String version, String assetName, String downloadUrl, String digest) {
            this.tag = tag;
            this.version = version;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
            this.digest = digest;
        }
    }
}
