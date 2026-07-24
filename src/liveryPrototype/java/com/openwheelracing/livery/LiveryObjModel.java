package com.openwheelracing.livery;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LiveryObjModel {
    private final List<LiveryFace> faces;

    public LiveryObjModel(List<LiveryFace> faces) {
        this.faces = Collections.unmodifiableList(new ArrayList<>(faces));
    }

    public List<LiveryFace> faces() {
        return faces;
    }

    public static LiveryObjModel load(Path path) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<LiveryFace> faces = new ArrayList<>();
        int materialRgb = 0xFF000000;
        String group = "";

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    positions.add(new float[] {Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3])});
                } else if (line.startsWith("g ")) {
                    group = line.substring(2).trim();
                } else if (line.startsWith("usemtl ")) {
                    materialRgb = parseMaterialColor(line.substring(7).trim());
                } else if (line.startsWith("f ")) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length == 4 || tokens.length == 5) {
                        float[] a = positions.get(vertexIndex(tokens[1]));
                        float[] b = positions.get(vertexIndex(tokens[2]));
                        float[] c = positions.get(vertexIndex(tokens[3]));
                        float[] d = tokens.length == 5 ? positions.get(vertexIndex(tokens[4])) : c;
                        faces.add(new LiveryFace(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], tokens.length - 1, materialRgb, group));
                    }
                }
            }
        }

        return new LiveryObjModel(faces);
    }

    private static int vertexIndex(String token) {
        int slash = token.indexOf('/');
        String value = slash >= 0 ? token.substring(0, slash) : token;
        return Integer.parseInt(value) - 1;
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
        } catch (NumberFormatException ignored) {
            return 0xFF000000;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
