import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {SIZE, clamp, fit, composite, blendPixels, decodeModel, legacyFaceColor, panelAt, safeName, History} from './core.mjs';

const $ = id => document.getElementById(id);
const model = decodeModel(window.__LIVERY_MODEL__);
const canvas = $('editor'), ctx = canvas.getContext('2d', {willReadFrequently: true});
const overlay = $('overlay'), pen = overlay.getContext('2d');
const atlas = document.createElement('canvas'); atlas.width = atlas.height = SIZE;
const atlasCtx = atlas.getContext('2d', {willReadFrequently: true});
const scratch = document.createElement('canvas'); scratch.width = scratch.height = SIZE;
const scratchCtx = scratch.getContext('2d', {willReadFrequently: true});
const images = new Map(), history = new History();
let state = {version: 1, palette: 0, base: null, layers: []};
let selected = null, selectedPanel = -1, tool = 'select', gesture = null;
let dirty = true, previewDirty = true, basePixels, lastPixels, lastLegacyPixels;
let renderer, scene, camera, controls, mesh, material, geometry, texture;
let indices;
let sequence = 0;
const status = text => { $('status').textContent = text; };
const layer = () => state.layers.find(item => item.id === selected);
const clone = value => JSON.parse(JSON.stringify(value));
const remember = () => { history.push(state); updateHistory(); };
const changed = () => { dirty = true; previewDirty = true; $('save-state').textContent = 'Unsaved changes · local only'; };
function updateHistory() { $('undo').disabled = !history.undo.length; $('redo').disabled = !history.redo.length; }
function invalidate() { basePixels = null; changed(); }
function bytesForColor(pixels, offset, color) {
    pixels[offset] = color >>> 16 & 255; pixels[offset + 1] = color >>> 8 & 255;
    pixels[offset + 2] = color & 255; pixels[offset + 3] = 255;
}
function flatTriangle(pixels, points, color) {
    const [a, b, c] = points;
    const edge = (p, q, x, y) => (x - p[0]) * (q[1] - p[1]) - (y - p[1]) * (q[0] - p[0]);
    const area = edge(a, b, c[0], c[1]); if (Math.abs(area) < .00001) return;
    const x0 = clamp(Math.floor(Math.min(a[0], b[0], c[0])), 0, 511), x1 = clamp(Math.ceil(Math.max(a[0], b[0], c[0])), 0, 511);
    const y0 = clamp(Math.floor(Math.min(a[1], b[1], c[1])), 0, 511), y1 = clamp(Math.ceil(Math.max(a[1], b[1], c[1])), 0, 511);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const e0 = edge(b, c, x + .5, y + .5), e1 = edge(c, a, x + .5, y + .5), e2 = edge(a, b, x + .5, y + .5);
        if ((e0 >= 0 && e1 >= 0 && e2 >= 0) || (e0 <= 0 && e1 <= 0 && e2 <= 0)) bytesForColor(pixels, (y * SIZE + x) * 4, color);
    }
}
function getBase() {
    if (basePixels) return basePixels.slice();
    if (state.base && images.has(state.base)) {
        const data = images.get(state.base).data;
        basePixels = data.slice();
    } else {
        basePixels = new Uint8ClampedArray(SIZE * SIZE * 4);
        const palette = model.palettes[state.palette];
        for (let i = 0; i < basePixels.length; i += 4) bytesForColor(basePixels, i, palette.body);
        for (let f = 0; f < model.faceCount; f++) {
            if (!model.paintable[f]) continue;
            const points = Array.from({length: 4}, (_, v) => [model.uvs[f * 8 + v * 2] * SIZE - .5, model.uvs[f * 8 + v * 2 + 1] * SIZE - .5]);
            flatTriangle(basePixels, points.slice(0, 3), palette.colors[f]);
            flatTriangle(basePixels, [points[0], points[2], points[3]], palette.colors[f]);
        }
    }
    return basePixels.slice();
}
function imagePixels(item) {
    const loaded = images.get(item.src); if (!loaded) return new Uint8ClampedArray(SIZE * SIZE * 4);
    const result = new Uint8ClampedArray(SIZE * SIZE * 4);
    const angle = item.angle * Math.PI / 180, co = Math.cos(angle), si = Math.sin(angle);
    const cx = item.x + item.w / 2, cy = item.y + item.h / 2;
    const radiusX = Math.abs(co * item.w / 2) + Math.abs(si * item.h / 2);
    const radiusY = Math.abs(si * item.w / 2) + Math.abs(co * item.h / 2);
    const x0 = clamp(Math.floor(cx - radiusX), 0, 511), x1 = clamp(Math.ceil(cx + radiusX), 0, 512);
    const y0 = clamp(Math.floor(cy - radiusY), 0, 511), y1 = clamp(Math.ceil(cy + radiusY), 0, 512);
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        let u, v;
        if (item.angle % 360 === 0) { u = (x - item.x) / item.w; v = (y - item.y) / item.h; }
        else {
            const dx = x + .5 - cx, dy = y + .5 - cy;
            u = (dx * co + dy * si) / item.w + .5; v = (-dx * si + dy * co) / item.h + .5;
        }
        if (u < 0 || v < 0 || u >= 1 || v >= 1) continue;
        let sx = Math.floor(u * loaded.width), sy = Math.floor(v * loaded.height);
        if (item.flipX) sx = loaded.width - 1 - sx;
        if (item.flipY) sy = loaded.height - 1 - sy;
        const from = (sy * loaded.width + sx) * 4, to = (y * SIZE + x) * 4;
        result.set(loaded.data.subarray(from, from + 4), to);
    }
    return result;
}
function layerPixels(item) {
    if (item.type === 'image') return imagePixels(item);
    scratchCtx.clearRect(0, 0, SIZE, SIZE); scratchCtx.save();
    scratchCtx.translate(item.x + item.w / 2, item.y + item.h / 2);
    scratchCtx.rotate(item.angle * Math.PI / 180);
    scratchCtx.scale(item.flipX ? -1 : 1, item.flipY ? -1 : 1);
    scratchCtx.translate(-item.w / 2, -item.h / 2);
    scratchCtx.scale(item.w / item.sourceW, item.h / item.sourceH);
    scratchCtx.strokeStyle = scratchCtx.fillStyle = item.color;
    scratchCtx.lineWidth = item.size; scratchCtx.lineCap = scratchCtx.lineJoin = 'round';
    scratchCtx.beginPath();
    if (item.type === 'stroke') {
        item.points.forEach((p, i) => i ? scratchCtx.lineTo(...p) : scratchCtx.moveTo(...p));
        if (item.points.length === 1) { scratchCtx.arc(...item.points[0], item.size / 2, 0, Math.PI * 2); scratchCtx.fill(); }
        else scratchCtx.stroke();
    } else if (item.type === 'rect') {
        if (item.filled) scratchCtx.fillRect(0, 0, item.sourceW, item.sourceH);
        else scratchCtx.strokeRect(item.size / 2, item.size / 2, Math.max(0, item.sourceW - item.size), Math.max(0, item.sourceH - item.size));
    } else if (item.type === 'ellipse') {
        const inset = item.filled ? 0 : item.size / 2;
        scratchCtx.ellipse(item.sourceW / 2, item.sourceH / 2, Math.max(.1, item.sourceW / 2 - inset), Math.max(.1, item.sourceH / 2 - inset), 0, 0, Math.PI * 2);
        item.filled ? scratchCtx.fill() : scratchCtx.stroke();
    } else if (item.type === 'line') {
        scratchCtx.moveTo(0, item.slope ? item.sourceH : 0); scratchCtx.lineTo(item.sourceW, item.slope ? 0 : item.sourceH); scratchCtx.stroke();
    } else if (item.type === 'text') {
        scratchCtx.font = `900 ${item.sourceH * .8}px sans-serif`; scratchCtx.textBaseline = 'middle';
        scratchCtx.fillText(item.text, 0, item.sourceH / 2, item.sourceW);
    }
    scratchCtx.restore();
    return scratchCtx.getImageData(0, 0, SIZE, SIZE).data;
}
function compose() {
    const pixels = getBase();
    for (const item of state.layers) if (item.visible) blendPixels(pixels, layerPixels(item), item.opacity);
    lastLegacyPixels = pixels.slice();
    // Picture textures normalize against white in ClientLiveryTextures.register.
    for (let i = 0; i < pixels.length; i += 4) {
        if (pixels[i + 3] === 255) continue;
        bytesForColor(pixels, i, composite((pixels[i+3]<<24 | pixels[i]<<16 | pixels[i+1]<<8 | pixels[i+2])>>>0, 0xffffffff));
    }
    // Pixel zero is the white texel used by nonpaintable model faces.
    pixels.set([255, 255, 255, 255], 0);
    lastPixels = pixels;
    const image = new ImageData(pixels, SIZE, SIZE); ctx.putImageData(image, 0, 0); atlasCtx.putImageData(image, 0, 0);
    if (texture) texture.needsUpdate = true;
    dirty = false; previewDirty = true;
}
function boundsPoint(item, x, y) {
    const angle = -item.angle * Math.PI / 180, dx = x - item.x - item.w / 2, dy = y - item.y - item.h / 2;
    return {x: dx * Math.cos(angle) - dy * Math.sin(angle) + item.w / 2,
        y: dx * Math.sin(angle) + dy * Math.cos(angle) + item.h / 2};
}
function selectAt(x, y) {
    for (const item of [...state.layers].reverse()) {
        if (!item.visible) continue;
        const p = boundsPoint(item, x, y);
        if (p.x >= 0 && p.y >= 0 && p.x <= item.w && p.y <= item.h) return item;
    }
    return null;
}
function drawOverlay() {
    pen.clearRect(0, 0, SIZE, SIZE); const zoom = SIZE / Math.max(1, overlay.getBoundingClientRect().width);
    if ($('guides').checked) {
        pen.lineWidth = zoom * .7;
        model.regions.forEach((p, i) => {
            pen.strokeStyle = i === selectedPanel ? '#d9ff8b' : '#b4d6d52b';
            pen.strokeRect(p.x, p.y, p.width, p.height);
        });
    }
    if (selectedPanel >= 0) {
        const p = model.regions[selectedPanel]; pen.strokeStyle = '#bcf27f'; pen.lineWidth = 1.5 * zoom; pen.strokeRect(p.x, p.y, p.width, p.height);
    }
    const item = layer();
    if (item && item.visible) {
        pen.save(); pen.translate(item.x + item.w / 2, item.y + item.h / 2); pen.rotate(item.angle * Math.PI / 180);
        pen.strokeStyle = '#fff'; pen.lineWidth = zoom; pen.setLineDash([4 * zoom, 3 * zoom]);
        pen.strokeRect(-item.w / 2, -item.h / 2, item.w, item.h); pen.setLineDash([]);
        pen.fillStyle = '#a8ed80'; pen.fillRect(item.w / 2 - 4 * zoom, item.h / 2 - 4 * zoom, 8 * zoom, 8 * zoom); pen.restore();
    }
}
function updateInspector() {
    const item = layer();
    for (const [field, key] of [['x','x'],['y','y'],['w','w'],['h','h'],['angle','angle'],['opacity','opacity']]) {
        const input = $('layer-' + field); input.disabled = !item;
        input.value = item ? Math.round(item[key] * (key === 'opacity' ? 100 : 1)) : '';
    }
    for (const id of ['delete','duplicate','layer-up','layer-down','flip-x','flip-y','fit-atlas','fit-panel']) $(id).disabled = !item;
    if (item) $('fit-panel').disabled = selectedPanel < 0;
}
function updateLayers() {
    $('layer-count').textContent = state.layers.length;
    $('layer-list').replaceChildren();
    for (const item of [...state.layers].reverse()) {
        const row = document.createElement('div'); row.className = 'layer-row' + (item.id === selected ? ' active' : '');
        const eye = document.createElement('button'); eye.textContent = item.visible ? '◉' : '○'; eye.title = item.visible ? 'Hide layer' : 'Show layer';
        eye.addEventListener('click', event => { event.stopPropagation(); remember(); item.visible = !item.visible; changed(); updateLayers(); });
        const name = document.createElement('span'); name.textContent = item.name;
        const kind = document.createElement('small'); kind.textContent = item.type;
        row.append(eye, name, kind); row.addEventListener('click', () => { selected = item.id; setTool('select'); updateLayers(); });
        $('layer-list').append(row);
    }
    if (!state.layers.length) { const text = document.createElement('div'); text.className = 'empty-layer'; text.textContent = 'Your canvas is ready. Paste a picture or make your first brush stroke.'; $('layer-list').append(text); }
    updateInspector(); drawOverlay();
}
function setPanel(index) {
    selectedPanel = index;
    const p = model.regions[index];
    $('focus-panel').disabled = !p;
    $('selected-panel').textContent = p ? `${p.name} · ${p.width} × ${p.height} px · atlas ${p.x}, ${p.y}` : 'Select a body panel on the car or atlas.';
    updateInspector(); drawOverlay();
}
function setTool(value) {
    tool = value; document.querySelectorAll('[data-tool]').forEach(b => b.classList.toggle('active', b.dataset.tool === tool));
    overlay.style.cursor = tool === 'select' ? 'move' : tool === 'picker' ? 'crosshair' : 'crosshair';
}
function newLayer(type, properties = {}) {
    return {id: crypto.randomUUID(), name: `${type[0].toUpperCase() + type.slice(1)} ${++sequence}`, type,
        x: 0, y: 0, w: 512, h: 512, sourceW: 512, sourceH: 512,
        angle: 0, flipX: false, flipY: false, opacity: 1, visible: true,
        color: $('color').value, size: +$('brush-size').value, filled: $('filled').checked, ...properties};
}
function addLayer(item) { state.layers.push(item); selected = item.id; changed(); updateLayers(); }
function point(event) {
    const rect = overlay.getBoundingClientRect();
    return {x: clamp((event.clientX - rect.left) * SIZE / rect.width, 0, 511), y: clamp((event.clientY - rect.top) * SIZE / rect.height, 0, 511)};
}
function eraserAt(item, x, y) {
    const pixels = layerPixels(item); scratchCtx.putImageData(new ImageData(new Uint8ClampedArray(pixels), SIZE, SIZE), 0, 0);
    scratchCtx.save(); scratchCtx.globalCompositeOperation = 'destination-out'; scratchCtx.lineCap = 'round';
    scratchCtx.lineWidth = +$('brush-size').value; scratchCtx.beginPath();
    const last = gesture.last || {x, y}; scratchCtx.moveTo(last.x, last.y); scratchCtx.lineTo(x, y); scratchCtx.stroke();
    scratchCtx.beginPath(); scratchCtx.arc(x, y, +$('brush-size').value / 2, 0, Math.PI * 2); scratchCtx.fill(); scratchCtx.restore();
    const src = scratch.toDataURL('image/png');
    if (item.src && item.src !== gesture.originalSrc) images.delete(item.src);
    images.set(src, {width: SIZE, height: SIZE, data: scratchCtx.getImageData(0, 0, SIZE, SIZE).data});
    Object.assign(item, {type: 'image', src, x: 0, y: 0, w: SIZE, h: SIZE, angle: 0, flipX: false, flipY: false});
    gesture.last = {x, y}; changed();
}
overlay.addEventListener('pointerdown', event => {
    if (event.button !== 0) return;
    overlay.focus(); overlay.setPointerCapture(event.pointerId);
    const p = point(event);
    if (tool === 'panel') { setPanel(panelAt(model.regions, p.x, p.y)); return; }
    if (tool === 'picker') {
        if (dirty) compose(); const offset = (Math.floor(p.y) * SIZE + Math.floor(p.x)) * 4;
        $('color').value = '#' + [...lastPixels.slice(offset, offset + 3)].map(n => n.toString(16).padStart(2, '0')).join('');
        status('Color sampled from the atlas.'); return;
    }
    if (tool === 'select') {
        const current = layer(); const local = current ? boundsPoint(current, p.x, p.y) : null;
        const tolerance = 9 * SIZE / overlay.getBoundingClientRect().width;
        const resize = current && local && Math.abs(local.x - current.w) < tolerance && Math.abs(local.y - current.h) < tolerance;
        const hit = resize ? current : selectAt(p.x, p.y);
        selected = hit?.id || null; updateLayers();
        if (hit) { remember(); gesture = {type: resize ? 'resize' : 'move', start: p, original: clone(hit), id: hit.id}; }
        else setPanel(panelAt(model.regions, p.x, p.y));
        return;
    }
    if (tool === 'eraser') {
        const item = layer(); if (!item) { status('Select a layer to erase, or delete a shape or stroke.'); return; }
        remember(); gesture = {type: 'eraser', start: p, originalSrc: item.src}; eraserAt(item, p.x, p.y); return;
    }
    remember();
    if (tool === 'brush') {
        const item = newLayer('stroke', {points: [[p.x, p.y]]}); addLayer(item); gesture = {type: 'brush', start: p, id: item.id};
    } else {
        const item = newLayer(tool, {x: p.x, y: p.y, w: 1, h: 1, sourceW: 1, sourceH: 1}); addLayer(item);
        gesture = {type: 'shape', start: p, id: item.id};
    }
});
overlay.addEventListener('pointermove', event => {
    const p = point(event); $('coordinates').textContent = `x ${Math.floor(p.x)}   y ${Math.floor(p.y)}`;
    if (!gesture) return;
    const item = layer(); if (!item) return;
    if (gesture.type === 'move') {
        item.x = Math.round(gesture.original.x + p.x - gesture.start.x); item.y = Math.round(gesture.original.y + p.y - gesture.start.y);
    } else if (gesture.type === 'resize') {
        const local = boundsPoint(gesture.original, p.x, p.y);
        item.w = clamp(Math.round(local.x), 1, 4096); item.h = clamp(Math.round(local.y), 1, 4096);
        if (event.shiftKey) item.h = Math.max(1, Math.round(item.w * gesture.original.h / gesture.original.w));
    } else if (gesture.type === 'brush') item.points.push([p.x, p.y]);
    else if (gesture.type === 'eraser') { eraserAt(item, p.x, p.y); return; }
    else if (gesture.type === 'shape') {
        item.x = Math.min(p.x, gesture.start.x); item.y = Math.min(p.y, gesture.start.y);
        item.w = Math.max(1, Math.abs(p.x - gesture.start.x)); item.h = Math.max(1, Math.abs(p.y - gesture.start.y));
        item.sourceW = item.w; item.sourceH = item.h;
        item.slope = (p.x - gesture.start.x) * (p.y - gesture.start.y) < 0;
    }
    changed(); updateInspector(); drawOverlay();
});
function endGesture() {
    if (!gesture) return;
    const item = layer();
    if (gesture.type === 'brush' && item) {
        const padding = item.size / 2 + 1;
        const bounds = item.points.reduce((b, p) => [Math.min(b[0],p[0]), Math.min(b[1],p[1]), Math.max(b[2],p[0]), Math.max(b[3],p[1])], [Infinity,Infinity,-Infinity,-Infinity]);
        const x = Math.floor(bounds[0] - padding), y = Math.floor(bounds[1] - padding);
        item.w = item.sourceW = Math.ceil(bounds[2] + padding) - x;
        item.h = item.sourceH = Math.ceil(bounds[3] + padding) - y;
        item.x = x; item.y = y; item.points = item.points.map(p => [p[0] - x, p[1] - y]);
    }
    gesture = null; changed(); updateLayers();
}
overlay.addEventListener('pointerup', endGesture); overlay.addEventListener('pointercancel', endGesture);

