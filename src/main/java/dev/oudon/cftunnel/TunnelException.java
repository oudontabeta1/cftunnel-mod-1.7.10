package dev.oudon.cftunnel;

/**
 * ユーザーにそのまま表示できるメッセージを持つ例外。
 */
public class TunnelException extends Exception {

    private static final long serialVersionUID = 1L;

    public TunnelException(String message) {
        super(message);
    }

    public TunnelException(String message, Throwable cause) {
        super(message, cause);
    }
}
