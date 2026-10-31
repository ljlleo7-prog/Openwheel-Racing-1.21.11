package com.openwheelracing.client.map;

/** North is up (-Z); equal scale on both axes avoids distorting circuit geometry. */
public record LiveMapProjection(double centerX, double centerZ, double scale, int x, int y, int width, int height) {
    public static LiveMapProjection fit(double minX, double minZ, double maxX, double maxZ, int x, int y, int width, int height) {
        double scale = Math.min((width - 12.0) / Math.max(20, maxX - minX), (height - 12.0) / Math.max(20, maxZ - minZ));
        return new LiveMapProjection((minX + maxX) * 0.5, (minZ + maxZ) * 0.5, Math.max(0.001, scale), x, y, width, height);
    }

    public int screenX(double worldX) { return x + width / 2 + (int) Math.round((worldX - centerX) * scale); }
    public int screenY(double worldZ) { return y + height / 2 + (int) Math.round((worldZ - centerZ) * scale); }
    public boolean contains(double worldX, double worldZ) {
        int sx = screenX(worldX), sy = screenY(worldZ);
        return sx >= x && sx < x + width && sy >= y && sy < y + height;
    }
}
