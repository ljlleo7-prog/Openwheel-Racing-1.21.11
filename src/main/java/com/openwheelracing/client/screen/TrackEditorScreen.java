package com.openwheelracing.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.track.TrackEditorMaterial;
import com.openwheelracing.content.track.TrackEditorMode;
import com.openwheelracing.content.track.TrackEditorOperation;
import com.openwheelracing.content.track.TrackEditorPreset;
import com.openwheelracing.network.OWRNetwork;
import com.openwheelracing.registry.OWRBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TrackEditorScreen extends Screen {
    private static final TrackEditorMode[] MODES = TrackEditorMode.values();
    private static final TrackEditorMaterial[] PAVEMENT_MATERIALS = {
        TrackEditorMaterial.ASPHALT,
        TrackEditorMaterial.PIT_LANE,
        TrackEditorMaterial.WHITE_CONCRETE,
        TrackEditorMaterial.LIGHT_GRAY_CONCRETE,
        TrackEditorMaterial.GRAY_CONCRETE,
        TrackEditorMaterial.BLACK_CONCRETE,
        TrackEditorMaterial.RED_CONCRETE,
        TrackEditorMaterial.CYAN_CONCRETE,
        TrackEditorMaterial.BLUE_CONCRETE,
        TrackEditorMaterial.SAND,
        TrackEditorMaterial.GRASS,
        TrackEditorMaterial.DIRT,
        TrackEditorMaterial.GRAVEL
    };
    private static final TrackEditorMaterial[] EDGE_MATERIALS = {
        TrackEditorMaterial.KERB,
        TrackEditorMaterial.BARRIER
    };
    private static final TrackEditorMaterial[] RUNOFF_MATERIALS = {
        TrackEditorMaterial.GRAVEL,
        TrackEditorMaterial.SAND,
        TrackEditorMaterial.DIRT,
        TrackEditorMaterial.GRASS,
        TrackEditorMaterial.LIGHT_GRAY_CONCRETE,
        TrackEditorMaterial.GRAY_CONCRETE,
        TrackEditorMaterial.RED_CONCRETE,
        TrackEditorMaterial.CYAN_CONCRETE,
        TrackEditorMaterial.BLUE_CONCRETE
    };
    private static final TrackEditorPreset[] PRESETS = TrackEditorPreset.values();
    private static final double[] ZOOM_LEVELS = {0.25, 0.5, 1.0, 2.0, 4.0};
    private static final int OUTER_MARGIN = 8;
    private static final int CONTROL_STRIP_HEIGHT = 34;
    private static final int QUEUE_CHUNK_SIZE = 64;
    private static final int QUEUE_SEND_DISTANCE = 480;
    private static final int QUEUE_SENDS_PER_TICK = 2;
    private static final int TERRAIN_SAMPLES_PER_FRAME = 384;
    private static final int TERRAIN_PRELOAD_SAMPLES_PER_TICK = 12;
    private static final int TERRAIN_PRELOAD_RADIUS_BLOCKS = 192;
    private static final int TERRAIN_TILE_BLOCKS = 64;
    private static final int MAX_TERRAIN_TILE_CACHE_SIZE = 8192;
    private static final int TERRAIN_CACHE_FLUSH_TILE_LIMIT = 2;
    private static final int TERRAIN_CACHE_FLUSH_TICKS = 600;
    private static final int TERRAIN_TILE_SAMPLE_BURST = 32;
    private static final int TERRAIN_IO_COMPLETIONS_PER_TICK = 4;
    private static final int TERRAIN_TEXTURE_UPLOADS_PER_FRAME = 12;
    private static final int MAX_TERRAIN_IO_QUEUE_SIZE = 512;
    private static final double IMPORT_SAMPLE_SPACING = 1.0;
    private static final String IMPORT_PATH = "openwheelracing/imports/lap-simulator-track.json";
    private static final List<PendingEditorOperation> PENDING_QUEUE = new ArrayList<>();
    private static final ThreadFactory TERRAIN_IO_THREAD_FACTORY = task -> {
        Thread thread = new Thread(task, "OWR Terrain Tile IO");
        thread.setDaemon(true);
        return thread;
    };
    private static final ExecutorService TERRAIN_IO_EXECUTOR = Executors.newSingleThreadExecutor(TERRAIN_IO_THREAD_FACTORY);
    private static final ArrayDeque<TerrainTileLoadResult> TERRAIN_TILE_LOAD_RESULTS = new ArrayDeque<>();
    private static final Set<Long> TERRAIN_TILE_LOADING = new HashSet<>();
    private static final Set<Long> TERRAIN_TILE_IO_MISSES = new HashSet<>();
    private static final Map<Long, TerrainTile> TERRAIN_TILES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, TerrainTile> eldest) {
            if (size() <= MAX_TERRAIN_TILE_CACHE_SIZE) {
                return false;
            }
            TerrainTile tile = eldest.getValue();
            if (tile.dirty) {
                queueTerrainTileSave(Minecraft.getInstance(), tile);
            }
            tile.closeTexture(Minecraft.getInstance());
            return true;
        }
    };
    private static int savedModeIndex;
    private static int savedPavementIndex;
    private static int savedEdgeIndex;
    private static int savedRunoffMaterialIndex;
    private static int savedPresetIndex;
    private static int savedZoomIndex = 2;
    private static int savedTrackWidth = 3;
    private static int savedClearHeight = 3;
    private static boolean savedSurfaceElevationMode = true;
    private static boolean savedFullSurfaceApplication;
    private static double savedCenterX = Double.MAX_VALUE;
    private static double savedCenterZ = Double.MAX_VALUE;
    private static int savedEditY;
    private static boolean terrainCacheLoaded;
    private static String terrainCacheNamespace = "unknown";
    private static int preloadCenterX = Integer.MIN_VALUE;
    private static int preloadCenterZ = Integer.MIN_VALUE;
    private static int preloadCenterY;
    private static int preloadCursor;
    private static boolean terrainCacheDirty;
    private static int terrainCacheFlushTicks;

    private final List<BlockPos> points = new ArrayList<>();
    private int modeIndex = savedModeIndex;
    private int pavementIndex = savedPavementIndex;
    private int edgeIndex = savedEdgeIndex;
    private int runoffMaterialIndex = savedRunoffMaterialIndex;
    private int presetIndex = savedPresetIndex;
    private int zoomIndex = savedZoomIndex;
    private int trackWidth = savedTrackWidth;
    private int editY = savedEditY;
    private double centerX = savedCenterX;
    private double centerZ = savedCenterZ;
    private boolean initialized;
    private boolean dragging;
    private double lastDragX;
    private double lastDragY;
    private int terrainSamplesThisFrame;
    private OverlayNotice notice;
    private Button clearQueueButton;
    private Button rerenderButton;
    private Button elevationModeButton;
    private Button surfaceApplicationButton;
    private ClearHeightSlider clearHeightSlider;
    private int lastQueuedCount;
    private int clearHeight = savedClearHeight;
    private boolean surfaceElevationMode = savedSurfaceElevationMode;
    private boolean fullSurfaceApplication = savedFullSurfaceApplication;
    private boolean showHelp;

    public TrackEditorScreen() {
        super(Component.translatable("screen.openwheelracing.track_editor"));
    }

    @Override
    public void onClose() {
        savedModeIndex = modeIndex;
        savedPavementIndex = pavementIndex;
        savedEdgeIndex = edgeIndex;
        savedRunoffMaterialIndex = runoffMaterialIndex;
        savedPresetIndex = presetIndex;
        savedZoomIndex = zoomIndex;
        savedTrackWidth = trackWidth;
        savedClearHeight = clearHeight;
        savedSurfaceElevationMode = surfaceElevationMode;
        savedFullSurfaceApplication = fullSurfaceApplication;
        savedCenterX = centerX;
        savedCenterZ = centerZ;
        savedEditY = editY;
        if (minecraft != null) {
            flushDirtyTerrainTiles(minecraft, TERRAIN_CACHE_FLUSH_TILE_LIMIT);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.player != null) {
            if (savedCenterX == Double.MAX_VALUE) {
                centerX = minecraft.player.getX();
                centerZ = minecraft.player.getZ();
                savedCenterX = centerX;
                savedCenterZ = centerZ;
                savedEditY = minecraft.player.blockPosition().getY();
                editY = savedEditY;
            }
            if (!terrainCacheLoaded) {
                terrainCacheLoaded = true;
                loadTerrainCache(minecraft.gameDirectory.toPath());
            }
        }
        initialized = true;
        MapBounds map = mapBounds();
        int controlTop = map.bottom + 6;
        int gap = 8;
        int buttonWidth = Math.min(104, Math.max(72, (map.width() - gap * 4) / 5));
        int sliderX = map.left + (buttonWidth + gap) * 3;
        int sliderWidth = Math.max(104, map.right - buttonWidth - gap - sliderX);
        elevationModeButton = addRenderableWidget(Button.builder(elevationModeLabel(), button -> toggleElevationMode())
            .bounds(map.left, controlTop, buttonWidth, 20)
            .build());
        surfaceApplicationButton = addRenderableWidget(Button.builder(surfaceApplicationLabel(), button -> toggleSurfaceApplication())
            .bounds(map.left + buttonWidth + gap, controlTop, buttonWidth, 20)
            .build());
        rerenderButton = addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.track_editor.rerender_tiles"), button -> rerenderNearbyTerrain())
            .bounds(map.left + (buttonWidth + gap) * 2, controlTop, buttonWidth, 20)
            .build());
        clearHeightSlider = addRenderableWidget(new ClearHeightSlider(sliderX, controlTop, sliderWidth, 20));
        clearQueueButton = addRenderableWidget(Button.builder(Component.translatable("screen.openwheelracing.track_editor.clear_queue"), button -> clearPendingQueue())
            .bounds(map.right - buttonWidth, controlTop, buttonWidth, 20)
            .build());
        updateClearQueueButton();
        updateToggleButtons();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideMap(event.x(), event.y())) {
            addPoint(screenToBlock(event.x(), event.y()));
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            removeLastPoint();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && isInsideMap(event.x(), event.y())) {
            dragging = true;
            lastDragX = event.x();
            lastDragY = event.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            centerX -= (event.x() - lastDragX) * blocksPerPixel();
            centerZ -= (event.y() - lastDragY) * blocksPerPixel();
            lastDragX = event.x();
            lastDragY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideMap(mouseX, mouseY)) {
            zoomIndex = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, zoomIndex + (scrollY > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if ((event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_Z) {
            OWRNetwork.sendToServer(new OWRNetwork.TrackEditorUndoMessage());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C) {
            clearPendingQueue();
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_M -> {
                modeIndex = (modeIndex + 1) % MODES.length;
                points.clear();
                return true;
            }
            case GLFW.GLFW_KEY_N -> {
                cycleMaterial();
                return true;
            }
            case GLFW.GLFW_KEY_P -> {
                presetIndex = (presetIndex + 1) % PRESETS.length;
                return true;
            }
            case GLFW.GLFW_KEY_O -> {
                runoffMaterialIndex = (runoffMaterialIndex + 1) % RUNOFF_MATERIALS.length;
                return true;
            }
            case GLFW.GLFW_KEY_I -> {
                importLapSimulatorTrack();
                return true;
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                trackWidth = Math.min(TrackEditorOperation.MAX_WIDTH, trackWidth + 1);
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                trackWidth = Math.max(1, trackWidth - 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (mode() == TrackEditorMode.POLYGON) {
                    commitIfReady();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                removeLastPoint();
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                recenterOnPlayer();
                return true;
            }
            case GLFW.GLFW_KEY_H -> {
                showHelp = !showHelp;
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                editY++;
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_LEFT_BRACKET -> {
                editY--;
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> {
                centerX -= panStep();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> {
                centerX += panStep();
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                centerZ -= panStep();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                centerZ += panStep();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        flushPendingQueue();
        processTerrainIoResults(TERRAIN_IO_COMPLETIONS_PER_TICK);
        sampleVisibleTerrain(TERRAIN_SAMPLES_PER_FRAME);
        flushTerrainCacheIfDue();
        if (notice != null && notice.ticksRemaining() > 0) {
            notice = new OverlayNotice(notice.text(), notice.color(), notice.ticksRemaining() - 1);
            if (notice.ticksRemaining() <= 0) {
                notice = null;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        terrainSamplesThisFrame = 0;
        renderMap(graphics);
        renderQueuedOperations(graphics);
        renderPendingGeometry(graphics);
        renderPlayerMarker(graphics);
        renderControlStrip(graphics);
        renderOverlayHud(graphics);
        renderNotice(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMap(GuiGraphics graphics) {
        MapBounds map = mapBounds();
        graphics.fill(map.left, map.top, map.right, map.bottom, 0xEE101214);
        renderTerrainSamples(graphics, map);
        renderGrid(graphics, map);
        renderScaleBar(graphics, map);
        graphics.renderOutline(map.left, map.top, map.width(), map.height(), 0xFFDA1A20);
    }

    private void renderTerrainSamples(GuiGraphics graphics, MapBounds map) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        int minWorldX = Math.min(screenToWorldX(map.left, map), screenToWorldX(map.right, map));
        int maxWorldX = Math.max(screenToWorldX(map.left, map), screenToWorldX(map.right, map));
        int minWorldZ = Math.min(screenToWorldZ(map.top, map), screenToWorldZ(map.bottom, map));
        int maxWorldZ = Math.max(screenToWorldZ(map.top, map), screenToWorldZ(map.bottom, map));
        int minTileX = tileCoord(minWorldX);
        int maxTileX = tileCoord(maxWorldX);
        int minTileZ = tileCoord(minWorldZ);
        int maxTileZ = tileCoord(maxWorldZ);
        int centerTileX = tileCoord((int) Math.floor(centerX));
        int centerTileZ = tileCoord((int) Math.floor(centerZ));
        int maxRadius = Math.max(Math.max(Math.abs(centerTileX - minTileX), Math.abs(centerTileX - maxTileX)), Math.max(Math.abs(centerTileZ - minTileZ), Math.abs(centerTileZ - maxTileZ)));
        int uploadsRemaining = TERRAIN_TEXTURE_UPLOADS_PER_FRAME;
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int tileX = centerTileX + dx;
                    int tileZ = centerTileZ + dz;
                    if (tileX < minTileX || tileX > maxTileX || tileZ < minTileZ || tileZ > maxTileZ) {
                        continue;
                    }
                    uploadsRemaining = renderTerrainTile(graphics, map, terrainTile(tileX, tileZ, editY), uploadsRemaining);
                }
            }
        }
    }

    private int renderTerrainTile(GuiGraphics graphics, MapBounds map, TerrainTile tile, int uploadsRemaining) {
        int left = Math.max(map.left, Math.min(worldToScreenX(tile.baseX), worldToScreenX(tile.baseX + TERRAIN_TILE_BLOCKS)));
        int right = Math.min(map.right, Math.max(worldToScreenX(tile.baseX), worldToScreenX(tile.baseX + TERRAIN_TILE_BLOCKS)));
        int top = Math.max(map.top, Math.min(worldToScreenY(tile.baseZ), worldToScreenY(tile.baseZ + TERRAIN_TILE_BLOCKS)));
        int bottom = Math.min(map.bottom, Math.max(worldToScreenY(tile.baseZ), worldToScreenY(tile.baseZ + TERRAIN_TILE_BLOCKS)));
        if (right <= left || bottom <= top) {
            return uploadsRemaining;
        }
        if (!tile.hasTexture() && !tile.hasSamples()) {
            return uploadsRemaining;
        }
        if (uploadsRemaining > 0 && tile.uploadTexture(Minecraft.getInstance())) {
            uploadsRemaining--;
        }
        Identifier location = tile.textureLocation();
        if (location != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, location, left, top, 0.0f, 0.0f, right - left, bottom - top, TERRAIN_TILE_BLOCKS, TERRAIN_TILE_BLOCKS, TERRAIN_TILE_BLOCKS, TERRAIN_TILE_BLOCKS);
        }
        return uploadsRemaining;
    }

    private void renderGrid(GuiGraphics graphics, MapBounds map) {
        double bpp = blocksPerPixel();
        int minorStep = gridStep();
        int startX = floorToStep(screenToBlock(map.left, map.top).getX(), minorStep) - minorStep;
        int endX = screenToBlock(map.right, map.top).getX() + minorStep;
        int startZ = floorToStep(screenToBlock(map.left, map.top).getZ(), minorStep) - minorStep;
        int endZ = screenToBlock(map.left, map.bottom).getZ() + minorStep;
        for (int x = startX; x <= endX; x += minorStep) {
            int sx = worldToScreenX(x);
            int color = x % 16 == 0 ? 0x88666666 : 0x44333333;
            graphics.fill(sx, map.top, sx + 1, map.bottom, color);
        }
        for (int z = startZ; z <= endZ; z += minorStep) {
            int sy = worldToScreenY(z);
            int color = z % 16 == 0 ? 0x88666666 : 0x44333333;
            graphics.fill(map.left, sy, map.right, sy + 1, color);
        }
    }

    private void renderScaleBar(GuiGraphics graphics, MapBounds map) {
        int fullLength = (int) Math.round(100.0 / blocksPerPixel());
        int maxLength = Math.max(48, map.width() - 40);
        int length = Math.min(fullLength, maxLength);
        int x = map.left + 16;
        int y = map.bottom - 24;
        graphics.fill(x - 6, y - 12, x + length + 58, y + 12, 0xAA000000);
        graphics.fill(x, y, x + length, y + 3, 0xFFFFFFFF);
        graphics.fill(x, y - 4, x + 2, y + 7, 0xFFFFFFFF);
        graphics.fill(x + length - 2, y - 4, x + length, y + 7, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.scale_100m"), x + length + 6, y - 4, 0xFFFFFFFF, false);
    }

    private void renderQueuedOperations(GuiGraphics graphics) {
        for (PendingEditorOperation queued : PENDING_QUEUE) {
            List<BlockPos> queuedPoints = queued.operation().points();
            for (int i = 1; i < queuedPoints.size(); i++) {
                drawLine(graphics, queuedPoints.get(i - 1), queuedPoints.get(i), 0xFF55FFAA);
            }
        }
    }

    private void renderPendingGeometry(GuiGraphics graphics) {
        for (int i = 0; i < points.size(); i++) {
            BlockPos point = points.get(i);
            int x = worldToScreenX(point.getX());
            int y = worldToScreenY(point.getZ());
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFFFFDD55);
            if (i > 0) {
                drawLine(graphics, points.get(i - 1), point, 0xFFFFDD55);
            }
        }
        if (mode() == TrackEditorMode.POLYGON && points.size() > 2) {
            drawLine(graphics, points.get(points.size() - 1), points.get(0), 0x99FFDD55);
        }
    }

    private void renderPlayerMarker(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int x = worldToScreenX(minecraft.player.getX());
        int y = worldToScreenY(minecraft.player.getZ());
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF55AAFF);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.player"), x + 6, y - 4, 0xFF55AAFF, false);
    }

    private void renderControlStrip(GuiGraphics graphics) {
        MapBounds map = mapBounds();
        graphics.fill(map.left, map.bottom + 2, map.right, this.height - OUTER_MARGIN, 0x88000000);
    }

    private void renderOverlayHud(GuiGraphics graphics) {
        MapBounds map = mapBounds();
        int x = map.left + 8;
        int y = map.top + 8;
        int panelWidth = showHelp ? Math.min(360, map.width() - 16) : Math.min(292, map.width() - 16);
        int panelHeight = showHelp ? 150 : 64;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0x88000000);
        graphics.renderOutline(x, y, panelWidth, panelHeight, 0xFFDA1A20);
        graphics.drawString(font, title, x + 6, y + 6, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.hud_line1", mode().name(), material().name(), trackWidth, blocksPerPixel()), x + 6, y + 18, 0xFFE6E6E6, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.hud_line2", preset().displayName(), runoffMaterial().name(), editY, elevationModeText(), surfaceApplicationText()), x + 6, y + 30, 0xFFE6E6E6, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.hud_line3", points.size(), requiredPointsText(), PENDING_QUEUE.size(), clearHeight), x + 6, y + 42, 0xFFE6E6E6, false);
        graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.hud_hint"), x + 6, y + 54, 0xFFB7FFB7, false);
        if (showHelp) {
            graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.map_help1"), x + 6, y + 76, 0xFFB7FFB7, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.map_help2"), x + 6, y + 88, 0xFFB7FFB7, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.map_help3"), x + 6, y + 100, 0xFFB7FFB7, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.map_help4"), x + 6, y + 112, 0xFFB7FFB7, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.map_help5"), x + 6, y + 124, 0xFFB7FFB7, false);
            graphics.drawString(font, Component.translatable("screen.openwheelracing.track_editor.map_help6"), x + 6, y + 136, 0xFFB7FFB7, false);
        }
    }

    private void renderNotice(GuiGraphics graphics) {
        if (notice == null) {
            return;
        }
        int textWidth = font.width(notice.text());
        int x = (this.width - textWidth) / 2;
        int y = mapBounds().top + 12;
        graphics.fill(x - 8, y - 6, x + textWidth + 8, y + 14, 0xBB000000);
        graphics.drawString(font, notice.text(), x, y, notice.color(), false);
    }

    private void importLapSimulatorTrack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Path path = minecraft.gameDirectory.toPath().resolve(IMPORT_PATH);
        try {
            List<LapSimulatorSection> sections = parseLapSimulatorSections(Files.readString(path, StandardCharsets.UTF_8));
            List<BlockPos> importedPoints = mapImportedPoints(sampleLapSimulatorCenterline(sections), sections.get(0));
            if (importedPoints.size() < 2) {
                showNotice(Component.translatable("screen.openwheelracing.track_editor.import_failed", "not enough unique points"), 0xFFFF7777);
                return;
            }
            int chunks = queuePath(importedPoints, QueueReason.IMPORT);
            updatePendingQueueStatus(Component.translatable("screen.openwheelracing.track_editor.import_queued", sections.size(), importedPoints.size(), chunks));
        } catch (IOException e) {
            showNotice(Component.translatable("screen.openwheelracing.track_editor.import_failed", IMPORT_PATH), 0xFFFF7777);
        } catch (IllegalArgumentException | JsonSyntaxException e) {
            showNotice(Component.translatable("screen.openwheelracing.track_editor.import_failed", e.getMessage()), 0xFFFF7777);
        }
    }

    private List<LapSimulatorSection> parseLapSimulatorSections(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray rawSections;
        if (root.isJsonArray()) {
            rawSections = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().get("sections") instanceof JsonArray sections) {
            rawSections = sections;
        } else {
            throw new IllegalArgumentException("missing sections array");
        }
        if (rawSections.size() < 2) {
            throw new IllegalArgumentException("track needs at least two sections");
        }
        List<LapSimulatorSection> sections = new ArrayList<>(rawSections.size());
        for (int i = 0; i < rawSections.size(); i++) {
            JsonElement element = rawSections.get(i);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("section " + i + " is not an object");
            }
            JsonObject section = element.getAsJsonObject();
            sections.add(new LapSimulatorSection(
                requiredString(section, "id", i),
                requiredFiniteDouble(section, "x", i),
                requiredFiniteDouble(section, "y", i),
                requiredFiniteDouble(section, "direction", i),
                requiredPositiveDouble(section, "width", i)
            ));
        }
        return sections;
    }

    private String requiredString(JsonObject object, String key, int sectionIndex) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("section " + sectionIndex + " missing " + key);
        }
        return value.getAsString();
    }

    private double requiredPositiveDouble(JsonObject object, String key, int sectionIndex) {
        double value = requiredFiniteDouble(object, key, sectionIndex);
        if (value <= 0.0) {
            throw new IllegalArgumentException("section " + sectionIndex + " " + key + " must be > 0");
        }
        return value;
    }

    private double requiredFiniteDouble(JsonObject object, String key, int sectionIndex) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("section " + sectionIndex + " missing " + key);
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("section " + sectionIndex + " invalid " + key);
        }
        return number;
    }

    private List<Vec2> sampleLapSimulatorCenterline(List<LapSimulatorSection> sections) {
        List<Vec2> points = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            LapSimulatorSection start = sections.get(i);
            LapSimulatorSection end = sections.get((i + 1) % sections.size());
            sampleBezierSegment(points, start, end);
        }
        return points;
    }

    private void sampleBezierSegment(List<Vec2> points, LapSimulatorSection start, LapSimulatorSection end) {
        Vec2 p0 = new Vec2(start.x(), start.y());
        Vec2 p3 = new Vec2(end.x(), end.y());
        double chord = p0.distanceTo(p3);
        if (chord < 0.01) {
            return;
        }
        double alpha = chord / 3.0;
        Vec2 c0 = p0.add(Vec2.fromDirection(start.direction()).scale(alpha));
        Vec2 c1 = p3.subtract(Vec2.fromDirection(end.direction()).scale(alpha));
        int subdivisions = Math.max(12, Math.min(256, (int) Math.ceil(chord / 2.0)));
        Vec2 previous = p0;
        double carried = points.isEmpty() ? 0.0 : IMPORT_SAMPLE_SPACING;
        addSampledPoint(points, p0);
        for (int i = 1; i <= subdivisions; i++) {
            Vec2 current = cubic(p0, c0, c1, p3, i / (double) subdivisions);
            double segmentLength = previous.distanceTo(current);
            while (carried + segmentLength >= IMPORT_SAMPLE_SPACING) {
                double t = (IMPORT_SAMPLE_SPACING - carried) / segmentLength;
                Vec2 sampled = previous.lerp(current, t);
                addSampledPoint(points, sampled);
                previous = sampled;
                segmentLength = previous.distanceTo(current);
                carried = 0.0;
            }
            carried += segmentLength;
            previous = current;
        }
    }

    private Vec2 cubic(Vec2 p0, Vec2 c0, Vec2 c1, Vec2 p3, double t) {
        double u = 1.0 - t;
        return new Vec2(
            u * u * u * p0.x() + 3.0 * u * u * t * c0.x() + 3.0 * u * t * t * c1.x() + t * t * t * p3.x(),
            u * u * u * p0.y() + 3.0 * u * u * t * c0.y() + 3.0 * u * t * t * c1.y() + t * t * t * p3.y()
        );
    }

    private void addSampledPoint(List<Vec2> points, Vec2 point) {
        if (points.isEmpty() || points.get(points.size() - 1).distanceTo(point) >= 0.5) {
            points.add(point);
        }
    }

    private List<BlockPos> mapImportedPoints(List<Vec2> importedPoints, LapSimulatorSection origin) {
        List<BlockPos> mapped = new ArrayList<>(importedPoints.size());
        BlockPos previous = null;
        int anchorX = (int) Math.round(centerX);
        int anchorZ = (int) Math.round(centerZ);
        for (Vec2 point : importedPoints) {
            BlockPos pos = withEditorY(
                (int) Math.round(anchorX + point.x() - origin.x()),
                (int) Math.round(anchorZ + point.y() - origin.y())
            );
            if (!pos.equals(previous)) {
                mapped.add(pos);
                previous = pos;
            }
        }
        if (mapped.size() > 1 && mapped.get(0).equals(mapped.get(mapped.size() - 1))) {
            mapped.remove(mapped.size() - 1);
        }
        return mapped;
    }

    private int queuePath(List<BlockPos> pathPoints, QueueReason reason) {
        int chunks = 0;
        for (int start = 0; start < pathPoints.size() - 1; start += QUEUE_CHUNK_SIZE - 1) {
            int end = Math.min(pathPoints.size(), start + QUEUE_CHUNK_SIZE);
            List<BlockPos> chunk = new ArrayList<>(pathPoints.subList(start, end));
            if (chunk.size() < 2) {
                continue;
            }
            PENDING_QUEUE.add(new PendingEditorOperation(new TrackEditorOperation(TrackEditorMode.FREEHAND, importMaterial(), trackWidth, chunk, facing(), preset(), runoffMaterial(), fullSurfaceApplication, clearHeight), reason));
            chunks++;
        }
        if (reason == QueueReason.IMPORT && pathPoints.size() > 2) {
            List<BlockPos> closingChunk = List.of(pathPoints.get(pathPoints.size() - 1), pathPoints.get(0));
            PENDING_QUEUE.add(new PendingEditorOperation(new TrackEditorOperation(TrackEditorMode.FREEHAND, importMaterial(), trackWidth, closingChunk, facing(), preset(), runoffMaterial(), fullSurfaceApplication, clearHeight), reason));
            chunks++;
        }
        return chunks;
    }

    private void enqueueOperation(TrackEditorOperation operation, QueueReason reason) {
        PENDING_QUEUE.add(new PendingEditorOperation(operation, reason));
        updatePendingQueueStatus(Component.translatable("screen.openwheelracing.track_editor.operation_queued", reason.displayName(), PENDING_QUEUE.size()));
    }

    private void flushPendingQueue() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || PENDING_QUEUE.isEmpty()) {
            return;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        int sent = 0;
        for (int i = 0; i < PENDING_QUEUE.size() && sent < QUEUE_SENDS_PER_TICK; ) {
            PendingEditorOperation queued = PENDING_QUEUE.get(i);
            if (!isOperationNearPlayer(queued.operation(), playerPos)) {
                i++;
                continue;
            }
            OWRNetwork.sendToServer(new OWRNetwork.TrackEditorPlaceMessage(queued.operation()));
            PENDING_QUEUE.remove(i);
            sent++;
        }
        if (sent > 0 || lastQueuedCount != PENDING_QUEUE.size()) {
            updatePendingQueueStatus(Component.translatable("screen.openwheelracing.track_editor.queue", PENDING_QUEUE.size()));
        }
    }

    private void clearPendingQueue() {
        int cleared = PENDING_QUEUE.size();
        if (cleared <= 0) {
            updateClearQueueButton();
            return;
        }
        PENDING_QUEUE.clear();
        updatePendingQueueStatus(Component.translatable("screen.openwheelracing.track_editor.queue_cleared", cleared));
    }

    private void updatePendingQueueStatus(Component status) {
        lastQueuedCount = PENDING_QUEUE.size();
        showNotice(status, 0xFFFFDD55);
        updateClearQueueButton();
    }

    private void updateClearQueueButton() {
        if (clearQueueButton != null) {
            clearQueueButton.active = !PENDING_QUEUE.isEmpty();
        }
    }

    private boolean isOperationNearPlayer(TrackEditorOperation operation, BlockPos playerPos) {
        for (BlockPos point : operation.points()) {
            if (playerPos.distManhattan(point) > QUEUE_SEND_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    private void addPoint(BlockPos point) {
        points.add(point.immutable());
        if (mode() != TrackEditorMode.POLYGON) {
            commitIfReady();
        }
    }

    private void commitIfReady() {
        if (points.size() < requiredPoints()) {
            return;
        }
        TrackEditorMode mode = mode();
        if (mode == TrackEditorMode.POLYGON && points.size() < 3) {
            return;
        }
        TrackEditorOperation operation = new TrackEditorOperation(mode, material(), trackWidth, new ArrayList<>(points), facing(), preset(), runoffMaterial(), fullSurfaceApplication, clearHeight);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !isOperationNearPlayer(operation, minecraft.player.blockPosition())) {
            enqueueOperation(operation, QueueReason.TOO_FAR);
        } else {
            OWRNetwork.sendToServer(new OWRNetwork.TrackEditorPlaceMessage(operation));
        }
        if (mode == TrackEditorMode.FREEHAND || mode == TrackEditorMode.EDGE) {
            BlockPos last = points.get(points.size() - 1);
            points.clear();
            points.add(last);
        } else {
            points.clear();
        }
    }

    private void removeLastPoint() {
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
        }
    }

    private void cycleMaterial() {
        if (mode() == TrackEditorMode.EDGE) {
            edgeIndex = (edgeIndex + 1) % EDGE_MATERIALS.length;
        } else {
            pavementIndex = (pavementIndex + 1) % PAVEMENT_MATERIALS.length;
        }
    }

    private void recenterOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
    }

    private int requiredPoints() {
        return switch (mode()) {
            case STRAIGHT, FREEHAND, EDGE -> 2;
            case ARC -> 3;
            case POLYGON -> 3;
        };
    }

    private String requiredPointsText() {
        return mode() == TrackEditorMode.POLYGON ? "3+" : Integer.toString(requiredPoints());
    }

    private TrackEditorMode mode() {
        return MODES[modeIndex];
    }

    private TrackEditorMaterial material() {
        return mode() == TrackEditorMode.EDGE ? EDGE_MATERIALS[edgeIndex] : importMaterial();
    }

    private TrackEditorMaterial importMaterial() {
        return PAVEMENT_MATERIALS[pavementIndex];
    }

    private TrackEditorPreset preset() {
        return PRESETS[presetIndex];
    }

    private TrackEditorMaterial runoffMaterial() {
        return RUNOFF_MATERIALS[runoffMaterialIndex];
    }

    private Direction facing() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? Direction.NORTH : minecraft.player.getDirection();
    }

    private double blocksPerPixel() {
        return ZOOM_LEVELS[zoomIndex];
    }

    private int panStep() {
        return (int) Math.round(blocksPerPixel() * 24.0);
    }

    private int gridStep() {
        double bpp = blocksPerPixel();
        if (bpp <= 0.5) {
            return 4;
        }
        if (bpp <= 2.0) {
            return 8;
        }
        return 16;
    }

    private boolean isInsideMap(double x, double y) {
        MapBounds map = mapBounds();
        return x >= map.left && x < map.right && y >= map.top && y < map.bottom;
    }

    private BlockPos screenToBlock(double x, double y) {
        MapBounds map = mapBounds();
        return withEditorY(screenToWorldX(x, map), screenToWorldZ(y, map));
    }

    private int screenToWorldX(double x, MapBounds map) {
        return (int) Math.floor(centerX + (x - (map.left + map.width() / 2.0)) * blocksPerPixel());
    }

    private int screenToWorldZ(double y, MapBounds map) {
        return (int) Math.floor(centerZ + (y - (map.top + map.height() / 2.0)) * blocksPerPixel());
    }

    private BlockPos withEditorY(int worldX, int worldZ) {
        Minecraft minecraft = Minecraft.getInstance();
        int y = editY;
        if (surfaceElevationMode && minecraft.level != null) {
            y = surfaceY(minecraft.level, worldX, worldZ);
        }
        return new BlockPos(worldX, y, worldZ);
    }

    private int worldToScreenX(double worldX) {
        MapBounds map = mapBounds();
        return (int) Math.round(map.left + map.width() / 2.0 + (worldX - centerX) / blocksPerPixel());
    }

    private int worldToScreenY(double worldZ) {
        MapBounds map = mapBounds();
        return (int) Math.round(map.top + map.height() / 2.0 + (worldZ - centerZ) / blocksPerPixel());
    }

    private void drawLine(GuiGraphics graphics, BlockPos a, BlockPos b, int color) {
        int ax = worldToScreenX(a.getX());
        int ay = worldToScreenY(a.getZ());
        int bx = worldToScreenX(b.getX());
        int by = worldToScreenY(b.getZ());
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        if (steps == 0) {
            graphics.fill(ax, ay, ax + 1, ay + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(ax + (bx - ax) * (i / (float) steps));
            int y = Math.round(ay + (by - ay) * (i / (float) steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void sampleVisibleTerrain(int budget) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MapBounds map = mapBounds();
        int minWorldX = Math.min(screenToWorldX(map.left, map), screenToWorldX(map.right, map));
        int maxWorldX = Math.max(screenToWorldX(map.left, map), screenToWorldX(map.right, map));
        int minWorldZ = Math.min(screenToWorldZ(map.top, map), screenToWorldZ(map.bottom, map));
        int maxWorldZ = Math.max(screenToWorldZ(map.top, map), screenToWorldZ(map.bottom, map));
        sampleTerrainTiles(minecraft.level, (int) Math.floor(centerX), (int) Math.floor(centerZ), editY, tileCoord(minWorldX), tileCoord(maxWorldX), tileCoord(minWorldZ), tileCoord(maxWorldZ), budget);
    }

    private static void sampleTerrainTiles(Level level, int centerBlockX, int centerBlockZ, int fallbackY, int minTileX, int maxTileX, int minTileZ, int maxTileZ, int budget) {
        int centerTileX = tileCoord(centerBlockX);
        int centerTileZ = tileCoord(centerBlockZ);
        int maxRadius = Math.max(Math.max(Math.abs(centerTileX - minTileX), Math.abs(centerTileX - maxTileX)), Math.max(Math.abs(centerTileZ - minTileZ), Math.abs(centerTileZ - maxTileZ)));
        int remaining = budget;
        for (int radius = 0; radius <= maxRadius && remaining > 0; radius++) {
            for (int dz = -radius; dz <= radius && remaining > 0; dz++) {
                for (int dx = -radius; dx <= radius && remaining > 0; dx++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int tileX = centerTileX + dx;
                    int tileZ = centerTileZ + dz;
                    if (tileX < minTileX || tileX > maxTileX || tileZ < minTileZ || tileZ > maxTileZ) {
                        continue;
                    }
                    TerrainTile tile = terrainTile(tileX, tileZ, fallbackY);
                    if (tile.loadingFromDisk()) {
                        continue;
                    }
                    int tileBudget = Math.min(remaining, TERRAIN_TILE_SAMPLE_BURST);
                    remaining -= tile.sample(level, fallbackY, tileBudget);
                }
            }
        }
    }

    private static TerrainTile terrainTile(int tileX, int tileZ, int fallbackY) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureTerrainCacheNamespace(minecraft);
        long key = tileKey(tileX, tileZ);
        TerrainTile tile = TERRAIN_TILES.get(key);
        if (tile != null) {
            return tile;
        }
        tile = new TerrainTile(tileX, tileZ, fallbackY);
        TERRAIN_TILES.put(key, tile);
        queueTerrainTileLoad(minecraft, tile);
        return tile;
    }

    private static int tileCoord(int block) {
        return Math.floorDiv(block, TERRAIN_TILE_BLOCKS);
    }

    private static long tileKey(int tileX, int tileZ) {
        return ((long) tileX << 32) ^ (tileZ & 0xFFFFFFFFL);
    }

    private static long tileKeyForBlock(int x, int z) {
        return tileKey(tileCoord(x), tileCoord(z));
    }

    private static long sampleKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static void storeTerrainSample(int x, int z, TerrainSample sample) {
        if (!sample.complete()) {
            return;
        }
        TerrainTile tile = TERRAIN_TILES.get(tileKeyForBlock(x, z));
        if (tile != null) {
            tile.setSample(x, z, sample);
        }
        terrainCacheDirty = true;
    }

    private void flushTerrainCacheIfDue() {
        if (!terrainCacheDirty || minecraft == null || ++terrainCacheFlushTicks < TERRAIN_CACHE_FLUSH_TICKS) {
            return;
        }
        ensureTerrainCacheNamespace(minecraft);
        terrainCacheFlushTicks = 0;
        flushDirtyTerrainTiles(minecraft, TERRAIN_CACHE_FLUSH_TILE_LIMIT);
    }

    private void rerenderNearbyTerrain() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int centerX = (int) Math.floor(minecraft.player.getX());
        int centerZ = (int) Math.floor(minecraft.player.getZ());
        int centerY = minecraft.player.blockPosition().getY();
        int radiusTiles = Math.max(1, TERRAIN_PRELOAD_RADIUS_BLOCKS / TERRAIN_TILE_BLOCKS);
        int centerTileX = tileCoord(centerX);
        int centerTileZ = tileCoord(centerZ);
        for (int tileX = centerTileX - radiusTiles; tileX <= centerTileX + radiusTiles; tileX++) {
            for (int tileZ = centerTileZ - radiusTiles; tileZ <= centerTileZ + radiusTiles; tileZ++) {
                terrainTile(tileX, tileZ, centerY).requestRerender();
            }
        }
        sampleTerrainTiles(minecraft.level, centerX, centerZ, centerY, centerTileX - radiusTiles, centerTileX + radiusTiles, centerTileZ - radiusTiles, centerTileZ + radiusTiles, TERRAIN_SAMPLES_PER_FRAME * 2);
        showNotice(Component.translatable("screen.openwheelracing.track_editor.rerender_started"), 0xFFB7FFB7);
    }

    private int terrainColor(Level level, int x, int z, boolean forceSample) {
        return colorForId(terrainSample(level, x, z, forceSample).colorId());
    }

    private int surfaceY(Level level, int x, int z) {
        return terrainSample(level, x, z, true).surfaceY();
    }

    private TerrainSample terrainSample(Level level, int x, int z, boolean forceSample) {
        int sampleStep = forceSample ? 2 : Math.max(2, (int) Math.ceil(blocksPerPixel()));
        int sampleX = Math.floorDiv(x, sampleStep) * sampleStep;
        int sampleZ = Math.floorDiv(z, sampleStep) * sampleStep;
        TerrainTile tile = terrainTile(tileCoord(sampleX), tileCoord(sampleZ), editY);
        TerrainSample cached = tile.sampleAt(sampleX, sampleZ);
        if (cached != null) {
            return cached;
        }
        if (!forceSample && terrainSamplesThisFrame >= TERRAIN_SAMPLES_PER_FRAME) {
            return new TerrainSample(editY, (byte) 0, false);
        }
        TerrainSample sampled = sampleTerrain(level, sampleX, sampleZ, editY);
        storeTerrainSample(sampleX, sampleZ, sampled);
        terrainSamplesThisFrame++;
        return sampled;
    }

    private static TerrainSample sampleTerrain(Level level, int x, int z, int fallbackY) {
        BlockPos chunkCheck = new BlockPos(x, fallbackY, z);
        if (!level.hasChunkAt(chunkCheck)) {
            return new TerrainSample(fallbackY, (byte) 0, false);
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (y < level.getMinY()) {
            return new TerrainSample(fallbackY, (byte) 1, true);
        }
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return new TerrainSample(y, colorIdFor(state), true);
    }

    private static byte colorIdFor(BlockState state) {
        Block block = state.getBlock();
        if (block == OWRBlocks.ASPHALT_TRACK.get()) {
            return (byte) 2;
        }
        if (block == OWRBlocks.PIT_LANE.get()) {
            return (byte) 3;
        }
        if (block == OWRBlocks.KERB.get()) {
            return (byte) 4;
        }
        if (block == OWRBlocks.BARRIER.get()) {
            return (byte) 5;
        }
        if (block == Blocks.GRASS_BLOCK) {
            return (byte) 6;
        }
        if (block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.SPRUCE_LEAVES || block == Blocks.JUNGLE_LEAVES || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES || block == Blocks.MANGROVE_LEAVES || block == Blocks.CHERRY_LEAVES) {
            return (byte) 7;
        }
        if (block == Blocks.OAK_LOG || block == Blocks.BIRCH_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG || block == Blocks.MANGROVE_LOG || block == Blocks.CHERRY_LOG) {
            return (byte) 8;
        }
        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT || block == Blocks.PODZOL) {
            return (byte) 9;
        }
        if (block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE) {
            return (byte) 10;
        }
        if (block == Blocks.SMOOTH_STONE || block == Blocks.STONE_BRICKS || block == Blocks.BRICKS || block == Blocks.POLISHED_ANDESITE || block == Blocks.POLISHED_DIORITE || block == Blocks.POLISHED_GRANITE) {
            return (byte) 11;
        }
        if (block == Blocks.SAND || block == Blocks.SANDSTONE || block == Blocks.SMOOTH_SANDSTONE) {
            return (byte) 12;
        }
        if (block == Blocks.RED_SAND || block == Blocks.RED_SANDSTONE || block == Blocks.SMOOTH_RED_SANDSTONE) {
            return (byte) 13;
        }
        if (block == Blocks.WATER) {
            return (byte) 14;
        }
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            return (byte) 15;
        }
        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK) {
            return (byte) 16;
        }
        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
            return (byte) 17;
        }
        if (block == Blocks.OAK_PLANKS || block == Blocks.BIRCH_PLANKS || block == Blocks.SPRUCE_PLANKS || block == Blocks.JUNGLE_PLANKS || block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS || block == Blocks.MANGROVE_PLANKS || block == Blocks.CHERRY_PLANKS) {
            return (byte) 18;
        }
        if (block == Blocks.GRAVEL) {
            return (byte) 19;
        }
        if (block == Blocks.WHITE_CONCRETE || block == Blocks.LIGHT_GRAY_CONCRETE) {
            return (byte) 20;
        }
        if (block == Blocks.GRAY_CONCRETE || block == Blocks.BLACK_CONCRETE) {
            return (byte) 21;
        }
        if (block == Blocks.RED_CONCRETE) {
            return (byte) 22;
        }
        return (byte) 23;
    }

    private static int colorForId(byte colorId) {
        return switch (Byte.toUnsignedInt(colorId)) {
            case 1 -> 0xFF101214;
            case 2 -> 0xFF202020;
            case 3 -> 0xFF4A4A4A;
            case 4 -> 0xFFCC3333;
            case 5 -> 0xFF888888;
            case 6 -> 0xFF4B7D3A;
            case 7 -> 0xFF2F6B31;
            case 8 -> 0xFF6F5136;
            case 9 -> 0xFF74543A;
            case 10 -> 0xFF777777;
            case 11 -> 0xFF8A8A8A;
            case 12 -> 0xFFC8B77B;
            case 13 -> 0xFFC47745;
            case 14 -> 0xFF315EAF;
            case 15 -> 0xFF8FC6DD;
            case 16 -> 0xFFE8F0F0;
            case 17 -> 0xFF9FB8C8;
            case 18 -> 0xFFA8794B;
            case 19 -> 0xFF737373;
            case 20 -> 0xFFBEBEBE;
            case 21 -> 0xFF505050;
            case 22 -> 0xFF8F2B25;
            case 23 -> 0xFF667066;
            default -> 0xFF181A1C;
        };
    }

    private int floorToStep(int value, int step) {
        return Math.floorDiv(value, step) * step;
    }

    private Component elevationModeText() {
        return Component.translatable(surfaceElevationMode ? "screen.openwheelracing.track_editor.elevation.surface" : "screen.openwheelracing.track_editor.elevation.height");
    }

    private Component surfaceApplicationText() {
        return Component.translatable(fullSurfaceApplication ? "screen.openwheelracing.track_editor.surface.full" : "screen.openwheelracing.track_editor.surface.keypoint");
    }

    private Component elevationModeLabel() {
        return Component.translatable("screen.openwheelracing.track_editor.elevation_button", elevationModeText());
    }

    private Component surfaceApplicationLabel() {
        return Component.translatable("screen.openwheelracing.track_editor.surface_button", surfaceApplicationText());
    }

    private Component clearHeightLabel() {
        return Component.translatable("screen.openwheelracing.track_editor.clear_height", clearHeight);
    }

    private void toggleElevationMode() {
        surfaceElevationMode = !surfaceElevationMode;
        updateToggleButtons();
        showNotice(Component.translatable("screen.openwheelracing.track_editor.elevation_mode_changed", elevationModeText()), 0xFFB7FFB7);
    }

    private void toggleSurfaceApplication() {
        fullSurfaceApplication = !fullSurfaceApplication;
        updateToggleButtons();
        showNotice(Component.translatable("screen.openwheelracing.track_editor.surface_application_changed", surfaceApplicationText()), 0xFFB7FFB7);
    }

    private void updateToggleButtons() {
        if (elevationModeButton != null) {
            elevationModeButton.setMessage(elevationModeLabel());
        }
        if (surfaceApplicationButton != null) {
            surfaceApplicationButton.setMessage(surfaceApplicationLabel());
        }
    }

    private void showNotice(Component text, int color) {
        notice = new OverlayNotice(text, color, 80);
    }

    private MapBounds mapBounds() {
        int left = OUTER_MARGIN;
        int top = OUTER_MARGIN;
        return new MapBounds(left, top, Math.max(left + 160, this.width - OUTER_MARGIN), Math.max(top + 80, this.height - OUTER_MARGIN - CONTROL_STRIP_HEIGHT));
    }

    public static void preloadAroundPlayer(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen instanceof TrackEditorScreen) {
            return;
        }
        ensureTerrainCacheNamespace(minecraft);
        if (!terrainCacheLoaded) {
            terrainCacheLoaded = true;
            loadTerrainCache(minecraft.gameDirectory.toPath());
        }
        int centerX = (int) Math.floor(minecraft.player.getX());
        int centerZ = (int) Math.floor(minecraft.player.getZ());
        int centerY = minecraft.player.blockPosition().getY();
        if (preloadCenterX == Integer.MIN_VALUE || Math.abs(centerX - preloadCenterX) > TERRAIN_PRELOAD_RADIUS_BLOCKS / 2 || Math.abs(centerZ - preloadCenterZ) > TERRAIN_PRELOAD_RADIUS_BLOCKS / 2) {
            preloadCenterX = centerX;
            preloadCenterZ = centerZ;
            preloadCenterY = centerY;
        }
        processTerrainIoResults(TERRAIN_IO_COMPLETIONS_PER_TICK);
        int radiusTiles = Math.max(1, TERRAIN_PRELOAD_RADIUS_BLOCKS / TERRAIN_TILE_BLOCKS);
        int centerTileX = tileCoord(preloadCenterX);
        int centerTileZ = tileCoord(preloadCenterZ);
        sampleTerrainTiles(minecraft.level, preloadCenterX, preloadCenterZ, preloadCenterY, centerTileX - radiusTiles, centerTileX + radiusTiles, centerTileZ - radiusTiles, centerTileZ + radiusTiles, TERRAIN_PRELOAD_SAMPLES_PER_TICK);
    }

    private static final String TERRAIN_TILE_CACHE_DIR = "openwheelracing/terrain-cache";
    private static final int TERRAIN_TILE_CACHE_MAGIC = 0x4F575254; // "OWRT"
    private static final int TERRAIN_TILE_CACHE_VERSION = 2;

    private static void ensureTerrainCacheNamespace(Minecraft minecraft) {
        String namespace = terrainCacheNamespace(minecraft);
        if (namespace.equals(terrainCacheNamespace)) {
            return;
        }
        terrainCacheNamespace = namespace;
        for (TerrainTile tile : TERRAIN_TILES.values()) {
            tile.closeTexture(minecraft);
        }
        TERRAIN_TILES.clear();
        TERRAIN_TILE_LOADING.clear();
        TERRAIN_TILE_IO_MISSES.clear();
        synchronized (TERRAIN_TILE_LOAD_RESULTS) {
            TERRAIN_TILE_LOAD_RESULTS.clear();
        }
        terrainCacheLoaded = false;
        terrainCacheDirty = false;
        terrainCacheFlushTicks = 0;
        preloadCenterX = Integer.MIN_VALUE;
        preloadCenterZ = Integer.MIN_VALUE;
        preloadCursor = 0;
    }

    private static String terrainCacheNamespace(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return "unknown/no_level";
        }
        String world = "unknown_world";
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            world = "singleplayer/" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            ServerData server = minecraft.getCurrentServer();
            if (server != null) {
                world = "multiplayer/" + server.ip;
            }
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        return sanitizeCachePath(world) + "/" + sanitizeCachePath(dimension);
    }

    private static String sanitizeCachePath(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private static void saveTerrainCache(Path gameDir) {
        ensureTerrainCacheNamespace(Minecraft.getInstance());
        flushDirtyTerrainTiles(Minecraft.getInstance(), Integer.MAX_VALUE);
    }

    private static void loadTerrainCache(Path gameDir) {
        terrainCacheDirty = false;
        terrainCacheFlushTicks = 0;
    }

    private static void queueTerrainTileLoad(Minecraft minecraft, TerrainTile tile) {
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        long key = tileKey(tile.tileX, tile.tileZ);
        if (TERRAIN_TILE_IO_MISSES.contains(key) || TERRAIN_TILE_LOADING.contains(key) || TERRAIN_TILE_LOADING.size() >= MAX_TERRAIN_IO_QUEUE_SIZE) {
            return;
        }
        TERRAIN_TILE_LOADING.add(key);
        Path gameDir = minecraft.gameDirectory.toPath();
        int tileX = tile.tileX;
        int tileZ = tile.tileZ;
        String namespace = terrainCacheNamespace;
        TERRAIN_IO_EXECUTOR.execute(() -> {
            TerrainTileLoadResult result = loadTerrainTileSnapshot(gameDir, namespace, tileX, tileZ);
            synchronized (TERRAIN_TILE_LOAD_RESULTS) {
                TERRAIN_TILE_LOAD_RESULTS.add(result);
            }
        });
    }

    private static TerrainTileLoadResult loadTerrainTileSnapshot(Path gameDir, String namespace, int tileX, int tileZ) {
        Path file = terrainTileFile(gameDir, namespace, tileX, tileZ);
        if (!Files.exists(file)) {
            return TerrainTileLoadResult.missing(tileX, tileZ);
        }
        byte[] colorId = new byte[TERRAIN_TILE_BLOCKS * TERRAIN_TILE_BLOCKS];
        short[] surfaceY = new short[TERRAIN_TILE_BLOCKS * TERRAIN_TILE_BLOCKS];
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != TERRAIN_TILE_CACHE_MAGIC || in.readInt() != TERRAIN_TILE_CACHE_VERSION) {
                return TerrainTileLoadResult.missing(tileX, tileZ);
            }
            if (in.readInt() != tileX || in.readInt() != tileZ) {
                return TerrainTileLoadResult.missing(tileX, tileZ);
            }
            int count = in.readInt();
            if (count < 0 || count > TERRAIN_TILE_BLOCKS * TERRAIN_TILE_BLOCKS) {
                return TerrainTileLoadResult.missing(tileX, tileZ);
            }
            for (int i = 0; i < count; i++) {
                int localX = in.readUnsignedByte();
                int localZ = in.readUnsignedByte();
                int index = terrainTileIndex(localX, localZ);
                surfaceY[index] = in.readShort();
                colorId[index] = in.readByte();
            }
            return TerrainTileLoadResult.loaded(tileX, tileZ, colorId, surfaceY);
        } catch (IOException ignored) {
            return TerrainTileLoadResult.missing(tileX, tileZ);
        }
    }

    private static void processTerrainIoResults(int maxResults) {
        int processed = 0;
        while (processed < maxResults) {
            TerrainTileLoadResult result;
            synchronized (TERRAIN_TILE_LOAD_RESULTS) {
                result = TERRAIN_TILE_LOAD_RESULTS.pollFirst();
            }
            if (result == null) {
                return;
            }
            long key = tileKey(result.tileX(), result.tileZ());
            TERRAIN_TILE_LOADING.remove(key);
            if (!result.loaded()) {
                TERRAIN_TILE_IO_MISSES.add(key);
                processed++;
                continue;
            }
            TerrainTile tile = TERRAIN_TILES.get(key);
            if (tile != null) {
                tile.loadSnapshot(result.colorId(), result.surfaceY());
            }
            processed++;
        }
    }

    private static int flushDirtyTerrainTiles(Minecraft minecraft, int maxTiles) {
        if (minecraft == null || minecraft.gameDirectory == null) {
            return 0;
        }
        int flushed = 0;
        boolean remainingDirty = false;
        for (TerrainTile tile : TERRAIN_TILES.values()) {
            if (!tile.dirty) {
                continue;
            }
            if (flushed >= maxTiles) {
                remainingDirty = true;
                continue;
            }
            queueTerrainTileSave(minecraft, tile);
            tile.dirty = false;
            flushed++;
        }
        terrainCacheDirty = remainingDirty;
        if (!terrainCacheDirty) {
            terrainCacheFlushTicks = 0;
        }
        return flushed;
    }

    private static void queueTerrainTileSave(Minecraft minecraft, TerrainTile tile) {
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        TerrainTileSnapshot snapshot = tile.snapshot();
        if (snapshot.count() <= 0) {
            return;
        }
        Path gameDir = minecraft.gameDirectory.toPath();
        String namespace = terrainCacheNamespace;
        TERRAIN_IO_EXECUTOR.execute(() -> saveTerrainTileSnapshot(gameDir, namespace, snapshot));
    }

    private static boolean saveTerrainTileSnapshot(Path gameDir, String namespace, TerrainTileSnapshot snapshot) {
        Path file = terrainTileFile(gameDir, namespace, snapshot.tileX(), snapshot.tileZ());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(TERRAIN_TILE_CACHE_MAGIC);
                out.writeInt(TERRAIN_TILE_CACHE_VERSION);
                out.writeInt(snapshot.tileX());
                out.writeInt(snapshot.tileZ());
                out.writeInt(snapshot.count());
                snapshot.writeSamples(out);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path terrainTileFile(Path gameDir, int tileX, int tileZ) {
        return terrainTileFile(gameDir, terrainCacheNamespace, tileX, tileZ);
    }

    private static Path terrainTileFile(Path gameDir, String namespace, int tileX, int tileZ) {
        return gameDir.resolve(TERRAIN_TILE_CACHE_DIR).resolve(namespace).resolve(tileX + "," + tileZ + ".dat");
    }

    private static int terrainTileIndex(int localX, int localZ) {
        return localZ * TERRAIN_TILE_BLOCKS + localX;
    }

    private record PendingEditorOperation(TrackEditorOperation operation, QueueReason reason) {
    }

    private enum QueueReason {
        IMPORT("screen.openwheelracing.track_editor.queue_reason.import"),
        TOO_FAR("screen.openwheelracing.track_editor.queue_reason.too_far"),
        TOO_MANY_POINTS("screen.openwheelracing.track_editor.queue_reason.too_many_points"),
        TOO_LARGE("screen.openwheelracing.track_editor.queue_reason.too_large");

        private final String key;

        QueueReason(String key) {
            this.key = key;
        }

        private Component displayName() {
            return Component.translatable(key);
        }
    }

    private record OverlayNotice(Component text, int color, int ticksRemaining) {
    }

    private record TerrainSample(int surfaceY, byte colorId, boolean complete) {
    }

    private static class TerrainTile {
        private final int tileX;
        private final int tileZ;
        private final int baseX;
        private final int baseZ;
        private final byte[] colorId = new byte[TERRAIN_TILE_BLOCKS * TERRAIN_TILE_BLOCKS];
        private final short[] surfaceY = new short[TERRAIN_TILE_BLOCKS * TERRAIN_TILE_BLOCKS];
        private int nextSample;
        private boolean dirty;
        private boolean textureDirty;
        private boolean locallySampled;
        private boolean rerenderRequested;
        private NativeImage textureImage;
        private Identifier textureLocation;
        private DynamicTexture texture;

        private TerrainTile(int tileX, int tileZ, int fallbackY) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.baseX = tileX * TERRAIN_TILE_BLOCKS;
            this.baseZ = tileZ * TERRAIN_TILE_BLOCKS;
            for (int z = 0; z < TERRAIN_TILE_BLOCKS; z++) {
                for (int x = 0; x < TERRAIN_TILE_BLOCKS; x++) {
                    int index = index(x, z);
                    surfaceY[index] = (short) fallbackY;
                }
            }
            advanceNextSample();
        }

        private int sample(Level level, int fallbackY, int budget) {
            int sampled = 0;
            int checked = 0;
            while (sampled < budget && checked < colorId.length) {
                if (nextSample >= colorId.length) {
                    nextSample = 0;
                    rerenderRequested = false;
                }
                int index = nextSample++;
                checked++;
                if (colorId[index] != 0 && !rerenderRequested) {
                    continue;
                }
                int localX = index % TERRAIN_TILE_BLOCKS;
                int localZ = index / TERRAIN_TILE_BLOCKS;
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                TerrainSample sample = sampleTerrain(level, worldX, worldZ, fallbackY);
                if (sample.complete()) {
                    storeTerrainSample(worldX, worldZ, sample);
                }
                sampled++;
            }
            return sampled;
        }

        private TerrainSample sampleAt(int worldX, int worldZ) {
            int localX = worldX - baseX;
            int localZ = worldZ - baseZ;
            if (localX < 0 || localX >= TERRAIN_TILE_BLOCKS || localZ < 0 || localZ >= TERRAIN_TILE_BLOCKS) {
                return null;
            }
            int index = index(localX, localZ);
            return colorId[index] == 0 ? null : new TerrainSample(surfaceY[index], colorId[index], true);
        }

        private boolean loadingFromDisk() {
            return TERRAIN_TILE_LOADING.contains(tileKey(tileX, tileZ));
        }

        private int colorAt(int localX, int localZ) {
            int index = index(Math.max(0, Math.min(TERRAIN_TILE_BLOCKS - 1, localX)), Math.max(0, Math.min(TERRAIN_TILE_BLOCKS - 1, localZ)));
            return colorForId(colorId[index]);
        }

        private void setSample(int worldX, int worldZ, TerrainSample sample) {
            int localX = worldX - baseX;
            int localZ = worldZ - baseZ;
            if (localX < 0 || localX >= TERRAIN_TILE_BLOCKS || localZ < 0 || localZ >= TERRAIN_TILE_BLOCKS) {
                return;
            }
            int index = index(localX, localZ);
            if (surfaceY[index] == (short) sample.surfaceY() && colorId[index] == sample.colorId()) {
                return;
            }
            surfaceY[index] = (short) sample.surfaceY();
            colorId[index] = sample.colorId();
            dirty = true;
            textureDirty = true;
            locallySampled = true;
            if (index == nextSample) {
                advanceNextSample();
            }
        }

        private void advanceNextSample() {
            if (rerenderRequested) {
                return;
            }
            int checked = 0;
            while (checked < colorId.length && colorId[nextSample] != 0) {
                nextSample = (nextSample + 1) % colorId.length;
                checked++;
            }
        }

        private boolean hasSamples() {
            for (byte color : colorId) {
                if (color != 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasTexture() {
            return textureLocation != null;
        }

        private Identifier textureLocation() {
            return textureLocation;
        }

        private boolean uploadTexture(Minecraft minecraft) {
            if (minecraft == null || !textureDirty || !hasSamples()) {
                return false;
            }
            if (textureImage == null) {
                textureImage = new NativeImage(TERRAIN_TILE_BLOCKS, TERRAIN_TILE_BLOCKS, true);
            }
            for (int localZ = 0; localZ < TERRAIN_TILE_BLOCKS; localZ++) {
                for (int localX = 0; localX < TERRAIN_TILE_BLOCKS; localX++) {
                    textureImage.setPixel(localX, localZ, colorForId(colorId[index(localX, localZ)]));
                }
            }
            Identifier location = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "dynamic/track_editor/terrain/" + terrainTextureId(tileX, tileZ));
            if (texture == null) {
                textureLocation = location;
                texture = new DynamicTexture(() -> "openwheelracing-track-editor-terrain-" + terrainTextureId(tileX, tileZ), textureImage);
                minecraft.getTextureManager().register(textureLocation, texture);
            }
            texture.upload();
            textureDirty = false;
            return true;
        }

        private void closeTexture(Minecraft minecraft) {
            if (minecraft != null && textureLocation != null) {
                minecraft.getTextureManager().release(textureLocation);
            }
            textureLocation = null;
            texture = null;
            if (textureImage != null) {
                textureImage.close();
                textureImage = null;
            }
            textureDirty = false;
        }

        private TerrainTileSnapshot snapshot() {
            byte[] colorCopy = colorId.clone();
            short[] surfaceCopy = surfaceY.clone();
            int count = 0;
            for (byte color : colorCopy) {
                if (color != 0) {
                    count++;
                }
            }
            return new TerrainTileSnapshot(tileX, tileZ, colorCopy, surfaceCopy, count);
        }

        private void loadSnapshot(byte[] loadedColorId, short[] loadedSurfaceY) {
            boolean changed = false;
            for (int i = 0; i < colorId.length && i < loadedColorId.length && i < loadedSurfaceY.length; i++) {
                if (loadedColorId[i] == 0 || (locallySampled && colorId[i] != 0)) {
                    continue;
                }
                if (colorId[i] != loadedColorId[i] || surfaceY[i] != loadedSurfaceY[i]) {
                    colorId[i] = loadedColorId[i];
                    surfaceY[i] = loadedSurfaceY[i];
                    changed = true;
                }
            }
            advanceNextSample();
            textureDirty |= changed;
        }

        private void requestRerender() {
            rerenderRequested = true;
            nextSample = 0;
            textureDirty = true;
        }

        private int index(int localX, int localZ) {
            return terrainTileIndex(localX, localZ);
        }
    }

    private record TerrainTileLoadResult(int tileX, int tileZ, boolean loaded, byte[] colorId, short[] surfaceY) {
        private static TerrainTileLoadResult missing(int tileX, int tileZ) {
            return new TerrainTileLoadResult(tileX, tileZ, false, null, null);
        }

        private static TerrainTileLoadResult loaded(int tileX, int tileZ, byte[] colorId, short[] surfaceY) {
            return new TerrainTileLoadResult(tileX, tileZ, true, colorId, surfaceY);
        }
    }

    private record TerrainTileSnapshot(int tileX, int tileZ, byte[] colorId, short[] surfaceY, int count) {
        private void writeSamples(DataOutputStream out) throws IOException {
            for (int localZ = 0; localZ < TERRAIN_TILE_BLOCKS; localZ++) {
                for (int localX = 0; localX < TERRAIN_TILE_BLOCKS; localX++) {
                    int index = terrainTileIndex(localX, localZ);
                    if (colorId[index] == 0) {
                        continue;
                    }
                    out.writeByte(localX);
                    out.writeByte(localZ);
                    out.writeShort(surfaceY[index]);
                    out.writeByte(colorId[index]);
                }
            }
        }
    }

    private static String terrainTextureId(int tileX, int tileZ) {
        return sanitizeResourcePath(terrainCacheNamespace) + "/" + tileX + "_" + tileZ;
    }

    private static String sanitizeResourcePath(String value) {
        String sanitized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]+", "_");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private class ClearHeightSlider extends AbstractSliderButton {
        private ClearHeightSlider(int x, int y, int width, int height) {
            super(x, y, width, height, clearHeightLabel(), clearHeight / (double) TrackEditorOperation.MAX_CLEAR_HEIGHT);
        }

        @Override
        protected void updateMessage() {
            setMessage(clearHeightLabel());
        }

        @Override
        protected void applyValue() {
            clearHeight = (int) Math.round(value * TrackEditorOperation.MAX_CLEAR_HEIGHT);
        }
    }

    private record LapSimulatorSection(String id, double x, double y, double direction, double width) {
    }

    private record Vec2(double x, double y) {
        private static Vec2 fromDirection(double degrees) {
            double radians = Math.toRadians(degrees);
            return new Vec2(Math.cos(radians), Math.sin(radians));
        }

        private Vec2 add(Vec2 other) {
            return new Vec2(x + other.x, y + other.y);
        }

        private Vec2 subtract(Vec2 other) {
            return new Vec2(x - other.x, y - other.y);
        }

        private Vec2 scale(double scalar) {
            return new Vec2(x * scalar, y * scalar);
        }

        private Vec2 lerp(Vec2 other, double t) {
            return new Vec2(x + (other.x - x) * t, y + (other.y - y) * t);
        }

        private double distanceTo(Vec2 other) {
            double dx = other.x - x;
            double dy = other.y - y;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }

    private record MapBounds(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}
