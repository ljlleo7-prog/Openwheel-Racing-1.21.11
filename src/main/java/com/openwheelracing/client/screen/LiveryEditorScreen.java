package com.openwheelracing.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.client.livery.ClientLiveryTextures;
import com.openwheelracing.client.livery.LiveryPreviewRenderer;
import com.openwheelracing.client.render.OpenwheelCarRenderer;
import com.openwheelracing.content.car.CarLiveryColors;
import com.openwheelracing.content.car.CarLiveryTexture;
import com.openwheelracing.content.item.PrototypeCarItem;
import com.openwheelracing.network.OWRNetwork;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class LiveryEditorScreen extends Screen {
    private static final int MAX_VIEW_SIZE = 320;
    private static final int MIN_VIEW_SIZE = 192;
    private static final String PREVIEW_TEX_ID = "editor_3d_preview";
    private static final String TEXTURE_TEX_ID = "editor_livery_canvas";
    private static final TextureAnnotation[] ANNOTATIONS = {
        new TextureAnnotation("Rear chassis", 24, 24, 216, 128, 0xFF4ADE80),
        new TextureAnnotation("Front chassis", 272, 24, 216, 128, 0xFF60A5FA),
        new TextureAnnotation("Left front wing", 24, 184, 216, 56, 0xFFFACC15),
        new TextureAnnotation("Right front wing", 272, 184, 216, 56, 0xFFFACC15),
        new TextureAnnotation("FL wheel", 24, 288, 96, 64, 0xFFF472B6),
        new TextureAnnotation("FR wheel", 144, 288, 96, 64, 0xFFF472B6),
        new TextureAnnotation("RL wheel", 272, 288, 96, 72, 0xFFC084FC),
        new TextureAnnotation("RR wheel", 392, 288, 96, 72, 0xFFC084FC)
    };

    private final Screen parent;
    private final ItemStack stack;

    private NativeImage liveryImage;
    private CarLiveryTexture texture;

    private LiveryPreviewRenderer.Preview preview;
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
    private boolean rotating;

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

        texture = PrototypeCarItem.getLiveryTexture(stack);
        if (liveryImage == null) {
            NativeImage loaded = ClientLiveryTextures.loadOrCreate(Minecraft.getInstance(), texture, PrototypeCarItem.getLiveryColors(stack).body());
            liveryImage = texture != null && texture.isPresent() ? copyImage(loaded) : loaded;
            previewDirty = true;
            textureDirty = true;
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
            .bounds(controlsX + 150, controlsY + 72, 128, 20).build());
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
            textureLocation = ClientLiveryTextures.register(Minecraft.getInstance(), TEXTURE_TEX_ID, copyImage(liveryImage));
            textureDirty = false;
        }

        graphics.drawString(font, "3D preview", previewX, previewY - 14, 0xFFE8EDF2, false);
        graphics.drawString(font, "Raw 512x512 texture", textureX, textureY - 14, 0xFFE8EDF2, false);
        drawImagePane(graphics, previewLocation, previewX, previewY, viewSize);
        drawImagePane(graphics, textureLocation, textureX, textureY, viewSize);
        drawAnnotations(graphics);

        if (insideTexture(mouseX, mouseY)) {
            int r = Math.max(1, Math.round(brushRadius * (float) viewSize / ClientLiveryTextures.SIZE));
            graphics.renderOutline(mouseX - r, mouseY - r, r * 2, r * 2, brushColor());
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
            paintTextureAt(event.x(), event.y());
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
        rotating = false;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void drawImagePane(GuiGraphics graphics, Identifier location, int x, int y, int size) {
        graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF050608);
        if (location != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, 0.0f, 0.0f, size, size, size, size, size, size);
        }
        graphics.renderOutline(x, y, size, size, 0xFF4A5568);
    }

    private void drawAnnotations(GuiGraphics graphics) {
        for (TextureAnnotation annotation : ANNOTATIONS) {
            int x = textureX + annotation.x() * viewSize / ClientLiveryTextures.SIZE;
            int y = textureY + annotation.y() * viewSize / ClientLiveryTextures.SIZE;
            int w = Math.max(1, annotation.width() * viewSize / ClientLiveryTextures.SIZE);
            int h = Math.max(1, annotation.height() * viewSize / ClientLiveryTextures.SIZE);
            graphics.renderOutline(x, y, w, h, annotation.color());
            graphics.fill(x + 1, y + 1, x + Math.min(w - 1, font.width(annotation.name()) + 5), y + 11, 0xAA050608);
            graphics.drawString(font, annotation.name(), x + 3, y + 3, annotation.color(), false);
        }
    }

    private void drawTextureCoordinate(GuiGraphics graphics, int mouseX, int mouseY) {
        int u = textureCoordinate(mouseX, textureX);
        int v = textureCoordinate(mouseY, textureY);
        graphics.drawString(font, "u " + u + "  v " + v, textureX, textureY + viewSize + 20, 0xFFB9C2CC, false);
    }

    private void paintTextureAt(double screenX, double screenY) {
        int centerU = textureCoordinate(screenX, textureX);
        int centerV = textureCoordinate(screenY, textureY);
        int color = brushColor();
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
        previewDirty = true;
        textureDirty = true;
    }

    private int textureCoordinate(double screenCoordinate, int paneStart) {
        int value = (int) ((screenCoordinate - paneStart) * ClientLiveryTextures.SIZE / viewSize);
        return Math.max(0, Math.min(ClientLiveryTextures.SIZE - 1, value));
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
            OWRNetwork.sendToServer(new OWRNetwork.SetLiveryTextureMessage(texture.id()));
            OpenwheelCarRenderer.invalidateLiveryCache(texture.id());
            status = "Saved " + texture.id() + ".png";
        } catch (IOException e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private void reset() {
        ClientLiveryTextures.fillTemplate(liveryImage, PrototypeCarItem.getLiveryColors(stack).body());
        previewDirty = true;
        textureDirty = true;
        status = "Template reset";
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
            super(x, y, width, height, Component.empty(), (brushRadius - 1) / 47.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Radius " + brushRadius));
        }

        @Override
        protected void applyValue() {
            brushRadius = 1 + (int) Math.round(value * 47.0);
        }
    }

    private record TextureAnnotation(String name, int x, int y, int width, int height, int color) {}
}
