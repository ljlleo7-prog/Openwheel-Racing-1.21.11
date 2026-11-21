package com.openwheelracing.client.livery;

import com.google.gson.Gson;
import com.mojang.serialization.JsonOps;
import com.openwheelracing.client.render.ColoredObjModel;
import com.openwheelracing.client.render.OpenwheelCarRenderer;
import com.openwheelracing.content.car.CarLiveryTexture;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveryCompatibilityTest {
    @Test
    void existingSerializedIdsKeepLegacySamplingAndPicturesOptInAcrossRoundTrips() {
        for (String id : new String[] {"", "livery_1784692988460", "custom_team", "picture_v1_demo"}) {
            CarLiveryTexture texture = new CarLiveryTexture(id);
            var encoded = CarLiveryTexture.CODEC.encodeStart(JsonOps.INSTANCE, texture).getOrThrow();
            var restored = CarLiveryTexture.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            assertEquals(id, restored.id());
            assertEquals(id.startsWith("picture_v1_"), restored.isPicture());
        }
    }

    @Test
    void pictureUvsStayWithinTheExistingAtlasAndLeaveTheWhiteTexelUnused() throws Exception {
        // Load the real model without starting a client or creating a GPU context.
        Path assets = Path.of("src/main/resources/assets/openwheelracing");
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
            new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
                if (method.getName().equals("getResource")) {
                    Path file = assets.resolve(((Identifier) args[0]).getPath());
                    return Files.isRegularFile(file) ? Optional.of(new Resource(null, () -> Files.newInputStream(file))) : Optional.empty();
                }
                throw new UnsupportedOperationException(method.getName());
            });
        var model = ColoredObjModel.load(resources, Identifier.fromNamespaceAndPath("openwheelracing", "objmodels/f1_car_2026.obj"));
        assertFalse(model.faces.isEmpty());
        int paintable = 0;
        for (var face : model.faces) {
            if (!face.hasLiveryRegion()) continue;
            paintable++;
            assertTrue(face.liveryRegionX() > 0 && face.liveryRegionY() > 0);
            assertTrue(face.liveryRegionX() + face.liveryRegionWidth() <= 512);
            assertTrue(face.liveryRegionY() + face.liveryRegionHeight() <= 512);
            float[] xyz = {face.x0(), face.y0(), face.z0(), face.x1(), face.y1(), face.z1(),
                face.x2(), face.y2(), face.z2(), face.x3(), face.y3(), face.z3()};
            for (int v = 0; v < 4; v++) {
                float u = face.liveryPixelX(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]);
                float y = face.liveryPixelY(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]);
                assertTrue(Float.isFinite(u) && u >= 0 && u < 512);
                assertTrue(Float.isFinite(y) && y >= 0 && y < 512);
            }
        }
        assertTrue(paintable > 0);
        // Export the exact runtime layout for the sample-world preparation script.
        Path output = Path.of("build/livery/runtime-faces.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new Gson().toJson(model.faces));
    }
    @Test
    void sharedWorkbenchHelpersPreserveOriginalTransformsAndLegacyFirstTriangleSampling() {
        var model = LiveryWorkbenchExporter.loadModel();
        float scale = 1.9f / (0.806891f - -0.806891f);
        for (var face : model.faces) {
            assertEquals(face.x0() * scale, OpenwheelCarRenderer.modelX(face.x0()));
            assertEquals((face.y0() - -0.341034f) * scale, OpenwheelCarRenderer.modelY(face.y0()));
            assertEquals((face.z0() - (-5.237548f + -0.467113f) * 0.5f) * scale, OpenwheelCarRenderer.modelZ(face.z0()));
            float[] uvs = OpenwheelCarRenderer.bakePictureUvs(face);
            if (!OpenwheelCarRenderer.isLiveryPaintable(face)) {
                for (float uv : uvs) assertEquals(0.5f / 512, uv);
                continue;
            }
            assertEquals((face.liveryPixelX(face.x0(), face.y0(), face.z0()) + 0.5f) / 512, uvs[0]);
            assertEquals((face.liveryPixelY(face.x0(), face.y0(), face.z0()) + 0.5f) / 512, uvs[1]);
            float x = (face.x0() + face.x1() + face.x2()) / 3.0f;
            float y = (face.y0() + face.y1() + face.y2()) / 3.0f;
            float z = (face.z0() + face.z1() + face.z2()) / 3.0f;
            int[] sample = OpenwheelCarRenderer.legacySamplePixel(face);
            assertEquals(Math.max(0, Math.min(511, Math.round(face.liveryPixelX(x, y, z)))), sample[0]);
            assertEquals(Math.max(0, Math.min(511, Math.round(face.liveryPixelY(x, y, z)))), sample[1]);
        }
    }

}
