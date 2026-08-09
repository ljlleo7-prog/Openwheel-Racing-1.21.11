package com.openwheelracing.client.render;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

public class ColoredObjModel {
    private static final int LIVERY_SIZE = 512;
    private static final int LIVERY_MARGIN = 8;
    private static final int LIVERY_GAP = 4;
    private static final int LIVERY_MIN_REGION = 6;
    private static final int LIVERY_SCALE = 110;
    private static final int LIVERY_MAX_REGION = 220;
    private static final int LIVERY_PROJECTION_TOP = 0;
    private static final int LIVERY_PROJECTION_SIDE = 1;
    private static final int LIVERY_PROJECTION_FRONT = 2;

    public record Face(
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float nx, float ny, float nz,
        int materialRgb,
        String group,
        int vertexCount,
        int liveryRegionX,
        int liveryRegionY,
        int liveryRegionWidth,
        int liveryRegionHeight,
        float liveryMinU,
        float liveryMaxU,
        float liveryMinV,
        float liveryMaxV,
        int liveryProjection
    ) {
        public boolean hasLiveryRegion() {
            return liveryRegionWidth > 0 && liveryRegionHeight > 0;
        }

        public float liveryPixelX(float x, float y, float z) {
            float sourceU = switch (liveryProjection) {
                case LIVERY_PROJECTION_SIDE -> z;
                case LIVERY_PROJECTION_FRONT, LIVERY_PROJECTION_TOP -> x;
                default -> x;
            };
            return liveryRegionX + (sourceU - liveryMinU) * Math.max(1, liveryRegionWidth - 1) / (liveryMaxU - liveryMinU);
        }

        public float liveryPixelY(float x, float y, float z) {
            float sourceV = switch (liveryProjection) {
                case LIVERY_PROJECTION_SIDE, LIVERY_PROJECTION_FRONT -> -y;
                case LIVERY_PROJECTION_TOP -> z;
                default -> z;
            };
            return liveryRegionY + (sourceV - liveryMinV) * Math.max(1, liveryRegionHeight - 1) / (liveryMaxV - liveryMinV);
        }
    }

    public final List<Face> faces;

    private ColoredObjModel(List<Face> faces) {
        this.faces = faces;
    }

    /**
     * Pre-compute one color per face for a given livery.
     * Call once per livery at load time; store the result and index into it during rendering
     * instead of re-classifying each face every frame.
     */
    public int[] bakeColors(java.util.function.Function<Face, Integer> colorFn) {
        int[] colors = new int[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            colors[i] = colorFn.apply(faces.get(i));
        }
        return colors;
    }

    public static ColoredObjModel load(ResourceManager rm, Identifier loc) {
        return load(rm, loc, true);
    }

