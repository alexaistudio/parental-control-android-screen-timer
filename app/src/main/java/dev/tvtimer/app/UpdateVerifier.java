package dev.tvtimer.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

final class UpdateVerifier {
    private UpdateVerifier() {
    }

    static VerificationResult verify(
            Context context,
            File apk,
            String expectedDigest,
            String expectedVersion
    ) throws IOException, PackageManager.NameNotFoundException {
        String actualDigest = "sha256:" + fileSha256(apk);
        if (!actualDigest.equalsIgnoreCase(expectedDigest)) {
            throw new IOException("SHA-256 downloaded APK does not match GitHub");
        }

        PackageManager packageManager = context.getPackageManager();
        PackageInfo installed = installedPackageInfo(packageManager, context.getPackageName());
        PackageInfo archive = archivePackageInfo(packageManager, apk);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            throw new IOException("Downloaded APK has another package name");
        }
        if (!expectedVersion.equals(archive.versionName)) {
            throw new IOException("Downloaded APK version does not match its release tag");
        }
        long installedVersion = longVersionCode(installed);
        long archiveVersion = longVersionCode(archive);
        if (archiveVersion <= installedVersion) {
            throw new IOException("Downloaded APK is not newer than the installed version");
        }
        if (!certificateDigests(installed).equals(certificateDigests(archive))) {
            throw new IOException("Downloaded APK is signed by another certificate");
        }
        return new VerificationResult(archive.versionName, archiveVersion, actualDigest);
    }

    private static String fileSha256(File file) throws IOException {
        MessageDigest digest = sha256();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo installedPackageInfo(PackageManager manager, String packageName)
            throws PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        return manager.getPackageInfo(packageName, flags);
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo archivePackageInfo(PackageManager manager, File apk) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        return manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
    }

    @SuppressWarnings("deprecation")
    private static Set<String> certificateDigests(PackageInfo packageInfo) throws IOException {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (packageInfo.signingInfo == null) {
                throw new IOException("APK signing information is missing");
            }
            signatures = packageInfo.signingInfo.getApkContentsSigners();
        } else {
            signatures = packageInfo.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            throw new IOException("APK has no signing certificate");
        }
        Set<String> result = new HashSet<>();
        MessageDigest digest = sha256();
        for (Signature signature : signatures) {
            result.add(toHex(digest.digest(signature.toByteArray())));
            digest.reset();
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private static long longVersionCode(PackageInfo packageInfo) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? packageInfo.getLongVersionCode()
                : packageInfo.versionCode;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    static final class VerificationResult {
        final String versionName;
        final long versionCode;
        final String digest;

        VerificationResult(String versionName, long versionCode, String digest) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.digest = digest;
        }
    }
}
