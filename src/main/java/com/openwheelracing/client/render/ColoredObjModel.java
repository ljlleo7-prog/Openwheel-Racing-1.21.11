package com.openwheelracing.client.render;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ColoredObjModel {
    public record Face(
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float nx, float ny, float nz,
        int materialRgb,
        String group
    ) {}

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
        List<float[]> positions = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        int materialRgb = 0xFF000000;
        String group = "";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(rm.getResource(loc).orElseThrow().open()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    positions.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                } else if (line.startsWith("g ")) {
                    group = line.substring(2).trim();
                } else if (line.startsWith("usemtl ")) {
                    materialRgb = parseMaterialColor(line.substring(7).trim());
                } else if (line.startsWith("f ")) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length != 4 && tokens.length != 5) {
                        continue;
                    }
                    float[] a = positions.get(vertexIndex(tokens[1]));
                    float[] b = positions.get(vertexIndex(tokens[2]));
                    float[] c = positions.get(vertexIndex(tokens[3]));
                    float[] d = tokens.length == 5 ? positions.get(vertexIndex(tokens[4])) : c;
                    float[] n = normal(a, b, c);
                    faces.add(new Face(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], n[0], n[1], n[2], materialRgb, group));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OBJ: " + loc, e);
        }

        return new ColoredObjModel(faces);
    }

    private static int vertexIndex(String token) {
        int slash = token.indexOf('/');
        String value = slash >= 0 ? token.substring(0, slash) : token;
        return Integer.parseInt(value) - 1;
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
}
