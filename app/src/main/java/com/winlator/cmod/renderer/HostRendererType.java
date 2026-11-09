package com.winlator.cmod.renderer;

public final class HostRendererType {
    public static final String OPENGL_GLADIO = "opengl_gladio";
    public static final String EGL = "egl";
    public static final String VULKAN = "vulkan";
    public static final String SURFACE_FLINGER = "surfaceflinger";

    private static final String[] VALUES = {
            OPENGL_GLADIO,
            EGL,
            VULKAN,
            SURFACE_FLINGER
    };

    private HostRendererType() {}

    public static String valueAt(int index) {
        return VALUES[index];
    }

    public static int indexOf(String value) {
        if (value == null) return 0;
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(value)) return i;
        }
        return 0;
    }

    public static boolean isSupported(String value) {
        if (value == null) return false;
        for (String renderer : VALUES) {
            if (renderer.equals(value)) return true;
        }
        return false;
    }

    public static String displayName(String value) {
        if (EGL.equals(value)) return "EGL";
        if (VULKAN.equals(value)) return "Vulkan";
        if (SURFACE_FLINGER.equals(value)) return "SurfaceFlinger";
        return "OpenGL + Gladio";
    }
}
