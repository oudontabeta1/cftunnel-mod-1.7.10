package dev.oudon.cftunnel;

import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 接続プロファイルの永続化。config/cftunnel/profiles.json
 */
public final class ProfileStore {

    private static final ProfileStore INSTANCE = new ProfileStore();

    private final List<TunnelProfile> profiles = new ArrayList<TunnelProfile>();
    private File file;

    private ProfileStore() {
    }

    public static ProfileStore get() {
        return INSTANCE;
    }

    public void load(File configDir) {
        file = new File(configDir, "profiles.json");
        profiles.clear();
        if (!file.isFile()) {
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), Config.UTF8)) {
            Data data = Config.GSON.fromJson(r, Data.class);
            if (data != null && data.profiles != null) {
                for (TunnelProfile p : data.profiles) {
                    if (p != null) {
                        profiles.add(p);
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            CfTunnelMod.LOG.warn("profiles.json の読み込みに失敗しました。バックアップを作成して空から開始します", e);
            backupBrokenFile();
        }
    }

    private void backupBrokenFile() {
        if (file == null || !file.isFile()) {
            return;
        }
        File backup = new File(file.getParentFile(), "profiles.json.broken");
        if (file.renameTo(backup)) {
            CfTunnelMod.LOG.warn("壊れた profiles.json を {} に退避しました", backup.getAbsolutePath());
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        data.profiles = new ArrayList<TunnelProfile>(profiles);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), Config.UTF8)) {
            Config.GSON.toJson(data, w);
        } catch (IOException e) {
            CfTunnelMod.LOG.warn("profiles.json の保存に失敗しました", e);
        }
    }

    public List<TunnelProfile> all() {
        return Collections.unmodifiableList(profiles);
    }

    public int size() {
        return profiles.size();
    }

    public TunnelProfile get(int index) {
        if (index < 0 || index >= profiles.size()) {
            return null;
        }
        return profiles.get(index);
    }

    public int indexOf(TunnelProfile profile) {
        return profiles.indexOf(profile);
    }

    public void add(TunnelProfile profile) {
        profiles.add(profile);
        save();
    }

    public void remove(int index) {
        if (index >= 0 && index < profiles.size()) {
            profiles.remove(index);
            save();
        }
    }

    public void replace(int index, TunnelProfile profile) {
        if (index >= 0 && index < profiles.size()) {
            profiles.set(index, profile);
            save();
        }
    }

    private static final class Data {
        int version = 1;
        List<TunnelProfile> profiles = new ArrayList<TunnelProfile>();
    }
}
