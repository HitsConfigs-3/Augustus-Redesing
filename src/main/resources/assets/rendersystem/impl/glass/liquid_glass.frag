#version 120

varying vec2 v_ScreenUV;
varying vec2 v_LocalUV;

uniform sampler2D tex;
uniform vec2 size;
uniform vec4 cornerRadius;
uniform float smoothness;

uniform float fresnelPower;
uniform float fresnelAlpha;
uniform float fresnelMix;
uniform float distortStrength;

uniform vec4 glassTint;
uniform vec4 ambientTint;
uniform float frostGrain;
uniform float sheenStrength;
uniform float saturationBoost;
uniform float globalAlpha;

uniform float borderWidth;
uniform vec4 borderColor;
uniform vec4 borderColorBottom;
uniform vec4 outerShadowColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

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
    float masterAlpha = clamp(globalAlpha, 0.0, 1.0);
    if (masterAlpha <= 0.002) {
        discard;
    }

    vec2 center = size * 0.5;
    vec2 boxHalf = max(vec2(1.0), center - 1.5);
    vec2 pixelPos = v_LocalUV * size;
    vec2 p = pixelPos - center;

    float d = sdRoundRectCorners(p, boxHalf, cornerRadius);
    float aa = max(0.65, smoothness);

    // Subtle outer hairline contrast shadow just outside the glass edge
    float outerShadowMask = (1.0 - smoothstep(0.0, 2.2, d)) * smoothstep(-0.5, 0.35, d);

    // Interior fill coverage (dist <= 0)
    float fillCov = 1.0 - smoothstep(-aa, aa, d);
    if (fillCov <= 0.002 && (outerShadowColor.a * outerShadowMask) <= 0.002) {
        discard;
    }

    // Subtle Liquid Glass Fresnel band confined crisply near bevel rim
    float distInside = max(0.0, -d);
    float rimWidth = min(6.0, min(boxHalf.x, boxHalf.y));
    float edgeBand = rimWidth > 0.5 ? clamp(1.0 - distInside / rimWidth, 0.0, 1.0) : 0.0;
    float fresnel = edgeBand > 0.001 ? clamp(pow(edgeBand, max(2.0, fresnelPower)), 0.0, 1.0) : 0.0;

    // Subtle optical refraction near edges over the Kawase blurred backdrop
    vec2 dir = length(p) > 0.001 ? normalize(p) : vec2(0.0);
    vec2 sampleUV = clamp(v_ScreenUV + vec2(dir.x, -dir.y) * fresnel * distortStrength, 0.0, 1.0);
    vec3 blurred = texture2D(tex, sampleUV).rgb;

    // Controlled acrylic backdrop saturation so bright grass/sky never blow out behind glass
    float luma = dot(blurred, vec3(0.299, 0.587, 0.114));
    float sat = saturationBoost > 0.01 ? saturationBoost : 1.0;
    vec3 glassBase = clamp(mix(vec3(luma), blurred, sat), 0.0, 1.0);

    // Real-time controllable frosted tint (GlassDarkness + GlassOpacity; 0 opacity leaves pure transparent glass)
    glassBase = mix(glassBase, glassTint.rgb, clamp(glassTint.a, 0.0, 1.0));

    // Uniform optional tint (no directional corner purple blotch)
    if (ambientTint.a > 0.001) {
        glassBase = mix(glassBase, ambientTint.rgb, clamp(ambientTint.a * 0.35, 0.0, 0.25));
    }

    // Subtle overhead top-edge crystal catchlight only
    float topGrad = 1.0 - clamp(v_LocalUV.y, 0.0, 1.0);
    float topPow = topGrad * topGrad;
    float topSheen = topPow * sheenStrength * 0.45;
    float topBevelMask = smoothstep(1.8, 0.0, distInside) * (0.25 + 0.75 * topPow);
    vec3 specColor = glassBase + vec3(topSheen);
    float rimSpecFactor = clamp(fresnel * fresnelAlpha * fresnelMix * 0.35 + topBevelMask * fresnelAlpha * 0.35, 0.0, 0.35);
    specColor = mix(specColor, vec3(0.95, 0.96, 1.0), rimSpecFactor);

    // Subtle frosted micro-grain prevents dark-gradient banding while keeping authentic etched-glass feel
    if (frostGrain > 0.0001 && glassTint.a > 0.02) {
        float grain = (hash12(gl_FragCoord.xy) - 0.5) * frostGrain * clamp(glassTint.a, 0.0, 1.0);
        specColor = clamp(specColor + vec3(grain), 0.0, 1.0);
    }

    // Crisp 1px crystal rim border with subtle overhead highlight
    float bw = max(0.0, borderWidth);
    float borderDist = abs(d + bw * 0.5) - bw * 0.5;
    vec4 effBorder = borderColorBottom.a > 0.001
        ? mix(borderColor, borderColorBottom, clamp(v_LocalUV.y, 0.0, 1.0))
        : borderColor;
    float borderCov = bw > 0.01 ? (1.0 - smoothstep(-aa, aa, borderDist)) * effBorder.a : 0.0;
    vec3 litBorderRgb = clamp(mix(effBorder.rgb, vec3(1.0), topPow * 0.14), 0.0, 1.0);

    vec3 outRgb = mix(specColor, litBorderRgb, borderCov);
    float outAlpha = clamp(fillCov + borderCov, 0.0, 1.0) * masterAlpha;

    // Blend subtle outside edge shadow behind the glass edge without tinting interior
    if (d > 0.0 && outerShadowColor.a > 0.001) {
        float shAlpha = outerShadowColor.a * outerShadowMask * (1.0 - clamp(fillCov + borderCov, 0.0, 1.0)) * masterAlpha;
        float totalAlpha = outAlpha + shAlpha;
        if (totalAlpha > 0.001) {
            outRgb = (outRgb * outAlpha + outerShadowColor.rgb * shAlpha) / totalAlpha;
            outAlpha = totalAlpha;
        }
    }

    if (outAlpha <= 0.002) {
        discard;
    }

    gl_FragColor = vec4(outRgb, outAlpha);
}
