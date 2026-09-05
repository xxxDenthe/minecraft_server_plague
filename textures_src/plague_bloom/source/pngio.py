import zlib, struct

# ---------- minimal PNG read ----------
def read_png(path):
    d = open(path, "rb").read()
    assert d[:8] == b"\x89PNG\r\n\x1a\n"
    i, idat, plte, trns, ihdr = 8, b"", None, None, None
    while i < len(d):
        ln = struct.unpack(">I", d[i:i+4])[0]; typ = d[i+4:i+8]; body = d[i+8:i+8+ln]; i += 12 + ln
        if typ == b"IHDR": ihdr = struct.unpack(">IIBBBBB", body)
        elif typ == b"IDAT": idat += body
        elif typ == b"PLTE": plte = body
        elif typ == b"tRNS": trns = body
        elif typ == b"IEND": break
    w, h, depth, ctype = ihdr[0], ihdr[1], ihdr[2], ihdr[3]
    assert depth == 8 and ctype in (2, 3, 6), (depth, ctype)
    nch = {2: 3, 3: 1, 6: 4}[ctype]
    raw, stride, out, prev = zlib.decompress(idat), w * nch, [], bytearray(w * nch)
    p = 0
    for _ in range(h):
        f = raw[p]; line = bytearray(raw[p+1:p+1+stride]); p += 1 + stride
        for x in range(stride):
            a = line[x-nch] if x >= nch else 0
            b = prev[x]; c = prev[x-nch] if x >= nch else 0
            if   f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        row = []
        for x in range(w):
            px = line[x*nch:(x+1)*nch]
            if ctype == 3:
                j = px[0]*3; row.append([plte[j], plte[j+1], plte[j+2], trns[px[0]] if trns and px[0] < len(trns) else 255])
            elif ctype == 2: row.append([px[0], px[1], px[2], 255])
            else:            row.append([px[0], px[1], px[2], px[3]])
        out.append(row)
    return w, h, out

def write_png(path, w, h, img):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in img)
    def ch(t, d):
        c = t + d; return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c))
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n"
        + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))

