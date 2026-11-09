package com.winlator.cmod.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.renderer.material.CursorMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;
import com.winlator.cmod.renderer.material.WindowMaterial;
import com.winlator.cmod.widget.WinlatorHUD;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRenderer implements GLSurfaceView.Renderer, HostRenderer, WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener {
    public final XServerView xServerView;
    private final XServer xServer;
    public final VertexAttribute quadVertices = new VertexAttribute("position", 2);
    private final float[] tmpXForm1 = XForm.getInstance();
    private final float[] tmpXForm2 = XForm.getInstance();
    private final CursorMaterial cursorMaterial = new CursorMaterial();
    private final WindowMaterial windowMaterial = new WindowMaterial();
    public final RendererViewTransformation viewTransformation = new RendererViewTransformation();
    private final Drawable rootCursorDrawable;
    private final ArrayList<RenderableWindow> renderableWindows = new ArrayList<>();
    private final Map<Drawable, Texture> textures = new IdentityHashMap<>();
    private final Map<Integer, Drawable> directContents = new HashMap<>();
    private int fullscreenMode = Container.FULLSCREEN_OFF;
    private int screenAlignment = Container.ALIGN_CENTER;
    private int filterMode = 0;
    private boolean renderingEnabled = true;
    public boolean viewportNeedsUpdate = true;
    private boolean cursorVisible = true;
    private boolean screenOffsetYRelativeToCursor = false;
    private String[] unviewableWMClasses = null;
    private float magnifierZoom = 1.0f;
    private boolean magnifierEnabled = true;
    public int surfaceWidth;
    public int surfaceHeight;
    private WinlatorHUD winlatorHUD;
    private boolean renderCursorEnabled = true;
    private long lastPointerRenderTimeNs = 0;
    private static final long MIN_POINTER_RENDER_INTERVAL_NS = 8_000_000L;

    public GLRenderer(XServerView xServerView, XServer xServer) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        rootCursorDrawable = createRootCursorDrawable();

        quadVertices.put(new float[]{
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
        });

        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        textures.clear();
        windowMaterial.destroy();
        cursorMaterial.destroy();
        quadVertices.destroy();
        quadVertices.put(new float[]{
                0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                1.0f, 1.0f
        });
        if (surfaceWidth > 0 && surfaceHeight > 0) updateViewTransformation();
        updateScene();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        updateViewTransformation();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!renderingEnabled) return;
        drawFrame();
    }

    public void drawFrame() {
        if (viewportNeedsUpdate && magnifierEnabled) {
            if (fullscreenMode == Container.FULLSCREEN_STRETCH) {
                GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            } else {
                GLES20.glViewport(viewTransformation.viewOffsetX,
                        surfaceHeight - viewTransformation.viewOffsetY - viewTransformation.viewHeight,
                        viewTransformation.viewWidth, viewTransformation.viewHeight);
            }
            viewportNeedsUpdate = false;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        renderScene();
    }

    @Override
    public void setFpsLimit(int fpsLimit) {
        xServer.setFpsLimit(fpsLimit);
    }

    @Override
    public int getFpsLimit() {
        return xServer.getFpsLimit();
    }

    public void setWinlatorHUD(WinlatorHUD hud) {
        this.winlatorHUD = hud;
        if (xServer.getWinlatorHUD() != hud) xServer.setWinlatorHUD(hud);
    }

    private void renderScene() {
        if (magnifierEnabled) {
            float f = !screenOffsetYRelativeToCursor ? magnifierZoom : 1.0f;
            float f2 = 0.0f;
            float clamp = f != 1.0f ? Mathf.clamp((xServer.pointer.getX() * f) - (xServer.screenInfo.width * 0.5f), 0.0f, xServer.screenInfo.width * Math.abs(1.0f - f)) : 0.0f;
            if (screenOffsetYRelativeToCursor || f != 1.0f) {
                f2 = Mathf.clamp((xServer.pointer.getY() * f) - (xServer.screenInfo.height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f)), 0.0f, xServer.screenInfo.height * (f != 1.0f ? Math.abs(1.0f - f) : 0.5f));
            }
            XForm.makeTransform(tmpXForm2, -clamp, -f2, f, f, 0.0f);
        } else if (fullscreenMode != Container.FULLSCREEN_STRETCH) {
            int i = 0;
            if (screenOffsetYRelativeToCursor) {
                short s = (short) (xServer.screenInfo.height / 2);
                i = Mathf.clamp(xServer.pointer.getY() - (s / 2), 0, (int) s);
            }
            XForm.makeTransform(tmpXForm2, viewTransformation.sceneOffsetX, viewTransformation.sceneOffsetY - i, viewTransformation.sceneScaleX, viewTransformation.sceneScaleY, 0.0f);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(viewTransformation.viewOffsetX,
                    surfaceHeight - viewTransformation.viewOffsetY - viewTransformation.viewHeight,
                    viewTransformation.viewWidth, viewTransformation.viewHeight);
        } else {
            XForm.identity(tmpXForm2);
        }

        renderWindows();

        if (cursorVisible && renderCursorEnabled) {
            renderCursor();
        }

        if (!magnifierEnabled && fullscreenMode != Container.FULLSCREEN_STRETCH) {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }
    }

    private void renderWindows() {
        windowMaterial.use();
        GLES20.glUniform2f(windowMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);
        quadVertices.bind(windowMaterial.programId);

        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
            Iterator<RenderableWindow> it = renderableWindows.iterator();
            while (it.hasNext()) {
                RenderableWindow next = it.next();
                renderDrawable(next.content, next.rootX, next.rootY, windowMaterial);
            }
        }
        quadVertices.disable();
    }

    private void renderDrawable(Drawable drawable, int x, int y, ShaderMaterial material) {
        if (drawable == null) return;
        synchronized (drawable.renderLock) {
            Texture texture = textures.get(drawable);
            if (texture == null) {
                texture = new Texture();
                applyFilterMode(texture);
                textures.put(drawable, texture);
            }
            texture.updateFromDrawable(drawable);
            if (texture.isFlipY()) {
                XForm.set(tmpXForm1, x, y + drawable.height, drawable.width, -drawable.height);
            } else {
                XForm.set(tmpXForm1, x, y, drawable.width, drawable.height);
            }
            XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());
            GLES20.glUniform1i(material.getUniformLocation("texture"), 0);
            GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    private void updateViewTransformation() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return;
        viewTransformation.update(surfaceWidth, surfaceHeight,
                xServer.screenInfo.width, xServer.screenInfo.height, fullscreenMode, screenAlignment);
        viewportNeedsUpdate = true;
    }

    private void applyFilterMode(Texture texture) {
        int filter = filterMode == 0 ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        texture.setMagFilter(filter);
        texture.setMinFilter(filter);
    }

    private void destroyGLResources() {
        for (Texture texture : textures.values()) texture.destroy();
        textures.clear();
        windowMaterial.destroy();
        cursorMaterial.destroy();
        quadVertices.destroy();
    }

    private Drawable createRootCursorDrawable() {
        Context context = xServerView.getContext();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return Drawable.fromBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options));
    }

    public ShaderMaterial getPassthroughMaterial() {
        return windowMaterial;
    }

    public VertexAttribute getQuadVertices() {
        return quadVertices;
    }

    @Override
    public int getSurfaceWidth() {
        return surfaceWidth;
    }

    @Override
    public int getSurfaceHeight() {
        return surfaceHeight;
    }

    @Override
    public boolean isFullscreen() {
        return fullscreenMode != Container.FULLSCREEN_OFF && fullscreenMode != Container.FULLSCREEN_FIT;
    }

    private void updateScene() {
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            renderableWindows.clear();
            collectRenderableWindows(xServer.windowManager.rootWindow, xServer.windowManager.rootWindow.getX(), xServer.windowManager.rootWindow.getY());
            pruneTextures();
        }
    }

    private void pruneTextures() {
        Set<Drawable> active = new HashSet<>();
        active.add(rootCursorDrawable);
        for (RenderableWindow window : renderableWindows) {
            if (window.content != null) active.add(window.content);
        }
        Window pointWindow = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = pointWindow != null ? pointWindow.attributes.getCursor() : null;
        if (cursor != null && cursor.cursorImage != null) active.add(cursor.cursorImage);
        Iterator<Map.Entry<Drawable, Texture>> iterator = textures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Drawable, Texture> entry = iterator.next();
            if (!active.contains(entry.getKey())) {
                entry.getValue().destroy();
                iterator.remove();
            }
        }
    }

    private void collectRenderableWindows(Window window, int x, int y) {
        if (!window.attributes.isMapped()) return;

        boolean isUnviewable = false;
        if (unviewableWMClasses != null) {
            String wmClass = window.getClassName();
            for (String unviewableWMClass : unviewableWMClasses) {
                if (wmClass.contains(unviewableWMClass)) {
                    isUnviewable = true;
                    break;
                }
            }
        }

        if (!isUnviewable) {
            Drawable content = directContents.get(window.id);
            renderableWindows.add(new RenderableWindow(content != null ? content : window.getContent(), x, y));
        }

        for (Window child : window.getChildren()) {
            collectRenderableWindows(child, child.getX() + x, child.getY() + y);
        }
    }

    @Override
    public void onMapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUnmapWindow(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onChangeWindowZOrder(Window window) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        Drawable content = window.getContent();
        xServerView.queueEvent(() -> {
            synchronized (directContents) {
                directContents.remove(window.id);
            }
            Texture texture = textures.get(content);
            if (texture != null) texture.setNeedsUpdate(true);
            updateScene();
        });
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable) {
        if (window == null || drawable == null) return;
        xServerView.queueEvent(() -> {
            synchronized (directContents) {
                directContents.put(window.id, drawable);
            }
            Texture texture = textures.get(drawable);
            if (texture != null) texture.setNeedsUpdate(true);
            updateScene();
        });
        xServerView.requestRender();
    }

    @Override
    public void onDestroyWindow(Window window) {
        Drawable content = window.getContent();
        xServerView.queueEvent(() -> {
            synchronized (directContents) {
                directContents.remove(window.id);
            }
            Texture texture = textures.remove(content);
            if (texture != null) texture.destroy();
            updateScene();
        });
    }

    @Override
    public void onUpdateWindowGeometry(Window window, boolean resized) {
        xServerView.queueEvent(this::updateScene);
        xServerView.requestRender();
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            xServerView.requestRender();
        }
    }

    @Override
    public void onPointerMove(short x, short y) {
        if (xServer.isRelativeMouseMovement() || !cursorVisible || !renderCursorEnabled) return;
        long now = System.nanoTime();
        if (now - lastPointerRenderTimeNs < MIN_POINTER_RENDER_INTERVAL_NS) return;
        lastPointerRenderTimeNs = now;
        xServerView.requestRender();
    }

    private void renderCursor() {
        cursorMaterial.use();
        GLES20.glUniform2f(cursorMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);
        quadVertices.bind(cursorMaterial.programId);

        Window pointWindow = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = pointWindow != null ? pointWindow.attributes.getCursor() : null;
        short x = xServer.pointer.getClampedX();
        short y = xServer.pointer.getClampedY();

        if (cursor != null) {
            if (cursor.isVisible()) renderDrawable(cursor.cursorImage, x - cursor.hotSpotX, y - cursor.hotSpotY, cursorMaterial);
        } else {
            renderDrawable(rootCursorDrawable, x, y, cursorMaterial);
        }
        quadVertices.disable();
    }

    public void setCursorVisible(boolean cursorVisible) {
        this.cursorVisible = cursorVisible;
        xServerView.requestRender();
    }

    public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {
        this.screenOffsetYRelativeToCursor = screenOffsetYRelativeToCursor;
        xServerView.requestRender();
    }

    @Override
    public float getMagnifierZoom() {
        return magnifierZoom;
    }

    public void setMagnifierZoom(float magnifierZoom) {
        this.magnifierZoom = magnifierZoom;
        xServerView.requestRender();
    }

    public void setMagnifierEnabled(boolean magnifierEnabled) {
        this.magnifierEnabled = magnifierEnabled;
        xServerView.requestRender();
    }

    public void setUnviewableWMClasses(String... unviewableWMNames) {
        this.unviewableWMClasses = unviewableWMNames;
    }

    public void removeDirectContent(int windowId, int drawableId) {
        xServerView.queueEvent(() -> {
            synchronized (directContents) {
                Drawable direct = directContents.get(windowId);
                if (direct != null && direct.id == drawableId) directContents.remove(windowId);
            }
            updateScene();
        });
    }

    @Override public XServerView getXServerView() { return xServerView; }

    @Override
    public void setRenderingEnabled(boolean enabled) {
        renderingEnabled = enabled;
        xServer.setRenderingEnabled(enabled);
        xServerView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        if (enabled) requestRender();
    }

    @Override public void requestRender() { xServerView.requestRender(); }

    @Override
    public void forceCleanup() {
        xServer.windowManager.removeOnWindowModificationListener(this);
        xServer.pointer.removeOnPointerMotionListener(this);
        renderingEnabled = false;
        xServerView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        xServerView.queueEvent(this::destroyGLResources);
    }

    @Override public boolean isCursorVisible() { return cursorVisible; }
    @Override public boolean isScreenOffsetYRelativeToCursor() { return screenOffsetYRelativeToCursor; }
    @Override public int getFullscreenMode() { return fullscreenMode; }
    @Override public int getScreenAlignment() { return screenAlignment; }
    @Override
    public void setUnviewableWMClasses(String wmClasses) {
        setUnviewableWMClasses(wmClasses == null || wmClasses.isEmpty()
                ? new String[0] : wmClasses.split(","));
    }

    @Override
    public void setFilterMode(int mode) {
        filterMode = mode;
        xServerView.queueEvent(() -> {
            for (Texture texture : textures.values()) applyFilterMode(texture);
        });
        requestRender();
    }

    @Override
    public void setFullscreenMode(int mode) {
        fullscreenMode = mode;
        updateViewTransformation();
        requestRender();
    }

    @Override
    public void toggleFullscreen() {
        setFullscreenMode(isFullscreen() ? Container.FULLSCREEN_OFF : Container.FULLSCREEN_STRETCH);
    }

    @Override public void setScreenAlignment(int alignment) {
        screenAlignment = alignment;
        updateViewTransformation();
        requestRender();
    }

    @Override public void setFpsWindowId(int id) {}

    @Override
    public void setFrameRating(Object fr) {
        setWinlatorHUD(fr instanceof WinlatorHUD ? (WinlatorHUD) fr : null);
    }

    public void setRenderCursorEnabled(boolean enabled) {
        this.renderCursorEnabled = enabled;
    }

    public void drawCursorExplicitly() {
        if (cursorVisible) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            renderCursor();
        }
    }
}