async function loadImage(src) {
    if (images.has(src)) return images.get(src);
    const image = new Image(); image.src = src; await image.decode();
    if (image.width > 4096 || image.height > 4096) throw new Error('Maximum image dimensions are 4096 × 4096.');
    const local = document.createElement('canvas'); local.width = image.width; local.height = image.height;
    const context = local.getContext('2d', {willReadFrequently: true}); context.drawImage(image, 0, 0);
    const result = {width: image.width, height: image.height, data: context.getImageData(0, 0, image.width, image.height).data};
    images.set(src, result); return result;
}
const dataURL = blob => new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(blob); });
async function importImage(blob, name = 'Pasted image', asAtlas = false) {
    if (blob.size > 16 * 1024 * 1024) throw new Error('Maximum image size is 16 MiB.');
    const src = await dataURL(blob), loaded = await loadImage(src);
    if (asAtlas && (loaded.width !== SIZE || loaded.height !== SIZE)) throw new Error('An atlas must be exactly 512 × 512. Use Add picture for other sizes.');
    remember();
    if (asAtlas) { state.base = src; state.layers = []; selected = null; invalidate(); updateLayers(); status('Atlas opened. The Picture / Legacy toggle compares the same pixels.'); }
    else {
        const panel = model.regions[selectedPanel] || {x: 0, y: 0, width: SIZE, height: SIZE};
        const placement = fit(loaded.width, loaded.height, panel.x, panel.y, panel.width, panel.height);
        addLayer(newLayer('image', {name, src, x: placement.x, y: placement.y, w: placement.width, h: placement.height, sourceW: loaded.width, sourceH: loaded.height}));
        setTool('select'); status('Picture added. Drag to move; drag its square handle to resize. Hold Shift to keep its ratio.');
    }
}
async function handleFile(file, atlasMode = false) { if (!file) return; try { await importImage(file, file.name, atlasMode); } catch (error) { status(error.message); } }
$('import-image').onclick = () => $('image-file').click(); $('import-atlas').onclick = () => $('atlas-file').click();
$('image-file').onchange = event => { handleFile(event.target.files[0]); event.target.value = ''; };
$('atlas-file').onchange = event => { handleFile(event.target.files[0], true); event.target.value = ''; };
document.addEventListener('paste', async event => {
    if (event.target.matches('input,textarea')) return;
    const item = [...event.clipboardData.items].find(item => item.type.startsWith('image/'));
    if (item) { event.preventDefault(); await handleFile(item.getAsFile()); }
    else status('Copy an actual image, or use Add picture to select a file.');
});
$('paste').onclick = async () => {
    try {
        const items = await navigator.clipboard.read();
        for (const item of items) { const type = item.types.find(type => type.startsWith('image/')); if (type) return handleFile(await item.getType(type)); }
        status('No image found in the clipboard. Copy an image and use Ctrl/Cmd+V.');
    } catch { status('Use Ctrl/Cmd+V to paste an image here, or Add picture. Your browser blocked direct clipboard access.'); }
};
const studio = document.querySelector('.editor');
studio.addEventListener('dragover', event => { event.preventDefault(); studio.classList.add('dragover'); });
studio.addEventListener('dragleave', event => { if (!studio.contains(event.relatedTarget)) studio.classList.remove('dragover'); });
studio.addEventListener('drop', event => { event.preventDefault(); studio.classList.remove('dragover'); handleFile(event.dataTransfer.files[0]); });
for (const button of document.querySelectorAll('[data-tool]')) button.onclick = () => setTool(button.dataset.tool);
$('brush-size').oninput = () => $('size-value').textContent = $('brush-size').value;
$('guides').onchange = drawOverlay;
function sizeCanvas() {
    const area = $('canvas-scroll'), factor = +$('zoom').value;
    const fitSize = Math.min(area.clientWidth, area.clientHeight);
    $('canvas-wrap').style.width = `${fitSize * factor}px`;
    $('canvas-wrap').style.margin = factor === 1 ? '0 auto' : '0';
    drawOverlay();
}
$('zoom').onchange = sizeCanvas;
new ResizeObserver(sizeCanvas).observe($('canvas-scroll'));
$('focus-panel').onclick = () => {
    const p = model.regions[selectedPanel]; if (!p) return;
    $('zoom').value = '4'; sizeCanvas();
    const area = $('canvas-scroll'), scale = $('canvas-wrap').getBoundingClientRect().width / SIZE;
    area.scrollLeft = Math.max(0, (p.x + p.width / 2) * scale - area.clientWidth / 2);
    area.scrollTop = Math.max(0, (p.y + p.height / 2) * scale - area.clientHeight / 2);
    status('Panel enlarged for painting. Use Zoom → Fit to return to the full atlas.');
};
function restore(next) { if (!next) return; state = next; selected = null; $('palette').value = state.palette; invalidate(); updateLayers(); updateHistory(); }
$('undo').onclick = () => restore(history.back(state)); $('redo').onclick = () => restore(history.forward(state));
$('delete').onclick = () => { if (!layer()) return; remember(); state.layers = state.layers.filter(item => item.id !== selected); selected = null; changed(); updateLayers(); };
$('duplicate').onclick = () => { const item = layer(); if (!item) return; remember(); addLayer({...clone(item), id: crypto.randomUUID(), name: item.name + ' copy', x: item.x + 8, y: item.y + 8}); };
for (const [id, delta] of [['layer-up', 1], ['layer-down', -1]]) $(id).onclick = () => {
    const index = state.layers.findIndex(item => item.id === selected), next = index + delta;
    if (index < 0 || next < 0 || next >= state.layers.length) return;
    remember(); [state.layers[index], state.layers[next]] = [state.layers[next], state.layers[index]]; changed(); updateLayers();
};
for (const field of ['x','y','w','h','angle','opacity']) $('layer-' + field).onchange = event => {
    const item = layer(); if (!item || !Number.isFinite(+event.target.value)) return; remember();
    let value = +event.target.value;
    if (field === 'w' || field === 'h') value = clamp(value, 1, 4096);
    if (field === 'opacity') value = clamp(value, 0, 100) / 100;
    item[field] = value; changed(); updateInspector(); drawOverlay();
};
for (const [id, field] of [['flip-x','flipX'],['flip-y','flipY']]) $(id).onclick = () => { const item = layer(); if (!item) return; remember(); item[field] = !item[field]; changed(); };
function fitLayer(panel) {
    const item = layer(); if (!item) return; remember();
    const placement = fit(item.sourceW, item.sourceH, panel.x, panel.y, panel.width, panel.height);
    Object.assign(item, {x: placement.x, y: placement.y, w: placement.width, h: placement.height, angle: 0}); changed(); updateInspector(); drawOverlay();
}
$('fit-atlas').onclick = () => fitLayer({x:0,y:0,width:SIZE,height:SIZE});
$('fit-panel').onclick = () => { if (selectedPanel >= 0) fitLayer(model.regions[selectedPanel]); };
$('add-text').onclick = () => { $('text-dialog').showModal(); $('text-value').select(); };
$('cancel-text').onclick = () => $('text-dialog').close();
$('text-form').onsubmit = event => {
    event.preventDefault(); const text = $('text-value').value.trim(); if (!text) return;
    remember(); const p = model.regions[selectedPanel] || {x: 96, y: 200, width: 320, height: 64};
    addLayer(newLayer('text', {name: text, text, x: p.x, y: p.y, w: p.width, h: Math.min(64, p.height), sourceW: 320, sourceH: 64}));
    $('text-dialog').close(); setTool('select');
};
function download(blob, filename) {
    const url = URL.createObjectURL(blob), anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 2000);
}
$('export').onclick = () => {
    if (dirty) compose(); const name = safeName('my_livery_' + new Date().toISOString().replace(/\D/g, '').slice(0,14));
    atlas.toBlob(blob => download(blob, name + '.png'), 'image/png'); status('Exported 512 × 512 PNG. In the mod, open it with Import atlas, then Save.');
};
$('save-project').onclick = () => { download(new Blob([JSON.stringify(state)], {type:'application/json'}), 'openwheel-livery.json'); $('save-state').textContent = 'Project saved · local only'; };
$('open-project').onclick = () => $('project-file').click();
$('project-file').onchange = async event => {
    const file = event.target.files[0]; if (!file) return;
    try {
        if (file.size > 64 * 1024 * 1024) throw new Error('Project is too large.');
        const next = JSON.parse(await file.text());
        if (next.version !== 1 || !Array.isArray(next.layers) || next.layers.length > 200 || !Number.isInteger(next.palette) || !model.palettes[next.palette]) throw new Error('Invalid livery project.');
        for (const item of next.layers) {
            if (!['image','stroke','rect','ellipse','line','text'].includes(item.type) || !['x','y','w','h','angle','opacity','sourceW','sourceH','size'].every(key => Number.isFinite(item[key])) || item.w <= 0 || item.h <= 0 || item.w > 4096 || item.h > 4096 || item.sourceW <= 0 || item.sourceH <= 0) throw new Error('Invalid layer data.');
            if (item.type === 'stroke' && (!Array.isArray(item.points) || item.points.length > 100000 || !item.points.every(p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite)))) throw new Error('Invalid brush stroke.');
            if (item.src && !item.src.startsWith('data:image/')) throw new Error('Project images must be embedded.');
        }
        if (next.base && !next.base.startsWith('data:image/')) throw new Error('Invalid atlas image.');
        for (const src of [next.base, ...next.layers.map(item => item.src)].filter(Boolean)) await loadImage(src);
        if (next.base && (images.get(next.base).width !== 512 || images.get(next.base).height !== 512)) throw new Error('Invalid base atlas dimensions.');
        remember(); restore(next); status('Project opened. All layers are editable.');
    } catch (error) { status('Could not open project: ' + error.message); }
    event.target.value = '';
};
$('help').onclick = () => $('help-dialog').showModal();
document.addEventListener('keydown', event => {
    if (event.target.matches('input,textarea,select') || document.querySelector('dialog[open]')) return;
    const cmd = event.ctrlKey || event.metaKey;
    if (cmd && event.key.toLowerCase() === 'z') { event.preventDefault(); (event.shiftKey ? $('redo') : $('undo')).click(); return; }
    if (cmd && event.key.toLowerCase() === 'y') { event.preventDefault(); $('redo').click(); return; }
    if (cmd && event.key.toLowerCase() === 's') { event.preventDefault(); $('save-project').click(); return; }
    if (cmd) return;
    if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); $('delete').click(); return; }
    const tools = {v:'select',b:'brush',e:'eraser',l:'line',r:'rect',o:'ellipse',i:'picker',p:'panel'};
    if (tools[event.key.toLowerCase()]) setTool(tools[event.key.toLowerCase()]);
    if (event.key === 'Escape') { selected = null; setTool('select'); updateLayers(); }
    const item = layer();
    if (item && event.key.startsWith('Arrow')) {
        event.preventDefault(); remember(); const amount = event.shiftKey ? 10 : 1;
        if (event.key === 'ArrowLeft') item.x -= amount; if (event.key === 'ArrowRight') item.x += amount;
        if (event.key === 'ArrowUp') item.y -= amount; if (event.key === 'ArrowDown') item.y += amount;
        changed(); updateInspector(); drawOverlay();
    }
});

