#version 120

uniform sampler2D tex;
uniform vec2 halfpixel;
uniform float offset;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 off = halfpixel * offset;

    vec3 sum = texture2D(tex, uv + vec2(-off.x * 2.0, 0.0)).rgb;
    sum += texture2D(tex, uv + vec2(-off.x, off.y)).rgb * 2.0;
    sum += texture2D(tex, uv + vec2(0.0, off.y * 2.0)).rgb;
    sum += texture2D(tex, uv + vec2(off.x, off.y)).rgb * 2.0;
    sum += texture2D(tex, uv + vec2(off.x * 2.0, 0.0)).rgb;
    sum += texture2D(tex, uv + vec2(off.x, -off.y)).rgb * 2.0;
    sum += texture2D(tex, uv + vec2(0.0, -off.y * 2.0)).rgb;
    sum += texture2D(tex, uv + vec2(-off.x, -off.y)).rgb * 2.0;

    gl_FragColor = vec4(sum / 12.0, 1.0);
}
