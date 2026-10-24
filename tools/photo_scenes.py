"""Draws the wallpapers of themes/iceland from photos: each one a Scenes-style picture (see scenes_wallpapers.py)
whose layout and colours come from a photo, not a tracing of it.

    python3 tools/photo_scenes.py PHOTOS OUT                 # every scene in tools/photo_scenes.txt
    python3 tools/photo_scenes.py PHOTOS OUT 01-lake-sunset  # just these

PHOTOS is a folder with the photos tools/photo_scenes.txt names; they are the theme author's own and are not in
this repo, so only they can redraw the set. OUT gets one 1920x1080 JPEG per scene, under 300 KB as
themes/README.md asks. Needs numpy, Pillow and vtracer (pip install numpy pillow vtracer) and Google Chrome or
Chromium, which renders the SVG; set CHROME to its path if it is not found.

What is drawn: a 16:9 band of the photo (portrait photos work) is read at 480x270. The sky becomes one smooth
gradient with the photo's warm and cool sides; the sun, if there is one, a disc with a glow (warm and with a band
along the horizon at sunset, pale by day); the land, stacked bands with smooth ridge lines, far to near, as the
Scenes hills are drawn; what stands on the horizon, a smoothed outline, with a stylised spruce for each narrow
peak; a lake, the sky and far land mirrored, or open sea, the photo's own water with soft surf. The colours are
graded warm in the light and cool in the shadow, so a photo's greys read as coloured light. As in the Scenes,
the lower third darkens towards black for the dock and the grid, and faint grain keeps the gradients from
banding on an 8-bit panel. The random seeds are fixed; Chrome's rendering is the only thing that varies.

Each line of tools/photo_scenes.txt is a scene: NAME PHOTO BAND_TOP [key=value ...]. BAND_TOP is where the band
starts, as a fraction of the photo's height. The keys and their defaults are in DEFAULTS:

  horizon    the horizon as a fraction of the band's height: the land starts there, and anything darker than
             darkland (L*, 0-100) that stands on it and rises above it (trees, a ridge, a headland) joins it
  land=fill  no flat horizon: the land is what is darker than the sky and reaches the bottom of the frame, and
             everything below it (slopes, a mountain)
  water      where a lake starts, as a fraction of the band's height; sea=1 for open sea instead of a mirror,
             foam the luminance (0-255) above which the water is surf
  (none of these: the land is what is darker than the sky, found by Otsu's threshold, as for a lake)
  sun        auto (the brightest sky spot, if it is bright and stands out), none, or x,y as fractions
  trees=0    no spruces: what stands on the horizon keeps its own outline (a cairn, a sea stack)
  landk      how many land bands; depth how much the bands follow height rather than colour
  warm, cool, chroma  the colour grade; glow the strength of the sun; lit the percentile of a band's
             brightness its colour is taken from (65: where the light falls on it)
"""
import base64
import io
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

import numpy as np
import vtracer
from PIL import Image, ImageFilter, ImageOps

W, H = 1920, 1080          # the wallpaper
SW, SH = 480, 270          # the photo is read at this size
TW, TH = 960, 540          # the SVG's viewBox, and the size shapes are traced at
SETTINGS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "photo_scenes.txt")
DEFAULTS = dict(horizon=0.0, land="auto", water=0.0, sea=0, foam=200.0, sun="auto", trees=1, darkland=32.0,
                landk=3, depth=60.0, warm=0.8, cool=1.0, chroma=1.3, glow=1.0, lit=65.0, shade=0.7, smooth=4.0,
                minarea=0.004, treew=50, suncontrast=30.0, sunlevel=235.0)
TEXT_KEYS = ("land", "sun")


# ---- colour ------------------------------------------------------------------------------------

M = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
WHITE = np.array([0.95047, 1.0, 1.08883])


def to_lab(rgb):
    """sRGB 0-255 to CIE L*a*b*."""
    c = rgb / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    xyz = lin @ M.T / WHITE
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)


def to_rgb(lab):
    fy = (lab[..., 0] + 16) / 116
    f = np.stack([fy + lab[..., 1] / 500, fy, fy - lab[..., 2] / 200], -1)
    lin = (np.where(f > 0.2069, f ** 3, (f - 16 / 116) / 7.787) * WHITE) @ np.linalg.inv(M).T
    c = np.where(lin <= 0.0031308, lin * 12.92, 1.055 * np.clip(lin, 0, None) ** (1 / 2.4) - 0.055)
    return np.clip(c * 255, 0, 255)


