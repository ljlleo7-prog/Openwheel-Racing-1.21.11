import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fit, composite, blendPixels, decodeModel, legacyFaceColor, safeName, History, panelAt} from './core.mjs';
const model = decodeModel(JSON.parse(readFileSync(new URL('../../build/livery/workbench-model.json', import.meta.url))));
test('aspect fit matches Java rounding and integer division', () => {
    for (const v of model.fitReference) assert.deepEqual(Object.values(fit(...v.slice(0,6))), v.slice(6));
    assert.throws(() => fit(0,1,0,0,2,2));
});
test('alpha blending matches every Java reference vector', () => {
    const rgba = v => [v>>>16&255,v>>>8&255,v&255,v>>>24];
    for (const [s,b,e] of model.compositeReference) {
        assert.equal(composite(s,b),e>>>0);
        const pixels=new Uint8ClampedArray(rgba(b));blendPixels(pixels,new Uint8ClampedArray(rgba(s)));
        assert.deepEqual([...pixels],rgba(e));
    }
});
test('binary buffers retain every face and white fallback UV', () => {
    assert.equal(model.positions.length,model.faceCount*12);assert.equal(model.normals.length,model.faceCount*3);
    assert.equal(model.uvs.length,model.faceCount*8);
    for(let f=0;f<model.faceCount;f++) {
        assert.ok([...model.positions.subarray(f*12,f*12+12)].every(Number.isFinite));
        if(!model.paintable[f]) assert.ok([...model.uvs.subarray(f*8,f*8+8)].every(v=>v===.5/512));
        else {assert.ok(model.panels[f]>=0&&model.panels[f]<model.regions.length);assert.ok(model.samples[f*2]<512&&model.samples[f*2+1]<512);}
    }
});
test('legacy uses Java sample coordinates and transparent fallback on every face', () => {
    const pixels=new Uint8ClampedArray(512*512*4);
    for(let f=0;f<model.faceCount;f++) {
        assert.equal(legacyFaceColor(model,f,pixels,0),model.palettes[0].colors[f]);
        if(model.paintable[f]) {
            const i=(model.samples[f*2+1]*512+model.samples[f*2])*4;
            pixels.set([18,52,86,255],i);assert.equal(legacyFaceColor(model,f,pixels,0),0xff123456);pixels.fill(0,i,i+4);
        }
    }
});
test('undo/redo restores layers and drops abandoned redo branches', () => {
    const h=new History(2),a={layers:[]},b={layers:[{x:5}]},c={layers:[{x:9}]};
    h.push(a);h.push(b);assert.deepEqual(h.back(c),b);assert.deepEqual(h.back(b),a);assert.deepEqual(h.forward(a),b);
    h.push(b);assert.equal(h.forward(c),null);
});
test('panel bounds and exported filenames match mod conventions', () => {
    assert.equal(panelAt([{x:8,y:9,width:10,height:10}],8,9),0);
    assert.equal(panelAt([{x:8,y:9,width:10,height:10}],18,19),-1);
    assert.equal(safeName('My Racing Livery!'),'picture_v1_my_racing_livery_');
});
