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
 * MOD 全体の設定。config/cftunnel/config.json に保存する。
 */
public final class Config {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Data DATA_DEFAULT = new Data();
    private static Data data = DATA_DEFAULT;
    private static File file;

    private Config() {
    }

    public static final class Data {
        /** cloudflared の起動待ちタイムアウト（秒）。 */
        public int readyTimeoutSeconds = 25;
        /** 自動ダウンロードする cloudflared のバージョン（GitHub のタグ）。 */
        public String cloudflaredVersion = "2026.11.1";
        /** cloudflared に追加で渡す引数（空白区切り）。 */
        public String extraArgs = "";
        /** 接続失敗時に GUI に表示するログ行数。 */
        public int logTailLines = 12;
    }

    public static void load(File configDir) {
        if (!configDir.isDirectory() && !configDir.mkdirs()) {
            CfTunnelMod.LOG.warn("設定ディレクトリを作成できませんでした: {}", configDir);
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
            CfTunnelMod.LOG.warn("config.json の読み込みに失敗したため既定値を使います", e);
            data = new Data();
        }
        normalize();
    }

    private static void normalize() {
        if (data.readyTimeoutSeconds < 5 || data.readyTimeoutSeconds > 300) {
            data.readyTimeoutSeconds = DATA_DEFAULT.readyTimeoutSeconds;
        }
        if (data.cloudflaredVersion == null || data.cloudflaredVersion.trim().isEmpty()) {
            data.cloudflaredVersion = DATA_DEFAULT.cloudflaredVersion;
        }
        if (data.extraArgs == null) {
            data.extraArgs = "";
        }
        if (data.logTailLines < 1 || data.logTailLines > 100) {
            data.logTailLines = DATA_DEFAULT.logTailLines;
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), UTF8)) {
            GSON.toJson(data, w);
        } catch (IOException e) {
            CfTunnelMod.LOG.warn("config.json の保存に失敗しました", e);
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
}
