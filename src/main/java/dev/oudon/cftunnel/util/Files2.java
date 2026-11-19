package dev.oudon.cftunnel.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

public final class Files2 {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Files2() {
    }

    public static String readAll(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes, UTF8);
        } catch (IOException e) {
            return "";
        }
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