// Minecraft's entity shader multiplies the sampled texture by a flat face color.
// Keep data RGB values unchanged: no tone mapping, sRGB conversion, or smoothing.
function updateMeshColors() {
    if (!geometry || !lastPixels) return;
    const mode = $('mode').value;
    const colors = geometry.getAttribute('faceColor').array;
    for (let f = 0; f < model.faceCount; f++) {
        const color = mode === 'picture' && model.paintable[f] ? 0xffffffff
            : mode === 'legacy' ? legacyFaceColor(model, f, lastLegacyPixels, state.palette) : model.palettes[state.palette].colors[f];
        for (let v = 0; v < 4; v++) {
            const offset = f * 12 + v * 3;
            colors[offset] = (color >>> 16 & 255) / 255; colors[offset + 1] = (color >>> 8 & 255) / 255; colors[offset + 2] = (color & 255) / 255;
        }
    }
    geometry.getAttribute('faceColor').needsUpdate = true;
    material.uniforms.picture.value = mode === 'picture';
    previewDirty = true;
}
function setupRenderer() {
    renderer = new THREE.WebGLRenderer({antialias: true, alpha: true, preserveDrawingBuffer: true});
    renderer.setPixelRatio(Math.min(devicePixelRatio, 2)); renderer.setClearColor(0, 0);
    renderer.toneMapping = THREE.NoToneMapping;
    $('viewport').append(renderer.domElement);
    scene = new THREE.Scene(); camera = new THREE.PerspectiveCamera(35, 1, .01, 100);
    controls = new OrbitControls(camera, renderer.domElement); controls.target.set(0, .5, 0);
    controls.minDistance = 3; controls.maxDistance = 18; controls.maxPolarAngle = Math.PI * .95;
    controls.addEventListener('change', () => previewDirty = true);
    geometry = new THREE.BufferGeometry(); geometry.setAttribute('position', new THREE.BufferAttribute(model.positions, 3));
    geometry.setAttribute('uv', new THREE.BufferAttribute(model.uvs, 2));
    const normals = new Float32Array(model.faceCount * 12);
    for (let f = 0; f < model.faceCount; f++) for (let v = 0; v < 4; v++) normals.set(model.normals.subarray(f * 3, f * 3 + 3), f * 12 + v * 3);
    geometry.setAttribute('normal', new THREE.BufferAttribute(normals, 3));
    geometry.setAttribute('faceColor', new THREE.BufferAttribute(new Float32Array(model.faceCount * 12), 3));
    indices = new Uint32Array(model.faceCount * 6);
    for (let f = 0; f < model.faceCount; f++) indices.set([0,1,2,2,3,0].map(v => f * 4 + v), f * 6);
    geometry.setIndex(new THREE.BufferAttribute(indices, 1));
    texture = new THREE.CanvasTexture(atlas); texture.flipY = false;
    texture.magFilter = texture.minFilter = THREE.NearestFilter; texture.generateMipmaps = false;
    texture.colorSpace = THREE.NoColorSpace;
    material = new THREE.ShaderMaterial({side: THREE.DoubleSide,
        uniforms: {atlas: {value: texture}, picture: {value: true}, lighting: {value: true}},
        vertexShader: `attribute vec3 faceColor;
            uniform bool lighting;
            varying vec2 texCoord; varying vec3 tintFront; varying vec3 tintBack;
            void main() {
                texCoord = uv;
                vec3 light0 = normalize(vec3(0.2, 1.0, -0.7));
                vec3 light1 = normalize(vec3(-0.2, 1.0, 0.7));
                vec2 light = vec2(dot(light0, normal), dot(light1, normal));
                float front = min(1.0, (max(0.0, light.x) + max(0.0, light.y)) * 0.6 + 0.4);
                float back = min(1.0, (max(0.0, -light.x) + max(0.0, -light.y)) * 0.6 + 0.4);
                tintFront = faceColor * (lighting ? front : 1.0);
                tintBack = faceColor * (lighting ? back : 1.0);
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }`,
        fragmentShader: `uniform sampler2D atlas; uniform bool picture;
            varying vec2 texCoord; varying vec3 tintFront; varying vec3 tintBack;
            void main() {
                vec4 pixel = picture ? texture2D(atlas, texCoord) : vec4(1.0);
                if (pixel.a < 0.1) discard;
                gl_FragColor = vec4(pixel.rgb * (gl_FrontFacing ? tintFront : tintBack), 1.0);
            }`});
    mesh = new THREE.Mesh(geometry, material); scene.add(mesh);
    const grid = new THREE.GridHelper(14, 28, 0x34434c, 0x25323b); grid.position.y = -.012;
    grid.material.transparent = true; grid.material.opacity = .45; scene.add(grid);
    const resize = () => {
        const width = $('viewport').clientWidth, height = $('viewport').clientHeight;
        renderer.setSize(width, height); camera.aspect = width / height; camera.updateProjectionMatrix(); previewDirty = true;
    };
    new ResizeObserver(resize).observe($('viewport')); resize();
    const raycaster = new THREE.Raycaster(); let down;
    renderer.domElement.addEventListener('pointerdown', event => down = {x:event.clientX, y:event.clientY});
    renderer.domElement.addEventListener('pointerup', event => {
        if (!down || Math.hypot(event.clientX-down.x, event.clientY-down.y) > 5) return;
        const rect = renderer.domElement.getBoundingClientRect();
        raycaster.setFromCamera(new THREE.Vector2((event.clientX-rect.left)/rect.width*2-1, -(event.clientY-rect.top)/rect.height*2+1), camera);
        const hit = raycaster.intersectObject(mesh)[0];
        if (!hit) return;
        const face = Math.floor(hit.faceIndex / 2);
        setPanel(model.panels[face]);
        if (selectedPanel < 0) status('That part is not paintable in the mod. Choose a body panel.');
        else status('Panel selected. Add or paste a picture to fit it here.');
    });
    setCamera('iso'); updateMeshColors();
    $('lighting').onchange = () => { material.uniforms.lighting.value = $('lighting').checked; previewDirty = true; };
    $('wireframe').onchange = () => { material.wireframe = $('wireframe').checked; previewDirty = true; };
    $('mode').onchange = updateMeshColors;
    $('run-proof').onclick = () => {
        const proof = verifyGpu();
        $('gpu-result').textContent = `GPU: ${proof.channelsChecked.toLocaleString()} RGBA channels checked; ${proof.mismatches} mismatches.`;
        status(proof.mismatches ? 'GPU pixel proof found a mismatch. See Renderer verification.' : 'GPU pixel proof passed: every atlas pixel matches, including orientation and raw RGB.');
    };
    $('screenshot').onclick = () => {
        renderer.render(scene, camera); renderer.domElement.toBlob(blob => download(blob, 'openwheel-livery-view.png'));
    };
}
function setCamera(view) {
    if (!camera) return;
    const positions = {iso:[7,5,9],left:[10,1,0],right:[-10,1,0],top:[0,11,.001],front:[0,1,11]};
    camera.position.set(...positions[view]); controls.target.set(0,.5,0); controls.update();
    document.querySelectorAll('[data-view]').forEach(button => button.classList.toggle('active', button.dataset.view === view));
    previewDirty = true;
}
for (const button of document.querySelectorAll('[data-view]')) button.onclick = () => setCamera(button.dataset.view);
model.palettes.forEach((palette, index) => { const option = document.createElement('option'); option.value = index; option.textContent = palette.name; $('palette').append(option); });
$('palette').onchange = () => { remember(); state.palette = +$('palette').value; invalidate(); status('Palette updated. Pasted images and drawings are preserved.'); };
function verifyModel() {
    let checked = 0;
    const assert = (condition, message) => { if (!condition) throw new Error(message); checked++; };
    assert(model.positions.length === model.faceCount * 12, 'Position buffer length');
    assert(model.uvs.length === model.faceCount * 8, 'UV buffer length');
    for (let f = 0; f < model.faceCount; f++) {
        assert(model.samples[f*2] < SIZE && model.samples[f*2+1] < SIZE, 'Legacy sample bounds');
        if (!model.paintable[f]) assert([...model.uvs.subarray(f*8,f*8+8)].every(v => v === .5/SIZE), 'White texel UV');
        else assert([...model.uvs.subarray(f*8,f*8+8)].every(v => Number.isFinite(v) && v > 0 && v < 1), 'Paintable UV range');
    }
    for (const [source, background, expected] of model.compositeReference) assert(composite(source,background) === (expected>>>0), 'Java alpha reference');
    for (const vector of model.fitReference) assert(JSON.stringify(Object.values(fit(...vector.slice(0,6)))) === JSON.stringify(vector.slice(6)), 'Java fit reference');
    $('verification-count').textContent = `${checked.toLocaleString()} checks passed`;
    $('verification-details').textContent = `Java model ${model.modelSha256.slice(0,16)} · Renderer ${model.rendererSha256.slice(0,16)} · ${model.faceCount.toLocaleString()} faces · ${model.regions.length} panels · Java alpha / fit reference vectors passed`;
    $('model-stats').textContent = `${model.faceCount.toLocaleString()} faces / ${model.regions.length} paintable panels`;
    return checked;
}
let verifyCount = 0;
try { verifyCount = verifyModel(); compose(); setupRenderer(); }
catch (error) { $('renderer-error').hidden = false; $('renderer-error').textContent = '3D preview could not start: ' + error.message + '. Use a browser with WebGL enabled. The editor still exports PNGs.'; console.error(error); }
updateLayers(); updateHistory();
new ResizeObserver(drawOverlay).observe(overlay);
function frame() {
    if (dirty) { compose(); updateMeshColors(); drawOverlay(); }
    if (previewDirty && renderer && scene) { renderer.render(scene,camera); previewDirty = false; }
    requestAnimationFrame(frame);
}
frame();
function verifyGpu() {
    if (dirty) compose();
    const target = new THREE.WebGLRenderTarget(SIZE, SIZE, {minFilter: THREE.NearestFilter, magFilter: THREE.NearestFilter, depthBuffer: false});
    const proofGeometry = new THREE.PlaneGeometry(2, 2);
    proofGeometry.setAttribute('uv', new THREE.Float32BufferAttribute([0,0,1,0,0,1,1,1],2));
    proofGeometry.setAttribute('faceColor', new THREE.Float32BufferAttribute(new Array(12).fill(1),3));
    const proofMaterial = material.clone();
    proofMaterial.uniforms.atlas.value = texture; proofMaterial.uniforms.lighting.value = false;
    proofMaterial.uniforms.picture.value = true; proofMaterial.wireframe = false;
    const proof = new THREE.Scene(); proof.add(new THREE.Mesh(proofGeometry,proofMaterial));
    const proofCamera = new THREE.OrthographicCamera(-1,1,1,-1,.1,10); proofCamera.position.z = 1;
    const prior = renderer.getRenderTarget(); renderer.setRenderTarget(target); renderer.render(proof,proofCamera);
    const readback = new Uint8Array(SIZE*SIZE*4); renderer.readRenderTargetPixels(target,0,0,SIZE,SIZE,readback);
    renderer.setRenderTarget(prior);
    let mismatches = 0;
    for (let y=0;y<SIZE;y++) for(let x=0;x<SIZE;x++) for(let c=0;c<4;c++) {
        if (readback[((SIZE-1-y)*SIZE+x)*4+c] !== lastPixels[(y*SIZE+x)*4+c]) mismatches++;
    }
    target.dispose(); proofGeometry.dispose(); proofMaterial.dispose(); previewDirty = true;
    return {channelsChecked: SIZE*SIZE*4, mismatches};
}
// Read-only diagnostics make the actual offline file testable without a separate test renderer.
window.liveryWorkbench = {
    ready: true, verificationCount: verifyCount, model,
    get state() { return clone(state); }, get selectedPanel() { return selectedPanel; },
    get pixels() { if (dirty) compose(); return [...lastPixels]; },
    get geometry() { return geometry; }, get renderer() { return renderer; },
    get camera() { return camera; }, get mesh() { return mesh; },
    get faceColors() { return geometry?.getAttribute('faceColor').array; },
    importImage, setPanel, compose, verifyGpu,
    get legacyPixels() { if (dirty) compose(); return [...lastLegacyPixels]; },
    render() { if (dirty) compose(); updateMeshColors(); renderer?.render(scene,camera); }
};
