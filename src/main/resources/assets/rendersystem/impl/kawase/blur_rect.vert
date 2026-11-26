#version 120

varying vec2 v_ScreenUV;
varying vec2 v_LocalUV;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    v_ScreenUV = (gl_Position.xy / gl_Position.w) * 0.5 + 0.5;
    v_LocalUV = gl_MultiTexCoord0.st;
}
