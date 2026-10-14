package com.quickactions.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** %SystemRoot%, even if PATH is broken */
    public static Path windowsDir() {
        String root = System.getenv("SystemRoot");
        if (root == null || root.isBlank()) root = System.getenv("windir");
        if (root == null || root.isBlank()) root = "C:\\Windows";
        return Path.of(root);
    }

    /** full path to a Windows system exe (explorer.exe, rundll32.exe, ...), or the bare name as a last resort */
    public static String systemExe(String relative) {
        Path full = windowsDir().resolve(relative);
        return Files.isRegularFile(full) ? full.toString() : Path.of(relative).getFileName().toString();
    }

    /** absolute path to powershell.exe so we don't depend on PATH */
    public static String executable() {
        Path full = windowsDir().resolve("System32").resolve("WindowsPowerShell").resolve("v1.0").resolve("powershell.exe");
        return Files.isRegularFile(full) ? full.toString() : "powershell.exe";
    }

    public static Result run(String script) throws IOException, InterruptedException {
        String prefix = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false; "
                + "$ErrorActionPreference = 'Stop'; ";
        String encoded = Base64.getEncoder().encodeToString((prefix + script).getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder(executable(), "-NoProfile", "-NonInteractive", "-STA",
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