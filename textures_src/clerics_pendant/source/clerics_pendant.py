"""Cleric's sun amulet - 16x16 Minecraft item icon, transparent background.
Same warm human palette as the altar: red cord + gold sun sigil."""
import math, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pngio import write_png

def rgb(h): return [int(h[i:i+2], 16) for i in (1, 3, 5)] + [255]

G = [rgb(c) for c in ("#5c4315", "#8f6c22", "#c19a35", "#e0c058", "#f6e49a")]  # gold 0..4
R = [rgb(c) for c in ("#3d1010", "#5a1919", "#8f2b2b", "#a83636")]             # cord 0..3

W = H = 16
img = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]

# --- braided red cord: a V converging on the bail --------------------------
for i in range(5):
    img[i][2 + i] = R[2 if i % 2 else 3]
    img[i][3 + i] = R[0]
    img[i][13 - i] = R[1 if i % 2 else 2]
    img[i][12 - i] = R[0]

# --- bail: the gold loop the cord threads through --------------------------
for x in range(6, 10):
    img[5][x] = G[1] if x in (6, 9) else G[3]

# --- medallion ------------------------------------------------------------
CX, CY = 7.5, 10.5
for y in range(6, 16):
    for x in range(W):
        dx, dy = x - CX, y - CY
        d = math.hypot(dx, dy)
        if d > 5.0: continue
        if d > 4.1:
            img[y][x] = G[1] if (dx + dy) < -2.0 else G[0]      # rim, lit top-left
            continue
        if d > 3.3:
            img[y][x] = G[3] if (dx + dy) < -2.5 else G[2]      # polished face
            continue
        spoke = abs(dx) < 0.6 or abs(dy) < 0.6
        if d <= 1.4 or (spoke and d <= 3.0):
            img[y][x] = G[4] if d <= 1.4 else G[3]               # sun core + four spokes
        else:
            img[y][x] = G[0] if (dx + dy) > 1.5 else G[1]        # engraved inset

write_png("clerics_pendant.png", W, H, img)

S = 16
big = [[img[y // S][x // S][:] for x in range(W * S)] for y in range(H * S)]
for row in big:
    for p in row:
        if p[3] == 0: p[:] = [40, 40, 46, 255]
write_png("clerics_pendant_preview.png", W * S, H * S, big)
print("ok")
