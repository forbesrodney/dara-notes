#!/usr/bin/env python3
"""Draws the desktop and iOS app icons from the app's artwork, art/foam_icon.svg.

The artwork is filled circles (<circle cx cy r fill>), which this reads and draws
anti-aliased at each size. It writes:
  desktopApp/icons/foam.icns (macOS, via iconutil), foam.ico (Windows), foam.png (Linux),
  desktopApp/src/main/resources/foam-icon.png (the window and Dock icon), and the iOS icon
  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/foam-1024.png (opaque, as iOS requires;
  iOS rounds the corners itself).
It also draws the window background of the macOS .dmg, an arrow from the app to the
Applications folder: desktopApp/packaging/dmg-background.tiff (1x and 2x, via tiffutil).
Uses only the Python standard library.
"""
import os, re, shutil, struct, subprocess, sys, tempfile, zlib
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "art/foam_icon.svg")
OUT = os.path.join(ROOT, "desktopApp/icons")
SVG = "{http://www.w3.org/2000/svg}"


def circles():
    """The circles as (cx, cy, r, rgb), with positions as fractions of the canvas."""
    root = ET.parse(SRC).getroot()
    size = float(root.get("viewBox").split()[2])
    out = []
    for c in root.iter(SVG + "circle"):
        color = c.get("fill").lstrip("#")
        if len(color) == 3:
            color = "".join(ch * 2 for ch in color)
        rgb = tuple(int(color[i:i + 2], 16) for i in (0, 2, 4))
        out.append((float(c.get("cx")) / size, float(c.get("cy")) / size, float(c.get("r")) / size, rgb))
    if not out:
        sys.exit(f"No <circle> elements in {SRC}; this script only draws circles.")
    return out


def render(px, margin, background=None):
    """RGBA bytes of the icon at px x px, with [margin] (fraction) of empty space on each side,
    over an opaque [background] (r, g, b) if given."""
    shapes = circles()
    scale = px * (1 - 2 * margin)
    offset = px * margin
    rows = []
    for y in range(px):
        row = bytearray([0])  # PNG filter: none
        for x in range(px):
            if background:
                r, g, b = background
                a = 1.0
            else:
                r = g = b = a = 0.0
            for cx, cy, cr, (cr_, cg, cb) in shapes:
                d = ((x + 0.5 - offset - cx * scale) ** 2 + (y + 0.5 - offset - cy * scale) ** 2) ** 0.5
                cov = min(1.0, max(0.0, cr * scale - d + 0.5))
                if cov:
                    # "over" compositing, premultiplied
                    r = cr_ * cov + r * (1 - cov)
                    g = cg * cov + g * (1 - cov)
                    b = cb * cov + b * (1 - cov)
                    a = cov + a * (1 - cov)
            if a:
                row += bytes((round(r / a), round(g / a), round(b / a), round(a * 255)))
            else:
                row += b"\0\0\0\0"
        rows.append(bytes(row))
    return b"".join(rows)


def png(px, margin, background=None):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", px, px, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(render(px, margin, background), 9)) + chunk(b"IEND", b""))


# The .dmg window, in points; scripts/macos-dmg.sh places the icons to match.
DMG_SIZE = (640, 400)
DMG_ICON_Y = 190                 # icon centers, from the top
DMG_ARROW = (262, 388)           # arrow tail and tip, between the two icons
DMG_COLORS = ((246, 246, 246), (160, 160, 160))  # background, arrow


def png_rgb(w, h, rows):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))


