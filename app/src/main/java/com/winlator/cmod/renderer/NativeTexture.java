package com.winlator.cmod.renderer;

import java.nio.ByteBuffer;

public abstract class NativeTexture {
    public abstract short getStride();
    public abstract ByteBuffer getVirtualData();
    public abstract void destroy();
}
