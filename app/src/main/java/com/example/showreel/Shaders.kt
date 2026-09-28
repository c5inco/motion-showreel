package com.example.showreel

object Shaders {

  /**
   * Domain-warped fbm "fluid" with glassy metaballs and topographic contour lines.
   * `iris` masks it to a growing circle; `cell` pixelates it into a grid (used as the
   * match-cut into the geometry scene).
   */
  const val FLUID = """
    uniform float2 res;
    uniform float time;
    uniform float iris;
    uniform float cell;
    uniform float fade;

    float hash21(float2 p) {
      p = fract(p * float2(123.34, 456.21));
      p += dot(p, p + 45.32);
      return fract(p.x * p.y);
    }

    float vnoise(float2 p) {
      float2 i = floor(p);
      float2 f = fract(p);
      float2 u = f * f * (3.0 - 2.0 * f);
      float a = hash21(i);
      float b = hash21(i + float2(1.0, 0.0));
      float c = hash21(i + float2(0.0, 1.0));
      float d = hash21(i + float2(1.0, 1.0));
      return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    float fbm(float2 p) {
      float v = 0.0;
      float a = 0.5;
      for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
      }
      return v;
    }

    half4 main(float2 fc) {
      float2 p = fc;
      if (cell > 1.5) {
        p = (floor(fc / cell) + 0.5) * cell;
      }
      float2 uv = (p - 0.5 * res) / res.x;
      float t = time * 0.35;

      float2 q = float2(fbm(uv * 2.2 + float2(0.0, t)),
                        fbm(uv * 2.2 + float2(5.2, 1.3) - t));
      float2 r = float2(fbm(uv * 2.2 + 3.5 * q + float2(1.7, 9.2) + 0.6 * t),
                        fbm(uv * 2.2 + 3.5 * q + float2(8.3, 2.8) - 0.5 * t));
      float f = fbm(uv * 2.2 + 3.5 * r);

      // Metaballs drifting on lissajous orbits.
      float m = 0.0;
      for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float2 c = float2(0.27 * sin(time * 1.1 + fi * 1.7),
                          0.55 * sin(time * 0.8 + fi * 2.3));
        float rr = 0.085 + 0.035 * sin(fi * 3.1 + time * 1.7);
        float2 d = uv - c;
        m += rr * rr / max(dot(d, d), 1e-4);
      }
      float blob = smoothstep(0.92, 1.08, m);
      float rim = smoothstep(0.55, 1.0, m) - smoothstep(1.0, 1.5, m);

      float3 deep = float3(0.035, 0.02, 0.09);
      float3 ember = float3(1.0, 0.29, 0.11);
      float3 cobalt = float3(0.24, 0.36, 1.0);
      float3 paper = float3(0.95, 0.94, 0.90);
      float3 gold = float3(1.0, 0.78, 0.35);

      float3 col = mix(deep, cobalt, clamp(f * f * 2.4, 0.0, 1.0));
      col = mix(col, ember, clamp(dot(q, q) * 0.9, 0.0, 1.0) * smoothstep(0.35, 0.8, r.y));
      col = mix(col, paper, smoothstep(0.70, 0.95, f) * 0.8);

      // Topographic contour lines riding the warp field.
      float cl = abs(fract(f * 8.0 - time * 0.3) - 0.5);
      col += (1.0 - blob) * smoothstep(0.035, 0.0, abs(cl - 0.45)) * 0.28 * paper;

      // Glass blobs: warm refraction inside, bright rim.
      float3 glass = mix(ember, gold, fbm(uv * 6.0 + r * 2.0 + time * 0.5));
      col = mix(col, glass, blob);
      col += rim * 0.45 * paper;

      float d = length(fc - 0.5 * res);
      float mask = 1.0 - smoothstep(iris - 1.5, iris + 1.5, d);
      float a = mask * fade;
      return half4(half3(col) * a, a);
    }
  """

  /** Full-frame lens pass: chromatic aberration, slice glitch, vignette, film grain. */
  const val POST = """
    uniform shader content;
    uniform float2 res;
    uniform float time;
    uniform float ca;
    uniform float glitch;
    uniform float grain;

    float h1(float n) { return fract(sin(n) * 43758.5453); }
    float h2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

    half4 main(float2 fc) {
      float2 uv = fc / res;
      float2 p = fc;

      float frame = floor(time * 24.0);
      float band = floor(uv.y * 28.0 + h1(frame) * 9.0);
      float g = step(0.55, h1(band * 1.37 + frame)) * (h1(band + frame * 3.1) - 0.5);
      p.x += g * glitch * res.x * 0.22;

      float2 dir = uv - 0.5;
      float2 off = dir * (ca * res.x * 0.03) + float2(glitch * res.x * 0.02, 0.0);
      half4 c = content.eval(p);
      half r = content.eval(p + off).r;
      half b = content.eval(p - off).b;
      half3 col = half3(r, c.g, b);

      float v = 1.0 - smoothstep(0.35, 1.15, length(dir * float2(1.0, res.y / res.x * 0.6)));
      col *= half(mix(0.55, 1.0, v));

      float n = h2(fc + fract(time * 7.13) * 100.0) - 0.5;
      col += half(n * grain);
      return half4(col, 1.0);
    }
  """
}
