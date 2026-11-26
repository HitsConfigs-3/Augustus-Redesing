#version 120

varying vec2 v_LocalUV;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    v_LocalUV = gl_MultiTexCoord0.st;
}
