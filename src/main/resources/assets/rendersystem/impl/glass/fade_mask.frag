#version 120

varying vec2 v_LocalUV;

uniform sampler2D u_tex;
uniform vec2 u_fbSize;
uniform float u_regionWidth;
uniform float u_regionHeight;
uniform float u_topFade;
uniform float u_bottomFade;
uniform float u_leftFade;
uniform float u_rightFade;

void main() {
    vec2 screenUV = gl_FragCoord.xy / max(u_fbSize, vec2(1.0));
    vec4 texel = texture2D(u_tex, screenUV);
    if (texel.a <= 0.001) {
        discard;
    }

    float uvX = clamp(v_LocalUV.x, 0.0, 1.0);
    float uvY = clamp(v_LocalUV.y, 0.0, 1.0);
    float pxFromTop = uvY * u_regionHeight;
    float pxFromBottom = (1.0 - uvY) * u_regionHeight;
    float pxFromLeft = uvX * u_regionWidth;
    float pxFromRight = (1.0 - uvX) * u_regionWidth;

    float mask = 1.0;
    if (u_topFade > 0.5) {
        mask *= smoothstep(0.0, u_topFade, pxFromTop);
    }
    if (u_bottomFade > 0.5) {
        mask *= smoothstep(0.0, u_bottomFade, pxFromBottom);
    }
    if (u_leftFade > 0.5) {
        mask *= smoothstep(0.0, u_leftFade, pxFromLeft);
    }
    if (u_rightFade > 0.5) {
        mask *= smoothstep(0.0, u_rightFade, pxFromRight);
    }

    vec4 outColor = texel * mask;
    if (outColor.a <= 0.001) {
        discard;
    }
    gl_FragColor = outColor;
}
