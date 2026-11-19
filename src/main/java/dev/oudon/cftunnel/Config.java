package dev.oudon.cftunnel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Global settings, stored in config/cftunnel/config.json.
 */
public final class Config {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Data DEFAULTS = new Data();
    private static Data data = DEFAULTS;
    private static File file;

    private Config() {
    }

    public static final class Data {
        /** Seconds to wait for cloudflared to accept connections. */
        public int readyTimeoutSeconds = 25;
        /** cloudflared release tag to download. */
        public String cloudflaredVersion = "2026.11.1";
        /** Extra arguments passed to cloudflared. */
        public String extraArgs = "";
        /** Number of log lines shown on failure. */
        public int logTailLines = 12;
        /** Explicit cloudflared path. Empty means auto-download. */
        public String cloudflaredPath = "";
    }

    public static void load(File configDir) {
        if (!configDir.isDirectory() && !configDir.mkdirs()) {
            CfTunnelMod.LOG.warn("Could not create config directory: {}", configDir);
        }
        file = new File(configDir, "config.json");
        if (!file.isFile()) {
            data = new Data();
            save();
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), UTF8)) {
            Data loaded = GSON.fromJson(r, Data.class);
            data = loaded != null ? loaded : new Data();
        } catch (IOException | JsonSyntaxException e) {
            CfTunnelMod.LOG.warn("Failed to read config.json, using defaults", e);
            data = new Data();
        }
        normalize();
    }

    private static void normalize() {
        if (data.readyTimeoutSeconds < 5 || data.readyTimeoutSeconds > 300) {
            data.readyTimeoutSeconds = DEFAULTS.readyTimeoutSeconds;
        }
        if (data.cloudflaredVersion == null || data.cloudflaredVersion.trim().isEmpty()) {
            data.cloudflaredVersion = DEFAULTS.cloudflaredVersion;
        }
        if (data.extraArgs == null) {
            data.extraArgs = "";
        }
        if (data.logTailLines < 1 || data.logTailLines > 100) {
            data.logTailLines = DEFAULTS.logTailLines;
        }
        if (data.cloudflaredPath == null) {
            data.cloudflaredPath = "";
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), UTF8)) {
            GSON.toJson(data, w);
        } catch (IOException e) {
            CfTunnelMod.LOG.warn("Failed to write config.json", e);
        }
    }

    public static int readyTimeoutSeconds() {
        return data.readyTimeoutSeconds;
    }

    public static String cloudflaredVersion() {
        return data.cloudflaredVersion;
    }

    public static String extraArgs() {
        return data.extraArgs;
    }

    public static int logTailLines() {
        return data.logTailLines;
    }

    public static String cloudflaredPath() {
        return data.cloudflaredPath;
    }

    public static void update(int timeoutSeconds, String version, String extraArgs, String cloudflaredPath) {
        data.readyTimeoutSeconds = timeoutSeconds;
        data.cloudflaredVersion = version;
        data.extraArgs = extraArgs;
        data.cloudflaredPath = cloudflaredPath;
        normalize();
        save();
    }

    public static File configDir() {
        return file == null ? null : file.getParentFile();
    }
}
