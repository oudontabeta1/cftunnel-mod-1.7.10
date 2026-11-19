package dev.oudon.cftunnel;

import dev.oudon.cftunnel.util.Ports;

import java.io.File;
import java.util.Objects;

/**
 * Keeps one tunnel warm for the server selected in the multiplayer list so the
 * server list ping can go through the tunnel (instead of showing a red X).
 */
public final class PingTunnel {

    private static final Object LOCK = new Object();

    private static String key;
    private static String hostname;
    private static int requestedPort;
    private static String tokenPath;
    private static int port = -1;
    private static TunnelProcess process;
    private static boolean starting;
    private static long retryAfter;
    private static String lastErrorLogged;

    private PingTunnel() {
    }

    public static void ensure(String serverKey, String host, int preferredPort, String token) {
        if (TunnelManager.get().isBusy()) {
            return;
        }
        synchronized (LOCK) {
            if (System.currentTimeMillis() < retryAfter) {
                return;
            }
            boolean sameTarget = serverKey.equals(key)
                    && host.equals(hostname)
                    && preferredPort == requestedPort
                    && Objects.equals(token, tokenPath);
            if (sameTarget && (starting || (process != null && process.isAlive()))) {
                return;
            }
            stopLocked();
            key = serverKey;
            hostname = host;
            requestedPort = preferredPort;
            tokenPath = token;
            starting = true;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                start(serverKey, host, preferredPort, token);
            }
        }, "cftunnel-ping").start();
    }

    private static void start(String targetKey, String host, int preferredPort, String token) {
        TunnelProcess local = null;
        try {
            if (AccessAuth.needsLogin(host)) {
                // Do not open a browser just for the server list; the join
                // flow handles the login before connecting.
                synchronized (LOCK) {
                    if (targetKey.equals(key)) {
                        starting = false;
                    }
                    retryAfter = System.currentTimeMillis() + 15000L;
                }
                return;
            }
            File binary = CloudflaredBinary.resolve(Config.cloudflaredPath(), null);
            ServiceToken serviceToken = ServiceToken.load(token);
            int chosen = preferredPort > 0 ? preferredPort
                    : Ports.findFree(25600, 25700);
            if (preferredPort > 0 && !Ports.waitFree(preferredPort, 3000L)) {
                throw new TunnelException(Lang.f("error.portInUse", Integer.valueOf(preferredPort)));
            }
            local = new TunnelProcess();
            local.start(binary, host, chosen, serviceToken);
            local.waitUntilReady(Config.readyTimeoutSeconds());

            synchronized (LOCK) {
                if (!targetKey.equals(key) || !Objects.equals(host, hostname)) {
                    local.stop();
                    starting = false;
                    return;
                }
                process = local;
                port = chosen;
                starting = false;
            }
            CfTunnelMod.LOG.info("Server list ping tunnel ready on 127.0.0.1:{}", Integer.valueOf(chosen));
        } catch (Throwable t) {
            if (local != null) {
                local.stop();
            }
            synchronized (LOCK) {
                if (targetKey.equals(key)) {
                    starting = false;
                }
                // Do not retry on every rendered frame.
                retryAfter = System.currentTimeMillis() + 30000L;
            }
            String text = t.toString();
            if (lastErrorLogged == null || !lastErrorLogged.equals(text)) {
                lastErrorLogged = text;
                CfTunnelMod.LOG.warn("Server list ping tunnel failed: {}", text);
            }
        }
    }

    public static int portFor(String serverKey) {
        synchronized (LOCK) {
            if (serverKey.equals(key) && !starting && process != null) {
                return port;
            }
        }
        return -1;
    }

    /**
     * Detaches the running tunnel so the join flow can take ownership of it.
     */
    public static Session takeSession() {
        synchronized (LOCK) {
            if (process == null) {
                return null;
            }
            Session session = new Session(process, port);
            process = null;
            port = -1;
            key = null;
            hostname = null;
            tokenPath = null;
            requestedPort = 0;
            starting = false;
            retryAfter = 0L;
            return session;
        }
    }

    public static final class Session {
        public final TunnelProcess process;
        public final int port;

        Session(TunnelProcess process, int port) {
            this.process = process;
            this.port = port;
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            stopLocked();
        }
    }

    private static void stopLocked() {
        key = null;
        hostname = null;
        tokenPath = null;
        requestedPort = 0;
        port = -1;
        starting = false;
        retryAfter = 0L;
        TunnelProcess current = process;
        process = null;
        if (current != null) {
            // Stopping waits for the child process; keep it off the main thread.
            Thread stopper = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (current != null) {
                        current.stop();
                    }
                }
            }, "cftunnel-ping-stop");
            stopper.setDaemon(true);
            stopper.start();
        }
    }
}
