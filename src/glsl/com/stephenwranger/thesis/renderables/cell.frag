#version 130

uniform vec2 altitudeRange;
uniform vec2 intensityRange;

in vec4 vertColor;
in float vertAltitude;
in float vertIntensity;

out vec4 fragColor;

void main() {
   vec4 color = vertColor;
   
   if(altitudeRange.x < altitudeRange.y) {
      float range = altitudeRange.y - altitudeRange.x;
      float value = (vertAltitude - altitudeRange.x) / range;
      color.r *= value;
      color.g *= value;
      color.b *= value;
   }
   
   if(intensityRange.x < intensityRange.y) {
      float range = intensityRange.y - intensityRange.x;
      float value = (vertIntensity - intensityRange.x) / range;
      color.r *= value;
      color.g *= value;
      color.b *= value;
   }
   
   fragColor = color;
}