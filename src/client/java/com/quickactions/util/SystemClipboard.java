package com.quickactions.util;

import com.quickactions.QuickActionsClient;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public final class SystemClipboard {
    private SystemClipboard() {
    }

    public static CompletableFuture<Boolean> copyImage(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            if (!PowerShell.available()) {
                QuickActionsClient.LOGGER.warn("Copying images to the clipboard is implemented for Windows only");
                return false;
            }
            if (viaPowerShell(file)) return true;
            return viaAwt(file);
        });
    }

    private static boolean viaPowerShell(Path file) {
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
            QuickActionsClient.LOGGER.warn("PowerShell clipboard copy failed ({}), trying AWT: {}",
                    PowerShell.executable(), file, e);
            return false;
        }
    }

    /** fallback without PowerShell; image + file list, same as the script above */
    private static boolean viaAwt(Path file) {
        try {
            if (GraphicsEnvironment.isHeadless()) return false;
            Image image = ImageIO.read(file.toFile());
            if (image == null) return false;
            File asFile = file.toAbsolutePath().toFile();
            Transferable data = new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor) || DataFlavor.javaFileListFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (DataFlavor.imageFlavor.equals(flavor)) return image;
                    if (DataFlavor.javaFileListFlavor.equals(flavor)) return List.of(asFile);
                    throw new UnsupportedFlavorException(flavor);
                }
            };
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(data, null);
            return true;
        } catch (Throwable t) {
            QuickActionsClient.LOGGER.error("AWT clipboard copy failed: {}", file, t);
            return false;
        }
    }
}