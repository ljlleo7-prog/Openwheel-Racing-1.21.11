import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--enable-webgl','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const page=await browser.newPage({viewport:{width:1600,height:1100},acceptDownloads:true});
const errors=[],requests=[];
page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))requests.push(r.url());});
async function point(x,y){const b=await page.locator('#overlay').boundingBox();return{x:b.x+x/512*b.width,y:b.y+y/512*b.height};}
async function drag(x,y,tx,ty){const a=await point(x,y),b=await point(tx,ty);await page.mouse.move(a.x,a.y);await page.mouse.down();await page.mouse.move(b.x,b.y,{steps:8});await page.mouse.up();await page.waitForTimeout(100);}
async function field(id,value){await page.fill('#'+id,''+value);await page.locator('#'+id).dispatchEvent('change');}
try{
 // All public HTML entry points must include actual CSS, not build placeholders.
 for(const entry of ['tools/livery-workbench/index.html','tools/livery-workbench/dist/index.html','build/livery/livery-workbench.html']){
  await page.goto(new URL('../../'+entry,import.meta.url).href);
  const style=await page.evaluate(()=>({background:getComputedStyle(document.body).backgroundColor,
   layout:getComputedStyle(document.querySelector('main')).display,
   accent:getComputedStyle(document.documentElement).getPropertyValue('--accent').trim(),
   placeholder:document.querySelector('style')?.textContent.includes('__STYLE__')}));
  assert.equal(style.placeholder,false,entry+' has compiled CSS');
  assert.equal(style.background,'rgb(11, 14, 18)',entry+' applies the background');
  assert.equal(style.layout,'grid',entry+' applies the split layout');
  assert.equal(style.accent,'#a8ed80',entry+' applies theme variables');
 }
 await page.goto(new URL('../../build/livery/livery-workbench.html',import.meta.url).href);
 await page.waitForFunction(()=>window.liveryWorkbench?.ready);await page.waitForTimeout(300);
 assert.equal(await page.locator('#renderer-error').isVisible(),false,'WebGL started');
 const atlasFit = await page.evaluate(()=>{const a=document.querySelector('#overlay').getBoundingClientRect(),b=document.querySelector('#canvas-scroll').getBoundingClientRect();return Math.abs(a.width-a.height)<1&&a.height<=b.height&&a.width<=b.width;});
 assert.ok(atlasFit,'Fit must show the whole square atlas');
 const checks=await page.evaluate(()=>{const a=window.liveryWorkbench,m=a.model;return{count:a.verificationCount,faces:m.faceCount,
  positionsEqual:a.geometry.getAttribute('position').array.every((v,i)=>v===m.positions[i]),uvsEqual:a.geometry.getAttribute('uv').array.every((v,i)=>v===m.uvs[i])};});
 assert.ok(checks.count>100000&&checks.positionsEqual&&checks.uvsEqual);
 for(const tool of ['brush','rect','ellipse','line']){await page.locator(`[data-tool="${tool}"]`).click();await drag(200,200,280,260);}
 assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers.length),4);
 await page.click('#undo');assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers.length),3);
 await page.click('#redo');assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers.length),4);
 await page.click('#add-text');await page.fill('#text-value','TEST 27');await page.locator('#text-form button.primary').click();
 assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers.at(-1).text),'TEST 27');
 await page.evaluate(async()=>{const c=document.createElement('canvas');c.width=c.height=32;const x=c.getContext('2d');
  for(const[color,px,py]of[['#ff0000',0,0],['#00ff00',16,0],['#0000ff',0,16],['#ffffff',16,16]]){x.fillStyle=color;x.fillRect(px,py,16,16);}
  const blob=await new Promise(r=>c.toBlob(r)),transfer=new DataTransfer();transfer.items.add(new File([blob],'clipboard.png',{type:'image/png'}));
  document.body.dispatchEvent(new ClipboardEvent('paste',{clipboardData:transfer,bubbles:true,cancelable:true}));});
 await page.waitForFunction(()=>window.liveryWorkbench.state.layers.at(-1)?.type==='image');
 for(const[id,value]of[['layer-x',100],['layer-y',100],['layer-w',64],['layer-h',64]])await field(id,value);
 const quadrants=await page.evaluate(()=>{const a=window.liveryWorkbench;a.render();const p=a.pixels;return[[110,110],[150,110],[110,150],[150,150]].map(([x,y])=>p.slice((y*512+x)*4,(y*512+x)*4+3));});
 assert.deepEqual(quadrants,[[255,0,0],[0,255,0],[0,0,255],[255,255,255]]);
 const gpu = await page.evaluate(()=>window.liveryWorkbench.verifyGpu());
 assert.equal(gpu.mismatches,0,'GPU texture orientation and RGB must match every atlas pixel');
 await page.locator('[data-tool="select"]').click();await drag(120,120,140,140);
 assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers.at(-1).x),120);
 await drag(184,184,204,204);assert.ok(await page.evaluate(()=>window.liveryWorkbench.state.layers.at(-1).w>=83));
 await field('layer-angle',25);await field('layer-opacity',50);await page.click('#flip-x');
 assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers.at(-1).flipX),true);
 await page.click('#duplicate');await page.click('#delete');
 await page.selectOption('#mode','legacy');
 const legacy=await page.evaluate(()=>{const a=window.liveryWorkbench;a.render();const p=a.legacyPixels,m=a.model,c=a.faceColors;let mismatch=0;
  for(let f=0;f<m.faceCount;f++){const i=(m.samples[f*2+1]*512+m.samples[f*2])*4,rgb=m.paintable[f]&&p[i+3]?[p[i],p[i+1],p[i+2]]:[m.palettes[0].colors[f]>>>16&255,m.palettes[0].colors[f]>>>8&255,m.palettes[0].colors[f]&255];
   for(let ch=0;ch<3;ch++)if(Math.abs(c[f*12+ch]-rgb[ch]/255)>1e-6)mismatch++;}return mismatch;});assert.equal(legacy,0);
 await page.selectOption('#mode','picture');
 const waitProject=page.waitForEvent('download');await page.click('#save-project');const project=await waitProject;
 const projectPath=path.join(root,'build/livery/browser-project.json');await project.saveAs(projectPath);const saved=JSON.parse(await readFile(projectPath,'utf8'));
 await page.setInputFiles('#project-file',projectPath);await page.waitForFunction(()=>document.querySelector('#status').textContent.includes('Project opened'));
 assert.deepEqual(await page.evaluate(()=>window.liveryWorkbench.state),saved);
 const waitPng=page.waitForEvent('download');await page.click('#export');const png=await waitPng;assert.ok(png.suggestedFilename().startsWith('picture_v1_'));
 const pngPath=path.join(root,'build/livery/browser-export.png');await png.saveAs(pngPath);const header=await readFile(pngPath);assert.equal(header.readUInt32BE(16),512);assert.equal(header.readUInt32BE(20),512);
 await page.setInputFiles('#atlas-file',pngPath);await page.waitForFunction(()=>window.liveryWorkbench.state.layers.length===0);
 // Check car-to-atlas picking against projected centers of the actual mesh.
 const candidates=await page.evaluate(()=>{
  const a=window.liveryWorkbench,m=a.model,V=a.mesh.position.constructor;a.render();const points=[];
  for(let f=0;f<m.faceCount&&points.length<60;f+=31)if(m.paintable[f]){
   const v=new V();for(let i=0;i<3;i++){v.x+=m.positions[f*12+i*3]/3;v.y+=m.positions[f*12+i*3+1]/3;v.z+=m.positions[f*12+i*3+2]/3;}
   v.project(a.camera);if(Math.abs(v.x)<.8&&Math.abs(v.y)<.7)points.push([v.x,v.y]);
  }return points;
 });
 let picked=false;const viewport=await page.locator('#viewport>canvas').boundingBox();
 for(const[x,y]of candidates){await page.mouse.click(viewport.x+(x+1)*viewport.width/2,viewport.y+(1-y)*viewport.height/2);if(await page.evaluate(()=>window.liveryWorkbench.selectedPanel>=0)){picked=true;break;}}
 assert.ok(picked,'Clicking a body panel selects its atlas region');
 await page.locator('[data-tool="panel"]').click();const region=await page.evaluate(()=>window.liveryWorkbench.model.regions.find(p=>p.width>20&&p.height>20));
 const pt=await point(region.x+region.width/2,region.y+region.height/2);await page.mouse.click(pt.x,pt.y);assert.ok(await page.evaluate(()=>window.liveryWorkbench.selectedPanel>=0));
 await page.setInputFiles('#image-file',pngPath);await page.waitForFunction(()=>window.liveryWorkbench.state.layers.length===1);await page.click('#fit-panel');
 assert.ok(await page.evaluate(()=>{const a=window.liveryWorkbench,p=a.model.regions[a.selectedPanel],l=a.state.layers[0];return l.x>=p.x&&l.y>=p.y&&l.x+l.w<=p.x+p.width&&l.y+l.h<=p.y+p.height;}));
 await page.locator('[data-tool="eraser"]').click();await drag(region.x+1,region.y+1,region.x+5,region.y+5);
 assert.equal(await page.evaluate(()=>window.liveryWorkbench.state.layers[0].type),'image');
 // A transparent legacy atlas must use palette fallback, not white.
 await page.evaluate(async()=>{const c=document.createElement('canvas');c.width=c.height=512;const blob=await new Promise(r=>c.toBlob(r));await window.liveryWorkbench.importImage(blob,'transparent.png',true);});
 await page.selectOption('#mode','legacy');
 const transparentFallback=await page.evaluate(()=>{const a=window.liveryWorkbench;a.render();const m=a.model,c=a.faceColors;return m.palettes[0].colors.every((color,f)=>[16,8,0].every((shift,ch)=>Math.abs(c[f*12+ch]-((color>>>shift)&255)/255)<1e-6));});
 assert.ok(transparentFallback,'Legacy transparent pixels use Java preset fallback');
 await page.selectOption('#mode','picture');
 await page.setInputFiles('#project-file',projectPath);await page.waitForFunction(()=>document.querySelector('#status').textContent.includes('Project opened'));
 await mkdir(path.join(root,'build/livery/screenshots'),{recursive:true});
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(150);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No horizontal mobile overflow');
 await page.screenshot({path:path.join(root,'build/livery/screenshots/workbench-mobile.png'),fullPage:true});
 await page.setViewportSize({width:1600,height:1100});await page.waitForTimeout(150);
 await page.screenshot({path:path.join(root,'build/livery/screenshots/workbench-desktop.png'),fullPage:true});
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 await page.click('#focus-panel');assert.equal(await page.locator('#zoom').inputValue(),'4');
 const result={passed:true,...checks,legacyColorMismatches:legacy,gpu,carPanelPicking:picked,transparentLegacyFallback:transparentFallback,offlineNetworkRequests:requests.length,browserErrors:errors.length};
 await writeFile(path.join(root,'build/livery/browser-verification.json'),JSON.stringify(result,null,2)+'\n');
 console.log(JSON.stringify(result,null,2));
}finally{await browser.close();}