    public static ColoredObjModel load(ResourceManager rm, Identifier loc, boolean buildLiveryAtlas) {
        List<float[]> positions = new ArrayList<>();
        List<ParsedFace> parsedFaces = new ArrayList<>();
        Map<String, Integer> materialColors = new HashMap<>();
        int materialRgb = 0xFF000000;
        String group = "";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(rm.getResource(loc).orElseThrow().open()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("mtllib ")) {
                    materialColors.putAll(loadMaterialColors(rm, loc, line.substring(7).trim()));
                } else if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    positions.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                } else if (line.startsWith("g ")) {
                    group = line.substring(2).trim();
                } else if (line.startsWith("usemtl ")) {
                    String material = line.substring(7).trim();
                    materialRgb = materialColors.getOrDefault(material, parseMaterialColor(material));
                } else if (line.startsWith("f ")) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length != 4 && tokens.length != 5) {
                        continue;
                    }
                    int[] vertexIndices = vertexIndices(tokens);
                    float[] a = positions.get(vertexIndices[0]);
                    float[] b = positions.get(vertexIndices[1]);
                    float[] c = positions.get(vertexIndices[2]);
                    float[] d = tokens.length == 5 ? positions.get(vertexIndices[3]) : c;
                    float[] n = normal(a, b, c);
                    parsedFaces.add(new ParsedFace(a, b, c, d, n, materialRgb, group, tokens.length - 1, vertexIndices));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OBJ: " + loc, e);
        }

        AtlasRegion[] regions = buildLiveryAtlas ? buildLiveryRegions(parsedFaces) : new AtlasRegion[parsedFaces.size()];
        List<Face> faces = new ArrayList<>(parsedFaces.size());
        for (int i = 0; i < parsedFaces.size(); i++) {
            ParsedFace face = parsedFaces.get(i);
            AtlasRegion region = regions[i];
            if (region == null) {
                region = AtlasRegion.NONE;
            }
            faces.add(new Face(
                face.x0(), face.y0(), face.z0(),
                face.x1(), face.y1(), face.z1(),
                face.x2(), face.y2(), face.z2(),
                face.x3(), face.y3(), face.z3(),
                face.nx(), face.ny(), face.nz(),
                face.materialRgb(), face.group(), face.vertexCount(),
                region.x(), region.y(), region.width(), region.height(),
                region.minU(), region.maxU(), region.minV(), region.maxV(), region.projection()
            ));
        }

        return new ColoredObjModel(faces);
    }

    private static int[] vertexIndices(String[] tokens) {
        int[] indices = new int[tokens.length - 1];
        for (int i = 1; i < tokens.length; i++) {
            indices[i - 1] = vertexIndex(tokens[i]);
        }
        return indices;
    }

    private static int vertexIndex(String token) {
        int slash = token.indexOf('/');
        String value = slash >= 0 ? token.substring(0, slash) : token;
        return Integer.parseInt(value) - 1;
    }

    private static AtlasRegion[] buildLiveryRegions(List<ParsedFace> faces) {
        AtlasRegion[] regions = new AtlasRegion[faces.size()];
        List<LiveryIsland> islands = connectedPaintableIslands(faces);
        for (LiveryIsland island : islands) {
            island.prepareRectangle();
        }
        islands.sort(Comparator.comparingInt(LiveryIsland::height).reversed().thenComparing(Comparator.comparingInt(LiveryIsland::width).reversed()));

        int x = LIVERY_MARGIN;
        int y = LIVERY_MARGIN;
        int rowHeight = 0;
        for (LiveryIsland island : islands) {
            if (x + island.width() > LIVERY_SIZE - LIVERY_MARGIN) {
                x = LIVERY_MARGIN;
                y += rowHeight + LIVERY_GAP;
                rowHeight = 0;
            }
            if (y + island.height() > LIVERY_SIZE - LIVERY_MARGIN) {
                throw new IllegalStateException("Prototype car livery atlas overflow");
            }
            AtlasRegion region = new AtlasRegion(x, y, island.width(), island.height(), island.minU(), island.maxU(), island.minV(), island.maxV(), island.projection());
            for (int faceIndex : island.faceIndices()) {
                regions[faceIndex] = region;
            }
            x += island.width() + LIVERY_GAP;
            rowHeight = Math.max(rowHeight, island.height());
        }
        return regions;
    }

    private static List<LiveryIsland> connectedPaintableIslands(List<ParsedFace> faces) {
        Map<String, List<Integer>> paintableByGroup = new HashMap<>();
        for (int i = 0; i < faces.size(); i++) {
            ParsedFace face = faces.get(i);
            if (isPaintableMaterial(face.materialRgb())) {
                paintableByGroup.computeIfAbsent(face.group(), key -> new ArrayList<>()).add(i);
            }
        }

        List<LiveryIsland> islands = new ArrayList<>();
        for (List<Integer> groupFaces : paintableByGroup.values()) {
            Map<Edge, List<Integer>> facesByEdge = new HashMap<>();
            for (int faceIndex : groupFaces) {
                int[] indices = faces.get(faceIndex).vertexIndices();
                for (int i = 0; i < indices.length; i++) {
                    facesByEdge.computeIfAbsent(new Edge(indices[i], indices[(i + 1) % indices.length]), key -> new ArrayList<>()).add(faceIndex);
                }
            }

            Map<Integer, List<Integer>> adjacent = new HashMap<>();
            for (List<Integer> connectedFaces : facesByEdge.values()) {
                for (int a : connectedFaces) {
                    List<Integer> neighbors = adjacent.computeIfAbsent(a, key -> new ArrayList<>());
                    for (int b : connectedFaces) {
                        if (a != b && !neighbors.contains(b)) {
                            neighbors.add(b);
                        }
                    }
                }
            }

            Map<Integer, Boolean> seen = new HashMap<>();
            for (int start : groupFaces) {
                if (seen.containsKey(start)) {
                    continue;
                }
                List<Integer> islandFaces = new ArrayList<>();
                Queue<Integer> queue = new ArrayDeque<>();
                queue.add(start);
                seen.put(start, true);
                while (!queue.isEmpty()) {
                    int faceIndex = queue.remove();
                    islandFaces.add(faceIndex);
                    for (int neighbor : adjacent.getOrDefault(faceIndex, List.of())) {
                        if (!seen.containsKey(neighbor)) {
                            seen.put(neighbor, true);
                            queue.add(neighbor);
                        }
                    }
                }
                islands.add(new LiveryIsland(islandFaces, faces));
            }
        }
        return islands;
    }

    private static boolean isPaintableMaterial(int materialRgb) {
        int rgb = materialRgb & 0x00FFFFFF;
        return rgb == 0x00030303 || rgb == 0x00FFFFFF;
    }

    private static float[] normal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0];
        float uy = b[1] - a[1];
        float uz = b[2] - a[2];
        float vx = c[0] - a[0];
        float vy = c[1] - a[1];
        float vz = c[2] - a[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0.00001f) {
            return new float[] {0.0f, 1.0f, 0.0f};
        }
        return new float[] {nx / length, ny / length, nz / length};
    }

    private static Map<String, Integer> loadMaterialColors(ResourceManager rm, Identifier objLoc, String materialFile) {
        Map<String, Integer> colors = new HashMap<>();
        Identifier materialLoc = sibling(objLoc, materialFile);
        Optional<net.minecraft.server.packs.resources.Resource> resource = rm.getResource(materialLoc);
        if (resource.isEmpty()) {
            return colors;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open()))) {
            String material = "";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl ")) {
                    material = line.substring(7).trim();
                } else if (line.startsWith("Kd ") && !material.isEmpty()) {
                    colors.put(material, parseDiffuseColor(line.substring(3).trim()));
                }
            }
        } catch (Exception ignored) {
            colors.clear();
        }
        return colors;
    }

    private static Identifier sibling(Identifier loc, String fileName) {
        String path = loc.getPath();
        int slash = path.lastIndexOf('/');
        String siblingPath = slash >= 0 ? path.substring(0, slash + 1) + fileName : fileName;
        return Identifier.fromNamespaceAndPath(loc.getNamespace(), siblingPath);
    }

    private static int parseDiffuseColor(String color) {
        String[] rgb = color.split("\\s+");
        if (rgb.length < 3) {
            return 0xFF000000;
        }
        try {
            int r = clamp(Math.round(Float.parseFloat(rgb[0].trim()) * 255.0f));
            int g = clamp(Math.round(Float.parseFloat(rgb[1].trim()) * 255.0f));
            int b = clamp(Math.round(Float.parseFloat(rgb[2].trim()) * 255.0f));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return 0xFF000000;
        }
    }

    private static int parseMaterialColor(String material) {
        String[] rgb = material.split(",");
        if (rgb.length != 3) {
            return 0xFF000000;
        }
        try {
            int r = clamp(Integer.parseInt(rgb[0].trim()));
            int g = clamp(Integer.parseInt(rgb[1].trim()));
            int b = clamp(Integer.parseInt(rgb[2].trim()));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        } catch (NumberFormatException e) {
            return 0xFF000000;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record Edge(int a, int b) {
        private Edge {
            if (a > b) {
                int swap = a;
                a = b;
                b = swap;
            }
        }
    }

    private record ParsedFace(
        float[] a, float[] b, float[] c, float[] d,
        float[] normal,
        int materialRgb,
        String group,
        int vertexCount,
        int[] vertexIndices
    ) {
        private float x0() { return a[0]; }
        private float y0() { return a[1]; }
        private float z0() { return a[2]; }
        private float x1() { return b[0]; }
        private float y1() { return b[1]; }
        private float z1() { return b[2]; }
        private float x2() { return c[0]; }
        private float y2() { return c[1]; }
        private float z2() { return c[2]; }
        private float x3() { return d[0]; }
        private float y3() { return d[1]; }
        private float z3() { return d[2]; }
        private float nx() { return normal[0]; }
        private float ny() { return normal[1]; }
        private float nz() { return normal[2]; }
    }

    private record AtlasRegion(int x, int y, int width, int height, float minU, float maxU, float minV, float maxV, int projection) {
        private static final AtlasRegion NONE = new AtlasRegion(0, 0, 0, 0, 0.0f, 1.0f, 0.0f, 1.0f, LIVERY_PROJECTION_TOP);
    }

    private static final class LiveryIsland {
        private final List<Integer> faceIndices;
        private final List<ParsedFace> faces;
        private int projection;
        private float minU;
        private float maxU;
        private float minV;
        private float maxV;
        private int width;
        private int height;

        private LiveryIsland(List<Integer> faceIndices, List<ParsedFace> faces) {
            this.faceIndices = faceIndices;
            this.faces = faces;
        }

        private List<Integer> faceIndices() {
            return faceIndices;
        }

        private int projection() {
            return projection;
        }

        private float minU() {
            return minU;
        }

        private float maxU() {
            return maxU;
        }

        private float minV() {
            return minV;
        }

        private float maxV() {
            return maxV;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private void prepareRectangle() {
            Bounds bounds = bounds();
            projection = chooseProjection(bounds, faces.get(faceIndices.get(0)).group());
            minU = Float.POSITIVE_INFINITY;
            maxU = Float.NEGATIVE_INFINITY;
            minV = Float.POSITIVE_INFINITY;
            maxV = Float.NEGATIVE_INFINITY;
            for (int faceIndex : faceIndices) {
                ParsedFace face = faces.get(faceIndex);
                include(face.x0(), face.y0(), face.z0());
                include(face.x1(), face.y1(), face.z1());
                include(face.x2(), face.y2(), face.z2());
                if (face.vertexCount() == 4) {
                    include(face.x3(), face.y3(), face.z3());
                }
            }
            widenTinyRanges();
            width = Math.max(LIVERY_MIN_REGION, Math.round((maxU - minU) * LIVERY_SCALE));
            height = Math.max(LIVERY_MIN_REGION, Math.round((maxV - minV) * LIVERY_SCALE));
            if (faceIndices.size() > 1000) {
                width = Math.max(width, 36);
                height = Math.max(height, 36);
            }
            int largest = Math.max(width, height);
            if (largest > LIVERY_MAX_REGION) {
                float scale = (float) LIVERY_MAX_REGION / largest;
                width = Math.max(LIVERY_MIN_REGION, Math.round(width * scale));
                height = Math.max(LIVERY_MIN_REGION, Math.round(height * scale));
            }
        }

        private void include(float x, float y, float z) {
            float u = switch (projection) {
                case LIVERY_PROJECTION_SIDE -> z;
                case LIVERY_PROJECTION_FRONT, LIVERY_PROJECTION_TOP -> x;
                default -> x;
            };
            float v = switch (projection) {
                case LIVERY_PROJECTION_SIDE, LIVERY_PROJECTION_FRONT -> -y;
                case LIVERY_PROJECTION_TOP -> z;
                default -> z;
            };
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        private Bounds bounds() {
            Bounds bounds = new Bounds();
            for (int faceIndex : faceIndices) {
                ParsedFace face = faces.get(faceIndex);
                bounds.include(face.x0(), face.y0(), face.z0());
                bounds.include(face.x1(), face.y1(), face.z1());
                bounds.include(face.x2(), face.y2(), face.z2());
                if (face.vertexCount() == 4) {
                    bounds.include(face.x3(), face.y3(), face.z3());
                }
            }
            return bounds;
        }

        private void widenTinyRanges() {
            if (maxU - minU < 0.001f) {
                minU -= 0.0005f;
                maxU += 0.0005f;
            }
            if (maxV - minV < 0.001f) {
                minV -= 0.0005f;
                maxV += 0.0005f;
            }
        }

        private static int chooseProjection(Bounds bounds, String group) {
            float xRange = bounds.maxX - bounds.minX;
            float yRange = bounds.maxY - bounds.minY;
            float zRange = bounds.maxZ - bounds.minZ;
            if (group.contains("Wheel") || group.contains("Endplate")) {
                return LIVERY_PROJECTION_SIDE;
            }
            if (zRange < xRange * 0.65f && zRange < yRange * 1.5f) {
                return LIVERY_PROJECTION_FRONT;
            }
            if (xRange < yRange * 1.1f && xRange < zRange * 0.8f) {
                return LIVERY_PROJECTION_SIDE;
            }
            return LIVERY_PROJECTION_TOP;
        }
    }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        private void include(float x, float y, float z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
    }

}
