"""Altar texture atlas 64x64. Human/clean palette - warm stone + gold + red cloth.
Deliberately NOT the desaturated gray-purple used by the infected blocks."""
import random, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pngio import write_png

W = H = 64
def rgb(h): return [int(h[i:i+2], 16) for i in (1, 3, 5)] + [255]

# --- palette ---------------------------------------------------------------
S = [rgb(c) for c in ("#6b6358", "#7d7466", "#8a8073", "#9d9384", "#b8ae9a", "#c9bfa9", "#d8cfba")]  # stone 0..6
G = [rgb(c) for c in ("#6e521a", "#a8802a", "#d8b44a", "#eccf72", "#faeaa8")]                        # gold  0..4
R = [rgb(c) for c in ("#5a1919", "#6a1e1e", "#8f2b2b", "#a83636", "#c05050")]                        # cloth 0..4

img = [[[0, 0, 0, 0] for _ in range(W)] for _ in range(H)]

def blit(ox, oy, fn):
    for y in range(16):
        for x in range(16):
            img[oy + y][ox + x] = fn(x, y)

rnd = random.Random(1871)
def grain(pal, base, spread=1):
    return pal[max(0, min(len(pal) - 1, base + rnd.choice([-spread, 0, 0, 0, spread])))]

# --- 1. carved stone side (0,0): recessed panel with a bevelled frame -------
def stone_side(x, y):
    edge = min(x, y, 15 - x, 15 - y)
    if edge == 0: return grain(S, 5)          # lit outer lip
    if edge == 1: return grain(S, 2)          # groove
    if edge == 2: return grain(S, 4)          # inner bevel
    base = 3 if (x + y) % 7 else 2            # faint diagonal tooling marks
    if y > 12 - (x % 3): base -= 1            # dust settling low
    return grain(S, base)

# --- 2. stone top (16,0): flat slab, two joint lines ------------------------
def stone_top(x, y):
    if x in (0, 15) or y in (0, 15): return grain(S, 2)
    if y == 8 or (y > 8 and x == 7): return grain(S, 1)   # mortar joints
    return grain(S, 4 if (x * 3 + y * 5) % 11 else 5)

# --- 3. red cloth (32,0): woven runner, gold fringe along the bottom --------
def cloth(x, y):
    if y >= 14: return G[3] if (x + y) % 2 else G[2]      # fringe
    if y == 13: return G[1]
    base = 2 + (1 if (x + y) % 4 == 0 else 0) - (1 if x % 5 == 0 else 0)
    if 5 <= x <= 10 and 4 <= y <= 9 and (x + y) % 3 == 0: base += 2   # woven emblem
    return R[max(0, min(4, base))]

# --- 4. polished gold (48,0): vertical sheen ------------------------------- 
def gold(x, y):
    band = 2
    if 3 <= x <= 5: band = 3                              # single soft sheen
    if x == 4 and 3 <= y <= 12: band = 4
    if x >= 13: band = 1                                  # shaded far edge
    if y <= 1 or y >= 14: band = 3                        # bright rims
    if (x * 5 + y * 3) % 17 == 0: band += 1
    return G[max(0, min(4, band))]

# --- 5. tablet face (0,16): stone plate with a gilded sun sigil -------------
def tablet(x, y):
    cx, cy = x - 7.5, y - 3.5
    d2 = cx * cx + cy * cy
    if d2 <= 6:   return G[3] if d2 <= 2 else G[2]        # sun disc
    if d2 <= 11:  return G[1]                             # inner ring
    if 14 <= d2 <= 34 and (abs(cx) < 0.9 or abs(cy) < 0.9 or abs(abs(cx) - abs(cy)) < 0.9):
        return G[2]                                       # eight rays
    if min(x, y, 15 - x, 15 - y) == 0: return grain(S, 2)
    return grain(S, 3)

# --- 6. plain stone (16,16): unlit / underside -----------------------------
def stone_dark(x, y):
    return grain(S, 1)

# --- 7. candle wax (32,16): cream pillar with drips ------------------------
Wx = [rgb(c) for c in ("#b8ab8c", "#d6cbaa", "#ece3c8", "#f7f2e2")]
def wax(x, y):
    band = 2
    if 3 <= x <= 5: band = 3
    if x >= 13: band = 0
    elif x >= 11: band = 1
    if x in (2, 9) and y > 3 + (x % 4): band = 3          # runny drips
    if y <= 1: band = 3
    return Wx[max(0, min(3, band))]

# --- 8. flame (48,16): warm teardrop on transparent ------------------------
F = [rgb(c) for c in ("#8a3a12", "#e07a1e", "#f6c245", "#fff3c0")]
def flame(x, y):
    cx, cy = x - 7.5, y - 10.0
    taper = 1.0 + max(0.0, (4.0 - y) * 0.55)              # pinch the tip
    d = (cx * cx) * taper / 24.0 + (cy * cy) / 42.0
    if d > 1.0 or y > 15: return [0, 0, 0, 0]
    if d > 0.66: return F[0]
    if d > 0.36: return F[1]
    if d > 0.13: return F[2]
    return F[3]

blit(0,  0,  stone_side)
blit(16, 0,  stone_top)
blit(32, 0,  cloth)
blit(48, 0,  gold)
blit(0,  16, tablet)
blit(16, 16, stone_dark)
blit(32, 16, wax)
blit(48, 16, flame)

write_png("class_altar.png", W, H, img)
print("wrote class_altar.png", W, "x", H)
