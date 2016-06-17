package com.stephenwranger.thesis.geospatial;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingSphere;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.PickingHit;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.EllipticalGeometry;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.textures.Texture2d;

/**
 * {@link Earth} is a {@link Renderable} that creates an {@link EllipticalGeometry} based on the {@link WGS84} surface 
 * projection. All images taken from The Celestial Motherlode (http://www.celestiamotherlode.net/catalog/earth.php)
 * 
 * @author rangers
 *
 */
public class Earth extends Renderable {
   private static final BoundingSphere BOUNDS = new BoundingSphere(new Tuple3d(), WGS84.EQUATORIAL_RADIUS);
   private static final Texture2d EARTH_1K = Texture2d.getTexture(Earth.class.getResourceAsStream("Earth.png"), GL2.GL_RGBA);
   
   private EllipticalGeometry geometry = null;
   
   public Earth() {
      super(new Tuple3d(), new Quat4d());
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      if(this.geometry == null) {
         this.geometry = new EllipticalGeometry(gl, WGS84.EQUATORIAL_RADIUS, 2, this::getAltitude);
         this.geometry.setTexture(EARTH_1K);
      }
      
      gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
      geometry.render(gl, glu, glDrawable, scene);
   }
   
   private double getAltitude(final double azimuth, final double elevation) {
      final double lonDeg = Math.toDegrees(azimuth);
      final double latDeg = Math.toDegrees(elevation);
      
      return WGS84.getAltitude(lonDeg, latDeg);
   }

   @Override
   public BoundingVolume getBoundingVolume() {
      return Earth.BOUNDS;
   }
   
   @Override
   public PickingHit getIntersection(final PickingRay ray) {
      return (this.geometry == null) ? PickingRay.NO_HIT : this.geometry.getIntersection(ray);
   }
}
