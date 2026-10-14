package com.quickactions.util;

import com.quickactions.QuickActionsClient;
import com.quickactions.ui.notify.Notifications;
import net.minecraft.client.Minecraft;

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
        String script = String.join(" ",
                "Add-Type -AssemblyName System.Windows.Forms;",
                "$owner = New-Object System.Windows.Forms.Form; $owner.TopMost = $true;",
                "$dialog = New-Object System.Windows.Forms.OpenFileDialog;",
                "$dialog.Title = " + PowerShell.quote(title) + ";",
                "$dialog.Filter = 'PNG images (*.png)|*.png';",
                "$dialog.CheckFileExists = $true;",
                "if ($dialog.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) {",
                "[Console]::Out.Write($dialog.FileName) }");
        return runDialog(script, "File picker");
    }

    public static CompletableFuture<Optional<Path>> pickSavePath(String title, String fileName, String extension) {
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
        return runDialog(script, "Save dialog");
    }

    private static CompletableFuture<Optional<Path>> runDialog(String script, String what) {
        return CompletableFuture.supplyAsync(() -> {
            if (!PowerShell.available()) {
                QuickActionsClient.LOGGER.warn("{} is implemented for Windows only", what);
                reportFailure();
                return Optional.<Path>empty();
            }
            try {
                PowerShell.Result result = PowerShell.run(script);
                if (!result.ok()) {
                    // cancel gives exit code 0 and empty output; anything else is a real failure
                    QuickActionsClient.LOGGER.error("{} failed, powershell exit code {} (powershell: {})",
                            what, result.exitCode(), PowerShell.executable());
                    reportFailure();
                    return Optional.<Path>empty();
                }
                String output = result.output();
                return output.isEmpty() ? Optional.<Path>empty() : Optional.of(Path.of(output));
            } catch (Exception e) {
                QuickActionsClient.LOGGER.error("{} failed (powershell: {})", what, PowerShell.executable(), e);
                reportFailure();
                return Optional.<Path>empty();
            }
        });
    }

    private static void reportFailure() {
        Minecraft.getInstance().execute(Notifications::failure);
    }
}