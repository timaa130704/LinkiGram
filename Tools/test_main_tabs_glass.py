#!/usr/bin/env python3
"""Compile and raster-test the production lens (pip install skia-python numpy).

This checks SkSL, not Android's RuntimeShader/RenderNode integration. Use CI
for the APK and a device for AGSL compatibility and animation/performance.
"""
import argparse
from pathlib import Path

import numpy as np
import skia


ROOT = Path(__file__).resolve().parents[1]
SHADER = ROOT / "TMessagesProj/src/main/res/raw/main_tabs_glass_shader.agsl"


def render(effect, scale=1, dark=True, position=0.5, press=0.0,
           intensity=1.0, asymmetric=False, tint_override=None):
    width, height = 384 // scale, 128 // scale
    y, x = np.mgrid[:height, :width]
    grid = ((x * scale // 12) % 2) ^ ((y * scale // 12) % 2)
    pixels = np.empty((height, width, 4), dtype=np.uint8)
    pixels[..., 0] = np.where(grid, 235, 45)
    pixels[..., 1] = np.where(grid, 100, 165)
    pixels[..., 2] = np.where(grid, 60, 230)
    pixels[..., 3] = 255
    image = skia.Image.fromarray(pixels, colorType=skia.kRGBA_8888_ColorType)
    builder = skia.RuntimeShaderBuilder(effect)
    builder.setChild("img", image.makeShader(skia.TileMode.kClamp, skia.TileMode.kClamp))
    alpha = 0.30 if dark else 0.24
    tint = (0.08, 0.09, 0.12) if dark else (0.96, 0.97, 1.0)
    uniforms = {
        "resolution": [float(width), float(height)],
        "center": [width / 2.0, height / 2.0],
        "size": [168.0 / scale, 36.0 / scale],
        "radius": [v / scale for v in ((12, 22, 30, 36) if asymmetric else (36,) * 4)],
        "thickness": 12.0 / scale,
        "refract_index": 1.5,
        "refract_intensity": float(intensity),
        "foreground_color_premultiplied": [v * alpha for v in tint] + [alpha],
        "light_position": float(position),
        "press_progress": float(press),
    }
    if tint_override is not None:
        uniforms["foreground_color_premultiplied"] = tint_override
    for name, value in uniforms.items():
        builder.setUniform(name, value)
    info = skia.ImageInfo.Make(width, height, skia.kRGBA_F32_ColorType, skia.kPremul_AlphaType)
    surface = skia.Surface.MakeRaster(info)
    surface.getCanvas().drawPaint(skia.Paint(Shader=builder.makeShader()))
    output = surface.makeImageSnapshot().toarray(colorType=skia.kRGBA_F32_ColorType)
    assert np.isfinite(output).all(), "Non-finite shader output"
    assert output[..., :3].min() >= 0.0 and output[..., :3].max() <= 1.0
    assert np.allclose(output[..., 3], 1.0), "Lens must cover the opaque page capture"
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", type=Path, help="Optional PNG of the actual Skia render")
    args = parser.parse_args()
    effect = skia.RuntimeEffect.MakeForShader(SHADER.read_text())
    count = 0
    for scale in (1, 2, 4, 8):
        for dark in (False, True):
            for position in (0.0, 0.5, 1.0):
                for press in (0.0, 1.0):
                    render(effect, scale, dark, position, press, asymmetric=True)
                    count += 1
    idle = render(effect)
    pressed = render(effect, press=1.0)
    moved = render(effect, position=0.0)
    plain = render(effect, intensity=0.0)
    assert np.abs(idle - pressed).max() > 0.005, "Press light is inactive"
    assert np.abs(idle - moved).max() > 0.005, "Moving light is inactive"
    assert np.abs(idle - plain).max() > 0.05, "Lens distortion is inactive"
    for tint in ([0.0, 0.0, 0.0, 0.0], [0.0, 0.0, 0.0, 1.0], [1.0, 1.0, 1.0, 1.0]):
        render(effect, tint_override=tint)
    if args.preview:
        rgba = (idle * 255).round().astype(np.uint8)
        image = skia.Image.fromarray(rgba, colorType=skia.kRGBA_8888_ColorType)
        image.save(str(args.preview), skia.kPNG)
    print(f"PASS: SkSL compiled; {count} theme/scale/light/press renders; tint extremes; active lens and lighting")


if __name__ == "__main__":
    main()
