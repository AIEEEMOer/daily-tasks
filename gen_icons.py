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
    # 白色对勾
    d.line([(62, 124), (86, 148)], fill=(255, 255, 255, 255), width=14, joint="curve")
    d.line([(86, 148), (132, 96)], fill=(255, 255, 255, 255), width=14, joint="curve")
    # 端点圆头
    for cx, cy in [(62, 124), (132, 96)]:
        d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=(255, 255, 255, 255))

    img.save(os.path.join(OUT, f"icon_{key}.png"))
    print("saved", key)

for k, (s, e) in PALETTES.items():
    make_icon(k, s, e)
print("done")
