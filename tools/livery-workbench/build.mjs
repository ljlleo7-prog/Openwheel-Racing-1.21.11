import {build} from 'esbuild';
import {readFile, writeFile, mkdir, copyFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const model = JSON.parse(await readFile(path.join(root, 'build/livery/workbench-model.json'), 'utf8'));
if (!model.sourceHashes) throw new Error('Refresh Java model with ./gradlew exportLiveryWorkbenchModel');
for (const [file, expected] of Object.entries(model.sourceHashes)) {
    const actual = createHash('sha256').update(await readFile(path.join(root, file))).digest('hex');
    if (actual !== expected) throw new Error(`Java export is stale (${file}). Run ./gradlew exportLiveryWorkbenchModel again.`);
}
const bundle = await build({entryPoints: [path.join(here, 'app.js')], bundle: true, minify: true,
    format: 'iife', target: 'es2022', write: false, legalComments: 'inline'});
const license = await readFile(path.join(here, 'node_modules/three/LICENSE'), 'utf8');
const css = await readFile(path.join(here, 'styles.css'), 'utf8');
const html = (await readFile(path.join(here, 'index.template.html'), 'utf8')).replace('__STYLE__', () => css)
    .replace('</head>', () => `<!-- Three.js license:\n${license}\n-->\n</head>`)
    .replace('__MODEL__', () => JSON.stringify(model).replace(/</g, '\u003c'))
    .replace('__SCRIPT__', () => bundle.outputFiles[0].text.replace(/<\/script/gi, '<\\/script'));
const dist = path.join(here, 'dist');
await mkdir(dist, {recursive: true});
await writeFile(path.join(dist, 'index.html'), html);
await writeFile(path.join(here, 'index.html'), html);
await copyFile(path.join(dist, 'index.html'), path.join(root, 'build/livery/livery-workbench.html'));
console.log(`Built offline HTML (${(Buffer.byteLength(html) / 1024 / 1024).toFixed(1)} MiB): build/livery/livery-workbench.html`);
