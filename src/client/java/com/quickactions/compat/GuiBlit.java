package com.quickactions.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.quickactions.compat.GuiBlit;
import net.minecraft.client.renderer.RenderPipelines;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * blit with GUI_TEXTURED, but without linking to the RenderPipeline type
 * it moved packages between 26.1 and 26.3 and the crash was NoSuchFieldError, so here we are
 */
public final class GuiBlit {
    private static final MethodHandle BLIT = find();

    private GuiBlit() {
    }

    /** old g.blit(pipeline, ...) minus the pipeline, texture is an Identifier (as Object, don't ask) */
    public static void texture(GuiGraphicsExtractor g, Object texture, int x, int y, float u, float v,
                               int w, int h, int texW, int texH) {
        try {
            BLIT.invokeExact(g, texture, x, y, u, v, w, h, texW, texH);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static MethodHandle find() {
        try {
            Object pipeline = RenderPipelines.class.getField("GUI_TEXTURED").get(null);
            for (Method m : GuiGraphicsExtractor.class.getMethods()) {
                // the 10 arg blit, there are other overloads, skip them
                if (!m.getName().equals("blit") || m.getParameterCount() != 10) continue;
                Class<?>[] p = m.getParameterTypes();
                if (!p[0].isInstance(pipeline)) continue;
                if (p[2] != int.class || p[3] != int.class || p[4] != float.class || p[5] != float.class) continue;

                MethodHandle handle = MethodHandles.publicLookup().unreflect(m);
                // glue the pipeline in as the 2nd arg so the caller doesn't need it
                handle = MethodHandles.insertArguments(handle, 1, pipeline);
                return handle.asType(MethodType.methodType(void.class, GuiGraphicsExtractor.class, Object.class,
                        int.class, int.class, float.class, float.class,
                        int.class, int.class, int.class, int.class));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GuiBlit: can't resolve RenderPipelines.GUI_TEXTURED / blit", e);
        }
        throw new IllegalStateException("GuiBlit: no matching GuiGraphicsExtractor.blit(...) found");
    }
}