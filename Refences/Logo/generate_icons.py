#!/usr/bin/env python3
"""从 Pient.png（2048×2048 RGBA 纯色剪影，内容 bbox 472,484-1574,1602）生成全套 Android 图标资源。

输出：
- app/src/main/res/mipmap-*/ic_launcher.png              legacy 白底蓝 logo（5 密度）
- app/src/main/res/mipmap-*/ic_launcher_background.png   adaptive 背景纯白（5 密度）
- app/src/main/res/mipmap-*/ic_launcher_foreground.png   adaptive 前景（原设计占比 53.8%）
- app/src/main/res/mipmap-*/ic_launcher_monochrome.png   monochrome 白色 alpha 剪影
- Refences/Logo/derived/ic_notification_*px.png          通知图标白剪影（24/36/48/72/96）
- Refences/Logo/derived/play_store_512.png               商店 512 白底
- Refences/Logo/derived/Pient_logo_vector.xml            VectorDrawable（由 SVG 转换）
- Refences/Logo/derived/preview_*.png                    供人工审阅的预览图
"""
import os
import re

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))  # Refences/Logo
APP_RES = os.path.normpath(os.path.join(ROOT, '..', '..', 'app', 'src', 'main', 'res'))
DERIVED = os.path.join(ROOT, 'derived')
os.makedirs(DERIVED, exist_ok=True)

SRC = Image.open(os.path.join(ROOT, 'Pient.png')).convert('RGBA')
ART = SRC.crop((472, 484, 1574, 1602))  # 1102×1118
ART_PROP = 1102 / 2048  # 0.538 —— adaptive 前景沿用原设计占比

DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
ADAPTIVE_BASE, LEGACY_BASE, NOTIF_BASE = 108, 48, 24


def fit_art(box):
    """等比缩放 ART 至可放入 box 的最大尺寸，返回 RGBA。"""
    s = min(box / ART.width, box / ART.height)
    return ART.resize((round(ART.width * s), round(ART.height * s)), Image.LANCZOS)


