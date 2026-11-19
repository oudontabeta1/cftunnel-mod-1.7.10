package dev.oudon.cftunnel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oudon.cftunnel.util.Files2;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether a hostname is protected by Cloudflare Access and whether a
 * valid, non-expired token is cached for it.
 */
public final class AccessAuth {

    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_\\-.]+");

    private static final Map<String, Boolean> REQUIRED = new ConcurrentHashMap<String, Boolean>();
    private static final Map<String, Long> CHECKED_AT = new ConcurrentHashMap<String, Long>();
    private static final Set<String> PROBING = Collections.synchronizedSet(new HashSet<String>());

    private AccessAuth() {
    }

    /**
     * Non-blocking check. Returns null while the first probe is running.
     */
    public static Boolean accessRequiredCached(final String host) {
        Long at = CHECKED_AT.get(host);
        if (at != null && System.currentTimeMillis() - at < CACHE_MS) {
            return REQUIRED.get(host);
        }
        if (PROBING.add(host)) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean required = probe(host);
                        REQUIRED.put(host, Boolean.valueOf(required));
                        CHECKED_AT.put(host, Long.valueOf(System.currentTimeMillis()));
                    } finally {
                        PROBING.remove(host);
                    }
                }
            }, "cftunnel-access-probe");
            thread.setDaemon(true);
            thread.start();
        }
        return null;
    }

    public static boolean accessRequiredBlocking(String host) {
        Long at = CHECKED_AT.get(host);
        if (at != null && System.currentTimeMillis() - at < CACHE_MS) {
            return Boolean.TRUE.equals(REQUIRED.get(host));
        }
        boolean required = probe(host);
        REQUIRED.put(host, Boolean.valueOf(required));
        CHECKED_AT.put(host, Long.valueOf(System.currentTimeMillis()));
        return required;
    }

    public static boolean hasValidToken(String host) {
        File dir = new File(System.getProperty("user.home", "."), ".cloudflared");
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        String prefix = host + "-";
        long now = System.currentTimeMillis();
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith(prefix) || !name.endsWith("-token")) {
                continue;
            }
            String raw = Files2.readAll(file);
            Matcher matcher = JWT.matcher(raw);
            if (!matcher.find()) {
                continue;
            }
            try {
                String[] parts = matcher.group().split("\\.");
                if (parts.length < 2) {
                    continue;
                }
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), UTF8);
                JsonObject json = new JsonParser().parse(payload).getAsJsonObject();
                if (json.has("exp") && json.get("exp").getAsLong() * 1000L > now + 60_000L) {
                    return true;
                }
            } catch (Exception ignored) {
                // Try the next token file.
            }
        }
        return false;
    }

    public static boolean needsLogin(String host) {
        Boolean required = accessRequiredCached(host);
        return Boolean.TRUE.equals(required) && !hasValidToken(host);
    }

    public static boolean needsLoginBlocking(String host) {
        return accessRequiredBlocking(host) && !hasValidToken(host);
    }

    private static boolean probe(String host) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://" + host + "/").openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "cftunnel/" + CfTunnelMod.VERSION);
            int code = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            String authenticate = connection.getHeaderField("WWW-Authenticate");
            if (code >= 300 && code < 400 && location != null
                    && location.contains("cloudflareaccess.com")) {
                return true;
            }
            return (code == 401 || code == 403) && authenticate != null
                    && authenticate.contains("Cloudflare-Access");
        } catch (Exception e) {
            CfTunnelMod.LOG.debug("Access probe failed for {}: {}", host, e.toString());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
