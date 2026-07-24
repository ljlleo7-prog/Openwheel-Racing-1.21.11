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
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class LiveryEditorScreen extends Screen {
    private static final int PREVIEW_SIZE = 320;
    private static final String PREVIEW_TEX_ID = "editor_3d_preview";
    private final Screen parent;
    private final ItemStack stack;

    // The 512×512 livery canvas written to disk
    private NativeImage liveryImage;
    private CarLiveryTexture texture;

    // Current 3D preview; rebuilt when dirty
    private LiveryPreviewRenderer.Preview preview;
    private boolean previewDirty = true;

    // Brush
    private int red = 235;
    private int green = 18;
    private int blue = 32;
    private int brushRadius = 8;

    // Interaction
    private float yaw = 35.0f;
    private boolean painting;
    private boolean rotating;

    // Layout
    private int previewX;
    private int previewY;
    private int panelX;
    private int panelY;

    private String status = "Left-drag to paint, right-drag to rotate";

    public LiveryEditorScreen(Screen parent, ItemStack stack) {
        super(Component.literal("Livery Editor"));
        this.parent = parent;
        this.stack = stack.copy();
    }

    @Override
    protected void init() {
        previewX = Math.max(8, (width - PREVIEW_SIZE) / 2 - 110);
        previewY = Math.max(28, (height - PREVIEW_SIZE) / 2);
        panelX = previewX + PREVIEW_SIZE + 20;
        panelY = previewY;

        texture = PrototypeCarItem.getLiveryTexture(stack);
        if (liveryImage == null) {
            liveryImage = ClientLiveryTextures.loadOrCreate(Minecraft.getInstance(), texture, PrototypeCarItem.getLiveryColors(stack).body());
            previewDirty = true;
        }

        clearWidgets();
        int y = panelY;
        addRenderableWidget(new RgbSlider(panelX, y + 28, 150, 18, 0));
        addRenderableWidget(new RgbSlider(panelX, y + 54, 150, 18, 1));
        addRenderableWidget(new RgbSlider(panelX, y + 80, 150, 18, 2));
        addRenderableWidget(new BrushSlider(panelX, y + 118, 150, 18));
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
            .bounds(panelX, y + 158, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> closeToParent())
            .bounds(panelX + 80, y + 158, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> reset())
            .bounds(panelX, y + 186, 150, 20).build());
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
            previewDirty = false;
        }

        // Upload and blit 3D preview
        if (preview != null) {
            Identifier loc = ClientLiveryTextures.register(Minecraft.getInstance(), PREVIEW_TEX_ID, copyPreview());
            graphics.fill(previewX - 2, previewY - 2, previewX + PREVIEW_SIZE + 2, previewY + PREVIEW_SIZE + 2, 0xFF050608);
            graphics.blit(RenderPipelines.GUI_TEXTURED, loc, previewX, previewY, 0.0f, 0.0f, PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_SIZE);
            graphics.renderOutline(previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE, 0xFF4A5568);
        }

        // Brush cursor ring on preview
        if (insidePreview(mouseX, mouseY)) {
            float scale = (float) PREVIEW_SIZE / ClientLiveryTextures.SIZE;
            int r = Math.max(1, Math.round(brushRadius * scale));
            graphics.renderOutline(mouseX - r, mouseY - r, r * 2, r * 2, brushColor());
        }

        // Side panel
        graphics.drawString(font, "Livery Editor", panelX, panelY + 2, 0xFFE8EDF2, false);
        graphics.drawString(font, "Brush color", panelX, panelY + 14, 0xFFB9C2CC, false);
        int color = brushColor();
        graphics.fill(panelX + 160, panelY + 28, panelX + 202, panelY + 70, color);
        graphics.renderOutline(panelX + 160, panelY + 28, 42, 42, 0xFF8792A2);
        graphics.drawString(font, CarLiveryColors.colorName(color), panelX + 154, panelY + 76, 0xFFB9C2CC, false);
        graphics.drawString(font, "Brush r=" + brushRadius, panelX, panelY + 104, 0xFFE8EDF2, false);
        graphics.drawString(font, "Yaw " + Math.round(yaw) + "°", panelX, panelY + 142, 0xFF8792A2, false);
        graphics.drawString(font, status, previewX, previewY + PREVIEW_SIZE + 8, 0xFF8792A2, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (insidePreview(event.x(), event.y())) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                painting = true;
                paintAt(event.x(), event.y());
                return true;
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rotating = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (painting) {
            paintAt(event.x(), event.y());
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
        painting = false;
        rotating = false;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void paintAt(double screenX, double screenY) {
        if (preview == null) {
            return;
        }
        // Convert screen coords to preview pixel coords
        int cx = (int) ((screenX - previewX) * PREVIEW_SIZE / PREVIEW_SIZE);
        int cy = (int) ((screenY - previewY) * PREVIEW_SIZE / PREVIEW_SIZE);
        cx = Math.max(0, Math.min(PREVIEW_SIZE - 1, cx));
        cy = Math.max(0, Math.min(PREVIEW_SIZE - 1, cy));
        // Brush footprint in preview space
        int color = brushColor();
        boolean painted = false;
        for (int dy = -brushRadius; dy <= brushRadius; dy++) {
            for (int dx = -brushRadius; dx <= brushRadius; dx++) {
                if (dx * dx + dy * dy > brushRadius * brushRadius) {
                    continue;
                }
                int px = cx + dx;
                int py = cy + dy;
                if (px < 0 || py < 0 || px >= PREVIEW_SIZE || py >= PREVIEW_SIZE) {
                    continue;
                }
                int u = preview.hitU(px, py);
                int v = preview.hitV(px, py);
                if (u < 0 || v < 0) {
                    continue;
                }
                liveryImage.setPixel(u, v, color);
                painted = true;
            }
        }
        if (painted) {
            previewDirty = true;
        }
    }

    private void rebuildPreview() {
        if (preview != null) {
            preview.image().close();
        }
        preview = LiveryPreviewRenderer.render(liveryImage, PrototypeCarItem.getLiveryColors(stack), yaw, PREVIEW_SIZE, PREVIEW_SIZE);
    }

    private NativeImage copyPreview() {
        NativeImage copy = new NativeImage(preview.width(), preview.height(), true);
        copy.copyFrom(preview.image());
        return copy;
    }

    private void save() {
        try {
            if (texture == null || !texture.isPresent()) {
                texture = ClientLiveryTextures.saveNew(Minecraft.getInstance(), liveryImage);
            } else {
                ClientLiveryTextures.save(Minecraft.getInstance(), texture.id(), liveryImage);
            }
            OWRNetwork.CHANNEL.send(new OWRNetwork.SetLiveryTextureMessage(texture.id()), PacketDistributor.SERVER.noArg());
            OpenwheelCarRenderer.invalidateLiveryCache(texture.id());
            status = "Saved ✓  " + texture.id() + ".png";
        } catch (IOException e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private void reset() {
        ClientLiveryTextures.fillTemplate(liveryImage, PrototypeCarItem.getLiveryColors(stack).body());
        previewDirty = true;
        status = "Template reset";
    }

    private boolean insidePreview(double x, double y) {
        return x >= previewX && y >= previewY && x < previewX + PREVIEW_SIZE && y < previewY + PREVIEW_SIZE;
    }

    private int brushColor() {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
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
            // Brush radius is in preview pixels; map to livery pixels below
        }
    }
}
