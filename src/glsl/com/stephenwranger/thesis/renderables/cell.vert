#version 130

uniform vec3 originOffset;
uniform mat4 mvp;

in float altitude;
in float intensity;

out vec4 vertColor;
out float vertAltitude;
out float vertIntensity;

void main(){
   gl_Position = mvp * vec4(gl_Vertex.xyz - originOffset, 1.0);
   vertColor = gl_Color;
}