def grade(rgb, p):
    """Warm light, cool shadow: greys take a peach hue when bright and a blue-violet one when dark, and every colour
    gains chroma. A photo's greys then read as coloured light, as the eye remembers the scene."""
    lab = to_lab(rgb)
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    grey = np.clip(1 - np.hypot(a, b) / 28, 0, 1)          # 1 for a neutral grey, 0 from chroma 28 up
    t = np.clip((L - 35) / 45, 0, 1)[..., None]           # 0 in the darks, 1 in the lights
    tint = np.array([10, -26]) * p["cool"] * (1 - t) + np.array([16, 30]) * p["warm"] * t
    lab[..., 1] = a * p["chroma"] + tint[..., 0] * grey * 0.8
    lab[..., 2] = b * p["chroma"] + tint[..., 1] * grey * 0.8
    return to_rgb(lab)


def lum(rgb):
    return rgb @ np.array([0.299, 0.587, 0.114])


def lit_colour(g, m, p):
    """A region's colour where the light falls on it: the mean of its pixels around the lit percentile of
    brightness, not its median, which shadows pull towards mud."""
    lv = lum(g[m])
    lo, hi = np.percentile(lv, [p["lit"] - 15, p["lit"] + 15])
    return g[m][(lv >= lo) & (lv <= hi)].mean(axis=0)


def hexc(c):
    return "#%02x%02x%02x" % tuple(int(round(v)) for v in np.clip(c, 0, 255))


# ---- masks and shapes --------------------------------------------------------------------------

def blur(a, sy, sx=None):
    """Separable Gaussian with edge padding; sx defaults to sy (Pillow does not blur float images)."""
    if a.ndim == 3:
        return np.stack([blur(a[..., i], sy, sx) for i in range(a.shape[2])], -1)
    a = a.astype(np.float32)
    for axis, s in ((0, sy), (1, sy if sx is None else sx)):
        r = max(1, int(3 * s))
        k = np.exp(-0.5 * (np.arange(-r, r + 1) / s) ** 2)
        k /= k.sum()
        pad = [(r, r) if i == axis else (0, 0) for i in range(2)]
        a = np.apply_along_axis(lambda v: np.convolve(v, k, "valid"), axis, np.pad(a, pad, mode="edge"))
    return a


def smooth_mask(m, sy, sx, minarea):
    """Blur and threshold again: the shape keeps its mass and loses its detail. A mask under minarea is dropped."""
    m = blur(m.astype(np.float32), sy, sx) > 0.5
    m = np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.ModeFilter(5))) > 127
    return m if m.mean() >= minarea else np.zeros_like(m)


def upmask(m):
    """A mask at trace size with smooth edges: upscale it lightly blurred, then threshold."""
    f = Image.fromarray((blur(m.astype(np.float32), 1.2) * 255).clip(0, 255).astype(np.uint8))
    return np.asarray(f.resize((TW, TH), Image.BICUBIC)) > 127


def trace(m, tmp):
    """vtracer's spline paths for a mask at trace size, without fills. The mask is padded first: a spline fit
    rounds the corners where a shape meets the frame, and the padding moves those corners off the picture."""
    pad = 80
    png, svg = os.path.join(tmp, "mask.png"), os.path.join(tmp, "mask.svg")
    Image.fromarray(np.where(np.pad(m, pad, mode="edge"), 0, 255).astype(np.uint8)).convert("RGB").save(png)
    vtracer.convert_image_to_svg_py(png, svg, colormode="binary", mode="spline", filter_speckle=40,
                                    corner_threshold=120, length_threshold=8.0, max_iterations=10,
                                    splice_threshold=60, path_precision=1)
    with open(svg) as f:
        paths = re.findall(r'<path d="([^"]+)"[^>]*?(transform="[^"]+")?\s*/>', f.read())
    return f'<g transform="translate({-pad} {-pad})">' + "".join(f'<path d="{d}" {t}/>' for d, t in paths) + "</g>"


