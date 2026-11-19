package dev.oudon.cftunnel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oudon.cftunnel.util.Files2;

import java.io.File;

/**
 * Cloudflare Access service token loaded from a file.
 *
 * Supported formats:
 * - JSON: client_id / client_secret (also id / secret / service_token_* variants)
 * - Plain text: two lines (id, secret) or one line "id:secret"
 */
public final class ServiceToken {

    public final String id;
    public final String secret;

    private ServiceToken(String id, String secret) {
        this.id = id;
        this.secret = secret;
    }

    public static ServiceToken load(String path) throws TunnelException {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path.trim());
        if (!file.isFile()) {
            throw new TunnelException(Lang.f("error.tokenMissing", path));
        }
        String raw = Files2.readAll(file).trim();
        if (raw.isEmpty()) {
            throw new TunnelException(Lang.f("error.tokenInvalid", path));
        }

        try {
            JsonObject object = new JsonParser().parse(raw).getAsJsonObject();
            String id = first(object, "client_id", "clientId", "id",
                    "service_token_id", "serviceTokenId");
            String secret = first(object, "client_secret", "clientSecret", "secret",
                    "service_token_secret", "serviceTokenSecret");
            if (id != null && secret != null) {
                return new ServiceToken(id, secret);
            }
        } catch (Exception ignored) {
            // Not JSON; fall back to the plain text format.
        }

        String id = null;
        String secret = null;
        String[] lines = raw.split("\\r?\\n");
        int found = 0;
        for (String line : lines) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            if (found == 0) {
                int colon = value.indexOf(':');
                if (colon > 0 && colon < value.length() - 1 && lines.length == 1) {
                    id = value.substring(0, colon).trim();
                    secret = value.substring(colon + 1).trim();
                    found = 2;
                    break;
                }
                id = value;
            } else if (found == 1) {
                secret = value;
            }
            found++;
        }
        if (id == null || secret == null || id.isEmpty() || secret.isEmpty()) {
            throw new TunnelException(Lang.f("error.tokenInvalid", path));
        }
        return new ServiceToken(id, secret);
    }

    private static String first(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                String value = object.get(key).getAsString().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
