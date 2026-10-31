package com.openwheelracing.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.openwheelracing.OpenwheelRacing;
import com.openwheelracing.content.race.map.LiveMapSnapshot.Geometry;
import com.openwheelracing.content.track.TrackMapSnapshot;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Static track geometry costs one textured quad per view, rather than thousands of GUI fills per frame. */
final class LiveMapBackground {
    private static final Cached[] CACHE = new Cached[2];

    private LiveMapBackground() {}

    static void clear() {
        for (int i = 0; i < CACHE.length; i++) {
            if (CACHE[i] != null) Minecraft.getInstance().getTextureManager().release(CACHE[i].location());
            CACHE[i] = null;
        }
    }

    static void draw(GuiGraphics graphics, Geometry geometry, LiveMapProjection projection, boolean finishView) {
        int slot = finishView ? 1 : 0;
        LiveMapProjection local = new LiveMapProjection(projection.centerX(), projection.centerZ(), projection.scale(), 0, 0, projection.width(), projection.height());
        Cached cached = CACHE[slot];
        if (cached == null || cached.geometry() != geometry || !cached.projection().equals(local)) {
            Identifier location = Identifier.fromNamespaceAndPath(OpenwheelRacing.MODID, "dynamic/live_map_" + slot);
            if (cached != null) Minecraft.getInstance().getTextureManager().release(cached.location());
            NativeImage image = new NativeImage(local.width(), local.height(), true);
            surface(image, geometry.surface().asphaltRuns(), local, 0xFF687883);
            surface(image, geometry.surface().pitRuns(), local, 0xFF356B86);
            for (int i = 1; i < geometry.route().size(); i++) {
                var a = geometry.route().get(i - 1);
                var b = geometry.route().get(i);
                LiveMapRenderer.rasterLine(local, local.screenX(a.x()), local.screenY(a.z()), local.screenX(b.x()), local.screenY(b.z()), (px, py) -> {
                    if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) image.setPixel(px, py, 0xFFBAC7CF);
                });
            }
            DynamicTexture texture = new DynamicTexture(() -> "openwheelracing-live-map-" + slot, image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            texture.upload();
            cached = new Cached(geometry, local, location);
            CACHE[slot] = cached;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, cached.location(), projection.x(), projection.y(), 0, 0,
            projection.width(), projection.height(), projection.width(), projection.height(), projection.width(), projection.height());
    }

    private static void surface(NativeImage image, List<TrackMapSnapshot.CellRun> runs, LiveMapProjection p, int color) {
        for (var run : runs) {
            int left = p.screenX(run.startX()), right = Math.max(left + 1, p.screenX(run.endX() + 1.0));
            int top = p.screenY(run.z()), bottom = Math.max(top + 1, p.screenY(run.z() + 1.0));
            for (int py = Math.max(0, top); py < Math.min(image.getHeight(), bottom); py++) {
                for (int px = Math.max(0, left); px < Math.min(image.getWidth(), right); px++) image.setPixel(px, py, color);
            }
        }
    }

    private record Cached(Geometry geometry, LiveMapProjection projection, Identifier location) {}
}
