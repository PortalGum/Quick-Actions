package com.quickactions.util;

import com.quickactions.QuickActionsClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class FilePicker {
    private static final String DEFAULT_TITLE = "Select world icon (64x64 PNG)";

    private FilePicker() {
    }

    public static CompletableFuture<Optional<Path>> pickPng() {
        return pickPng(DEFAULT_TITLE);
    }

    public static CompletableFuture<Optional<Path>> pickPng(String title) {
        String script = buildScript(title);

        return CompletableFuture.supplyAsync(() -> {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (!os.contains("win")) {
                QuickActionsClient.LOGGER.warn("File picker is implemented for Windows only (os: {})", os);
                return Optional.empty();
            }
            try {
                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", script).start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .replace("\uFEFF", "")
                        .trim();
                process.waitFor();
                return output.isEmpty() ? Optional.<Path>empty() : Optional.of(Path.of(output));
            } catch (Exception e) {
                QuickActionsClient.LOGGER.error("File picker failed", e);
                return Optional.empty();
            }
        });
    }

    public static CompletableFuture<Optional<Path>> pickSavePath(String title, String fileName, String extension) {
        return CompletableFuture.supplyAsync(() -> {
            if (!PowerShell.available()) {
                QuickActionsClient.LOGGER.warn("File picker is implemented for Windows only");
                return Optional.<Path>empty();
            }
            String ext = extension.toLowerCase(Locale.ROOT);
            String filter = ext.toUpperCase(Locale.ROOT) + " image (*." + ext + ")|*." + ext + "|All files (*.*)|*.*";
            String script = String.join(" ",
                    "Add-Type -AssemblyName System.Windows.Forms;",
                    "$owner = New-Object System.Windows.Forms.Form; $owner.TopMost = $true;",
                    "$dialog = New-Object System.Windows.Forms.SaveFileDialog;",
                    "$dialog.Title = " + PowerShell.quote(title) + ";",
                    "$dialog.FileName = " + PowerShell.quote(fileName) + ";",
                    "$dialog.Filter = " + PowerShell.quote(filter) + ";",
                    "$dialog.DefaultExt = " + PowerShell.quote(ext) + ";",
                    "$dialog.AddExtension = $true; $dialog.OverwritePrompt = $true;",
                    "$dialog.InitialDirectory = [Environment]::GetFolderPath('MyPictures');",
                    "if ($dialog.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) {",
                    "[Console]::Out.Write($dialog.FileName) }");
            try {
                String output = PowerShell.run(script).output();
                return output.isEmpty() ? Optional.<Path>empty() : Optional.of(Path.of(output));
            } catch (Exception e) {
                QuickActionsClient.LOGGER.error("Save dialog failed", e);
                return Optional.<Path>empty();
            }
        });
    }

    private static String buildScript(String title) {
        String safeTitle = title.replace("'", "''");
        return String.join(" ",
                "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false;",
                "Add-Type -AssemblyName System.Windows.Forms;",
                "$owner = New-Object System.Windows.Forms.Form; $owner.TopMost = $true;",
                "$dialog = New-Object System.Windows.Forms.OpenFileDialog;",
                "$dialog.Title = '" + safeTitle + "';",
                "$dialog.Filter = 'PNG images (*.png)|*.png';",
                "$dialog.CheckFileExists = $true;",
                "if ($dialog.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) {",
                "[Console]::Out.Write($dialog.FileName) }");
    }
}
