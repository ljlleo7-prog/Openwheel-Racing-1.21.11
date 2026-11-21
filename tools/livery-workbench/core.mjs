export const SIZE = 512;
export const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
export function fit(sw, sh, x, y, width, height) {
    if ([sw, sh, width, height].some(v => v <= 0)) throw new Error('Dimensions must be positive');
    const scale = Math.min(width / sw, height / sh);
    const w = Math.max(1, Math.round(sw * scale)), h = Math.max(1, Math.round(sh * scale));
    return {x: x + Math.trunc((width - w) / 2), y: y + Math.trunc((height - h) / 2), width: w, height: h};
}
export function composite(source, background) {
    const a = source >>> 24, inverse = 255 - a;
    const channel = shift => Math.floor((((source >>> shift) & 255) * a + ((background >>> shift) & 255) * inverse + 127) / 255);
    return (0xff000000 | channel(16) << 16 | channel(8) << 8 | channel(0)) >>> 0;
}
export function blendPixels(target, source, opacity = 1) {
    for (let i = 0; i < target.length; i += 4) {
        const alpha = Math.round(source[i + 3] * opacity), inverse = 255 - alpha;
        if (!alpha) continue;
        for (let c = 0; c < 3; c++) target[i + c] = Math.floor((source[i + c] * alpha + target[i + c] * inverse + 127) / 255);
        target[i + 3] = 255;
    }
}
export function decode(encoded, Type) {
    const raw = Uint8Array.from(atob(encoded), c => c.charCodeAt(0));
    return new Type(raw.buffer);
}
export function decodeModel(data) {
    return {...data, positions: decode(data.positions, Float32Array), normals: decode(data.normals, Float32Array),
        uvs: decode(data.uvs, Float32Array), samples: decode(data.samples, Uint16Array),
        panels: decode(data.panels, Int16Array), paintable: decode(data.paintable, Uint8Array),
        palettes: data.palettes.map(p => ({...p, colors: decode(p.colors, Uint32Array)}))};
}
export function panelAt(regions, x, y) {
    return regions.findIndex(p => x >= p.x && y >= p.y && x < p.x + p.width && y < p.y + p.height);
}
export function legacyFaceColor(model, face, pixels, palette) {
    const fallback = model.palettes[palette].colors[face];
    if (!model.paintable[face]) return fallback;
    const offset = (model.samples[face * 2 + 1] * SIZE + model.samples[face * 2]) * 4;
    return pixels[offset + 3] ? ((pixels[offset + 3] << 24) | pixels[offset] << 16 | pixels[offset + 1] << 8 | pixels[offset + 2]) >>> 0 : fallback;
}
export function safeName(value) {
    const clean = value.toLowerCase().replace(/[^a-z0-9_-]/g, '_').replace(/^picture_v1_/, '').slice(0, 60);
    return 'picture_v1_' + (clean || 'my_livery');
}
export class History {
    constructor(limit = 40) { this.limit = limit; this.undo = []; this.redo = []; }
    push(state) { this.undo.push(JSON.stringify(state)); if (this.undo.length > this.limit) this.undo.shift(); this.redo = []; }
    back(state) { if (!this.undo.length) return null; this.redo.push(JSON.stringify(state)); return JSON.parse(this.undo.pop()); }
    forward(state) { if (!this.redo.length) return null; this.undo.push(JSON.stringify(state)); return JSON.parse(this.redo.pop()); }
}
