package com.openwheelracing.client.livery;

import com.google.gson.Gson;
import com.openwheelracing.client.render.ColoredObjModel;
import com.openwheelracing.client.render.OpenwheelCarRenderer;
import com.openwheelracing.content.car.CarLivery;
import com.openwheelracing.content.car.CarLiveryColors;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Development exporter: all model values are computed by production Java code. */
public final class LiveryWorkbenchExporter {
    private LiveryWorkbenchExporter() {}

    static ColoredObjModel loadModel() {
        Path assets = Path.of("src/main/resources/assets/openwheelracing");
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
            new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
                if (method.getName().equals("getResource")) {
                    Path file = assets.resolve(((Identifier) args[0]).getPath());
                    return Files.isRegularFile(file) ? Optional.of(new Resource(null, () -> Files.newInputStream(file))) : Optional.empty();
                }
                throw new UnsupportedOperationException(method.getName());
            });
        return ColoredObjModel.load(resources, Identifier.fromNamespaceAndPath("openwheelracing", "objmodels/f1_car_2026.obj"));
    }

    public static void main(String[] args) throws Exception {
        ColoredObjModel model = loadModel();
        int count = model.faces.size();
        ByteBuffer positions = buffer(count * 12 * 4);
        ByteBuffer normals = buffer(count * 3 * 4);
        ByteBuffer uvs = buffer(count * 8 * 4);
        ByteBuffer samples = buffer(count * 2 * 2);
        ByteBuffer panels = buffer(count * 2);
        ByteBuffer paintable = buffer(count);
        Map<String, Integer> panelIndices = new LinkedHashMap<>();
        List<Map<String, Object>> regions = new ArrayList<>();
        for (ColoredObjModel.Face face : model.faces) {
            float[] xyz = {face.x0(), face.y0(), face.z0(), face.x1(), face.y1(), face.z1(),
                face.x2(), face.y2(), face.z2(), face.x3(), face.y3(), face.z3()};
            for (int v = 0; v < 4; v++) {
                positions.putFloat(OpenwheelCarRenderer.modelX(xyz[v * 3]));
                positions.putFloat(OpenwheelCarRenderer.modelY(xyz[v * 3 + 1]));
                positions.putFloat(OpenwheelCarRenderer.modelZ(xyz[v * 3 + 2]));
            }
            normals.putFloat(face.nx()).putFloat(face.ny()).putFloat(face.nz());
            for (float value : OpenwheelCarRenderer.bakePictureUvs(face)) uvs.putFloat(value);
            boolean canPaint = OpenwheelCarRenderer.isLiveryPaintable(face);
            paintable.put((byte) (canPaint ? 1 : 0));
            int[] sample = canPaint ? OpenwheelCarRenderer.legacySamplePixel(face) : new int[] {0, 0};
            samples.putShort((short) sample[0]).putShort((short) sample[1]);
            int panel = -1;
            if (canPaint) {
                String key = face.group() + ':' + face.liveryRegionX() + ':' + face.liveryRegionY() + ':'
                    + face.liveryRegionWidth() + ':' + face.liveryRegionHeight();
                if (!panelIndices.containsKey(key)) {
                    panelIndices.put(key, panelIndices.size());
                    regions.add(Map.of("name", face.group(), "x", face.liveryRegionX(), "y", face.liveryRegionY(),
                        "width", face.liveryRegionWidth(), "height", face.liveryRegionHeight()));
                }
                panel = panelIndices.get(key);
            }
            panels.putShort((short) panel);
        }
        List<Map<String, Object>> palettes = new ArrayList<>();
        List<CarLiveryColors> colors = new ArrayList<>();
        List<String> names = new ArrayList<>();
        names.add("Custom / default");
        colors.add(CarLiveryColors.DEFAULT);
        for (CarLivery preset : CarLivery.values()) {
            names.add(preset.displayName());
            colors.add(CarLiveryColors.fromPreset(preset));
        }
        for (int i = 0; i < colors.size(); i++) {
            CarLiveryColors palette = colors.get(i);
            ByteBuffer fallback = buffer(count * 4);
            for (ColoredObjModel.Face face : model.faces) fallback.putInt(OpenwheelCarRenderer.liveryColor(face, palette, null));
            palettes.add(Map.of("name", names.get(i), "body", palette.body(), "colors", encode(fallback)));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 1);
        result.put("size", 512);
        result.put("faceCount", count);
        result.put("positions", encode(positions));
        result.put("normals", encode(normals));
        result.put("uvs", encode(uvs));
        result.put("samples", encode(samples));
        result.put("panels", encode(panels));
        result.put("paintable", encode(paintable));
        result.put("regions", regions);
        result.put("palettes", palettes);
        Map<String, String> sourceHashes = new LinkedHashMap<>();
        for (String source : List.of(
                "src/main/resources/assets/openwheelracing/objmodels/f1_car_2026.obj",
                "src/main/resources/assets/openwheelracing/objmodels/f1_car_2026.mtl",
                "src/main/java/com/openwheelracing/client/render/ColoredObjModel.java",
                "src/main/java/com/openwheelracing/client/render/OpenwheelCarRenderer.java",
                "src/main/java/com/openwheelracing/client/livery/ClientLiveryTextures.java",
                "src/main/java/com/openwheelracing/client/livery/PictureLivery.java",
                "src/main/java/com/openwheelracing/content/car/CarLivery.java",
                "src/main/java/com/openwheelracing/content/car/CarLiveryColors.java")) {
            if (Files.isRegularFile(Path.of(source))) sourceHashes.put(source, sha(Path.of(source)));
        }
        result.put("sourceHashes", sourceHashes);
        result.put("modelSha256", sha(Path.of("src/main/resources/assets/openwheelracing/objmodels/f1_car_2026.obj")));
        result.put("rendererSha256", sha(Path.of("src/main/java/com/openwheelracing/client/render/OpenwheelCarRenderer.java")));
        // Java reference vectors verify JS alpha compositing and aspect-fit rounding.
        List<int[]> composites = new ArrayList<>();
        for (int source : new int[] {0x00FFFFFF, 0x80FF0000, 0xFFFFFFFF, 0x01010203, 0x7F12ABEF}) {
            for (int background : new int[] {0xFF123456, 0xFF0000FF, 0xFFFFFFFF}) {
                composites.add(new int[] {source, background, PictureLivery.composite(source, background)});
            }
        }
        result.put("compositeReference", composites);
        List<int[]> fits = new ArrayList<>();
        for (int[] dimensions : new int[][] {{400, 200, 10, 20, 100, 100}, {200, 400, 10, 20, 100, 100},
                {4096, 1, 3, 7, 6, 220}, {7, 13, 8, 8, 35, 16}}) {
            var fit = PictureLivery.fit(dimensions[0], dimensions[1], dimensions[2], dimensions[3], dimensions[4], dimensions[5]);
            fits.add(new int[] {dimensions[0], dimensions[1], dimensions[2], dimensions[3], dimensions[4], dimensions[5],
                fit.x(), fit.y(), fit.width(), fit.height()});
        }
        result.put("fitReference", fits);
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new Gson().toJson(result));
        System.out.println("Exported " + count + " faces and " + regions.size() + " panels to " + output);
    }

    private static ByteBuffer buffer(int length) { return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN); }
    private static String encode(ByteBuffer bytes) { return Base64.getEncoder().encodeToString(bytes.array()); }
    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
