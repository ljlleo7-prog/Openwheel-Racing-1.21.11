# Offline livery workbench

Build and open the self-contained HTML:

```sh
# From the repository root:
./gradlew exportLiveryWorkbenchModel
npm --prefix tools/livery-workbench ci
npm --prefix tools/livery-workbench run build
open build/livery/livery-workbench.html
```

The generated file includes Three.js, the model, CSS, and all application code. It works directly from disk, without a server or CDN. Copy that one HTML file to share the tool. Use a browser with WebGL enabled. No images or projects are uploaded.

The build also writes the same finished page to `tools/livery-workbench/index.html` and `tools/livery-workbench/dist/index.html`; either opens directly. The build source is `index.template.html`, which contains placeholders and is not an application entry point.

The left half is an orbitable 3D view; the right half is the paint editor. Select a panel on the car or atlas, then paste an actual image with Ctrl/Cmd+V, drop a file, or use **Add picture**. The image fits the panel. **Focus panel** zooms and scrolls the editor to small panels for painting. **Move** drags layers; the square handle resizes them (Shift keeps the aspect ratio). Position, size, rotation, opacity, mirroring, ordering, and visibility have controls below the canvas. Brush, eraser, line, rectangle, ellipse, text, color picker, undo/redo, and zoom are available. Keyboard shortcuts are shown in tooltips. The direct **Paste image** button depends on browser clipboard permissions; keyboard paste and file selection remain available.

**Save project** downloads editable JSON with embedded images; **Open project** restores it. **Export PNG** creates an opaque 512×512 atlas with a `picture_v1_` filename and the reserved white pixel at (0, 0). In the mod's livery editor, select **Import atlas**, choose this PNG, and **Save**. Use **Import PNG** in the mod for placing a single image on one panel instead. No world editing is required.

## Renderer fidelity and verification

`exportLiveryWorkbenchModel` runs the production Java `ColoredObjModel` and `OpenwheelCarRenderer` helpers. It exports transformed vertex positions, original normals, half-pixel UV coordinates, paintability, panel rectangles, preset face colors, and the legacy first-triangle sample coordinates. Float32 buffers preserve the Java values; the browser does not implement the atlas packer independently. Quads use Minecraft's `0,1,2,2,3,0` indices, including degenerate triangle quads. Both faces render, as in `entityCutoutNoCull`.

The picture shader uses nearest filtering, no mipmaps, top-left image coordinates, raw texture/face RGB multiplication, and no additional color-space conversion or tone mapping. Its optional directional lighting uses Minecraft 1.21.11's `light.glsl` equation and default overworld light directions, including separate front/back lighting for the no-cull entity pipeline. The browser is a material/UV proof, not a complete Minecraft scene renderer: environment lightmap, fog, overlays, world orientation, resource-pack/shader changes, camera projection, and post-processing can differ. Turn lighting off for a raw color proof.

**Legacy** uses the exact exported first-triangle sample pixel and exported preset fallback color. For an imported transparent atlas, it uses the original alpha to select fallback colors; **Picture** normalizes transparency against white, as `ClientLiveryTextures` does. **Original preset** disables the PNG entirely.

The page checks model buffers, UV bounds, white texels, and Java-generated aspect-fit/alpha-compositing reference vectors at startup. Model and renderer source hashes identify the Java export. The build checks hashes of the model, material, atlas packer, renderer, compositing, and palette sources to reject stale exports. **Pixel proof** performs a GPU readback of the current atlas using the same material shader.

Run checks:

```sh
./gradlew build exportLiveryWorkbenchModel
npm --prefix tools/livery-workbench run build
npm --prefix tools/livery-workbench test
npm --prefix tools/livery-workbench run test:browser
```

Browser tests use Playwright and an installed Google Chrome in a temporary browser profile. They load the final HTML via `file://`, compare every position/UV with the Java buffers and every legacy face color with its Java sample, check GPU texture readback against all 1,048,576 RGBA channels, exercise clipboard image paste and pointer-based tools/transforms, round-trip projects and PNG files, and check desktop/mobile layouts. Screenshots and test export files go under `build/livery/`.

Dependencies are pinned in `package-lock.json`; bundled Three.js retains its MIT license notice. Sources are under this directory; generated `dist/` and `node_modules/` are ignored.
