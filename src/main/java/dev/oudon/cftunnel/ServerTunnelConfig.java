package dev.oudon.cftunnel;

/**
 * Per-server Cloudflare tunnel settings, keyed by the server's original address.
 */
public class ServerTunnelConfig {

    public String serverKey = "";
    public boolean enabled;
    /** Public hostname of the tunnel, for example mc.example.com. */
    public String hostname = "";
    /** Local port used by cloudflared. 0 means auto. */
    public int localPort;
    /** Optional Cloudflare Access service token file. */
    public String serviceTokenPath = "";

    public ServerTunnelConfig() {
    }

    public ServerTunnelConfig(String serverKey) {
        this.serverKey = serverKey;
    }

    public String hostnameOr(String fallback) {
        String value = hostname == null ? "" : hostname.trim();
        return value.isEmpty() ? fallback : value;
    }

    public String tokenPathOrEmpty() {
        return serviceTokenPath == null ? "" : serviceTokenPath.trim();
    }
}
