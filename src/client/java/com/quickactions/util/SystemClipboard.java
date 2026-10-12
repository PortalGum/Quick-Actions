package com.quickactions.util;

import com.quickactions.QuickActionsClient;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class SystemClipboard {
    private SystemClipboard() {
    }

    public static CompletableFuture<Boolean> copyImage(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            if (!PowerShell.available()) {
                QuickActionsClient.LOGGER.warn("Copying images to the clipboard is implemented for Windows only");
                return false;
            }
            String script = String.join(" ",
                    "Add-Type -AssemblyName System.Windows.Forms;",
                    "Add-Type -AssemblyName System.Drawing;",
                    "$path = " + PowerShell.quote(file.toAbsolutePath().toString()) + ";",
                    "$source = [System.Drawing.Image]::FromFile($path);",
                    "try { $bitmap = New-Object System.Drawing.Bitmap($source) } finally { $source.Dispose() };",
                    "$files = New-Object System.Collections.Specialized.StringCollection;",
                    "[void]$files.Add($path);",
                    "$data = New-Object System.Windows.Forms.DataObject;",
                    "$data.SetImage($bitmap);",
                    "$data.SetFileDropList($files);",
                    "[System.Windows.Forms.Clipboard]::SetDataObject($data, $true);",
                    "$bitmap.Dispose()");
            try {
                return PowerShell.run(script).ok();
            } catch (Exception e) {
                QuickActionsClient.LOGGER.error("Failed to copy image to the clipboard: {}", file, e);
                return false;
            }
        });
    }
}
