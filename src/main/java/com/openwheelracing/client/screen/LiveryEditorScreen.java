package com.openwheelracing.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.client.livery.ClientLiveryTextures;
import com.openwheelracing.client.livery.ClientLiveryTextures.TemplateRegion;
import com.openwheelracing.client.livery.LiveryPreviewRenderer;
import com.openwheelracing.client.render.OpenwheelCarRenderer;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.network.OWRNetwork;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class LiveryEditorScreen extends Screen {
    private static final int MAX_VIEW_SIZE = 320;
    private static final int MIN_VIEW_SIZE = 192;
    private static final int MAX_HISTORY = 32;
    private static final int[] TEXTURE_ZOOM_FACTORS = {0, 1, 2, 4, 8};
    private static final int FIT_TEXTURE_ZOOM_INDEX = 0;
    private static final int MAX_TEXTURE_ZOOM_INDEX = TEXTURE_ZOOM_FACTORS.length - 1;
    private static final String PREVIEW_TEX_ID = "editor_3d_preview";
    private static final String TEXTURE_TEX_ID = "editor_livery_canvas";

    private final Screen parent;
    private final ItemStack stack;

    private NativeImage liveryImage;
    private CarLiveryTexture texture;

    private LiveryPreviewRenderer.Preview preview;
    private List<TextureAnnotation> annotations = List.of();
    private final ArrayDeque<NativeImage> undoStack = new ArrayDeque<>();
    private final ArrayDeque<NativeImage> redoStack = new ArrayDeque<>();
    private int textureZoomIndex = FIT_TEXTURE_ZOOM_INDEX;
    private int textureViewU;
    private int textureViewV;
    private boolean previewDirty = true;
    private boolean textureDirty = true;
    private Identifier previewLocation;
    private Identifier textureLocation;

    private int red = 235;
    private int green = 18;
    private int blue = 32;
    private int brushRadius = 8;

    private float yaw = 35.0f;
    private boolean paintingTexture;
    private boolean panningTexture;
    private boolean rotating;
    private double lastPanX;
    private double lastPanY;

    private int viewSize;
    private int previewX;
    private int previewY;
    private int textureX;
    private int textureY;
    private int controlsX;
    private int controlsY;

    private String status = "Paint the texture on the right; rotate the preview with right-drag";

    public LiveryEditorScreen(Screen parent, ItemStack stack) {
        super(Component.literal("Livery Editor"));
        this.parent = parent;
        this.stack = stack.copy();
    }

    @Override
    protected void init() {
        viewSize = Math.max(MIN_VIEW_SIZE, Math.min(MAX_VIEW_SIZE, Math.min((width - 48) / 2, height - 150)));
        int totalWidth = viewSize * 2 + 24;
        previewX = Math.max(8, (width - totalWidth) / 2);
        previewY = Math.max(28, (height - viewSize - 110) / 2);
        textureX = previewX + viewSize + 24;
        textureY = previewY;
        controlsX = previewX;
        controlsY = previewY + viewSize + 28;
        clampTextureView();

        texture = PrototypeCarItem.getLiveryTexture(stack);
        if (liveryImage == null) {
            NativeImage loaded = ClientLiveryTextures.loadOrCreate(Minecraft.getInstance(), texture, PrototypeCarItem.getLiveryColors(stack).body());
            liveryImage = texture != null && texture.isPresent() ? copyImage(loaded) : loaded;
            annotations = buildAnnotations();
            previewDirty = true;
            textureDirty = true;
        }

        if (annotations.isEmpty()) {
            annotations = buildAnnotations();
        }

        clearWidgets();
        addRenderableWidget(new RgbSlider(controlsX, controlsY + 16, 128, 18, 0));
        addRenderableWidget(new RgbSlider(controlsX, controlsY + 40, 128, 18, 1));
        addRenderableWidget(new RgbSlider(controlsX, controlsY + 64, 128, 18, 2));
        addRenderableWidget(new BrushSlider(controlsX + 150, controlsY + 16, 128, 18));
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
            .bounds(controlsX + 150, controlsY + 46, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> closeToParent())
            .bounds(controlsX + 218, controlsY + 46, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> reset())
            .bounds(controlsX + 150, controlsY + 72, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zoom -"), button -> zoomTexture(-1, textureX + viewSize / 2.0, textureY + viewSize / 2.0))
            .bounds(controlsX + 218, controlsY + 72, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zoom +"), button -> zoomTexture(1, textureX + viewSize / 2.0, textureY + viewSize / 2.0))
            .bounds(controlsX + 284, controlsY + 72, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), button -> undo())
            .bounds(controlsX + 350, controlsY + 72, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Redo"), button -> redo())
            .bounds(controlsX + 410, controlsY + 72, 52, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEE14181E);

        if (previewDirty) {
            rebuildPreview();
            previewLocation = ClientLiveryTextures.register(Minecraft.getInstance(), PREVIEW_TEX_ID, copyImage(preview.image()));
            previewDirty = false;
        }
        if (textureDirty) {
            textureLocation = ClientLiveryTextures.register(Minecraft.getInstance(), TEXTURE_TEX_ID, renderTextureView());
            textureDirty = false;
        }

        graphics.drawString(font, "3D preview", previewX, previewY - 14, 0xFFE8EDF2, false);
        graphics.drawString(font, "Texture " + textureZoomLabel(), textureX, textureY - 14, 0xFFE8EDF2, false);
        drawImagePane(graphics, previewLocation, previewX, previewY, viewSize);
        drawImagePane(graphics, textureLocation, textureX, textureY, viewSize);
        drawAnnotations(graphics);

        if (insideTexture(mouseX, mouseY)) {
            drawBrushCursor(graphics, mouseX, mouseY);
            drawTextureCoordinate(graphics, mouseX, mouseY);
        }

        graphics.drawString(font, "Brush color", controlsX, controlsY + 2, 0xFFB9C2CC, false);
        int color = brushColor();
        graphics.fill(controlsX + 292, controlsY + 16, controlsX + 334, controlsY + 58, color);
        graphics.renderOutline(controlsX + 292, controlsY + 16, 42, 42, 0xFF8792A2);
        graphics.drawString(font, CarLiveryColors.colorName(color), controlsX + 286, controlsY + 64, 0xFFB9C2CC, false);
        graphics.drawString(font, "Brush r=" + brushRadius + " px", controlsX + 150, controlsY + 2, 0xFFE8EDF2, false);
        graphics.drawString(font, "Yaw " + Math.round(yaw) + "°", previewX, previewY + viewSize + 8, 0xFF8792A2, false);
        graphics.drawString(font, status, textureX, textureY + viewSize + 8, 0xFF8792A2, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (insideTexture(event.x(), event.y()) && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            paintingTexture = true;
            beginHistoryEdit();
            paintTextureAt(event.x(), event.y());
            return true;
        }
        if (insideTexture(event.x(), event.y()) && (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && textureZoomIndex != FIT_TEXTURE_ZOOM_INDEX) {
            panningTexture = true;
            lastPanX = event.x();
            lastPanY = event.y();
            return true;
        }
        if (insidePreview(event.x(), event.y()) && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rotating = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (paintingTexture) {
            paintTextureAt(event.x(), event.y());
            return true;
        }
        if (panningTexture) {
            panTexture(event.x() - lastPanX, event.y() - lastPanY);
            lastPanX = event.x();
            lastPanY = event.y();
            return true;
        }
        if (rotating) {
            yaw = (float) ((yaw - dragX * 0.6) % 360.0);
            previewDirty = true;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        paintingTexture = false;
        panningTexture = false;
        rotating = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (control && keyCode == GLFW.GLFW_KEY_Z && !shift) {
            undo();
            return true;
        }
        if (control && (keyCode == GLFW.GLFW_KEY_Y || keyCode == GLFW.GLFW_KEY_Z && shift)) {
            redo();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoomTexture(1, textureX + viewSize / 2.0, textureY + viewSize / 2.0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomTexture(-1, textureX + viewSize / 2.0, textureY + viewSize / 2.0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_0) {
            resetTextureZoom();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideTexture(mouseX, mouseY)) {
            zoomTexture(scrollY > 0.0 ? 1 : -1, mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public void removed() {
        super.removed();
        if (preview != null) {
            preview.image().close();
            preview = null;
        }
        clearHistory(undoStack);
        clearHistory(redoStack);
    }

    private void drawImagePane(GuiGraphics graphics, Identifier location, int x, int y, int size) {
        graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF050608);
        if (location != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, 0.0f, 0.0f, size, size, size, size, size, size);
        }
        graphics.renderOutline(x, y, size, size, 0xFF4A5568);
    }

    private void drawAnnotations(GuiGraphics graphics) {
        for (TextureAnnotation annotation : annotations) {
            int left = texturePixelScreenX(annotation.x());
            int top = texturePixelScreenY(annotation.y());
            int right = texturePixelScreenX(annotation.x() + annotation.width());
            int bottom = texturePixelScreenY(annotation.y() + annotation.height());
            int clippedLeft = Math.max(textureX, left);
            int clippedTop = Math.max(textureY, top);
            int clippedRight = Math.min(textureX + viewSize, right);
            int clippedBottom = Math.min(textureY + viewSize, bottom);
            if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) {
                continue;
            }
            int w = clippedRight - clippedLeft;
            int h = clippedBottom - clippedTop;
            graphics.renderOutline(clippedLeft, clippedTop, w, h, annotation.color());
            if (w > font.width(annotation.name()) + 6 && h > 12) {
                graphics.fill(clippedLeft + 1, clippedTop + 1, clippedLeft + Math.min(w - 1, font.width(annotation.name()) + 5), clippedTop + 11, 0xAA050608);
                graphics.drawString(font, annotation.name(), clippedLeft + 3, clippedTop + 3, annotation.color(), false);
            }
        }
    }

    private void drawTextureCoordinate(GuiGraphics graphics, int mouseX, int mouseY) {
        int u = textureUCoordinate(mouseX);
        int v = textureVCoordinate(mouseY);
        graphics.drawString(font, "u " + u + "  v " + v, textureX, textureY + viewSize + 20, 0xFFB9C2CC, false);
    }

    private void drawBrushCursor(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerU = textureUCoordinate(mouseX);
        int centerV = textureVCoordinate(mouseY);
        if (brushRadius <= 1) {
            int x = texturePixelScreenX(centerU);
            int y = texturePixelScreenY(centerV);
            int size = texturePixelScreenSize();
            graphics.renderOutline(x, y, size, size, brushColor());
            return;
        }
        int diameter = Math.max(1, Math.round((brushRadius * 2.0f - 1.0f) * texturePixelScreenSize()));
        int x = texturePixelScreenX(centerU) - diameter / 2;
        int y = texturePixelScreenY(centerV) - diameter / 2;
        graphics.renderOutline(x, y, diameter, diameter, brushColor());
    }

    private void paintTextureAt(double screenX, double screenY) {
        int centerU = textureUCoordinate(screenX);
        int centerV = textureVCoordinate(screenY);
        int color = brushColor();
        if (brushRadius <= 1) {
            liveryImage.setPixel(centerU, centerV, color);
        } else {
            for (int dy = -brushRadius; dy <= brushRadius; dy++) {
                for (int dx = -brushRadius; dx <= brushRadius; dx++) {
                    if (dx * dx + dy * dy > brushRadius * brushRadius) {
                        continue;
                    }
                    int u = centerU + dx;
                    int v = centerV + dy;
                    if (u < 0 || v < 0 || u >= ClientLiveryTextures.SIZE || v >= ClientLiveryTextures.SIZE) {
                        continue;
                    }
                    liveryImage.setPixel(u, v, color);
                }
            }
        }
        previewDirty = true;
        textureDirty = true;
    }

    private int textureUCoordinate(double screenX) {
        int localX = Math.max(0, Math.min(viewSize - 1, (int) (screenX - textureX)));
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return clamp(localX * ClientLiveryTextures.SIZE / viewSize, 0, ClientLiveryTextures.SIZE - 1);
        }
        return clamp(textureViewU + localX / textureZoomFactor(), 0, ClientLiveryTextures.SIZE - 1);
    }

    private int textureVCoordinate(double screenY) {
        int localY = Math.max(0, Math.min(viewSize - 1, (int) (screenY - textureY)));
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return clamp(localY * ClientLiveryTextures.SIZE / viewSize, 0, ClientLiveryTextures.SIZE - 1);
        }
        return clamp(textureViewV + localY / textureZoomFactor(), 0, ClientLiveryTextures.SIZE - 1);
    }

    private NativeImage renderTextureView() {
        NativeImage image = new NativeImage(viewSize, viewSize, true);
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            for (int y = 0; y < viewSize; y++) {
                int sourceV = y * ClientLiveryTextures.SIZE / viewSize;
                for (int x = 0; x < viewSize; x++) {
                    int sourceU = x * ClientLiveryTextures.SIZE / viewSize;
                    image.setPixel(x, y, liveryImage.getPixel(sourceU, sourceV));
                }
            }
            return image;
        }
        int factor = textureZoomFactor();
        for (int y = 0; y < viewSize; y++) {
            int sourceV = clamp(textureViewV + y / factor, 0, ClientLiveryTextures.SIZE - 1);
            for (int x = 0; x < viewSize; x++) {
                int sourceU = clamp(textureViewU + x / factor, 0, ClientLiveryTextures.SIZE - 1);
                image.setPixel(x, y, liveryImage.getPixel(sourceU, sourceV));
            }
        }
        return image;
    }

    private void zoomTexture(int delta, double focusX, double focusY) {
        int nextZoomIndex = clamp(textureZoomIndex + delta, FIT_TEXTURE_ZOOM_INDEX, MAX_TEXTURE_ZOOM_INDEX);
        if (nextZoomIndex == textureZoomIndex) {
            status = "Texture zoom " + textureZoomLabel();
            return;
        }
        int focusU = textureUCoordinate(focusX);
        int focusV = textureVCoordinate(focusY);
        int localX = Math.max(0, Math.min(viewSize - 1, (int) (focusX - textureX)));
        int localY = Math.max(0, Math.min(viewSize - 1, (int) (focusY - textureY)));
        textureZoomIndex = nextZoomIndex;
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            textureViewU = 0;
            textureViewV = 0;
        } else {
            int factor = textureZoomFactor();
            textureViewU = focusU - localX / factor;
            textureViewV = focusV - localY / factor;
            clampTextureView();
        }
        textureDirty = true;
        status = "Texture zoom " + textureZoomLabel();
    }

    private void resetTextureZoom() {
        textureZoomIndex = FIT_TEXTURE_ZOOM_INDEX;
        textureViewU = 0;
        textureViewV = 0;
        textureDirty = true;
        status = "Texture zoom Fit";
    }

    private void panTexture(double dragX, double dragY) {
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return;
        }
        int oldU = textureViewU;
        int oldV = textureViewV;
        int factor = textureZoomFactor();
        textureViewU -= Math.round((float) dragX / factor);
        textureViewV -= Math.round((float) dragY / factor);
        clampTextureView();
        if (oldU != textureViewU || oldV != textureViewV) {
            textureDirty = true;
        }
    }

    private void clampTextureView() {
        int visible = visibleTexturePixels();
        textureViewU = clamp(textureViewU, 0, Math.max(0, ClientLiveryTextures.SIZE - visible));
        textureViewV = clamp(textureViewV, 0, Math.max(0, ClientLiveryTextures.SIZE - visible));
    }

    private int visibleTexturePixels() {
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return ClientLiveryTextures.SIZE;
        }
        return Math.max(1, (viewSize + textureZoomFactor() - 1) / textureZoomFactor());
    }

    private int textureZoomFactor() {
        return TEXTURE_ZOOM_FACTORS[textureZoomIndex];
    }

    private String textureZoomLabel() {
        return textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX ? "Fit" : textureZoomFactor() + "x";
    }

    private int texturePixelScreenX(int u) {
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return textureX + u * viewSize / ClientLiveryTextures.SIZE;
        }
        return textureX + (u - textureViewU) * textureZoomFactor();
    }

    private int texturePixelScreenY(int v) {
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return textureY + v * viewSize / ClientLiveryTextures.SIZE;
        }
        return textureY + (v - textureViewV) * textureZoomFactor();
    }

    private int texturePixelScreenSize() {
        if (textureZoomIndex == FIT_TEXTURE_ZOOM_INDEX) {
            return Math.max(1, Math.round(viewSize / (float) ClientLiveryTextures.SIZE));
        }
        return textureZoomFactor();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void rebuildPreview() {
        if (preview != null) {
            preview.image().close();
        }
        preview = LiveryPreviewRenderer.render(liveryImage, PrototypeCarItem.getLiveryColors(stack), yaw, viewSize, viewSize);
    }

    private void save() {
        try {
            if (texture == null || !texture.isPresent()) {
                texture = ClientLiveryTextures.saveNew(Minecraft.getInstance(), liveryImage);
            } else {
                ClientLiveryTextures.save(Minecraft.getInstance(), texture.id(), liveryImage);
            }
            byte[] pngBytes = ClientLiveryTextures.readPngBytes(Minecraft.getInstance(), texture.id());
            OWRNetwork.sendToServer(new OWRNetwork.UploadLiveryTextureMessage(texture.id(), pngBytes));
            OWRNetwork.sendToServer(new OWRNetwork.SetLiveryTextureMessage(texture.id()));
            OpenwheelCarRenderer.invalidateLiveryCache(texture.id());
            status = "Saved " + texture.id() + ".png";
        } catch (IOException e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private void reset() {
        beginHistoryEdit();
        ClientLiveryTextures.fillTemplate(liveryImage, PrototypeCarItem.getLiveryColors(stack).body());
        previewDirty = true;
        textureDirty = true;
        status = "Template reset";
    }

    private void beginHistoryEdit() {
        undoStack.addLast(copyImage(liveryImage));
        while (undoStack.size() > MAX_HISTORY) {
            NativeImage removed = undoStack.removeFirst();
            removed.close();
        }
        clearHistory(redoStack);
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            status = "Nothing to undo";
            return;
        }
        redoStack.addLast(copyImage(liveryImage));
        replaceLiveryImage(undoStack.removeLast());
        status = "Undo";
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            status = "Nothing to redo";
            return;
        }
        undoStack.addLast(copyImage(liveryImage));
        replaceLiveryImage(redoStack.removeLast());
        status = "Redo";
    }

    private void replaceLiveryImage(NativeImage image) {
        NativeImage old = liveryImage;
        liveryImage = image;
        old.close();
        previewDirty = true;
        textureDirty = true;
    }

    private void clearHistory(ArrayDeque<NativeImage> stack) {
        while (!stack.isEmpty()) {
            stack.removeLast().close();
        }
    }

    private List<TextureAnnotation> buildAnnotations() {
        List<TextureAnnotation> result = new ArrayList<>();
        int colorIndex = 0;
        for (TemplateRegion region : ClientLiveryTextures.templateRegions(Minecraft.getInstance())) {
            result.add(new TextureAnnotation(annotationName(region.group()), region.x(), region.y(), region.width(), region.height(), annotationColor(colorIndex++)));
        }
        return result;
    }

    private String annotationName(String group) {
        return switch (group) {
            case "Chassis" -> "Body";
            case "Left-FW-Endplate" -> "Left FW";
            case "Right-FW-Endplate" -> "Right FW";
            case "Wheel_Front_Left" -> "FL wheel";
            case "Wheel_Front_Right" -> "FR wheel";
            case "Wheel_Rear_Left" -> "RL wheel";
            case "Wheel_Rear_Right" -> "RR wheel";
            default -> group;
        };
    }

    private int annotationColor(int index) {
        int[] colors = {0xFF4ADE80, 0xFF60A5FA, 0xFFFACC15, 0xFFF472B6, 0xFFC084FC, 0xFF22D3EE};
        return colors[index % colors.length];
    }

    private boolean insidePreview(double x, double y) {
        return x >= previewX && y >= previewY && x < previewX + viewSize && y < previewY + viewSize;
    }

    private boolean insideTexture(double x, double y) {
        return x >= textureX && y >= textureY && x < textureX + viewSize && y < textureY + viewSize;
    }

    private int brushColor() {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), true);
        copy.copyFrom(source);
        return copy;
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private class RgbSlider extends AbstractSliderButton {
        private final int channel;

        private RgbSlider(int x, int y, int width, int height, int channel) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.channel = channel;
            value = currentValue() / 255.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name() + " " + currentValue()));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(value * 255.0);
            switch (channel) {
                case 0 -> red = v;
                case 1 -> green = v;
                case 2 -> blue = v;
                default -> {}
            }
        }

        private int currentValue() {
            return switch (channel) {
                case 0 -> red;
                case 1 -> green;
                case 2 -> blue;
                default -> 0;
            };
        }

        private String name() {
            return switch (channel) {
                case 0 -> "R";
                case 1 -> "G";
                case 2 -> "B";
                default -> "?";
            };
        }
    }

    private class BrushSlider extends AbstractSliderButton {
        private BrushSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (brushRadius - 1) / 31.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Radius " + brushRadius));
        }

        @Override
        protected void applyValue() {
            brushRadius = 1 + (int) Math.round(value * 31.0);
        }
    }

    private record TextureAnnotation(String name, int x, int y, int width, int height, int color) {}
}
