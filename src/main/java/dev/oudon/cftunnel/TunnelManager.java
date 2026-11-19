package dev.oudon.cftunnel;

import dev.oudon.cftunnel.client.GuiTunnelError;
import dev.oudon.cftunnel.util.Files2;
import dev.oudon.cftunnel.util.Ports;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * トンネルのライフサイクル（起動・接続・後始末）を一元管理する。
 */
public final class TunnelManager {

    public enum State {
        IDLE, STARTING, READY, CONNECTED, FAILED
    }

    /** 接続試行が失敗したまま放置された場合の上限（ミリ秒）。 */
    private static final long HANDOFF_TIMEOUT_MS = 90_000L;

    private static final TunnelManager INSTANCE = new TunnelManager();

    private final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<Runnable>();

    private volatile State state = State.IDLE;
    private volatile String statusText = "";
    private volatile double progress = -1.0;
    private volatile String errorMessage = "";
    private volatile String errorLogTail = "";
    private volatile String errorLogPath = "";

    private volatile boolean loginRunning;
    private volatile String loginMessage = "";

    private volatile TunnelProcess process;
    /** 接続試行ごとの識別子。古いワーカーの結果を無視するために使う。 */
    private volatile int session;
    private Thread worker;
    private GuiScreen parent;
    private boolean joinedWorld;
    private long handoffAt;
    private long disconnectSeenAt;

    private TunnelManager() {
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

    public boolean isBusy() {
        return state == State.STARTING || state == State.READY;
    }

    public void connect(GuiScreen parentScreen, TunnelProfile profile) {
        if (profile == null || !profile.hasValidHostname()) {
            return;
        }
        cancel();
        final int mySession = session;
        this.parent = parentScreen;
        this.errorMessage = "";
        this.errorLogTail = "";
        this.errorLogPath = "";
        this.progress = -1.0;
        this.statusText = Lang.t("status.preparing");
        this.joinedWorld = false;
        this.disconnectSeenAt = 0L;
        this.state = State.STARTING;

        final TunnelProfile snapshot = profile.copy();
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runConnect(snapshot, mySession);
            }
        }, "cftunnel-connect");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean isCurrent(int mySession) {
        return mySession == session;
    }

    private void runConnect(TunnelProfile profile, int mySession) {
        TunnelProcess local = null;
        try {
            File binary = CloudflaredBinary.resolve(profile, new CloudflaredBinary.Progress() {
                @Override
                public void report(String message, double fraction) {
                    if (state == State.STARTING) {
                        statusText = message;
                        progress = fraction;
                    }
                }
            });
            if (!isCurrent(mySession)) {
                return;
            }

            statusText = Lang.t("status.startingProcess");
            progress = -1.0;
            int port = profile.localPort > 0 ? profile.localPort : Ports.findFree(25600, 25700);
            if (port <= 0) {
                throw new TunnelException(Lang.t("error.noFreePort"));
            }
            if (!Ports.isFree(port)) {
                throw new TunnelException(Lang.f("error.portInUse", Integer.valueOf(port)));
            }

            local = new TunnelProcess();
            process = local;
            local.start(binary, profile, port);

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
        CfTunnelMod.LOG.error("トンネル接続に失敗しました: {}", message, t);

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
        ServerData serverData = new ServerData("Cloudflare Tunnel", "127.0.0.1:" + port);
        GuiScreen from = parent != null ? parent : mc.currentScreen;
        handoffAt = System.currentTimeMillis();
        joinedWorld = false;
        state = State.CONNECTED;
        CfTunnelMod.LOG.info("127.0.0.1:{} へ接続します", Integer.valueOf(port));
        mc.displayGuiScreen(new GuiConnecting(from, mc, serverData));
    }

    public void cancel() {
        session++;
        stopQuietly();
        disconnectSeenAt = 0L;
        state = State.IDLE;
        statusText = "";
        progress = -1.0;
    }

    /**
     * クライアント tick から毎フレーム呼ばれる。メインスレッド処理と切断検知を行う。
     */
    public void tick() {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                CfTunnelMod.LOG.error("メインスレッド処理で例外が発生しました", t);
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
            CfTunnelMod.LOG.info("サーバーから切断されたためトンネルを停止します");
            cancel();
            return;
        }
        if (!joinedWorld && state == State.CONNECTED
                && System.currentTimeMillis() - handoffAt > HANDOFF_TIMEOUT_MS) {
            CfTunnelMod.LOG.warn("サーバーへ参加できないままタイムアウトしたためトンネルを停止します");
            cancel();
            return;
        }

        // 参加前に切断された場合、cloudflared 側の本当の原因を表示する
        if (!joinedWorld && state == State.CONNECTED) {
            if (mc.currentScreen instanceof GuiDisconnected) {
                if (disconnectSeenAt == 0L) {
                    disconnectSeenAt = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - disconnectSeenAt >= 800L) {
                    String tail = current.tailText(Config.logTailLines());
                    String path = current.logPath();
                    stopQuietly();
                    state = State.IDLE;
                    String message = Lang.t("error.disconnected");
                    if (tail.contains("bad handshake") || tail.contains("failed to connect to origin")
                            || tail.contains("502")) {
                        message = Lang.t("error.handshake");
                    }
                    mc.displayGuiScreen(new GuiTunnelError(parent, message, tail, path));
                    return;
                }
            } else {
                disconnectSeenAt = 0L;
            }
        }
        if (!current.isAlive()) {
            CfTunnelMod.LOG.warn("cloudflared が予期せず終了しました");
            stopQuietly();
            state = State.IDLE;
            return;
        }
        if (state == State.READY) {
            // 接続画面へ遷移するまでの短い猶予。プロセスが生きていれば何もしない。
            statusText = Lang.t("status.handoff");
        }
    }

    public void runOnMainThread(Runnable runnable) {
        if (runnable != null) {
            mainThreadTasks.add(runnable);
        }
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
                stopQuietly();
            }
        }, "cftunnel-shutdown"));
    }

    // ------------------------------------------------------------------
    // ログイン
    // ------------------------------------------------------------------

    public void loginAsync(final TunnelProfile profile, final Runnable onFinished) {
        if (loginRunning || profile == null || !profile.hasValidHostname()) {
            return;
        }
        loginRunning = true;
        loginMessage = Lang.t("status.loginPreparing");
        final TunnelProfile snapshot = profile.copy();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    File binary = CloudflaredBinary.resolve(snapshot, null);
                    File logFile = new File(new File(CfTunnelMod.logDir(), "cftunnel"),
                            "login-" + System.currentTimeMillis() + ".log");
                    File dir = logFile.getParentFile();
                    if (dir != null && !dir.isDirectory()) {
                        dir.mkdirs();
                    }
                    loginMessage = Lang.t("status.loginBrowser");
                    int code = CloudflaredBinary.login(binary, snapshot.hostnameOrEmpty(), logFile);
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
