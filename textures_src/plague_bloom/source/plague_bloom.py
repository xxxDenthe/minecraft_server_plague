"""plague_bloom - 16x16 Minecraft item icon, transparent background.
Plague palette: washed-out ash grey, neutral-warm (no blue, no lilac in the greys).
Violet only as a sparing accent, where the sickness itself shows through."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pngio import write_png

def rgb(h): return [int(h[i:i+2], 16) for i in (1, 3, 5)] + [255]

# ash / bone, dark -> light
A = [rgb(c) for c in ("#1c1a18", "#2c2b28", "#403c36", "#544f47", "#6b6559",
                      "#847d6f", "#9d9587")]
# the sickness, used sparingly
V = [rgb(c) for c in ("#3a2740", "#4e3654", "#6b4a72")]

W = H = 16
img = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]
def put(x, y, c):
    if 0 <= x < W and 0 <= y < H: img[y][x] = c

# --- the bud: a closed teardrop of overlapping scales ----------------------
BUD = {1: (7, 8), 2: (6, 9), 3: (5, 10), 4: (5, 10), 5: (4, 11),
       6: (4, 11), 7: (4, 11), 8: (5, 10), 9: (6, 9), 10: (7, 8)}
for y, (x0, x1) in BUD.items():
    for x in range(x0, x1 + 1):
        nb = [(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        rim = any(n[1] not in BUD or not (BUD[n[1]][0] <= n[0] <= BUD[n[1]][1]) for n in nb)
        if rim:
            put(x, y, A[1]); continue
        lit = 5 + round((-(x - 7.5) - (y - 6.0)) * 0.45)
        put(x, y, A[max(2, min(6, lit))])

# short ticks where the outer scales overlap - just a hint, not stripes
for x, y in ((6, 7), (6, 8), (9, 6), (9, 7)): put(x, y, A[3])

# the split where the bloom has cracked open - the only violet on the item
for y in range(5, 8): put(7, y, V[2])
put(7, 8, V[1]); put(8, 6, V[1])

# pale bone flecks where the husk has dried out
for x, y in ((6, 3), (5, 5), (9, 4)): put(x, y, A[6])

# --- calyx: sepals gripping the base of the bud ---------------------------
for x, y in ((6, 10), (9, 10), (5, 11), (10, 11)): put(x, y, A[2])
for x in range(6, 10): put(x, 11, A[1])

# --- stem -----------------------------------------------------------------
for y in range(11, 16):
    put(7, y, A[3]); put(8, y, A[1])

# --- two shrivelled leaves ------------------------------------------------
LEFT  = ((6, 12), (5, 12), (4, 12), (6, 13), (5, 13), (4, 13), (3, 13), (4, 14), (3, 14))
RIGHT = ((9, 12), (9, 13), (10, 13), (11, 13), (10, 14), (11, 14), (12, 14))
for x, y in LEFT:  put(x, y, A[4])
for x, y in RIGHT: put(x, y, A[3])
for x, y in ((3, 13), (3, 14), (4, 14), (11, 14), (12, 14)): put(x, y, A[1])

write_png("plague_bloom.png", W, H, img)

S = 16
big = [[img[y // S][x // S][:] for x in range(W * S)] for y in range(H * S)]
for row in big:
    for p in row:
        if p[3] == 0: p[:] = [200, 200, 205, 255]
write_png("plague_bloom_preview.png", W * S, H * S, big)
print("ok")
