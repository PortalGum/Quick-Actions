package com.quickactions.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class PowerShell {
    private PowerShell() {
    }

    public record Result(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    public static boolean available() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    public static Result run(String script) throws IOException, InterruptedException {
        String prefix = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false; "
                + "$ErrorActionPreference = 'Stop'; ";
        String encoded = Base64.getEncoder().encodeToString((prefix + script).getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-STA",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .trim();
        int code = process.waitFor();
        return new Result(code, output);
    }
}
