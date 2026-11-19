package dev.oudon.cftunnel;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import dev.oudon.cftunnel.client.GuiTunnelError;
import dev.oudon.cftunnel.util.Files2;
import dev.oudon.cftunnel.util.Ports;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.IChatComponent;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Owns the tunnel lifecycle: start, hand off to Minecraft, clean up.
 */
public final class TunnelManager {

    public enum State {
        IDLE, STARTING, READY, CONNECTED, FAILED
    }

    /** Abort the handoff if the client never joins. */
    private static final long HANDOFF_TIMEOUT_MS = 90_000L;

    private static final TunnelManager INSTANCE = new TunnelManager();

    /** GuiDisconnected.message / field_146304_f */
    private static final Field DISCONNECT_MESSAGE = findDisconnectMessage();

    private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<Runnable>();

    private volatile State state = State.IDLE;
    private volatile String statusText = "";
    private volatile double progress = -1.0;
    private volatile String errorMessage = "";
    private volatile String errorLogTail = "";
    private volatile String errorLogPath = "";

    private volatile boolean loginRunning;
    private volatile String loginMessage = "";
    private volatile String loginUrl = "";

    private volatile TunnelProcess process;
    private volatile int session;
    private Thread worker;
    private GuiScreen parent;
    private String displayName = "";
    private boolean joinedWorld;
    private long handoffAt;
    private long disconnectSeenAt;

    private TunnelManager() {
    }

