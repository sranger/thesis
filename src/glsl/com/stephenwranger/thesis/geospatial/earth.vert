#version 130

varying vec3 normal;

void main(){
   gl_TexCoord[0] = gl_MultiTexCoord0;
   gl_Position = ftransform();
}