def place_on(canvas, img):
    canvas.alpha_composite(img, ((canvas.width - img.width) // 2, (canvas.height - img.height) // 2))
    return canvas


def white_silhouette(img):
    out = Image.new('RGBA', img.size, (255, 255, 255, 255))
    out.putalpha(img.split()[3])
    return out


def tint_silhouette(img, rgb):
    out = Image.new('RGBA', img.size, rgb + (255,))
    out.putalpha(img.split()[3])
    return out


# ---------- 1. app mipmap ----------
for d, m in DENSITIES.items():
    a = int(ADAPTIVE_BASE * m)
    fg = place_on(Image.new('RGBA', (a, a), (0, 0, 0, 0)), fit_art(round(a * ART_PROP)))
    fg.save(os.path.join(APP_RES, f'mipmap-{d}', 'ic_launcher_foreground.png'))
    Image.new('RGBA', (a, a), (255, 255, 255, 255)).save(
        os.path.join(APP_RES, f'mipmap-{d}', 'ic_launcher_background.png'))
    white_silhouette(fg).save(os.path.join(APP_RES, f'mipmap-{d}', 'ic_launcher_monochrome.png'))

    l = int(LEGACY_BASE * m)
    place_on(Image.new('RGBA', (l, l), (255, 255, 255, 255)), fit_art(round(l * 0.78))).convert('RGB').save(
        os.path.join(APP_RES, f'mipmap-{d}', 'ic_launcher.png'))

    n = int(NOTIF_BASE * m)
    nf = white_silhouette(place_on(Image.new('RGBA', (n, n), (0, 0, 0, 0)), fit_art(round(n * 0.9))))
    nf.save(os.path.join(DERIVED, f'ic_notification_{n}px.png'))

# ---------- 2. Play Store 512 ----------
ps = place_on(Image.new('RGBA', (512, 512), (255, 255, 255, 255)), fit_art(round(512 * 0.75)))
ps.save(os.path.join(DERIVED, 'play_store_512.png'))

# ---------- 3. 预览图 ----------
# 方形 launcher 效果
p1 = place_on(Image.new('RGBA', (432, 432), (255, 255, 255, 255)), fit_art(round(432 * ART_PROP)))
p1.save(os.path.join(DERIVED, 'preview_launcher_432.png'))
# 圆形遮罩模拟
mask = Image.new('L', (432, 432), 0)
ImageDraw.Draw(mask).ellipse((0, 0, 432, 432), fill=255)
p2 = p1.copy()
p2.putalpha(mask)
p2.save(os.path.join(DERIVED, 'preview_launcher_circle_432.png'))
# 主题图标（monochrome）暗底白剪影 / 亮底深剪影
m = fit_art(round(432 * ART_PROP))
p3 = place_on(Image.new('RGBA', (432, 432), (13, 17, 23, 255)), white_silhouette(m))
p3.save(os.path.join(DERIVED, 'preview_monochrome_dark_432.png'))
p4 = place_on(Image.new('RGBA', (432, 432), (255, 255, 255, 255)), tint_silhouette(m, (31, 35, 40)))
p4.save(os.path.join(DERIVED, 'preview_monochrome_light_432.png'))
# 通知图标 96px（状态栏深色底上）
n96 = white_silhouette(place_on(Image.new('RGBA', (96, 96), (43, 43, 43, 255)), fit_art(round(96 * 0.9))))
n96.save(os.path.join(DERIVED, 'preview_notification_96.png'))

# ---------- 4. VectorDrawable（SVG → XML，烘焙 transform） ----------
# ⚠️ 已弃用（2026-08-31）：Pient.svg 的 potrace path 端点出界（与 2048 viewBox 不匹配，实测
#    y 达 -6247 / x 达 -1679），烘焙产物 vector 在屏渲染形状严重扭曲。
#    关于页 logo 改用 app/src/main/res/drawable-nodpi/pient_logo.png（Pient.png 512 缩放）。
#    若未来需恢复 vector：先用 potrace 从 Pient.png 重新生成正确的 SVG 再烘焙。
SVG_PATH = os.path.join(ROOT, 'Pient.svg')
data = open(SVG_PATH, encoding='utf-8').read()
paths = re.findall(r'<path d="([^"]+)"', data)
# transform: translate(0,2048) scale(0.1,-0.1)  →  x'=0.1x, y'=2048-0.1y

def bake(path_data):
    """烘焙 transform: translate(0,2048) scale(0.1,-0.1) → x'=0.1x, y'=2048-0.1y。
    正确处理相对命令（小写）：先转绝对坐标（potrace 输出 M/c/z/m，相对偏移需累加当前点）。"""
    toks = re.findall(r'[A-Za-z]|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?', path_data)
    out, cx, cy, sx, sy = [], 0.0, 0.0, 0.0, 0.0
    cmd, rel, i = None, False, 0
    while i < len(toks):
        t = toks[i]
        if t in 'MmLlHhVvCcSsQqTtAaZz':
            cmd, rel = t, t.islower()
            i += 1
            if cmd in 'Zz':
                out.append('Z')
                cx, cy = sx, sy
                cmd = None
                continue
        if cmd is None:
            raise ValueError(f'number before command: {t}')
        u = cmd.upper()
        nums = []
        while i < len(toks) and re.match(r'^-?\d', toks[i]):
            nums.append(float(toks[i])); i += 1

        def emit(x, y):
            nonlocal cx, cy
            ax = x + (cx if rel else 0.0)
            ay = y + (cy if rel else 0.0)
            out.append(f'{0.1 * ax:.1f} {2048 - 0.1 * ay:.1f}')
            cx, cy = ax, ay

        if u == 'M':
            out.append('M')
            emit(nums[0], nums[1])
            sx, sy = cx, cy
            for j in range(2, len(nums) - 1, 2):  # M 后续坐标对按 L 处理
                out.append('L'); emit(nums[j], nums[j + 1])
        elif u == 'L':
            for j in range(0, len(nums) - 1, 2):
                out.append('L'); emit(nums[j], nums[j + 1])
        elif u == 'H':
            for x in nums:
                out.append('L'); emit(x, 0.0)
        elif u == 'V':
            for y in nums:
                out.append('L'); emit(0.0, y)
        elif u == 'C':
            for j in range(0, len(nums) - 5, 6):
                out.append('C')
                for k in range(3):
                    emit(nums[j + 2 * k], nums[j + 2 * k + 1])
        else:
            raise ValueError(f'unsupported cmd {cmd}')
    return ' '.join(out)

vd = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
      '    android:width="48dp" android:height="48dp"',
      '    android:viewportWidth="2048" android:viewportHeight="2048">']
for p in paths:
    vd.append(f'    <path android:fillColor="#477CB9" android:pathData="{bake(p)}" />')
vd.append('</vector>')
with open(os.path.join(DERIVED, 'Pient_logo_vector.xml'), 'w', encoding='utf-8') as f:
    f.write('\n'.join(vd) + '\n')

print('DONE')
print('mipmap:', sorted(os.listdir(os.path.join(APP_RES, 'mipmap-mdpi'))))
print('derived:', sorted(os.listdir(DERIVED)))