    private static Field findDisconnectMessage() {
        try {
            return ReflectionHelper.findField(GuiDisconnected.class, "message", "field_146304_f");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String disconnectDetail(GuiScreen screen) {
        if (DISCONNECT_MESSAGE == null || !(screen instanceof GuiDisconnected)) {
            return "";
        }
        try {
            Object value = DISCONNECT_MESSAGE.get(screen);
            if (value instanceof IChatComponent) {
                return ((IChatComponent) value).getUnformattedText().trim();
            }
        } catch (Throwable t) {
            // ignore
        }
        return "";
    }

    public static TunnelManager get() {
        return INSTANCE;
    }

    public State state() {
        return state;
    }

    public String statusText() {
        return statusText;
    }

    public double progress() {
        return progress;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String errorLogTail() {
        return errorLogTail;
    }

    public String errorLogPath() {
        return errorLogPath;
    }

    public boolean isLoginRunning() {
        return loginRunning;
    }

    public String loginMessage() {
        return loginMessage;
    }

    public String loginUrl() {
        return loginUrl;
    }

    public boolean isBusy() {
        return state == State.STARTING || state == State.READY;
    }

    public void connect(GuiScreen parentScreen, String serverName, String hostname,
                        int preferredPort, String tokenPath) {
        if (hostname == null || hostname.trim().isEmpty()) {
            return;
        }
        PingTunnel.stop();
        cancel();
        final int mySession = session;
        this.parent = parentScreen;
        this.displayName = serverName == null || serverName.isEmpty() ? hostname : serverName;
        this.errorMessage = "";
        this.errorLogTail = "";
        this.errorLogPath = "";
        this.progress = -1.0;
        this.statusText = Lang.t("status.preparing");
        this.joinedWorld = false;
        this.disconnectSeenAt = 0L;
        this.state = State.STARTING;

        final String host = hostname.trim();
        final int port = Math.max(0, preferredPort);
        final String token = tokenPath == null ? "" : tokenPath.trim();
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runConnect(host, port, token, mySession);
            }
        }, "cftunnel-connect");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean isCurrent(int mySession) {
        return mySession == session;
    }

    /**
     * Reads the login URL that cloudflared prints, so it can be shown in the GUI
     * when the browser does not open.
     */
    private Thread watchLoginUrl(final File logFile) {
        loginUrl = "";
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 600; i++) {
                    String text = Files2.readAll(logFile);
                    java.util.regex.Matcher matcher =
                            java.util.regex.Pattern.compile("https://\\S+").matcher(text);
                    if (matcher.find()) {
                        loginUrl = matcher.group();
                        return;
                    }
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "cftunnel-login-url");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void runConnect(String hostname, int preferredPort, String tokenPath, int mySession) {
        TunnelProcess local = null;
        try {
            File binary = CloudflaredBinary.resolve(Config.cloudflaredPath(),
                    new CloudflaredBinary.Progress() {
                        @Override
                        public void report(String message, double fraction) {
                            if (state == State.STARTING) {
                                statusText = message;
                                progress = fraction;
                            }
                        }
                    });
            ServiceToken serviceToken = ServiceToken.load(tokenPath);
            if (!isCurrent(mySession)) {
                return;
            }

            // Cloudflare Access: complete the browser login before Minecraft
            // connects, otherwise the connection is reset and looks like a kick.
            if (AccessAuth.needsLoginBlocking(hostname)) {
                statusText = Lang.t("status.waitingLogin");
                progress = -1.0;
                File loginDir = new File(CfTunnelMod.logDir(), "cftunnel");
                if (!loginDir.isDirectory() && !loginDir.mkdirs()) {
                    throw new TunnelException(Lang.f("error.mkdirFailed", loginDir.getAbsolutePath()));
                }
                File loginLog = new File(loginDir, "login-" + System.currentTimeMillis() + ".log");
                Thread watcher = watchLoginUrl(loginLog);
                int code = CloudflaredBinary.login(binary, hostname, loginLog);
                watcher.interrupt();
                if (code != 0) {
                    throw new TunnelException(Lang.f("status.loginFailed",
                            Integer.valueOf(code), loginLog.getAbsolutePath()));
                }
                loginUrl = "";
                if (!isCurrent(mySession)) {
                    return;
                }
            }

            statusText = Lang.t("status.startingProcess");
            progress = -1.0;
            int port;
            if (preferredPort > 0) {
                if (!Ports.waitFree(preferredPort, 5000L)) {
                    throw new TunnelException(Lang.f("error.portInUse", Integer.valueOf(preferredPort)));
                }
                port = preferredPort;
            } else {
                port = Ports.findFree(25600, 25700);
                if (port <= 0) {
                    throw new TunnelException(Lang.t("error.noFreePort"));
                }
            }

            local = new TunnelProcess();
            process = local;
            local.start(binary, hostname, port, serviceToken);

            statusText = Lang.t("status.waitingReady");
            local.waitUntilReady(Config.readyTimeoutSeconds());
            if (!isCurrent(mySession)) {
                local.stop();
                return;
            }

            statusText = Lang.t("status.handoff");
            final int readyPort = port;
            state = State.READY;
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    handoff(readyPort, mySession);
                }
            });
        } catch (Throwable t) {
            if (!isCurrent(mySession)) {
                return;
            }
            fail(t, local);
        }
    }

    private void fail(Throwable t, TunnelProcess failed) {
        if (failed != null) {
            failed.stop();
        }
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        errorMessage = message;
        errorLogTail = failed != null ? failed.tailText(Config.logTailLines()) : "";
        errorLogPath = failed != null ? failed.logPath() : "";
        process = null;
        state = State.FAILED;
        CfTunnelMod.LOG.error("Tunnel connection failed: {}", message, t);

        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || state != State.FAILED) {
                    return;
                }
                mc.displayGuiScreen(new GuiTunnelError(parent, errorMessage,
                        errorLogTail, errorLogPath));
            }
        });
    }

    private void handoff(int port, int mySession) {
        if (!isCurrent(mySession)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        ServerData serverData = new ServerData(displayName, "127.0.0.1:" + port);
        GuiScreen from = parent != null ? parent : mc.currentScreen;
        handoffAt = System.currentTimeMillis();
        joinedWorld = false;
        state = State.CONNECTED;
        CfTunnelMod.LOG.info("Connecting to 127.0.0.1:{}", Integer.valueOf(port));
        // Creating GuiConnecting directly leaves FML's playClientBlock null and
        // breaks the handshake, so use the official entry point.
        FMLClientHandler.instance().connectToServer(from, serverData);
    }

    public void cancel() {
        session++;
        stopQuietly();
        disconnectSeenAt = 0L;
        loginUrl = "";
        state = State.IDLE;
        statusText = "";
        progress = -1.0;
    }

    /**
     * Called every client tick. Drains main-thread work and watches for disconnects.
     */
    public void tick() {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                CfTunnelMod.LOG.error("Exception in main-thread task", t);
            }
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        TunnelProcess current = process;
        if (current == null || (state != State.CONNECTED && state != State.READY)) {
            return;
        }

        if (mc.thePlayer != null && mc.theWorld != null) {
            joinedWorld = true;
        }
        if (joinedWorld && mc.theWorld == null) {
            CfTunnelMod.LOG.info("Disconnected from server, stopping tunnel");
            cancel();
            return;
        }
        if (!joinedWorld && state == State.CONNECTED
                && System.currentTimeMillis() - handoffAt > HANDOFF_TIMEOUT_MS) {
            CfTunnelMod.LOG.warn("Timed out waiting to join, stopping tunnel");
            cancel();
            return;
        }

        // If the join fails before the world loads, surface the real cloudflared error.
        if (!joinedWorld && state == State.CONNECTED) {
            if (mc.currentScreen instanceof GuiDisconnected) {
                if (disconnectSeenAt == 0L) {
                    disconnectSeenAt = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - disconnectSeenAt >= 800L) {
                    String tail = current.tailText(Config.logTailLines());
                    String path = current.logPath();
                    String detail = disconnectDetail(mc.currentScreen);
                    stopQuietly();
                    state = State.IDLE;
                    boolean tunnelError = tail.contains("ERR")
                            || tail.contains("bad handshake")
                            || tail.contains("failed to connect to origin")
                            || tail.contains("502")
                            || tail.contains("dial tcp")
                            || tail.contains("connection refused");
                    String message;
                    if (tunnelError) {
                        message = Lang.t("error.handshake");
                    } else if (!detail.isEmpty()) {
                        message = Lang.f("error.disconnectedDetail", detail);
                    } else {
                        message = Lang.t("error.disconnected");
                    }
                    CfTunnelMod.LOG.info("Disconnected before joining: {}", detail);
                    mc.displayGuiScreen(new GuiTunnelError(parent, message, tail, path));
                    return;
                }
            } else {
                disconnectSeenAt = 0L;
            }
        }
        if (!current.isAlive()) {
            CfTunnelMod.LOG.warn("cloudflared exited unexpectedly");
            stopQuietly();
            state = State.IDLE;
            return;
        }
        if (state == State.READY) {
            statusText = Lang.t("status.handoff");
        }
    }

    public void runOnMainThread(Runnable runnable) {
        if (runnable != null) {
            mainThreadTasks.add(runnable);
        }
    }

    /**
     * Takes over a tunnel that was kept warm for the server list so the normal
     * disconnect handling still stops it.
     */
    public void adopt(PingTunnel.Session session) {
        if (session == null) {
            return;
        }
        cancel();
        process = session.process;
        joinedWorld = false;
        disconnectSeenAt = 0L;
        handoffAt = System.currentTimeMillis();
        displayName = "Cloudflare Tunnel";
        state = State.CONNECTED;
        CfTunnelMod.LOG.info("Adopted server list tunnel on 127.0.0.1:{}", Integer.valueOf(session.port));
    }

    public void stopQuietly() {
        TunnelProcess current = process;
        process = null;
        if (current != null) {
            current.stop();
        }
    }

    public void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                PingTunnel.stop();
                stopQuietly();
            }
        }, "cftunnel-shutdown"));
    }

    public void loginAsync(final String hostname, final Runnable onFinished) {
        if (loginRunning || hostname == null || hostname.trim().isEmpty()) {
            return;
        }
        loginRunning = true;
        loginMessage = Lang.t("status.loginPreparing");
        final String host = hostname.trim();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    File binary = CloudflaredBinary.resolve(Config.cloudflaredPath(), null);
                    File logFile = new File(new File(CfTunnelMod.logDir(), "cftunnel"),
                            "login-" + System.currentTimeMillis() + ".log");
                    File dir = logFile.getParentFile();
                    if (dir != null && !dir.isDirectory()) {
                        dir.mkdirs();
                    }
                    loginMessage = Lang.t("status.loginBrowser");
                    Thread watcher = watchLoginUrl(logFile);
                    int code = CloudflaredBinary.login(binary, host, logFile);
                    watcher.interrupt();
                    String output = Files2.readAll(logFile);
                    if (code == 0) {
                        result = Lang.t("status.loginOk");
                    } else if (output.contains("failed to find Access application")) {
                        result = Lang.t("status.loginNotProtected");
                    } else {
                        result = Lang.f("status.loginFailed",
                                Integer.valueOf(code), logFile.getAbsolutePath());
                    }
                } catch (Throwable t) {
                    String message = t.getMessage();
                    result = message == null ? t.getClass().getSimpleName() : message;
                }
                loginMessage = result;
                loginRunning = false;
                if (onFinished != null) {
                    runOnMainThread(onFinished);
                }
            }
        }, "cftunnel-login");
        thread.setDaemon(true);
        thread.start();
    }
}
