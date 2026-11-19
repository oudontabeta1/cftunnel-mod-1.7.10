package dev.oudon.cftunnel;

import net.minecraft.util.StatCollector;

/**
 * 言語ファイル用の小さなヘルパー。
 */
public final class Lang {

    private Lang() {
    }

    public static String t(String key) {
        return StatCollector.translateToLocal("cftunnel." + key);
    }

    public static String f(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("cftunnel." + key, args);
    }
}
