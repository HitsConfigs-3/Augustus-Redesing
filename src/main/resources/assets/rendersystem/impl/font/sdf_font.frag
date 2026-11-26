#version 120

varying vec2 v_TexCoord;
varying vec4 v_Color;

uniform sampler2D u_tex;
uniform float u_range;
uniform vec2 u_atlasSize;
uniform float u_thickness;
uniform float u_softness;
uniform vec4 u_shadowColor;

float computePxRange() {
    vec2 unitRange = vec2(u_range) / max(u_atlasSize, vec2(1.0));
    vec2 fw = max(fwidth(v_TexCoord), vec2(0.00001));
    vec2 screenTexSize = vec2(1.0) / fw;
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    float pxRange = computePxRange();
    float sd = texture2D(u_tex, v_TexCoord).a - 0.5 + u_thickness;
    float screenDist = sd * pxRange;
    float soft = max(0.45, u_softness);
    float fgAlpha = smoothstep(-soft, soft, screenDist) * v_Color.a;

    if (u_shadowColor.a > 0.002) {
        vec2 pxUV = abs(vec2(dFdx(v_TexCoord.x), dFdy(v_TexCoord.y)));
        vec2 shadowUV = v_TexCoord - vec2(pxUV.x * 0.5, pxUV.y * 0.9);
        float shSd = texture2D(u_tex, shadowUV).a - 0.5 + u_thickness;
        float shDist = shSd * pxRange;
        float shAlpha = smoothstep(-soft * 1.35, soft * 1.35, shDist) * u_shadowColor.a * v_Color.a;

        float outAlpha = fgAlpha + shAlpha * (1.0 - fgAlpha);
        if (outAlpha <= 0.002) {
            discard;
        }
        vec3 outRgb = (v_Color.rgb * fgAlpha + u_shadowColor.rgb * shAlpha * (1.0 - fgAlpha)) / outAlpha;
        gl_FragColor = vec4(outRgb, outAlpha);
    } else {
        if (fgAlpha <= 0.002) {
            discard;
        }
        gl_FragColor = vec4(v_Color.rgb, fgAlpha);
    }
}
