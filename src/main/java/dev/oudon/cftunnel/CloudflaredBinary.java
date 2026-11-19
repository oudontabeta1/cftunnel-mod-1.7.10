package dev.oudon.cftunnel;

import dev.oudon.cftunnel.util.Files2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resolves, downloads and validates the cloudflared executable.
 */
public final class CloudflaredBinary {

    public interface Progress {
        void report(String message, double fraction);
    }

    private static final String RELEASE_BASE = "https://github.com/cloudflare/cloudflared/releases/download/";
    private static final Map<String, Long> VERIFIED = new HashMap<String, Long>();
    private static final Object RESOLVE_LOCK = new Object();
    private static final Object LOGIN_LOCK = new Object();
    private static boolean loginRunning;
    private static String loginHost;
    private static int loginResult;

    private CloudflaredBinary() {
    }

    public static File resolve(String explicitPath, Progress progress) throws Exception {
        synchronized (RESOLVE_LOCK) {
            String configuredPath = explicitPath == null ? "" : explicitPath.trim();
            if (!configuredPath.isEmpty()) {
                File configured = new File(configuredPath);
                if (!configured.isFile()) {
                    throw new TunnelException(Lang.f("error.pathMissing", configuredPath));
                }
                return configured;
            }

            File target = cachedBinaryFile();
            if (verify(target)) {
                return target;
            }
            // Reuse the existing binary if possible; replacing a running
            // executable is denied by Windows, so only delete after a failed
            // verification.
            if (target.exists()) {
                Files2.deleteQuietly(target);
            }
            download(target, progress);
            if (!verify(target)) {
                Files2.deleteQuietly(target);
                throw new TunnelException(Lang.f("error.verifyFailed", target.getAbsolutePath()));
            }
            return target;
        }
    }

    public static File cachedBinaryFile() {
        String name = isWindows() ? "cloudflared.exe" : "cloudflared";
        return new File(new File(CfTunnelMod.configDir(), "bin"), name);
    }

