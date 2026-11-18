#!/usr/bin/env python3
"""Draws the app icons: a gold Celtic knot on navy (scripts/KnotIcon.java holds the design).

It writes:
  desktopApp/icons/app.icns (macOS, via iconutil), app.ico (Windows), app.png (Linux),
  desktopApp/src/main/resources/app-icon.png (the window and Dock icon), the iOS icon
  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png (opaque, as iOS requires;
  iOS rounds the corners itself), the Android adaptive icon (androidApp/src/main/res:
  mipmap-anydpi-v26/ic_launcher.xml and its layers), the artwork, art/dara_icon.svg, and the
  Google Play listing's icon and feature graphic, art/play/icon-512.png and feature-graphic.png.
It also draws the window background of the macOS .dmg, an arrow from the app to the
Applications folder: desktopApp/packaging/dmg-background.tiff (1x and 2x, via tiffutil).
Needs a JDK (java on the PATH) for KnotIcon.java; otherwise only the Python standard library.
"""
import os, shutil, struct, subprocess, sys, tempfile, zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "desktopApp/icons")


def knot(*jobs):
    """Runs KnotIcon.java with [jobs] (each a list of its arguments)."""
    args = [a for job in jobs for a in job]
    subprocess.run(["java", os.path.join(ROOT, "scripts/KnotIcon.java"), *args], check=True)


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
    mac_margin, other_margin = "0.1", "0.03"
    res = os.path.join(ROOT, "desktopApp/src/main/resources")
    ios = os.path.join(ROOT, "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
    os.makedirs(res, exist_ok=True)
    os.makedirs(ios, exist_ok=True)
    os.makedirs(os.path.join(ROOT, "art/play"), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        jobs, ico = [], []
        iconset = os.path.join(tmp, "app.iconset")
        os.mkdir(iconset)
        for base in (16, 32, 128, 256, 512):
            for mult, suffix in ((1, ""), (2, "@2x")):
                jobs.append(["png", str(base * mult), "tile", mac_margin, os.path.join(iconset, f"icon_{base}x{base}{suffix}.png")])
        for size in (16, 24, 32, 48, 64, 128, 256):
            ico.append((size, os.path.join(tmp, f"ico{size}.png")))
            jobs.append(["png", str(size), "tile", other_margin, ico[-1][1]])
        jobs += [
            ["png", "512", "tile", other_margin, os.path.join(OUT, "app.png")],
            ["png", "512", "tile", mac_margin, os.path.join(res, "app-icon.png")],
            ["png", "1024", "full", os.path.join(ios, "AppIcon-1024.png")],
            ["svg", os.path.join(ROOT, "art/dara_icon.svg")],
            ["android", os.path.join(ROOT, "androidApp/src/main/res")],
            # Google Play: opaque, full square (Play rounds the corners itself).
            ["png", "512", "full", os.path.join(ROOT, "art/play/icon-512.png")],
            ["feature", os.path.join(ROOT, "art/play/feature-graphic.png")],
        ]
        knot(*jobs)
        # macOS .icns from the .iconset folder.
        if shutil.which("iconutil"):
            subprocess.run(["iconutil", "-c", "icns", iconset, "-o", os.path.join(OUT, "app.icns")], check=True)
        else:
            print("iconutil not found (macOS only): skipped app.icns", file=sys.stderr)
        # Windows .ico holding PNG images.
        images = [open(path, "rb").read() for _, path in ico]
        header = struct.pack("<HHH", 0, 1, len(ico))
        offset = 6 + 16 * len(ico)
        entries = b""
        for (s, _), data in zip(ico, images):
            entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(data), offset)
            offset += len(data)
        with open(os.path.join(OUT, "app.ico"), "wb") as f:
            f.write(header + entries + b"".join(images))
    print("Wrote desktopApp/icons/app.icns, app.ico, app.png, desktopApp/src/main/resources/app-icon.png,")
    print("  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png, the Android adaptive icon")
    print("  art/dara_icon.svg, and art/play/icon-512.png and feature-graphic.png (Google Play)")
    write_dmg_background()


if __name__ == "__main__":
    main()
