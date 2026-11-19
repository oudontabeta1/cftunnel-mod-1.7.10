package dev.oudon.cftunnel.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Random;

public final class Ports {

    private static final Random RANDOM = new Random();

    private Ports() {
    }

    public static int findFree(int from, int to) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int port = from + RANDOM.nextInt(Math.max(1, to - from + 1));
            if (isFree(port)) {
                return port;
            }
        }
        try {
            ServerSocket socket = new ServerSocket(0);
            try {
                return socket.getLocalPort();
            } finally {
                socket.close();
            }
        } catch (IOException e) {
            return -1;
        }
    }

    public static boolean isFree(int port) {
        if (port <= 0 || port > 65535) {
            return false;
        }
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 解放失敗は無視してよい
                }
            }
        }
    }
}
