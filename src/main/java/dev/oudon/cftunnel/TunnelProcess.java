package dev.oudon.cftunnel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Manages the "cloudflared access tcp" child process.
 */
public final class TunnelProcess {

    private static final int MAX_TAIL_LINES = 200;

    private final Deque<String> tail = new ArrayDeque<String>();
    private Process process;
    private Thread reader;
    private File logFile;
    private int port = -1;

    public void start(File cloudflared, String hostname, int localPort) throws Exception {
        this.port = localPort;
        this.logFile = newLogFile(hostname);

        List<String> command = new ArrayList<String>();
        command.add(cloudflared.getAbsolutePath());
        command.addAll(splitArgs(Config.extraArgs()));
        command.add("access");
        command.add("tcp");
        command.add("--hostname");
        command.add(hostname);
        command.add("--url");
        command.add("127.0.0.1:" + localPort);

        CfTunnelMod.LOG.info("Starting cloudflared: {}", join(command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.environment().put("NO_AUTOUPDATE", "true");
        process = pb.start();
        startLogReader();
    }

    private void startLogReader() {
        reader = new Thread(new Runnable() {
            @Override
            public void run() {
                Writer writer = null;
                try {
                    writer = new OutputStreamWriter(new FileOutputStream(logFile, true), Config.UTF8);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), Config.UTF8));
                    String line;
                    while ((line = in.readLine()) != null) {
                        writer.write(line);
                        writer.write('\n');
                        writer.flush();
                        synchronized (tail) {
                            tail.addLast(line);
                            while (tail.size() > MAX_TAIL_LINES) {
                                tail.removeFirst();
                            }
                        }
                    }
                } catch (Exception e) {
                    CfTunnelMod.LOG.debug("Stopped reading cloudflared output", e);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (Exception ignored) {
                            // closing on shutdown can fail; ignore
                        }
                    }
                }
            }
        }, "cftunnel-log-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Waits until cloudflared listens on the local port.
     */
    public void waitUntilReady(int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (process == null) {
                throw new TunnelException(Lang.t("error.processNotFound"));
            }
            if (!process.isAlive()) {
                throw new TunnelException(Lang.f("error.processExited",
                        Integer.valueOf(process.exitValue()), tailText(Config.logTailLines())));
            }
            if (canConnect(port)) {
                return;
            }
            Thread.sleep(150L);
        }
        stop();
        throw new TunnelException(Lang.f("error.readyTimeout",
                Integer.valueOf(timeoutSeconds), tailText(Config.logTailLines())));
    }

    private static boolean canConnect(int port) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 400);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public int port() {
        return port;
    }

    public File logFile() {
        return logFile;
    }

    public String logPath() {
        return logFile == null ? "" : logFile.getAbsolutePath();
    }

    public String tailText(int lines) {
        StringBuilder sb = new StringBuilder();
        synchronized (tail) {
            int skip = Math.max(0, tail.size() - lines);
            int index = 0;
            for (String line : tail) {
                if (index++ < skip) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public void stop() {
        Process p = process;
        process = null;
        if (p == null) {
            return;
        }
        try {
            p.destroy();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            CfTunnelMod.LOG.debug("Exception while stopping cloudflared", e);
        }
    }

    private static File newLogFile(String hostname) {
        File dir = new File(CfTunnelMod.logDir(), "cftunnel");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            CfTunnelMod.LOG.warn("Could not create log directory: {}", dir.getAbsolutePath());
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        String host = hostname == null ? "" : hostname.replaceAll("[^A-Za-z0-9.-]", "_");
        return new File(dir, stamp + "-" + (host.isEmpty() ? "tunnel" : host) + ".log");
    }

    static List<String> splitArgs(String raw) {
        List<String> result = new ArrayList<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static String join(List<String> parts) {
        return Arrays.toString(parts.toArray(new String[parts.size()]));
    }
}
