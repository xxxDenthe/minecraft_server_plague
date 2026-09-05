"""plague_bloom crop - eight growth stages, 16x16 each, transparent background.
Drawn for the vanilla crossed-plane crop model, so each tile is a clump of
stalks, not one stem. Same plague palette as the item icon."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pngio import write_png

def rgb(h): return [int(h[i:i+2], 16) for i in (1, 3, 5)] + [255]

A = [rgb(c) for c in ("#1c1a18", "#2c2b28", "#403c36", "#544f47", "#6b6559",
                      "#847d6f", "#9d9587")]
V = [rgb(c) for c in ("#3a2740", "#4e3654", "#6b4a72")]
W = H = 16

# x, height offset (+ = shorter), stalk shade, leaf side, stage it sprouts
STALKS = [
    ( 2,  1, 3, -1, 0),
    ( 5,  0, 4,  1, 1),
    ( 8,  2, 3, -1, 0),
    (11,  0, 5,  1, 0),
    (14,  1, 4, -1, 2),
    ( 3,  4, 3,  1, 3),
    (12,  4, 3, -1, 4),
    ( 7,  5, 4,  1, 5),
]

# how tall the tallest stalk reaches, per stage (top row; 15 = ground)
TOPS = [12, 10, 9, 8, 7, 6, 6, 5]
# (bud height in px, bud width in px) per stage; 0 = no bud yet
BUDS = [(0, 0), (0, 0), (0, 0), (0, 0), (1, 1), (2, 1), (3, 3), (4, 3)]
# how many of the stalks carry a bud, per stage
BUDDED = [0, 0, 0, 0, 2, 3, 4, 5]


def draw_stage(stage):
    img = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]
    def put(x, y, c):
        if 0 <= x < W and 0 <= y < H: img[y][x] = c

    bh, bw = BUDS[stage]
    budded = 0
    for x, drop, shade, side, born in STALKS:
        if stage < born: continue
        top = min(14, TOPS[stage] + drop)

        for y in range(top, 16):
            put(x, y, A[shade])

        if 15 - top >= 4:                                   # a leaf, once tall enough
            ly = top + (15 - top) // 2
            put(x + side, ly, A[max(1, shade - 1)])
            put(x + side * 2, ly + 1, A[max(1, shade - 1)])

        if bh == 0 or budded >= BUDDED[stage]: continue
        budded += 1
        by = top - 1                                        # bud sits straight on the stalk
        if bw == 1:
            for k in range(bh): put(x, by - k, A[5 - k])
            continue
        for k in range(bh - 1):                             # body: three wide
            put(x - 1, by - k, A[6]); put(x, by - k, A[5]); put(x + 1, by - k, A[4])
        put(x, by - bh + 1, A[5])                           # tapered tip
        put(x - 1, by, A[3]); put(x + 1, by, A[3])          # shaded underside
        if stage == 7:                                      # ripe: the crack shows
            put(x, by - 1, V[2]); put(x, by - 2, V[1])
    return img


imgs = [draw_stage(s) for s in range(8)]
for s, img in enumerate(imgs):
    write_png("plague_bloom_crop_stage%d.png" % s, W, H, img)

S = 8
SKY, SOIL = [143, 168, 192, 255], [70, 52, 36, 255]
strip = [[(SKY if y < H * S * 11 // 16 else SOIL)[:] for _ in range(W * S * 8)]
         for y in range(H * S)]
for s, img in enumerate(imgs):
    for y in range(H * S):
        for x in range(W * S):
            p = img[y // S][x // S]
            if p[3]: strip[y][s * W * S + x] = p[:]
    for y in range(H * S):
        strip[y][s * W * S] = [96, 94, 90, 255]
write_png("plague_bloom_stages_preview.png", W * S * 8, H * S, strip)
print("ok, 8 stages")