def dmg_background(scale):
    """The .dmg background at [scale] (1 or 2), as PNG bytes."""
    w, h = DMG_SIZE[0] * scale, DMG_SIZE[1] * scale
    tail, tip = DMG_ARROW[0] * scale, DMG_ARROW[1] * scale
    cy = DMG_ICON_Y * scale
    half_shaft, half_head, head_len = 7 * scale, 26 * scale, 42 * scale
    base = tip - head_len
    # The head's two slanted edges, as unit normals pointing out of the triangle.
    n = (half_head, head_len)
    length = (n[0] ** 2 + n[1] ** 2) ** 0.5
    nx, ny = n[0] / length, n[1] / length
    bg, fg = DMG_COLORS

    def coverage(x, y):
        # Shaft: a rectangle (overlapping the head a little so they join cleanly).
        shaft = min(x - tail, base + 2 - x, half_shaft - abs(y - cy))
        # Head: inside the base line and both slanted edges.
        head = min(x - base, -((x - tip) * nx + abs(y - cy) * ny))
        return min(1.0, max(0.0, max(shaft, head) + 0.5))

    plain = bytes([0]) + bytes(bg) * w
    rows = []
    for y in range(h):
        if abs(y + 0.5 - cy) > half_head + 1:
            rows.append(plain)
            continue
        row = bytearray([0])
        for x in range(w):
            c = coverage(x + 0.5, y + 0.5) if tail - 1 <= x <= tip + 1 else 0.0
            row += bytes(round(f * c + b * (1 - c)) for f, b in zip(fg, bg))
        rows.append(bytes(row))
    return png_rgb(w, h, rows)


def write_dmg_background():
    if not shutil.which("tiffutil"):
        print("tiffutil not found (macOS only): skipped the .dmg background", file=sys.stderr)
        return
    out = os.path.join(ROOT, "desktopApp/packaging")
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        files = []
        for scale, name in ((1, "bg.png"), (2, "bg@2x.png")):
            files.append(os.path.join(tmp, name))
            with open(files[-1], "wb") as f:
                f.write(dmg_background(scale))
        subprocess.run(["tiffutil", "-cathidpicheck", *files, "-out", os.path.join(out, "dmg-background.tiff")],
                       check=True, capture_output=True)
    print("Wrote desktopApp/packaging/dmg-background.tiff")


def main():
    os.makedirs(OUT, exist_ok=True)
    # macOS icons leave about a tenth of the canvas empty around the artwork.
    mac_margin, other_margin = 0.1, 0.03
    cache = {}

    def get(px, margin):
        if (px, margin) not in cache:
            cache[(px, margin)] = png(px, margin)
        return cache[(px, margin)]

    # macOS .icns from an .iconset folder.
    if shutil.which("iconutil"):
        with tempfile.TemporaryDirectory() as tmp:
            iconset = os.path.join(tmp, "foam.iconset")
            os.mkdir(iconset)
            for base in (16, 32, 128, 256, 512):
                for mult, suffix in ((1, ""), (2, "@2x")):
                    with open(os.path.join(iconset, f"icon_{base}x{base}{suffix}.png"), "wb") as f:
                        f.write(get(base * mult, mac_margin))
            subprocess.run(["iconutil", "-c", "icns", iconset, "-o", os.path.join(OUT, "foam.icns")], check=True)
    else:
        print("iconutil not found (macOS only): skipped foam.icns", file=sys.stderr)

    # Windows .ico holding PNG images.
    sizes = (16, 24, 32, 48, 64, 128, 256)
    images = [get(s, other_margin) for s in sizes]
    header = struct.pack("<HHH", 0, 1, len(sizes))
    offset = 6 + 16 * len(sizes)
    entries = b""
    for s, data in zip(sizes, images):
        entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    with open(os.path.join(OUT, "foam.ico"), "wb") as f:
        f.write(header + entries + b"".join(images))

    # Linux, and the window icon.
    with open(os.path.join(OUT, "foam.png"), "wb") as f:
        f.write(get(512, other_margin))
    res = os.path.join(ROOT, "desktopApp/src/main/resources")
    os.makedirs(res, exist_ok=True)
    with open(os.path.join(res, "foam-icon.png"), "wb") as f:
        f.write(get(512, mac_margin))
    ios = os.path.join(ROOT, "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
    os.makedirs(ios, exist_ok=True)
    with open(os.path.join(ios, "foam-1024.png"), "wb") as f:
        f.write(png(1024, 0.12, background=(255, 255, 255)))
    print("Wrote desktopApp/icons/foam.icns, foam.ico, foam.png, desktopApp/src/main/resources/foam-icon.png")
    print("  and iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/foam-1024.png")
    write_dmg_background()


if __name__ == "__main__":
    main()
