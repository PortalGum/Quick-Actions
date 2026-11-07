package com.quickactions.compat.legacy;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * image to the windows clipboard through jna (the game already ships it)
 * only used on 26.1/26.2, 26.3 does this through sdl
 */
final class WindowsClipboard {
    private static final int CF_DIB = 8;
    private static final int GMEM_MOVEABLE = 0x0002;
    private static final int BMP_FILE_HEADER = 14; // CF_DIB wants the bmp without this part

    // not private on purpose, jna proxies don't like private interfaces
    interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean OpenClipboard(Pointer owner);

        boolean CloseClipboard();

        boolean EmptyClipboard();

        Pointer SetClipboardData(int format, Pointer handle);
    }

    interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer GlobalAlloc(int flags, long bytes);

        Pointer GlobalLock(Pointer handle);

        boolean GlobalUnlock(Pointer handle);

        Pointer GlobalFree(Pointer handle);
    }

    private WindowsClipboard() {
    }

    /** off the game thread, this part is slow on big screenshots */
    static byte[] encodeDib(Path file) throws IOException {
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) {
            throw new IOException("Not a readable image: " + file);
        }
        // bmp has no alpha and imageio refuses argb, so plain rgb
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, w, h, source.getRGB(0, 0, w, h, null, 0, w), 0, w);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "bmp", out)) {
            throw new IOException("No ImageIO writer for BMP");
        }
        byte[] bmp = out.toByteArray();
        return Arrays.copyOfRange(bmp, BMP_FILE_HEADER, bmp.length);
    }

    /** game thread */
    static void put(byte[] dib) {
        Kernel32 k32 = Kernel32.INSTANCE;
        User32 u32 = User32.INSTANCE;

        Pointer handle = k32.GlobalAlloc(GMEM_MOVEABLE, dib.length);
        if (handle == null) throw new IllegalStateException("GlobalAlloc failed");
        Pointer memory = k32.GlobalLock(handle);
        if (memory == null) {
            k32.GlobalFree(handle);
            throw new IllegalStateException("GlobalLock failed");
        }
        memory.write(0, dib, 0, dib.length);
        k32.GlobalUnlock(handle);

        if (!u32.OpenClipboard(null)) {
            k32.GlobalFree(handle);
            throw new IllegalStateException("OpenClipboard failed (another app is holding it?)");
        }
        try {
            u32.EmptyClipboard();
            if (u32.SetClipboardData(CF_DIB, handle) == null) {
                k32.GlobalFree(handle);
                throw new IllegalStateException("SetClipboardData failed");
            }
            // success: windows owns the memory now, don't free it
        } finally {
            u32.CloseClipboard();
        }
    }
}