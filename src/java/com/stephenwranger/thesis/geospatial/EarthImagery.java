package com.stephenwranger.thesis.geospatial;

import com.jogamp.opengl.GL;
import com.stephenwranger.graphics.renderables.EllipticalSegment;
import com.stephenwranger.graphics.utils.textures.Texture2d;

public class EarthImagery {
   private static final Texture2d BASE_EARTH_TEXTURE = EarthImagery.getBaseTexture();
   private static String          EARTH_IMAGERY_PATH = System.getProperty("earth.imagery");

   private EarthImagery() {
      //statics only
   }

   public static void setImagery(final EllipticalSegment segment) {
      if ((EarthImagery.EARTH_IMAGERY_PATH == null) || EarthImagery.EARTH_IMAGERY_PATH.isEmpty()) {
         segment.setTexture(EarthImagery.BASE_EARTH_TEXTURE, null);
      } else {
         // TODO: pull from high res imagery
      }
   }

   private static Texture2d getBaseTexture() {
      return Texture2d.getTexture(Earth.class.getResourceAsStream("world.topo.bathy.200401.3x5400x2700.png"), GL.GL_RGBA);
   }
}
