package dev.oudon.cftunnel;

import com.google.gson.JsonSyntaxException;
import net.minecraft.client.multiplayer.ServerData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores per-server tunnel settings in config/cftunnel/servers.json.
 */
public final class ServerConfigStore {

    private static final ServerConfigStore INSTANCE = new ServerConfigStore();

    private final List<ServerTunnelConfig> configs = new ArrayList<ServerTunnelConfig>();
    private File file;

    private ServerConfigStore() {
    }

    public static ServerConfigStore get() {
        return INSTANCE;
    }

    public void load(File configDir) {
        file = new File(configDir, "servers.json");
        configs.clear();
        if (!file.isFile()) {
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), Config.UTF8)) {
            Data data = Config.GSON.fromJson(r, Data.class);
            if (data != null && data.servers != null) {
                for (ServerTunnelConfig cfg : data.servers) {
                    if (cfg != null && cfg.serverKey != null && !cfg.serverKey.isEmpty()) {
                        configs.add(cfg);
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            CfTunnelMod.LOG.warn("Failed to read servers.json, starting empty", e);
            if (file.renameTo(new File(file.getParentFile(), "servers.json.broken"))) {
                CfTunnelMod.LOG.warn("Moved broken servers.json aside");
            }
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        Data data = new Data();
        data.servers = new ArrayList<ServerTunnelConfig>(configs);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), Config.UTF8)) {
            Config.GSON.toJson(data, w);
        } catch (IOException e) {
            CfTunnelMod.LOG.warn("Failed to write servers.json", e);
        }
    }

    public ServerTunnelConfig find(ServerData server) {
        if (server == null || server.serverIP == null) {
            return null;
        }
        String key = server.serverIP.trim();
        for (ServerTunnelConfig cfg : configs) {
            if (key.equals(cfg.serverKey)) {
                return cfg;
            }
        }
        return null;
    }

    public ServerTunnelConfig findOrCreate(ServerData server) {
        ServerTunnelConfig existing = find(server);
        if (existing != null) {
            return existing;
        }
        ServerTunnelConfig cfg = new ServerTunnelConfig(server == null ? "" : server.serverIP.trim());
        cfg.hostname = hostnameFromAddress(cfg.serverKey);
        configs.add(cfg);
        return cfg;
    }

    public void store(ServerTunnelConfig cfg) {
        ServerTunnelConfig existing = null;
        for (ServerTunnelConfig current : configs) {
            if (current.serverKey.equals(cfg.serverKey)) {
                existing = current;
                break;
            }
        }
        if (existing == null) {
            configs.add(cfg);
        } else {
            existing.enabled = cfg.enabled;
            existing.hostname = cfg.hostname;
            existing.localPort = cfg.localPort;
        }
        save();
    }

    public static String hostnameFromAddress(String address) {
        if (address == null) {
            return "";
        }
        String value = address.trim();
        int colon = value.lastIndexOf(':');
        if (colon > 0 && colon < value.length() - 1) {
            String port = value.substring(colon + 1);
            if (port.matches("[0-9]{1,5}")) {
                value = value.substring(0, colon);
            }
        }
        return value;
    }

    public static boolean isValidHostname(String host) {
        return host != null && !host.isEmpty() && host.length() <= 253
                && host.matches("[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?")
                && !host.contains("..");
    }

    private static final class Data {
        int version = 1;
        List<ServerTunnelConfig> servers = new ArrayList<ServerTunnelConfig>();
    }
}
