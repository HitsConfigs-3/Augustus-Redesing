#version 120

varying vec2 v_ScreenUV;
varying vec2 v_LocalUV;

uniform sampler2D tex;
uniform vec2 size;
uniform vec4 cornerRadius;
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

void main() {
    float alpha = 1.0;
    if (cornerRadius.x > 0.0 || cornerRadius.y > 0.0 || cornerRadius.z > 0.0 || cornerRadius.w > 0.0) {
        vec2 pixelPos = v_LocalUV * size;
        vec2 halfSize = size * 0.5;
        vec2 p = pixelPos - halfSize;
        float dist = sdRoundRectCorners(p, halfSize, cornerRadius);
        alpha = 1.0 - smoothstep(-smoothness, smoothness, dist);
    }

    if (alpha <= 0.0) {
        discard;
    }

    vec3 blurred = texture2D(tex, clamp(v_ScreenUV, 0.0, 1.0)).rgb;
    gl_FragColor = vec4(blurred, alpha);
}
