#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Range:
    minimum: float | None = None
    maximum: float | None = None

    def contains(self, value: float) -> bool:
        if self.minimum is not None and value < self.minimum:
            return False
        if self.maximum is not None and value > self.maximum:
            return False
        return True


@dataclass(frozen=True)
class Rule:
    group: str
    x: Range | None
    y: Range | None
    z: Range | None
    abs_x_min: float | None = None
    wheel_y_band: bool = False
    material: str = "3,3,3"

    def matches(self, x: float, y: float, z: float, lowest_y: float) -> bool:
        if self.x is not None and not self.x.contains(x):
            return False
        if self.y is not None and not self.y.contains(y):
            return False
        if self.z is not None and not self.z.contains(z):
            return False
        if self.abs_x_min is not None and abs(x) <= self.abs_x_min:
            return False
        if self.wheel_y_band and not Range(lowest_y, lowest_y + 0.58).contains(y):
            return False
        return True


RULES = [
    Rule("Wheel_Front_Left", Range(-0.795, -0.500), None, Range(-1.842, -1.270), wheel_y_band=True, material="42,46,54"),
    Rule("Wheel_Front_Right", Range(0.500, 0.795), None, Range(-1.842, -1.271), wheel_y_band=True, material="42,46,54"),
    Rule("Wheel_Rear_Left", Range(-0.795, -0.410), None, Range(-4.780, -4.196), wheel_y_band=True, material="42,46,54"),
    Rule("Wheel_Rear_Right", Range(0.410, 0.794), None, Range(-4.780, -4.196), wheel_y_band=True, material="42,46,54"),
    Rule("Underfloor", Range(-0.806, 0.806), Range(maximum=-0.250), Range(-4.195, -1.902), material="34,38,48"),
    Rule("Diffuser", Range(-0.400, 0.400), Range(maximum=0.000), Range(maximum=-4.195), material="34,38,48"),
    Rule("Left-FW-Endplate", Range(maximum=-0.500), None, Range(minimum=-1.200), material="160,160,160"),
    Rule("Right-FW-Endplate", Range(minimum=0.500), None, Range(minimum=-1.200), material="160,160,160"),
]

CHASSIS_GROUP = "Chassis"
CHASSIS_MATERIAL = "3,3,3"
MATERIAL_MAP = {
    "Carbon_Fiber_-_Plain": "3,3,3",
    "Carbon_Fiber_-_Twill": "34,38,48",
    "Fabric_Black": "34,38,48",
    "FRONT_NOSE__0": "3,3,3",
    "Rubber_-_Weathered": "42,46,54",
    "Steel_-_Satin": "160,160,160",
    "Titanium_-_Polished": "191,186,179",
    "Paint_-_Metallic_Silver": "3,3,3",
    "Gold_-_Polished": "244,229,167",
    "Glass_Red": "196,53,39",
    "LED_-_SMD_5630_-_50lm_White": "237,23,27",
    "Paint_-_Enamel_Glossy_White": "255,255,255",
    "Glass_-_Heavy_Color": "42,46,54",
    "Cherry": "34,38,48",
}


def parse_vertex_index(token: str) -> int:
    return int(token.split("/", 1)[0]) - 1


def face_centroid(face_tokens: list[str], vertices: list[tuple[float, float, float]]) -> tuple[float, float, float]:
    points = [vertices[parse_vertex_index(token)] for token in face_tokens]
    count = len(points)
    return (
        sum(point[0] for point in points) / count,
        sum(point[1] for point in points) / count,
        sum(point[2] for point in points) / count,
    )


def classify(x: float, y: float, z: float, lowest_y: float) -> tuple[str, str]:
    for rule in RULES:
        if rule.matches(x, y, z, lowest_y):
            return rule.group, rule.material
    return CHASSIS_GROUP, CHASSIS_MATERIAL


