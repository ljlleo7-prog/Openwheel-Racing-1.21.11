#!/usr/bin/env python3
"""Prepare picture-livery display cars in a CLOSED development world.

Run ./gradlew lapTimingTest first to export the actual runtime atlas layout.
Requires Pillow. Existing entities are preserved; changed region files are backed up.
"""
import argparse
import fcntl
import gzip
import io
import json
import math
from pathlib import Path
import shutil
import struct
import time
import uuid
import zlib
from PIL import Image, ImageDraw, ImageFont


class Nbt:
    """Small typed NBT reader/writer so existing numeric tag types are preserved."""
    formats = {1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}

    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.stream.read(struct.calcsize('>' + fmt)))[0]

    def string(self):
        return self.stream.read(self.number('H')).decode('utf8')

    def value(self, kind):
        if kind in self.formats:
            return self.number(self.formats[kind])
        if kind == 7:
            return self.stream.read(self.number('i'))
        if kind == 8:
            return self.string()
        if kind == 9:
            child = self.number('b')
            return child, [self.value(child) for _ in range(self.number('i'))]
        if kind == 10:
            result = {}
            while (child := self.number('b')) != 0:
                name = self.string()
                result[name] = child, self.value(child)
            return result
        if kind in (11, 12):
            return [self.number('i' if kind == 11 else 'q') for _ in range(self.number('i'))]
        raise ValueError(kind)

    def root(self):
        kind = self.number('b')
        name = self.string()
        return name, self.value(kind)

    @staticmethod
    def text(value):
        raw = value.encode('utf8')
        return struct.pack('>H', len(raw)) + raw

    @classmethod
    def encode(cls, kind, value):
        if kind in cls.formats:
            return struct.pack('>' + cls.formats[kind], value)
        if kind == 7:
            return struct.pack('>i', len(value)) + value
        if kind == 8:
            return cls.text(value)
        if kind == 9:
            child, entries = value
            return struct.pack('>bi', child, len(entries)) + b''.join(cls.encode(child, x) for x in entries)
        if kind == 10:
            return b''.join(bytes([t]) + cls.text(k) + cls.encode(t, v) for k, (t, v) in value.items()) + b'\0'
        if kind in (11, 12):
            return struct.pack('>i', len(value)) + b''.join(struct.pack('>i' if kind == 11 else '>q', x) for x in value)
        raise ValueError(kind)


