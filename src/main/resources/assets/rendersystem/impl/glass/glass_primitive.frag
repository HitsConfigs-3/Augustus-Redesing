#version 120

varying vec2 v_LocalUV;

uniform vec2 size;
uniform vec4 cornerRadius;
uniform vec4 fillColor;
uniform vec4 fillColorBottom;
uniform float gradientMix;
uniform vec4 borderColor;
uniform vec4 borderColorBottom;
uniform float borderWidth;
uniform float topHighlightAlpha;
uniform float innerBottomAlpha;
uniform vec2 hoverPos;
uniform float hoverLightAlpha;
uniform float hoverLightRadius;
uniform vec4 hoverLightColor;
uniform float shadowBlur;
uniform vec2 shadowSize;
uniform vec2 shadowOffset;
uniform float smoothness;

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

float erfApprox(float x) {
    float s = sign(x);
    float a = abs(x);
    float y = 1.0 + (0.278393 + (0.230389 + 0.078108 * a * a) * a) * a;
    y = y * y;
    return s - s / (y * y);
}

vec4 over(vec4 base, vec4 top) {
    float a = top.a + base.a * (1.0 - top.a);
    if (a < 0.0001) return vec4(0.0);
    return vec4((top.rgb * top.a + base.rgb * base.a * (1.0 - top.a)) / a, a);
}

void main() {
    vec2 halfSize = max(vec2(0.5), size * 0.5);
    vec2 pixelPos = v_LocalUV * size;
    vec2 p = pixelPos - halfSize;

    // Analytical Gaussian SDF drop-shadow / glow mode (adapted from CoolClient shape.fsh)
    if (shadowBlur > 0.01) {
        vec2 boxHalf = max(vec2(0.5), shadowSize * 0.5);
        float dShadow = sdRoundRectCorners(p - shadowOffset, boxHalf, cornerRadius);
        float sigma = max(0.5, shadowBlur * 0.55);
        float shadowCov = 0.5 * (1.0 - erfApprox(dShadow / (sigma * 1.41421356)));
        vec4 sh = vec4(fillColor.rgb, clamp(fillColor.a * shadowCov, 0.0, 1.0));
        if (sh.a <= 0.002) {
            discard;
        }
        gl_FragColor = sh;
        return;
    }

    float inset = borderWidth > 0.01 ? 0.5 : 0.0;
    float d = sdRoundRectCorners(p, max(vec2(0.5), halfSize - inset), cornerRadius);
    float aa = max(0.50, smoothness);

    float fillCov = 1.0 - smoothstep(-aa, aa, d);
    vec4 gradFill = mix(fillColor, mix(fillColor, fillColorBottom, clamp(v_LocalUV.y, 0.0, 1.0)), clamp(gradientMix, 0.0, 1.0));
    vec4 result = vec4(gradFill.rgb, gradFill.a * fillCov);

    // Subtle localized cursor reveal inside glass surfaces
    float cursorProximity = 0.0;
    if (hoverLightAlpha > 0.001 && fillCov > 0.01) {
        float rad = max(12.0, hoverLightRadius);
        float distCursor = length(pixelPos - hoverPos);
        cursorProximity = 1.0 - smoothstep(0.0, rad, distCursor);
        if (cursorProximity > 0.001) {
            vec4 spot = vec4(hoverLightColor.rgb, clamp(hoverLightAlpha * cursorProximity * cursorProximity * fillCov, 0.0, 0.32));
            result = over(result, spot);
        }
    }

    // Subtle crisp 1px overhead glass reflection
    if (topHighlightAlpha > 0.001 && fillCov > 0.01) {
        float edgeY = clamp((pixelPos.y - borderWidth * 0.5) / 1.8, 0.0, 1.0);
        float innerMask = smoothstep(-2.0, -0.3, d) * (1.0 - edgeY);
        float sideFade = smoothstep(0.0, 0.12, v_LocalUV.x) * smoothstep(1.0, 0.88, v_LocalUV.x);
        vec4 topGlow = vec4(0.97, 0.98, 1.0, topHighlightAlpha * innerMask * sideFade * fillCov);
        result = over(result, topGlow);
    }

    if (innerBottomAlpha > 0.001 && fillCov > 0.01) {
        float botEdgeY = clamp((size.y - pixelPos.y - borderWidth * 0.5) / 2.0, 0.0, 1.0);
        float innerBotMask = smoothstep(-2.0, -0.3, d) * (1.0 - botEdgeY);
        vec4 botShade = vec4(0.01, 0.01, 0.03, innerBottomAlpha * innerBotMask * fillCov);
        result = over(result, botShade);
    }

    if (borderWidth > 0.01 && (borderColor.a > 0.001 || borderColorBottom.a > 0.001)) {
        vec4 effBorder = borderColorBottom.a > 0.001
            ? mix(borderColor, borderColorBottom, clamp(v_LocalUV.y, 0.0, 1.0))
            : borderColor;
        if (cursorProximity > 0.01 && hoverLightAlpha > 0.001) {
            effBorder.rgb = mix(effBorder.rgb, hoverLightColor.rgb, clamp(cursorProximity * 0.40, 0.0, 0.50));
            effBorder.a = clamp(effBorder.a + cursorProximity * 0.14, 0.0, 1.0);
        }
        float ringDist = abs(d + borderWidth * 0.5) - borderWidth * 0.5;
        float ringCov = 1.0 - smoothstep(-aa, aa, ringDist);
        vec4 ring = vec4(effBorder.rgb, effBorder.a * ringCov);
        result = over(result, ring);
    }

    if (result.a <= 0.002) {
        discard;
    }

    gl_FragColor = result;
}
