#version 120

varying vec2 v_TexCoord;
varying vec4 v_Color;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    v_TexCoord = gl_MultiTexCoord0.st;
    v_Color = gl_Color;
}