def components(m):
    """The connected regions of a mask, 4-neighbour, by flood fill."""
    lab = np.zeros(m.shape, np.int32)
    n = 0
    for y0, x0 in zip(*np.nonzero(m)):
        if lab[y0, x0]:
            continue
        n += 1
        stack = [(y0, x0)]
        lab[y0, x0] = n
        while stack:
            y, x = stack.pop()
            for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= yy < m.shape[0] and 0 <= xx < m.shape[1] and m[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n
                    stack.append((yy, xx))
    return [lab == i for i in range(1, n + 1)]


def otsu(L):
    """The luminance that best splits a picture into dark and light."""
    hist = np.histogram(L, 64, (0, 255))[0].astype(float)
    best, thr = -1, 128
    for i in range(1, 64):
        w0, w1 = hist[:i].sum(), hist[i:].sum()
        if w0 and w1:
            v = w0 * w1 * ((hist[:i] * np.arange(i)).sum() / w0 - (hist[i:] * np.arange(i, 64)).sum() / w1) ** 2
            if v > best:
                best, thr = v, i * 4
    return thr


def kmeans(x, k, iters=12, seed=3):
    rnd = np.random.default_rng(seed)
    c = x[rnd.choice(len(x), k, replace=False)]
    for _ in range(iters):
        idx = np.argmin(((x[:, None] - c[None]) ** 2).sum(-1), axis=1)
        c = np.array([x[idx == i].mean(0) if np.any(idx == i) else c[i] for i in range(k)])
    return idx


def png_uri(rgb):
    buf = io.BytesIO()
    Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def outline_path(top, base):
    """A shape from a top line (one y per column, at trace size) down to y=base, a little past the frame's sides."""
    pts = " L".join(f"{x * TW / (len(top) - 1):.1f},{y:.1f}" for x, y in enumerate(top))
    return f"M-4,{top[0]:.1f} L{pts} L{TW + 4},{top[-1]:.1f} L{TW + 4},{base:.1f} L-4,{base:.1f} Z"


# ---- the land ----------------------------------------------------------------------------------

def silhouette(m, g, gid, tmp, defs, shade=1.0):
    """A land shape in one colour, a little lighter at its top edge, as haze makes it."""
    rows = np.nonzero(m.any(axis=1))[0]
    cut = rows[0] + max(2, (rows[-1] - rows[0]) // 5)
    top = np.median(g[m & (np.arange(SH)[:, None] < cut)], axis=0) * shade
    bottom = np.median(g[m], axis=0) * 0.8 * shade
    defs.append(f'<linearGradient id="{gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{hexc(top)}"/>'
                f'<stop offset="1" stop-color="{hexc(bottom)}"/></linearGradient>')
    return f'<g fill="url(#{gid})" filter="url(#edge)">{trace(upmask(m), tmp)}</g>'


def land_bands(land, g, p, defs):
    """The land as depth bands, as the Scenes hills are drawn: each band from its ridge line down to the bottom,
    far ones first. The bands are the photo's colour groups ordered by height; a ridge line is where its band
    starts in each column, smoothed along x. The farthest keeps its outline (a mountain's peak)."""
    rows = np.arange(SH)[:, None]
    k = p["landk"]
    feats = np.concatenate([to_lab(blur(g, 2.0))[land] * np.array([1.0, 0.5, 0.5]),
                            (rows / SH * p["depth"]).repeat(SW, 1)[land][:, None]], 1)
    idx = kmeans(feats, k)
    order = np.argsort([np.nonzero(land)[0][idx == i].mean() if np.any(idx == i) else 1e9 for i in range(k)])
    rank = np.full(land.shape, -1)
    rank[land] = np.argsort(order)[idx]
    rank = np.maximum.accumulate(rank, axis=0)            # once a column reaches a nearer band it stays in it
    out, prev = [], None
    for i in range(k):
        hit = rank >= i
        prof = np.where(hit.any(axis=0), hit.argmax(axis=0), SH).astype(float)
        if i == 0:
            prof = blur(prof[None, :], 0.01, 1.2)[0]
        else:
            # a band missing from some columns would drop to the bottom there; a wide median keeps the ridge whole
            prof = np.array([np.median(prof[max(0, x - 30):x + 31]) for x in range(SW)])
            prof = blur(prof[None, :], 0.01, p["smooth"] * 4)[0]
        if prev is not None:
            prof = np.maximum(prof, prev + 2)
        prev = prof
        if not (rank == i).any():
            continue
        top = lit_colour(g, rank == i, p)
        defs.append(f'<linearGradient id="band{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{hexc(top)}"/>'
                    f'<stop offset="1" stop-color="{hexc(top * .7)}"/></linearGradient>')
        out.append(f'<path d="{outline_path(prof * TH / SH, TH + 4)}" fill="url(#band{i})" filter="url(#edge)"/>')
    return out


def spruce(x, base, h, rnd):
    """A stylised spruce: its tiers as stacked notched triangles, its tip at base - h."""
    n = max(4, min(8, int(h / 14)))
    w = h * 0.36
    left, right = [], []
    for i in range(n):
        t = (i + 1) / n
        y = base - h + h * t ** 1.05
        hw = w * (0.18 + 0.82 * t) * rnd.uniform(0.9, 1.1)
        left += [(x - hw, y), (x - hw * 0.45, y - h / n * 0.25)]
        right += [(x + hw, y), (x + hw * 0.45, y - h / n * 0.25)]
    left[-1], right[-1] = (x - w * 0.16, base), (x + w * 0.16, base)
    pts = [(x, base - h)] + right + [(x + w * 0.08, base + 4), (x - w * 0.08, base + 4)] + left[::-1]
    return "M" + " L".join(f"{a:.1f},{b:.1f}" for a, b in pts) + " Z"


def uprights(top, horizon, p):
    """What stands on the horizon, from its outline (one y per column at trace size). Broad forms, a ridge or a
    mass of trees, are what a morphological opening of the outline keeps: it removes anything narrower than
    treew. Each clear spike it removed becomes a stylised spruce. With trees=0 the outline stays as it is."""
    hgt = horizon - top
    n, w = len(hgt), int(p["treew"])
    if not p["trees"]:
        return f'<path d="{outline_path(horizon - blur(hgt[None, :], 0.01, 2)[0], horizon + 14)}"/>'

    def run(a, f):
        return np.array([f(a[max(0, i - w // 2):i + w // 2 + 1]) for i in range(n)])
    body = blur(run(run(hgt, np.min), np.max)[None, :], 0.01, 4)[0]
    spike = hgt - body
    rnd = np.random.default_rng(7)
    peaks = []
    for x in range(n):
        r = int(max(6, hgt[x] * 0.22))
        if spike[x] >= 14 and spike[x] == spike[max(0, x - r):x + r + 1].max() and \
                not any(abs(x - q) < r for q, _ in peaks):
            peaks.append((x, hgt[x]))
    return "".join(f'<path d="{spruce(x, horizon, h * 1.02, rnd)}"/>' for x, h in peaks) + \
        f'<path d="{outline_path(horizon - body, horizon + 14)}"/>'


def horizon_land(big, rgb, g, p, tmp, defs):
    """The land under a given horizon, as bands, and what stands on it. Returns the land mask and its layers."""
    rows = np.arange(SH)[:, None]
    hy = int(p["horizon"] * SH)
    objs = np.zeros((SH, SW), bool)
    for m in components(smooth_mask(to_lab(blur(rgb, 1.0))[..., 0] < p["darkland"], 1.0, 1.0, 0)):
        r = np.nonzero(m.any(axis=1))[0]
        if r[-1] >= hy > r[0] and m.sum() > 30:
            objs |= m
    land = smooth_mask(np.broadcast_to(rows >= hy, (SH, SW)), 1.0, 1.0, 0) | objs
    layers = land_bands(land & (rows >= hy), g, p, defs)
    if objs.any():
        # the outline from above, at trace size: per column the highest dark pixel near the objects, so a spruce
        # keeps its tip and tiers and bare trees become a treeline, with no holes or islands
        hi = np.asarray(big.resize((TW, TH), Image.LANCZOS)).astype(np.float32)
        near = np.asarray(Image.fromarray((objs * 255).astype(np.uint8)).resize((TW, TH)).filter(
            ImageFilter.MaxFilter(9))) > 127
        up = (blur(to_lab(hi)[..., 0], 1.5) < p["darkland"]) & near & (np.arange(TH)[:, None] < (hy + 8) * 2)
        up = blur(up.astype(np.float32), 2.5) > 0.5
        hyT = hy * TH / SH
        top = np.where(up.any(axis=0), up.argmax(axis=0), TH).astype(float)
        top = blur(np.minimum(top, hyT)[None, :], 0.01, 1.5)[0]
        layers.insert(1, f'<g fill="{hexc(lit_colour(g, objs, p) * .9)}" filter="url(#edge)">{uprights(top, hyT, p)}</g>')
    return land, layers


def fill_land(rgb, L, g, p, defs):
    """Land that does not stand on one flat horizon: what is darker than the sky (never bright blue sky, which can
    be darker than a cloud) and reaches the bottom of the frame, and everything below it in its column."""
    raw = to_lab(blur(rgb, 1.5))
    blue = (raw[..., 2] < -12) & (raw[..., 0] > 45)
    dark = smooth_mask((L < otsu(L)) & ~blue, p["smooth"] * 0.5, p["smooth"] * 0.5, 0)
    base = np.zeros_like(dark)
    for m in components(dark):
        if m[-3:].any():
            base |= m
    top = np.where(base.any(axis=0), base.argmax(axis=0), SH)
    land = np.arange(SH)[:, None] >= top[None, :]
    return land, land_bands(land, g, p, defs)


# ---- the scene ---------------------------------------------------------------------------------

def band(photo, top):
    """The 16:9 band of a photo that starts at top (a fraction of its height), at wallpaper size."""
    im = ImageOps.exif_transpose(Image.open(photo)).convert("RGB")
    w, h = im.size
    bh = round(w * 9 / 16)
    y = min(max(0, round(top * h)), h - bh)
    return im.crop((0, y, w, y + bh)).resize((W, H), Image.LANCZOS)


def find_sun(L, zone, p):
    """The brightest spot in zone, if it is bright enough and stands out from the sky around it: a sun burns out
    a wide patch, where a white cloud or the haze on the horizon is only a little brighter than its sky."""
    Lb = np.where(zone, blur(L, 2), 0)
    y, x = np.unravel_index(np.argmax(Lb), Lb.shape)
    d = np.hypot(*np.ogrid[-y:SH - y, -x:SW - x])
    ring = (d > 22) & (d < 40) & zone
    if Lb[y, x] > p["sunlevel"] and ring.any() and Lb[y, x] - np.median(L[ring]) > p["suncontrast"]:
        return x * TW / SW, y * TH / SH
    return None


def sun_layers(sun, g, sky, p, defs):
    """The sun's disc and glow. A sun high in the sky or in a sky that is not warm is a day sun: a pale disc in a
    white glow. A low sun in a warm sky gets a warm glow and a band of colour along the horizon. Returns the
    elements and whether it is a day sun."""
    sx, sy = sun
    gl = p["glow"]
    cy, cx = int(sy * SH / TH), int(sx * SW / TW)
    around = (np.hypot(*np.ogrid[-cy:SH - cy, -cx:SW - cx]) < 45) & sky
    day = sy < TH * .35 or (around.any() and to_lab(g[around]).mean(axis=0)[2] < 18)
    if day:
        defs.append(f'<radialGradient id="sunglow"><stop offset="0" stop-color="#ffffff" stop-opacity="{min(1, .9 * gl):.2f}"/>'
                    f'<stop offset=".15" stop-color="#fff8e8" stop-opacity="{min(1, .55 * gl):.2f}"/>'
                    f'<stop offset=".5" stop-color="#fff4dc" stop-opacity="{.15 * gl:.2f}"/>'
                    '<stop offset="1" stop-color="#ffffff" stop-opacity="0"/></radialGradient>')
        return (f'<circle cx="{sx:.0f}" cy="{sy:.0f}" r="{TW * .22:.0f}" fill="url(#sunglow)"/>'
                f'<circle cx="{sx:.0f}" cy="{sy:.0f}" r="{TW * .016:.1f}" fill="#fffdf6" filter="url(#soft)"/>'), True
    defs.append(f'<radialGradient id="sunglow"><stop offset="0" stop-color="#fff1d6" stop-opacity="{min(1, .95 * gl):.2f}"/>'
                f'<stop offset=".18" stop-color="#ffc27a" stop-opacity="{min(1, .7 * gl):.2f}"/>'
                f'<stop offset=".5" stop-color="#ff8c5a" stop-opacity="{.25 * gl:.2f}"/>'
                '<stop offset="1" stop-color="#ff7a5a" stop-opacity="0"/></radialGradient>'
                f'<radialGradient id="horizonband"><stop offset="0" stop-color="#ffb066" stop-opacity="{min(1, .85 * gl):.2f}"/>'
                f'<stop offset=".45" stop-color="#ff9060" stop-opacity="{.35 * gl:.2f}"/>'
                '<stop offset="1" stop-color="#ff8060" stop-opacity="0"/></radialGradient>')
    return (f'<ellipse cx="{sx:.0f}" cy="{sy:.0f}" rx="{TW * .62:.0f}" ry="{TH * .07:.0f}" fill="url(#horizonband)"/>'
            f'<circle cx="{sx:.0f}" cy="{sy:.0f}" r="{TW * .2:.0f}" fill="url(#sunglow)"/>'
            f'<circle cx="{sx:.0f}" cy="{sy:.0f}" r="{TW * .018:.1f}" fill="#fff6e4" filter="url(#soft)"/>'), False


def water_layers(wy, field, near, rgb, g, sun, day, p, tmp, defs):
    """A lake (the sky and far land mirrored at the water line) or open sea (the photo's own water, smoothed along
    the swell, with soft surf), then the sun's streak and thin light streaks as in the Scenes."""
    rows = np.arange(SH)[:, None]
    ty = wy * TH / SH
    defs.append(f'<clipPath id="lake"><rect y="{ty:.1f}" width="{TW}" height="{TH - ty:.1f}"/></clipPath>')
    out = ['<g clip-path="url(#lake)">']
    if p["sea"]:
        wpx = (rows >= wy) & ~np.any(near + [np.zeros((SH, SW), bool)], axis=0)
        sea = blur(g * wpx[..., None], 3, 30) / (blur(wpx.astype(np.float32), 3, 30) + 1e-4)[..., None]
        sea = np.where(rows[..., None] >= wy, sea, sea[wy][None])
        out.append(f'<image href="{png_uri(sea)}" width="{TW}" height="{TH}" preserveAspectRatio="none"/>')
        # surf: smoothed along the swell and blurred at the edges, never a hard white shape
        light = lum(blur(rgb, 1.0))
        foam = smooth_mask(wpx & (light > p["foam"]), 1.5, 14.0, p["minarea"] * 0.5)
        core = smooth_mask(wpx & (light > p["foam"] + 15), 1.2, 10.0, p["minarea"] * 0.3)
        if foam.any():
            out.append(f'<g fill="#eef3f6" opacity=".56" filter="url(#cloud)">{trace(upmask(foam), tmp)}</g>')
        if core.any():
            out.append(f'<g fill="#ffffff" opacity=".48" filter="url(#blur4)">{trace(upmask(core), tmp)}</g>')
    else:
        wcol = np.median(g[(rows >= wy).repeat(SW, 1) & ~np.any(near, axis=0)] if near else g[wy:].reshape(-1, 3),
                         axis=0)
        defs.append('<linearGradient id="water" x1="0" y1="0" x2="0" y2="1">'
                    f'<stop offset="0" stop-color="{hexc(wcol)}" stop-opacity=".08"/>'
                    f'<stop offset="1" stop-color="{hexc(wcol * .55)}" stop-opacity=".5"/></linearGradient>'
                    '<filter id="mirror" x="-5%" y="-5%" width="110%" height="110%">'
                    '<feGaussianBlur stdDeviation="3 1.2"/></filter>')
        # an opaque base first: the mirror's blur would pull in the black page at the edges
        out.append(f'<rect y="{ty:.1f}" width="{TW}" height="{TH - ty:.1f}" '
                   f'fill="{hexc(np.median(field[max(0, wy - 8):wy].reshape(-1, 3), axis=0))}"/>'
                   f'<g transform="translate(0 {2 * ty:.1f}) scale(1 -1)" filter="url(#mirror)">'
                   '<use href="#sky"/><use href="#far"/></g>'
                   f'<rect y="{ty:.1f}" width="{TW}" height="{TH - ty:.1f}" fill="url(#water)"/>')
    streak = sun is not None and not day
    if streak:
        gl = p["glow"]
        defs.append(f'<radialGradient id="streak"><stop offset="0" stop-color="#ffe2b8" stop-opacity="{min(1, .9 * gl):.2f}"/>'
                    f'<stop offset=".35" stop-color="#ffb47a" stop-opacity="{.35 * gl:.2f}"/>'
                    '<stop offset="1" stop-color="#ff9a6a" stop-opacity="0"/></radialGradient>')
        out.append(f'<ellipse cx="{sun[0]:.0f}" cy="{ty:.1f}" rx="{TW * .07:.0f}" ry="{(TH - ty) * 1.1:.0f}" '
                   'fill="url(#streak)" filter="url(#soft)"/>')
    rnd = np.random.default_rng(5)
    rip = []
    for y in np.arange(ty + 3, TH, 5):
        depth = (y - ty) / (TH - ty)
        for _ in range(2):                                 # across the water, faint
            rip.append((rnd.integers(0, TW - 40), y, rnd.integers(20, 110), "#ffe6cc", rnd.uniform(.04, .16)))
        if streak:                                         # under the sun, bright and widening with depth
            for _ in range(2):
                w = rnd.integers(8, 30 + int(60 * depth))
                rip.append((sun[0] + rnd.normal(0, 18 + 50 * depth) - w / 2, y, w, "#fff0d8",
                            rnd.uniform(.25, .7) * (1 - .6 * depth)))
    out.append("".join(f'<rect x="{x:.0f}" y="{y:.1f}" width="{w}" height=".9" fill="{c}" opacity="{o:.2f}"/>'
                       for x, y, w, c, o in rip))
    return "".join(out) + "</g>"


def scene(photo, top, p, tmp):
    """The SVG for one photo."""
    big = band(photo, top)
    rgb = np.asarray(big.resize((SW, SH), Image.LANCZOS)).astype(np.float32)
    g = grade(blur(rgb, 1.0), p)
    L = lum(blur(rgb, 2.0))
    wy = int(round(p["water"] * SH)) or SH                 # the first row of water, or none
    rows = np.arange(SH)[:, None]
    above = np.broadcast_to(rows < wy, (SH, SW))
    defs, layers, far, near = [], [], [], []

    if p["horizon"] > 0:
        land, layers = horizon_land(big, rgb, g, p, tmp, defs)
        far = [land]
    elif p["land"] == "fill":
        land, layers = fill_land(rgb, L, g, p, defs)
        far = [land]
    else:
        # land is what is darker than the sky; beyond the water line it is mirrored, in front of it it is not,
        # and a dark shape that does not reach the water line is a cloud
        land = smooth_mask(L < (otsu(L) if p["land"] == "auto" else float(p["land"]) * 255), p["smooth"],
                           p["smooth"], p["minarea"])
        for m in components(land):
            if m.mean() < p["minarea"]:
                continue
            r = np.nonzero(m.any(axis=1))[0]
            if wy < SH and r[0] >= wy - 2:
                if m.mean() >= p["minarea"] * 3:           # a speck in the water is not land
                    near.append(m)
            elif wy < SH and r[-1] >= SH - 2 and m[wy:].mean() > m[:wy].mean():
                near.append(m)
            elif wy == SH or r[-1] >= wy - 3:
                far.append(m & above)
    sky = above & ~np.any(far, axis=0) if far else above.copy()

    # the sky: one smooth field from sky pixels only, so the land does not bleed into it, smoothed until it is
    # nearly one gradient that keeps the photo's warm and cool sides
    field = blur(g * sky[..., None], 26) / (blur(sky.astype(np.float32), 26) + 1e-4)[..., None]
    sky_body = [f'<image href="{png_uri(field)}" width="{TW}" height="{TH}" preserveAspectRatio="none"/>']
    lab = to_lab(g)
    warm = (lab[..., 1] > 10) & (lab[..., 2] > 28) & (lab[..., 0] > 50) & sky
    warm = smooth_mask(warm, p["smooth"], p["smooth"] * 2.5, p["minarea"] * 0.3) if warm.any() else warm
    if warm.any():
        # where the photo's sky is orange, a wide glow without a shape
        ys, xs = np.nonzero(warm)
        col = np.percentile(g[warm], 80, axis=0)
        defs.append(f'<radialGradient id="warmglow"><stop offset="0" stop-color="{hexc(col)}" '
                    f'stop-opacity="{min(1, .55 * p["glow"]):.2f}"/><stop offset="1" stop-color="{hexc(col)}" '
                    'stop-opacity="0"/></radialGradient>')
        sky_body.append(f'<ellipse cx="{xs.mean() * TW / SW:.0f}" cy="{ys.mean() * TH / SH:.0f}" '
                        f'rx="{max(80, xs.std() * 2.6 * TW / SW):.0f}" ry="{max(30, ys.std() * 2.6 * TH / SH):.0f}" '
                        'fill="url(#warmglow)"/>')

    sun, day = None, False
    if p["sun"] == "auto":
        hy = int(p["horizon"] * SH) + 4 if p["horizon"] > 0 else wy
        sun = find_sun(L, sky if p["land"] == "fill" else np.broadcast_to(rows < hy, (SH, SW)), p)
    elif p["sun"] != "none":
        fx, fy = map(float, p["sun"].split(","))
        sun = (fx * TW, fy * TH)
    if sun:
        elements, day = sun_layers(sun, g, sky, p, defs)
        sky_body.append(elements)

    if not layers:
        layers = [silhouette(m, g, f"far{k}", tmp, defs, p["shade"])
                  for k, m in enumerate(sorted(far, key=lambda m: np.nonzero(m.any(axis=1))[0][0]))]
    body = ['<g id="sky">' + "".join(sky_body) + "</g>", '<g id="far">' + "".join(layers) + "</g>"]
    if wy < SH:
        body.append(water_layers(wy, field, near, rgb, g, sun, day, p, tmp, defs))
    body += [silhouette(m, g, f"near{k}", tmp, defs) for k, m in enumerate(near)]

    filters = "".join(f'<filter id="{fid}" x="-{m}%" y="-{n}%" width="{100 + 2 * m}%" height="{100 + 2 * n}%">'
                      f'<feGaussianBlur stdDeviation="{s}"/></filter>'
                      for fid, s, m, n in (("edge", .5, 5, 5), ("soft", 1, 10, 10), ("blur4", 4, 20, 50),
                                           ("cloud", 10, 50, 100)))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {TW} {TH}" width="{W}" height="{H}" '
            f'preserveAspectRatio="none"><defs>{filters}{"".join(defs)}</defs>{"".join(body)}</svg>')


# ---- rendering ---------------------------------------------------------------------------------

def chrome():
    for c in (os.environ.get("CHROME"), r"C:\Program Files\Google\Chrome\Application\chrome.exe",
              r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
              "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
              shutil.which("google-chrome"), shutil.which("chromium"), shutil.which("chromium-browser")):
        if c and os.path.exists(c):
            return c
    sys.exit("Chrome or Chromium not found; set CHROME to its path")


def render(svg, png):
    """A headless Chrome screenshot of the SVG at wallpaper size."""
    html = svg + ".html"
    with open(html, "w") as f:
        f.write(f'<html><body style="margin:0;background:#000"><img src="{pathlib.Path(svg).as_uri()}" '
                f'style="display:block;width:{W}px;height:{H}px"></body></html>')
    subprocess.run([chrome(), "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--window-size={W},{H}",
                    f"--screenshot={png}", pathlib.Path(html).as_uri()],
                   check=True, capture_output=True, timeout=120)


def finish(png, jpg):
    """The lower third darkened towards black for the dock and the grid, faint fixed grain against banding,
    and the JPEG."""
    a = np.asarray(Image.open(png).convert("RGB")).astype(np.float32)
    ramp = np.clip((np.arange(H) - H * 0.55) / (H * 0.45), 0, 1) ** 1.3
    a *= (1 - 0.72 * ramp)[:, None, None]
    a += np.random.default_rng(1).normal(0, 4, a.shape[:2])[:, :, None] * 0.45
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(jpg, quality=86, optimize=True)


def settings(path=SETTINGS):
    """The scenes in the settings file: (name, photo, band top, settings)."""
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.split("#")[0].split()
            if not line:
                continue
            name, photo, top, *kv = line
            p = dict(DEFAULTS)
            for item in kv:
                k, v = item.split("=", 1)
                if k not in p:
                    sys.exit(f"{name}: unknown setting {k}")
                p[k] = v if k in TEXT_KEYS else int(float(v)) if isinstance(DEFAULTS[k], int) else float(v)
            out.append((name, photo, float(top), p))
    return out


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    photos, out, only = sys.argv[1], sys.argv[2], set(sys.argv[3:])
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, photo, top, p in settings():
            if only and name not in only:
                continue
            svg, png, jpg = os.path.join(tmp, name + ".svg"), os.path.join(tmp, name + ".png"), \
                os.path.join(out, name + ".jpg")
            with open(svg, "w") as f:
                f.write(scene(os.path.join(photos, photo), top, p, tmp))
            render(os.path.abspath(svg), os.path.abspath(png))
            finish(png, jpg)
            print(name, os.path.getsize(jpg) // 1024, "KB")


if __name__ == "__main__":
    main()