def read_obj_faces(path: Path) -> tuple[list[tuple[float, float, float]], list[list[str]], list[str]]:
    vertices: list[tuple[float, float, float]] = []
    faces: list[list[str]] = []
    face_materials: list[str] = []
    material = CHASSIS_MATERIAL

    for raw_line in path.read_text(errors="ignore").splitlines():
        line = raw_line.strip()
        if line.startswith("v "):
            parts = line.split()
            vertices.append((float(parts[1]), float(parts[2]), float(parts[3])))
        elif line.startswith("usemtl "):
            material = line.split(" ", 1)[1].strip()
        elif line.startswith("f "):
            face_tokens = line.split()[1:]
            if len(face_tokens) in (3, 4):
                faces.append(face_tokens)
                face_materials.append(material)

    return vertices, faces, face_materials


def build_material_reference(path: Path | None) -> dict[tuple[int, int, int], list[tuple[float, float, float, str]]] | None:
    if path is None:
        return None
    vertices, faces, materials = read_obj_faces(path)
    buckets: dict[tuple[int, int, int], list[tuple[float, float, float, str]]] = defaultdict(list)
    for face_tokens, material in zip(faces, materials):
        x, y, z = face_centroid(face_tokens, vertices)
        buckets[bucket_key(x, y, z)].append((x, y, z, material))
    return buckets


def bucket_key(x: float, y: float, z: float) -> tuple[int, int, int]:
    size = 0.08
    return (int(x / size), int(y / size), int(z / size))


def nearest_reference_material(x: float, y: float, z: float, buckets: dict[tuple[int, int, int], list[tuple[float, float, float, str]]] | None, fallback: str) -> str:
    if buckets is None:
        return fallback
    bx, by, bz = bucket_key(x, y, z)
    best_material = fallback
    best_distance = float("inf")
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for rx, ry, rz, material in buckets.get((bx + dx, by + dy, bz + dz), []):
                    distance = (rx - x) ** 2 + (ry - y) ** 2 + (rz - z) ** 2
                    if distance < best_distance:
                        best_distance = distance
                        best_material = material
    return normalize_material(best_material)


def normalize_material(material: str) -> str:
    return MATERIAL_MAP.get(material, material)


def convert(source: Path, target: Path, material_reference: Path | None = None) -> Counter[str]:
    vertices, faces, source_materials = read_obj_faces(source)
    reference_buckets = build_material_reference(material_reference)

    lowest_y = min(vertex[1] for vertex in vertices)
    output: list[str] = [
        "# Grouped by scripts/group_quad_obj.py",
        "# Source: " + str(source),
        f"# Material reference: {material_reference}" if material_reference is not None else "# Material reference: none",
        f"# Wheel Y band: {lowest_y:.6f} to {lowest_y + 0.58:.6f}",
    ]
    output.extend(f"v {x:.6f} {y:.6f} {z:.6f}" for x, y, z in vertices)

    counts: Counter[str] = Counter()
    material_counts: Counter[str] = Counter()
    current_group: str | None = None
    current_material: str | None = None

    for face_tokens, source_material in zip(faces, source_materials):
        x, y, z = face_centroid(face_tokens, vertices)
        group, material = classify(x, y, z, lowest_y)
        if group not in {"Underfloor", "Diffuser"}:
            material = normalize_material(source_material)
            material = nearest_reference_material(x, y, z, reference_buckets, material)
        if group != current_group:
            output.append("g " + group)
            current_group = group
            current_material = None
        if material != current_material:
            output.append("usemtl " + material)
            current_material = material
        output.append("f " + " ".join(face_tokens))
        counts[group] += 1
        material_counts[material] += 1

    target.write_text("\n".join(output) + "\n")
    counts.update({"material:" + material: count for material, count in material_counts.items()})
    return counts


def main() -> None:
    parser = argparse.ArgumentParser(description="Group a quad OBJ by Openwheel Racing spatial part rules.")
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--material-reference", type=Path)
    args = parser.parse_args()

    counts = convert(args.source, args.target, args.material_reference)
    for group, count in sorted((key, value) for key, value in counts.items() if not key.startswith("material:")):
        print(f"{group}: {count}")
    print("materials:")
    for material, count in sorted((key.removeprefix("material:"), value) for key, value in counts.items() if key.startswith("material:")):
        print(f"  {material}: {count}")
    print(f"total faces: {sum(value for key, value in counts.items() if not key.startswith('material:'))}")
    print(f"wrote: {args.target}")


if __name__ == "__main__":
    main()
