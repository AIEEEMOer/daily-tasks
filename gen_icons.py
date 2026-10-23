# -*- coding: utf-8 -*-
# 生成 7 套配色启动图标：对角渐变圆角方块 + PASS 文字 + 白色对勾
from PIL import Image, ImageDraw, ImageFont
import os

SIZE = 192
RADIUS = 42
OUT = r"C:\Android\DailyCheckIn\app\src\main\res\drawable"

PALETTES = {
    "green":  ((0x3E, 0xD5, 0x98), (0x1F, 0xA3, 0x71)),
    "blue":   ((0x6B, 0xB8, 0xF7), (0x2E, 0x86, 0xDE)),
    "orange": ((0xFF, 0xB1, 0x77), (0xF2, 0x7E, 0x2E)),
    "purple": ((0xBB, 0xA3, 0xF0), (0x8B, 0x5C, 0xF6)),
    "pink":   ((0xF8, 0xA3, 0xC3), (0xE8, 0x63, 0x9A)),
    "teal":   ((0x57, 0xD4, 0xCE), (0x1D, 0xA3, 0x9B)),
    "red":    ((0xF5, 0x8E, 0x87), (0xE0, 0x52, 0x4C)),
}

def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

def qbez(p0, p1, p2, n=48):
    """二次贝塞尔曲线采样点"""
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        pts.append((x, y))
    return pts

def hand(d, cx, cy, tx, ty, base_w, fill):
    """锥形指针：轴心端宽 base_w，远端收成尖点（像真实时钟指针）"""
    import math
    dx, dy = tx - cx, ty - cy
    length = math.hypot(dx, dy)
    px, py = -dy / length, dx / length   # 单位垂直向量
    hw = base_w / 2.0
    d.polygon([
        (cx + px * hw, cy + py * hw),
        (cx - px * hw, cy - py * hw),
        (tx, ty),
    ], fill=fill)

def make_icon(key, start, end):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # 对角渐变（左上 -> 右下）
    grad = Image.new("RGBA", (SIZE, SIZE))
    gd = ImageDraw.Draw(grad)
    for y in range(SIZE):
        for x in range(0, SIZE, 4):  # 步进加速，肉眼无差
            t = (x + y) / (2.0 * SIZE)
            gd.rectangle([x, y, x + 3, y], fill=lerp(start, end, t) + (255,))
    # 圆角蒙版
    mask = Image.new("L", (SIZE, SIZE), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=RADIUS, fill=255)
    img.paste(grad, (0, 0), mask)

    d = ImageDraw.Draw(img)
    # PASS 文字（对勾上方）
    font = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 46)
    text = "PASS"
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text(((SIZE - tw) / 2 - bbox[0], 38 - bbox[1]), text, font=font, fill=(255, 255, 255, 255))
    # 时钟指针对勾：时针短而粗（左下），分针长而细（右上），中间轴心圆点
    # 指针为锥形：轴心端粗、远端收尖
    white = (255, 255, 255, 255)
    cx, cy = 86, 138                      # 轴心（对号谷底位置）
    hx, hy = 54, 114                      # 时针尖端（短）
    mx, my = 138, 84                      # 分针尖端（长）
    hand(d, cx, cy, hx, hy, 17, white)    # 时针：短粗，远端尖
    hand(d, cx, cy, mx, my, 9, white)     # 分针：长细，远端尖
    # 轴心圆点
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=white)

    img.save(os.path.join(OUT, f"icon_{key}.png"))
    print("saved", key)

# 固定使用绿色图标（已移除图标随配色变色功能）
s, e = PALETTES["green"]
make_icon("green", s, e)
print("done")
