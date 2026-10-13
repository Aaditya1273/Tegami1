# /// script
# dependencies = ["pillow", "numpy", "scipy"]
# ///
"""Cut the fox poses, the 3D icons and the launcher art out of the art sheets in the repo root, into
app/src/main/res/drawable-nodpi as WebP. Re-run after replacing a sheet: `uv run scripts/cut_art.py`."""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable-nodpi"

# Poses: (sheet, cols, rows) -> {(row, col): name}. Each pose is tied to one real state in the app.
POSES = {
    ("Nine Poses of a Charming Fox Messenger.png", 3, 3): {
        (0, 1): "fox_deliver", (0, 2): "fox_content", (1, 0): "fox_read", (1, 1): "fox_verified",
        (2, 0): "fox_cheer", (2, 1): "fox_review", (2, 2): "fox_wave",
    },
    ("Six Poses of a Charming Fox Mascot.png", 3, 2): {(0, 1): "fox_guardian", (0, 2): "fox_official"},
}

# Rows to drop from the top of a cell where the pose above touches this one (the waving paw meets its feet).
TOP_TRIM = {"fox_wave": 5}

# Icons: a point inside each icon (full-size sheet pixels) -> name.
ICONS = {
    "Glossy Red and Gold UI Icon Asset Sheet.png": {
        (140, 170): "art_envelope", (400, 150): "art_envelope_open", (1430, 160): "art_letter",
        (90, 370): "art_wallet", (1310, 370): "art_shield_crest", (1450, 370): "art_lock",
        (100, 550): "art_statement", (260, 550): "art_scroll", (560, 555): "art_fingerprint",
        (1180, 550): "art_magnifier", (1300, 555): "art_check", (1440, 540): "art_shield_check",
        (265, 865): "art_chat", (510, 850): "art_calendar", (630, 850): "art_gear", (754, 850): "art_home", (1260, 855): "art_globe",
        (1400, 860): "art_chain", (1175, 970): "art_eye", (980, 850): "art_send", (1490, 690): "art_charm",
    },
    "Luxe Crimson UI Icon Collection.png": {
        (790, 460): "art_coins", (410, 700): "art_seal", (540, 910): "art_clock",
    },
}


def save(img: Image.Image, name: str, max_side: int):
    img.thumbnail((max_side, max_side), Image.LANCZOS)
    img.save(OUT / f"{name}.webp", "WEBP", quality=88, method=6)
    print(f"{name}: {img.size}")


def trim(img: Image.Image, pad: int = 6) -> Image.Image:
    a = np.array(img)[..., 3]
    ys, xs = np.where(a > 24)
    return img.crop((max(xs.min() - pad, 0), max(ys.min() - pad, 0), xs.max() + pad, ys.max() + pad))


def largest(img: Image.Image) -> Image.Image:
    """Keep the biggest shape in a cell, so ear tips or tails reaching in from a neighbouring pose are dropped."""
    arr = np.array(img)
    # Small pieces on the cell's top edge are the feet of the pose above.
    raw, m = ndimage.label(arr[..., 3] > 24)
    sizes = np.bincount(raw.ravel())
    for i in set(raw[0]) - {0}:
        if sizes[i] < 0.05 * sizes[1:].max():
            arr[..., 3][raw == i] = 0
    lab, n = ndimage.label(ndimage.binary_closing(arr[..., 3] > 60, iterations=4))
    if n > 1:
        keep = ndimage.binary_dilation(lab == np.bincount(lab[lab > 0]).argmax(), iterations=6)
        arr[..., 3] = np.where(keep, arr[..., 3], 0)
    return Image.fromarray(arr)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for (sheet, cols, rows), names in POSES.items():
        im = Image.open(ROOT / sheet).convert("RGBA")
        cw, ch = im.width / cols, im.height / rows
        for (r, c), name in names.items():
            cell = im.crop((int(c * cw), int(r * ch), int((c + 1) * cw), int((r + 1) * ch)))
            cell = trim(largest(cell), 0)
            cell = cell.crop((0, TOP_TRIM.get(name, 0), cell.width, cell.height))
            save(trim(largest(cell)), name, 600)

    for sheet, points in ICONS.items():
        im = Image.open(ROOT / sheet).convert("RGBA")
        arr = np.array(im)
        lab, _ = ndimage.label(ndimage.binary_closing(arr[..., 3] > 90, iterations=3))
        for (x, y), name in points.items():
            # Nearest labelled pixel to the point, so a point on a gap still finds its icon.
            ids = lab[max(y - 40, 0):y + 40, max(x - 40, 0):x + 40]
            ids = ids[ids > 0]
            if ids.size == 0:
                raise SystemExit(f"no icon near {x},{y} for {name}")
            target = np.bincount(ids).argmax()
            sl = ndimage.find_objects((lab == target).astype(int))[0]
            box = (sl[1].start, sl[0].start, sl[1].stop, sl[0].stop)
            # Keep only this icon's pixels (drops neighbouring petals and sparks).
            mask = Image.fromarray(((lab == target) * 255).astype(np.uint8)).crop(box)
            mask = Image.fromarray(ndimage.binary_dilation(np.array(mask) > 0, iterations=4).astype(np.uint8) * 255)
            piece = im.crop(box)
            alpha = np.minimum(np.array(piece)[..., 3], np.array(mask))
            piece.putalpha(Image.fromarray(alpha))
            save(trim(piece, 2), name, 224)

    # Launcher art: the square inside the icon's frame, centred on a 108dp canvas at 80dp. The launcher mask shows
    # the middle 72dp, so ears and seal stay in view and the frame's rounded corners stay outside it.
    icon = Image.open(ROOT / "icon.png").convert("RGBA").crop((112, 100, 1142, 1130))
    canvas = Image.new("RGBA", (432, 432), (74, 10, 14, 255))
    art = icon.resize((320, 320), Image.LANCZOS)
    canvas.alpha_composite(art, (56, 56))
    canvas.save(OUT / "ic_launcher_art.webp", "WEBP", quality=90, method=6)
    print("ic_launcher_art: (432, 432)")


if __name__ == "__main__":
    main()
