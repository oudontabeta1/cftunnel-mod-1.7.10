package dev.oudon.cftunnel;

/**
 * トンネル接続プロファイル。Gson でそのまま永続化する。
 */
public class TunnelProfile {

    public String name = "";
    public String hostname = "";
    /** 0 のときは空きポートを自動選択する。 */
    public int localPort = 0;
    /** 空のときは自動解決した cloudflared を使う。 */
    public String cloudflaredPath = "";

    public TunnelProfile() {
    }

    public TunnelProfile(String name, String hostname) {
        this.name = name;
        this.hostname = hostname;
    }

    public TunnelProfile copy() {
        TunnelProfile p = new TunnelProfile();
        p.name = name;
        p.hostname = hostname;
        p.localPort = localPort;
        p.cloudflaredPath = cloudflaredPath;
        return p;
    }

    public boolean hasValidHostname() {
        return hostname != null && !hostname.trim().isEmpty();
    }

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return hasValidHostname() ? hostname : "(no name)";
    }

    public String cloudflaredPathOrEmpty() {
        return cloudflaredPath == null ? "" : cloudflaredPath.trim();
    }

    public String hostnameOrEmpty() {
        return hostname == null ? "" : hostname.trim();
    }
}