class Region:
    def __init__(self, path):
        self.path = path
        self.raw = path.read_bytes() if path.exists() else bytes(8192)
        self.changed = {}

    def read(self, cx, cz):
        index = cx % 32 + cz % 32 * 32
        offset = int.from_bytes(self.raw[index * 4:index * 4 + 3], 'big') * 4096
        if not offset:
            return '', None
        length = int.from_bytes(self.raw[offset:offset + 4], 'big')
        compression = self.raw[offset + 4]
        payload = self.raw[offset + 5:offset + 4 + length]
        if compression == 2:
            payload = zlib.decompress(payload)
        elif compression == 1:
            payload = gzip.decompress(payload)
        elif compression != 3:
            raise ValueError(f'Unsupported region compression: {compression}')
        return Nbt(payload).root()

    def put(self, cx, cz, name, data):
        payload = zlib.compress(b'\x0a' + Nbt.text(name) + Nbt.encode(10, data))
        self.changed[cx % 32 + cz % 32 * 32] = struct.pack('>i', len(payload) + 1) + b'\x02' + payload

    def save(self, backup):
        # Repack unchanged chunk payloads byte-for-byte; only offsets may move.
        header = bytearray(self.raw[:8192])
        body = bytearray()
        for index in range(1024):
            offset = int.from_bytes(self.raw[index * 4:index * 4 + 3], 'big') * 4096
            if index in self.changed:
                chunk = self.changed[index]
                header[4096 + index * 4:4100 + index * 4] = struct.pack('>I', int(time.time()))
            elif offset:
                length = int.from_bytes(self.raw[offset:offset + 4], 'big') + 4
                chunk = self.raw[offset:offset + length]
            else:
                continue
            sector = 2 + len(body) // 4096
            count = math.ceil(len(chunk) / 4096)
            if count > 255:
                raise ValueError('External chunks are not supported')
            header[index * 4:index * 4 + 4] = sector.to_bytes(3, 'big') + bytes([count])
            body.extend(chunk + bytes(count * 4096 - len(chunk)))
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if self.path.exists():
            backup.mkdir(parents=True, exist_ok=True)
            shutil.copy2(self.path, backup / self.path.name)
        temporary = self.path.with_suffix('.mca.tmp')
        temporary.write_bytes(header + body)
        temporary.replace(self.path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('world', type=Path)
    parser.add_argument('--layout', type=Path, default=Path('build/livery/runtime-faces.json'))
    args = parser.parse_args()
    world = args.world
    with (world / 'session.lock').open('rb+') as lock:
        fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        level = Nbt(gzip.decompress((world / 'level.dat').read_bytes())).root()[1]['Data'][1]
        player = level['Player'][1]
        if player['Dimension'][1] != 'minecraft:overworld':
            raise ValueError('Save the player in the overworld first')
        px, py, pz = player['Pos'][1][1]
        faces = json.loads(args.layout.read_text())
        panels = sorted({(f['liveryRegionX'], f['liveryRegionY'], f['liveryRegionWidth'], f['liveryRegionHeight'])
                         for f in faces if f['liveryRegionWidth'] > 0})
        textures = world.parent.parent / 'openwheelracing/liveries'
        textures.mkdir(parents=True, exist_ok=True)
        output = Path('build/livery/samples')
        output.mkdir(parents=True, exist_ok=True)
        font = ImageFont.load_default()
        styles = [('cyan', '#082438', '#19e6dc'), ('sunset', '#521948', '#ff944d'),
                  ('checker', '#151a25', '#edf4fc'), ('map', '#124f37', '#c9ff72')]
        ids = []
        for number, (name, base, accent) in enumerate(styles, 1):
            source = Image.new('RGB', (480, 160), base)
            draw = ImageDraw.Draw(source)
            if name == 'map':
                source = Image.open(world / 'arnis_world_map.png').convert('RGB').resize((480, 160))
                draw = ImageDraw.Draw(source)
            elif name == 'checker':
                for y in range(0, 160, 20):
                    for x in range(0, 480, 20):
                        if (x // 20 + y // 20) % 2 == 0:
                            draw.rectangle((x, y, x + 19, y + 19), fill=accent)
            else:
                for x in range(-160, 600, 85):
                    draw.polygon([(x, 160), (x + 42, 160), (x + 160, 0), (x + 118, 0)], fill=accent)
            draw.rounded_rectangle((145, 40, 335, 120), 12, fill=base, outline=accent, width=4)
            label = Image.new('RGBA', (100, 24))
            ImageDraw.Draw(label).text((2, 5), f'OWR {number:02d}', font=font, fill=accent)
            source.paste(label.resize((160, 40)), (160, 60), label.resize((160, 40)))
            source.save(output / f'{name}_source.png')
            atlas = Image.new('RGB', (512, 512), base)
            for x, y, w, h in panels:
                if name == 'map':
                    image = source.resize((w, h))
                else:
                    image = source.copy()
                    image.thumbnail((w, h), Image.Resampling.LANCZOS)
                atlas.paste(image, (x + (w - image.width) // 2, y + (h - image.height) // 2))
            atlas.putpixel((0, 0), (255, 255, 255))
            identifier = f'picture_v1_demo_{name}'
            atlas.save(textures / f'{identifier}.png')
            atlas.save(output / f'{identifier}.png')
            ids.append((identifier, f'Picture {number}: {name.title()}'))
        # Identical PNG under an old-format id visibly demonstrates legacy face sampling.
        legacy_id = 'livery_demo_legacy_checker'
        shutil.copy2(textures / 'picture_v1_demo_checker.png', textures / f'{legacy_id}.png')
        ids.append((legacy_id, 'Legacy: face sampled checker'))
        terrain = {}
        entities = {}
        backup = world / 'openwheelracing' / ('livery-sample-backup-' + time.strftime('%Y%m%d-%H%M%S'))

        def ground(x, z):
            key = x // 512, z // 512
            region = terrain.setdefault(key, Region(world / 'region' / f'r.{key[0]}.{key[1]}.mca'))
            _, chunk = region.read(x // 16, z // 16)
            sections = {s['Y'][1]: s for s in chunk['sections'][1][1]}

            def block(y):
                section = sections.get(y // 16)
                if not section or 'block_states' not in section:
                    return 'minecraft:air'
                states = section['block_states'][1]
                palette = states['palette'][1][1]
                index = 0
                if 'data' in states:
                    bits = max(4, (len(palette) - 1).bit_length())
                    per = 64 // bits
                    position = y % 16 * 256 + z % 16 * 16 + x % 16
                    index = (states['data'][1][position // per] >> (position % per * bits)) & ((1 << bits) - 1)
                return palette[index]['Name'][1]

            air = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
            for y in range(math.floor(py) + 5, math.floor(py) - 6, -1):
                if block(y) not in air and block(y + 1) in air and block(y + 2) in air:
                    return y + 1.0
            raise ValueError(f'No clear display ground at {x}, {z}')

        locations = []
        # Re-running is idempotent through deterministic UUIDs.
        for i, (identifier, label) in enumerate(ids):
            x, z = math.floor(px) - 12 + i * 6, math.floor(pz) + 12
            y = ground(x, z)
            key = x // 512, z // 512
            region = entities.setdefault(key, Region(world / 'entities' / f'r.{key[0]}.{key[1]}.mca'))
            cx, cz = x // 16, z // 16
            index = cx % 32 + cz % 32 * 32
            if index in region.changed:
                blob = region.changed[index]
                name, chunk = Nbt(zlib.decompress(blob[5:])).root()
            else:
                name, chunk = region.read(cx, cz)
            if chunk is None:
                chunk = {'DataVersion': (3, 4671), 'Position': (11, [cx, cz]), 'Entities': (9, (10, []))}
            uid = uuid.uuid5(uuid.NAMESPACE_URL, 'openwheelracing/livery-sample/' + identifier)
            ints = list(struct.unpack('>iiii', uid.bytes))
            existing = chunk['Entities'][1][1]
            if not any(e.get('UUID') == (11, ints) for e in existing):
                existing.append({'id': (8, 'openwheelracing:prototype_car'), 'UUID': (11, ints),
                                 'Pos': (9, (6, [x + .5, y, z + .5])), 'Motion': (9, (6, [0., 0., 0.])),
                                 'Rotation': (9, (5, [180., 0.])), 'OnGround': (1, 1),
                                 'CustomName': (8, label), 'CustomNameVisible': (1, 1),
                                 'PersistenceRequired': (1, 1), 'TyresFitted': (1, 1),
                                 'LiveryTexture': (8, identifier), 'Livery': (3, 0)})
                region.put(cx, cz, name, chunk)
            locations.append({'name': label, 'texture': identifier, 'position': [x + .5, y, z + .5]})
        for region in entities.values():
            if region.changed:
                region.save(backup)
        manifest = {'world': world.name, 'cars': locations, 'backup': str(backup),
                    'sources': str(output), 'note': 'Existing cars and terrain were preserved.'}
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
