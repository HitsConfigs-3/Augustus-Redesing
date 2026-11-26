#version 120

uniform sampler2D tex;
uniform vec2 halfpixel;
uniform float offset;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 off = halfpixel * offset;

    vec3 sum = texture2D(tex, uv).rgb * 4.0;
    sum += texture2D(tex, uv - off).rgb;
    sum += texture2D(tex, uv + off).rgb;
    sum += texture2D(tex, uv + vec2(off.x, -off.y)).rgb;
    sum += texture2D(tex, uv + vec2(-off.x, off.y)).rgb;

    gl_FragColor = vec4(sum / 8.0, 1.0);
}