    /**
     * Runs "cloudflared access login", which opens the browser.
     */
    public static int login(File cloudflared, String hostname, File logFile) throws Exception {
        String url = hostname == null ? "" : hostname.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        // A trailing slash makes cloudflared's redirect_url match an Access
        // application registered on "/" (otherwise the browser can show
        // "Unable to find your Access application / invalid URL").
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        final String loginUrl = url;

        synchronized (LOGIN_LOCK) {
            if (loginRunning && hostname != null && hostname.equals(loginHost)) {
                // Another join already started the browser login; wait for it.
                long deadline = System.currentTimeMillis() + 300_000L;
                while (loginRunning && System.currentTimeMillis() < deadline) {
                    try {
                        LOGIN_LOCK.wait(500L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return loginResult;
            }
            loginRunning = true;
            loginHost = hostname;
            loginResult = -1;
        }

        try {
            if (AccessAuth.hasValidToken(hostname)) {
                loginResult = 0;
                return 0;
            }
            ProcessBuilder pb = new ProcessBuilder(
                    cloudflared.getAbsolutePath(), "access", "login", loginUrl);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            Process process = pb.start();
            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new TunnelException(Lang.t("error.loginTimeout"));
            }
            loginResult = process.exitValue();
            return loginResult;
        } finally {
            synchronized (LOGIN_LOCK) {
                loginRunning = false;
                loginHost = null;
                LOGIN_LOCK.notifyAll();
            }
        }
    }

    private static void download(File target, Progress progress) throws Exception {
        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new TunnelException(Lang.f("error.mkdirFailed", dir.getAbsolutePath()));
        }

        String asset = assetName();
        String url = RELEASE_BASE + Config.cloudflaredVersion() + "/" + asset;
        CfTunnelMod.LOG.info("Downloading cloudflared: {}", url);

        File archive = target;
        boolean macArchive = asset.endsWith(".tgz");
        if (macArchive) {
            archive = new File(dir, "cloudflared-download.tgz");
        }
        File tmp = new File(dir, archive.getName() + ".part");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "cftunnel/" + CfTunnelMod.VERSION);
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new TunnelException(Lang.f("error.downloadHttp", Integer.valueOf(code), url));
            }
            long total = connection.getContentLengthLong();
            if (progress != null) {
                progress.report(Lang.t("status.downloading"), total > 0 ? 0.0 : -1.0);
            }
            long read = 0L;
            long lastReported = 0L;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(tmp)) {
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    read += n;
                    if (progress != null && total > 0 && read - lastReported > 1024 * 1024) {
                        lastReported = read;
                        progress.report(Lang.t("status.downloading"), (double) read / (double) total);
                    }
                }
            }
            if (read < 1024 * 1024) {
                throw new TunnelException(Lang.f("error.downloadTooSmall", Long.valueOf(read)));
            }
            Files.move(tmp.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files2.deleteQuietly(tmp);
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (macArchive) {
            extractTarGz(archive, dir);
            Files2.deleteQuietly(archive);
        }
        if (target.setExecutable(true)) {
            CfTunnelMod.LOG.debug("Marked cloudflared as executable: {}", target.getAbsolutePath());
        }
        if (progress != null) {
            progress.report(Lang.t("status.verifying"), 1.0);
        }
    }

    private static void extractTarGz(File archive, File dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xzf", archive.getAbsolutePath(), "-C", dir.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(dir, "extract.log")));
        Process process = pb.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new TunnelException(Lang.t("error.extractTimeout"));
        }
        if (process.exitValue() != 0) {
            throw new TunnelException(Lang.f("error.extractFailed", Integer.valueOf(process.exitValue())));
        }
    }

    private static String assetName() throws TunnelException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("arm") || arch.contains("aarch");
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.contains("64") && !arm;

        if (os.contains("win")) {
            return x64 || arm64 ? "cloudflared-windows-amd64.exe" : "cloudflared-windows-386.exe";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return arm64 ? "cloudflared-darwin-arm64.tgz" : "cloudflared-darwin-amd64.tgz";
        }
        if (os.contains("linux")) {
            if (arm64) {
                return "cloudflared-linux-arm64";
            }
            if (arm) {
                return "cloudflared-linux-arm";
            }
            return x64 ? "cloudflared-linux-amd64" : "cloudflared-linux-386";
        }
        throw new TunnelException(Lang.f("error.unsupportedPlatform", os, arch));
    }

    private static boolean isVerified(File file) {
        synchronized (VERIFIED) {
            Long cached = VERIFIED.get(file.getAbsolutePath());
            return cached != null && cached.longValue() == file.lastModified();
        }
    }

    private static boolean verify(File file) {
        if (!file.isFile() || file.length() < 1024 * 1024) {
            return false;
        }
        if (isVerified(file)) {
            return true;
        }
        if (!hasValidMagic(file)) {
            CfTunnelMod.LOG.warn("Unexpected executable format: {}", file.getAbsolutePath());
            return false;
        }
        file.setExecutable(true);
        try {
            ProcessBuilder pb = new ProcessBuilder(file.getAbsolutePath(), "--version");
            pb.redirectErrorStream(true);
            File out = new File(file.getParentFile(), "version-check.log");
            pb.redirectOutput(ProcessBuilder.Redirect.to(out));
            Process process = pb.start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) {
                return false;
            }
            String text = Files2.readAll(out).toLowerCase(Locale.ROOT);
            Files2.deleteQuietly(out);
            if (!text.contains("cloudflared")) {
                return false;
            }
            synchronized (VERIFIED) {
                VERIFIED.put(file.getAbsolutePath(), Long.valueOf(file.lastModified()));
            }
            return true;
        } catch (Exception e) {
            CfTunnelMod.LOG.warn("Failed to run cloudflared --version: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    private static boolean hasValidMagic(File file) {
        byte[] head = new byte[4];
        try (InputStream in = new java.io.FileInputStream(file)) {
            if (in.read(head) < 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        if (isWindows()) {
            return head[0] == 'M' && head[1] == 'Z';
        }
        if (head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F') {
            return true;
        }
        int magic = ((head[0] & 0xff) << 24) | ((head[1] & 0xff) << 16)
                | ((head[2] & 0xff) << 8) | (head[3] & 0xff);
        return magic == 0xfeedface || magic == 0xfeedfacf
                || magic == 0xcefaedfe || magic == 0xcffaedfe;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
