"""Approximate preview of the implemented Apex Launcher home layout.
Draws with PIL using the same design tokens as the Android resources."""
from PIL import Image, ImageDraw, ImageFont

DP = 2.625
def d(v): return int(round(v * DP))

BASE = "/home/user/ApexLauncher/"
F_BOLD = BASE + "app_pojavlauncher/src/main/res/font/noto_sans_bold.ttf"
F_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
ACCENT=(43,217,192); ACCENT_L=(95,240,217); CYAN=(35,199,232)
TEXT=(234,242,255); DIM=(155,172,198); FAINT=(109,126,150)
INK=(8,13,22)

W, H = 2340, 1080
img = Image.new("RGBA", (W, H), (8, 13, 22, 255))

def layer():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))

def stamp(overlay):
    img.alpha_composite(overlay)

def font(p, s): return ImageFont.truetype(p, s)
def txt(ov, xy, s, f, fill, anchor="la"):
    ImageDraw.Draw(ov).text(xy, s, font=f, fill=fill, anchor=anchor)

# ---------- backdrop ----------------------------------------------------------
bg = Image.open(BASE + "app_pojavlauncher/src/main/res/drawable-nodpi/apex_background.jpg").convert("RGB")
sc = max(W / bg.width, H / bg.height)
bg = bg.resize((int(bg.width * sc), int(bg.height * sc)), Image.LANCZOS)
bg = bg.crop(((bg.width - W)//2, (bg.height - H)//2, (bg.width - W)//2 + W, (bg.height - H)//2 + H))
img.paste(bg.convert("RGBA"), (0, 0))
ov = layer(); ImageDraw.Draw(ov).rectangle([0,0,W,H], fill=(6,11,20,120)); stamp(ov)

# ---------- helpers -----------------------------------------------------------
def vgrad(overlay, box, top, bottom):
    x0,y0,x1,y1 = box; h = max(1, y1-y0)
    strip = Image.new("RGBA", (1, h))
    px = strip.load()
    for y in range(h):
        t = y/max(1,h-1)
        px[0,y] = tuple(int(top[i] + (bottom[i]-top[i])*t) for i in range(4))
    overlay.alpha_composite(strip.resize((max(1,x1-x0), h)), (x0,y0))

def hgrad(overlay, box, left, right):
    x0,y0,x1,y1 = box; w = max(1, x1-x0); h = max(1, y1-y0)
    strip = Image.new("RGBA", (w, 1))
    px = strip.load()
    for x in range(w):
        t = x/max(1,w-1)
        px[x,0] = tuple(int(left[i] + (right[i]-left[i])*t) for i in range(4))
    overlay.alpha_composite(strip.resize((w,h)), (x0,y0))

def rrect(ov, box, r, fill=None, outline=None, width=1):
    ImageDraw.Draw(ov).rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def ellipse(ov, box, fill=None, outline=None, width=1):
    ImageDraw.Draw(ov).ellipse(box, fill=fill, outline=outline, width=width)

def cube(ov, cx, cy, s):
    dr = ImageDraw.Draw(ov)
    dr.polygon([(cx,cy-s),(cx+s,cy-s/2),(cx,cy),(cx-s,cy-s/2)], fill=(255,176,138,255))
    dr.polygon([(cx-s,cy-s/2),(cx,cy),(cx,cy+s),(cx-s,cy+s/2)], fill=(255,122,89,255))
    dr.polygon([(cx+s,cy-s/2),(cx,cy),(cx,cy+s),(cx+s,cy+s/2)], fill=(217,84,47,255))

def icon(ov, kind, cx, cy, size, color, w=2):
    dr = ImageDraw.Draw(ov); r = size/2
    if kind == "home":
        dr.polygon([(cx,cy-r),(cx+r,cy-r*.05),(cx+r*.5,cy-r*.05),(cx+r*.5,cy+r*.85),
                    (cx-r*.5,cy+r*.85),(cx-r*.5,cy-r*.05),(cx-r,cy-r*.05)], fill=color)
    elif kind == "library":
        dr.rounded_rectangle([cx-r,cy-r*.75,cx+r,cy+r*.75], radius=r*.25, outline=color, width=w)
        dr.line([(cx-r*.55,cy-r*.25),(cx+r*.55,cy-r*.25)], fill=color, width=w)
        dr.line([(cx-r*.55,cy+r*.1),(cx+r*.15,cy+r*.1)], fill=color, width=w)
    elif kind == "store":
        dr.rounded_rectangle([cx-r,cy-r*.35,cx+r,cy+r*.8], radius=r*.25, outline=color, width=w)
        dr.line([(cx-r,cy-r*.35),(cx,cy-r*.9),(cx+r,cy-r*.35)], fill=color, width=w)
    elif kind == "controls":
        dr.rounded_rectangle([cx-r,cy-r*.7,cx+r,cy+r*.7], radius=r*.45, outline=color, width=w)
        dr.ellipse([cx-r*.35,cy-r*.2,cx+r*.15,cy+r*.3], fill=color)
    else:
        dr.ellipse([cx-r,cy-r,cx+r,cy+r], outline=color, width=w)
        dr.ellipse([cx-r*.32,cy-r*.32,cx+r*.32,cy+r*.32], fill=color)

# ---------- navigation rail ---------------------------------------------------
X0, Y0 = d(12), d(12)
rail = (X0, Y0, X0 + d(86), H - d(12))
ov = layer(); vgrad(ov, rail, (11,18,32,217), (10,15,24,166)); stamp(ov)
ov = layer(); rrect(ov, rail, d(20), outline=(255,255,255,40)); stamp(ov)

ov = layer(); cube(ov, (rail[0]+rail[2])//2, rail[1] + d(12) + d(16), d(15)); stamp(ov)

nav = [("home","Home"),("library","Instances"),("store","Discover"),("controls","Controls")]
y = rail[1] + d(12) + d(34) + d(12)
for i,(kind,label) in enumerate(nav):
    sel = (i == 0)
    item = (rail[0]+d(6), y, rail[2]-d(6), y+d(54))
    ov = layer()
    if sel: rrect(ov, item, d(14), fill=(43,217,192,46), outline=(43,217,192,102))
    stamp(ov)
    ov = layer()
    icon(ov, kind, (item[0]+item[2])//2, y+d(18), d(20), (ACCENT if sel else DIM)+(255,), max(2,d(1.6)))
    txt(ov, ((item[0]+item[2])//2, y+d(44)), label, font(F_BOLD, d(9)), (TEXT if sel else DIM)+(255,), "mm")
    stamp(ov)
    y += d(54)
item = (rail[0]+d(6), rail[3]-d(12)-d(54), rail[2]-d(6), rail[3]-d(12))
ov = layer(); icon(ov, "settings", (item[0]+item[2])//2, item[1]+d(18), d(20), DIM+(255,), max(2,d(1.6)))
txt(ov, ((item[0]+item[2])//2, item[1]+d(44)), "Settings", font(F_BOLD, d(9)), DIM+(255,), "mm"); stamp(ov)

# ---------- header ------------------------------------------------------------
hx = rail[2] + d(12)
ov = layer()
txt(ov, (hx, d(14)), "APEX LAUNCHER", font(F_BOLD, d(13)), TEXT+(255,))
txt(ov, (hx, d(34)), "Minecraft: Java Edition", font(F_REG, d(9)), DIM+(255,))
stamp(ov)

cw, chh = d(180), d(42)
chip = (W-d(12)-cw, d(12)+d(5), W-d(12), d(12)+d(5)+chh)
ov = layer(); rrect(ov, chip, chh//2, fill=(255,255,255,28), outline=(255,255,255,36)); stamp(ov)
av = (chip[0]+d(8), chip[1]+d(7), chip[0]+d(8)+d(28), chip[1]+d(7)+d(28))
ov = layer(); rrect(ov, av, d(5), fill=(86,124,166,255)); stamp(ov)
ov = layer(); ellipse(ov, (av[0]+d(18), av[1]+d(18), av[0]+d(26), av[1]+d(26)), fill=(80,225,130,255)); stamp(ov)
ov = layer(); txt(ov, (chip[0]+d(44), chip[1]+chh//2), "Steve", font(F_BOLD, d(12)), TEXT+(255,), "lm"); stamp(ov)

# ---------- hero --------------------------------------------------------------
hero_top = d(12) + d(52)
right_w = d(210)
info_x1 = W - d(12); info_x0 = info_x1 - right_w
hero = (hx, hero_top, info_x0 - d(12), H - d(12))
hw, hhh = hero[2]-hero[0], hero[3]-hero[1]

art = Image.open(BASE + "app_pojavlauncher/src/main/res/drawable-nodpi/apex_hero_art.jpg").convert("RGB")
sc = max(hw/art.width, hhh/art.height)
art = art.resize((int(art.width*sc)+1, int(art.height*sc)+1), Image.LANCZOS)
cx = (art.width-hw)//2; cy = int((art.height-hhh)*0.35)
art = art.crop((cx, cy, cx+hw, cy+hhh))
mask = Image.new("L", (hw, hhh), 0)
ImageDraw.Draw(mask).rounded_rectangle([0,0,hw,hhh], radius=d(20), fill=255)
img.paste(art.convert("RGBA"), (hero[0], hero[1]), mask)

ov = layer()
hgrad(ov, hero, (7,12,22,238), (7,12,22,0))     # text readability, left -> out
bottom = Image.new("RGBA", (hw, d(90)), (0,0,0,0))
stamp_fade = Image.new("RGBA", (1, d(90)))
p = stamp_fade.load()
for i in range(d(90)):
    t = i/max(1,d(90)-1)
    p[0,i] = (7,12,22,int(150*(1-t)))
ov.alpha_composite(stamp_fade.resize((hw,d(90))), (hero[0], hero[3]-d(90)))
# keep the gradient inside the rounded hero
clip = Image.new("L", (W,H), 0)
ImageDraw.Draw(clip).rounded_rectangle(hero, radius=d(20), fill=255)
ov.putalpha(Image.composite(ov.getchannel("A"), Image.new("L",(W,H),0), clip))
stamp(ov)
ov = layer(); rrect(ov, hero, d(20), outline=(255,255,255,58)); stamp(ov)

px, py = hero[0]+d(14), hero[1]+d(14)
tag_f = font(F_BOLD, d(9))
tw = ImageDraw.Draw(img).textlength("CURRENT INSTANCE", font=tag_f)
ov = layer()
rrect(ov, (px, py, px+tw+d(18), py+d(22)), d(11), fill=(43,217,192,51), outline=(43,217,192,102))
txt(ov, (px+d(9), py+d(11)), "CURRENT INSTANCE", tag_f, ACCENT_L+(255,), "lm")
txt(ov, (px, py+d(34)), "Fabric 1.21.4", font(F_BOLD, d(30)), TEXT+(255,))
txt(ov, (px, py+d(84)), "1.21.4  ·  vulkan_zink  ·  4 GB", font(F_REG, d(13)), DIM+(255,))
stamp(ov)

# change version + edit
by = hero[3]-d(14)-d(40)
ghost = (px, by, px+d(150), by+d(40))
ov = layer(); rrect(ov, ghost, d(14), fill=(255,255,255,18), outline=(255,255,255,43)); stamp(ov)
ov = layer(); icon(ov, "library", ghost[0]+d(18), by+d(20), d(16), TEXT+(255,), max(2,d(1.5)))
txt(ov, (ghost[0]+d(34), by+d(20)), "Change version", font(F_BOLD, d(11)), TEXT+(255,), "lm"); stamp(ov)
edit = (ghost[2]+d(6), by, ghost[2]+d(6)+d(40), by+d(40))
ov = layer(); rrect(ov, edit, d(14), fill=(255,255,255,18), outline=(255,255,255,43)); stamp(ov)
ov = layer(); ImageDraw.Draw(ov).ellipse((edit[0]+d(15), edit[1]+d(9), edit[0]+d(25), edit[1]+d(19)), fill=TEXT+(255,))
rrect(ov, (edit[0]+d(11), edit[1]+d(22), edit[0]+d(29), edit[1]+d(31)), d(5), fill=TEXT+(255,)); stamp(ov)

# play
pw, ph = d(150), d(52)
play = (hero[2]-d(14)-pw, hero[3]-d(14)-ph, hero[2]-d(14), hero[3]-d(14))
ov = layer(); vgrad(ov, play, (43,217,192,255), (35,199,232,255))
mask = Image.new("L", (pw, ph), 0); ImageDraw.Draw(mask).rounded_rectangle([0,0,pw,ph], radius=d(16), fill=255)
grad = ov.crop(play); img.paste(grad.convert("RGB"), (play[0], play[1]), mask)
ov = layer(); rrect(ov, play, d(16), outline=(255,255,255,70)); stamp(ov)
tx = play[0]+d(32)
ov = layer()
ImageDraw.Draw(ov).polygon([(tx, play[1]+d(18)), (tx, play[3]-d(18)), (tx+d(18), (play[1]+play[3])//2)], fill=INK+(255,))
txt(ov, (tx+d(28), (play[1]+play[3])//2), "PLAY", font(F_BOLD, d(20)), INK+(255,), "lm"); stamp(ov)

# more
more = (hero[2]-d(12)-d(34), hero[1]+d(12), hero[2]-d(12), hero[1]+d(12)+d(34))
ov = layer(); ellipse(ov, more, fill=(255,255,255,31), outline=(255,255,255,48)); stamp(ov)
ov = layer()
for i in (-1,0,1):
    ccx = (more[0]+more[2])//2 + i*d(7)
    ellipse(ov, (ccx-d(2), (more[1]+more[3])//2-d(2), ccx+d(2), (more[1]+more[3])//2+d(2)), fill=TEXT+(255,))
stamp(ov)

# ---------- right column ------------------------------------------------------
sess = (info_x0, hero_top, info_x1, hero_top+d(118))
ov = layer(); rrect(ov, sess, d(20), fill=(22,32,50,92), outline=(255,255,255,40)); stamp(ov)
ov = layer()
icon(ov, "settings", sess[0]+d(20), sess[1]+d(20), d(13), ACCENT+(255,), max(2,d(1.4)))
txt(ov, (sess[0]+d(34), sess[1]+d(20)), "SESSION", font(F_BOLD, d(9)), DIM+(255,), "lm")
txt(ov, (sess[0]+d(14), sess[1]+d(34)), "14:32", font(F_BOLD, d(30)), TEXT+(255,))
txt(ov, (sess[0]+d(14), sess[1]+d(82)), "Free RAM 3.2 GB of 8.0 GB", font(F_REG, d(11)), DIM+(255,))
txt(ov, (sess[0]+d(14), sess[1]+d(98)), "4 GB allocated", font(F_REG, d(11)), DIM+(255,))
stamp(ov)

rec = (info_x0, sess[3]+d(12), info_x1, H-d(12))
ov = layer(); rrect(ov, rec, d(20), fill=(22,32,50,92), outline=(255,255,255,40)); stamp(ov)
ov = layer()
txt(ov, (rec[0]+d(14), rec[1]+d(16)), "RECENTLY PLAYED", font(F_BOLD, d(9)), DIM+(255,))
txt(ov, (rec[2]-d(14), rec[1]+d(16)), "View all", font(F_REG, d(11)), ACCENT+(255,), "ra")
stamp(ov)
rows = [("Fabric 1.21.4","1.21.4",(120,170,220)),("Vanilla 1.20.1","1.20.1",(150,200,150)),("Forge 1.12.2","1.12.2",(210,160,120))]
ry = rec[1]+d(34)
for title, sub, col in rows:
    row = (rec[0]+d(14), ry, rec[2]-d(14), ry+d(46))
    ov = layer(); rrect(ov, row, d(23), fill=(255,255,255,26), outline=(255,255,255,32)); stamp(ov)
    ov = layer(); rrect(ov, (row[0]+d(6), row[1]+d(6), row[0]+d(40), row[1]+d(40)), d(6), fill=col+(255,)); stamp(ov)
    ov = layer()
    txt(ov, (row[0]+d(48), row[1]+d(15)), title, font(F_BOLD, d(11)), TEXT+(255,))
    txt(ov, (row[0]+d(48), row[1]+d(30)), sub, font(F_REG, d(9)), FAINT+(255,))
    pb = (row[2]-d(6)-d(34), row[1]+d(6), row[2]-d(6), row[1]+d(40))
    ellipse(ov, pb, fill=(255,255,255,28), outline=(255,255,255,48))
    ccx, ccy = (pb[0]+pb[2])//2, (pb[1]+pb[3])//2
    ImageDraw.Draw(ov).polygon([(ccx-d(4), ccy-d(7)), (ccx-d(4), ccy+d(7)), (ccx+d(8), ccy)], fill=ACCENT+(255,))
    stamp(ov)
    ry += d(54)

img.convert("RGB").save(BASE + "design/preview/apex-home-landscape.png", quality=93)
print("rendered")
