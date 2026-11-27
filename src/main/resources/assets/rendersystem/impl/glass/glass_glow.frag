#version 120

// A radial glow clipped by a rounded-rectangle silhouette.
//
// WHY a second fragment program instead of another mode in glass_primitive.frag: the reference's
// brand area carries a violet wash that is strongest at the window's top-left corner and decays to
// nothing by ~230 px across and ~90 px down, and the wash must not leak past the window's own 15 px
// corner. A plain glow primitive would paint over that corner -- the GUI's only scissor is a
// rectangle -- so the falloff and the round-rect coverage have to be evaluated together, per pixel,
// in the same pass. Keeping it in its own program also means the shape primitive's uniform set (and
// therefore every existing rounded box, well, card and shadow) is untouched.

varying vec2 v_LocalUV;

uniform vec2 size;
uniform vec4 cornerRadius;
uniform vec2 glowCenter;
uniform vec2 glowRadius;
uniform vec2 glowExponent;
uniform float smoothness;
uniform vec4 glowColor;

float sdRoundRectCorners(vec2 p, vec2 b, vec4 radii) {
    float r = radii.x;
    if (p.x > 0.0 && p.y > 0.0) {
        r = radii.z;
    } else if (p.x > 0.0 && p.y <= 0.0) {
        r = radii.y;
    } else if (p.x <= 0.0 && p.y > 0.0) {
        r = radii.w;
    }
    r = min(r, min(b.x, b.y));
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 halfSize = max(vec2(0.5), size * 0.5);
    vec2 pixelPos = v_LocalUV * size;
    vec2 p = pixelPos - halfSize;

    float d = sdRoundRectCorners(p, halfSize, cornerRadius);
    float aa = max(0.50, smoothness);
    float clip = 1.0 - smoothstep(-aa, aa, d);
    if (clip <= 0.002) {
        discard;
    }

    // Separable super-Gaussian: 1.0 at the centre, and a per-axis exponent so the wash can fall faster
    // across than it does down (the reference's is x^2.5 by y^1.5, which no single exponent matches).
    vec2 q = (pixelPos - glowCenter) / max(vec2(1.0, 1.0), glowRadius);
    float falloff = exp(-pow(abs(q.x), glowExponent.x) - pow(abs(q.y), glowExponent.y));
    float alpha = glowColor.a * falloff * clip;
    if (alpha <= 0.002) {
        discard;
    }

    gl_FragColor = vec4(glowColor.rgb, alpha);
}