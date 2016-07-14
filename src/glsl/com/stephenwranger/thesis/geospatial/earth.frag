#version 130

uniform sampler2D tex0;

void main() {
   float u = gl_TexCoord[0].s;
   float v = gl_TexCoord[0].t;
   
   if(u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
      discard;
   }
   
   vec4 texture = texture2D(tex0, gl_TexCoord[0].st);
   
   if(texture.a == 0.0) {
      discard;
   }
   
   gl_FragColor = texture;
}