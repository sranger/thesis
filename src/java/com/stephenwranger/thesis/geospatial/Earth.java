package com.stephenwranger.thesis.geospatial;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingSphere;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.PickingHit;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.renderables.EllipticalGeometry;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.textures.Texture2d;

/**
 * {@link Earth} is a {@link Renderable} that creates an {@link EllipticalGeometry} based on the {@link WGS84} surface 
 * projection. All images taken from The Celestial Motherlode (http://www.celestiamotherlode.net/catalog/earth.php) or
 * Visible Earth (http://visibleearth.nasa.gov/view.php?id=73580)
 * 
 * @author rangers
 *
 */
public class Earth extends Renderable {
   private static final BoundingSphere BOUNDS = new BoundingSphere(new Tuple3d(), WGS84.EQUATORIAL_RADIUS);
//   private static final Texture2d EARTH_TEXTURE = Texture2d.getTexture(Earth.class.getResourceAsStream("Earth.png"), GL2.GL_RGBA);
   private static final Texture2d EARTH_TEXTURE = Texture2d.getTexture(Earth.class.getResourceAsStream("world.topo.bathy.200401.3x5400x2700.png"), GL2.GL_RGBA);
   
   private EllipticalGeometry geometry = null;
   private boolean isWireframe = false;
   
   public Earth() {
      super(new Tuple3d(), new Quat4d());
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      if(this.geometry == null) {
         this.geometry = new EllipticalGeometry(gl, WGS84.EQUATORIAL_RADIUS, 3, this::getAltitude);
         this.geometry.setTexture(EARTH_TEXTURE);
      }
      
      gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, (isWireframe) ? GL2.GL_LINE : GL2.GL_FILL);
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
   public double[] getNearFar(final Scene scene) {
      final double fovy = scene.getFOV();
      final double aspect = scene.getSurfaceWidth() / scene.getSurfaceHeight();
      final double fovx = fovy * aspect;
      
      final double sin = Math.sin(Math.min(fovx, fovy) * 0.5);
      final double closeDistance = (WGS84.EQUATORIAL_RADIUS / sin);
      
      final Tuple3d cameraPosition = scene.getCameraPosition();
      final Vector3d camVector = new Vector3d(cameraPosition);
      final double distance = camVector.length();
      
      if(distance <= closeDistance) {
         return new double[] { 0.1, distance };
      } else {
         return new double[] { distance / 3000.0, distance };
      }
   }
   
   @Override
   public PickingHit getIntersection(final PickingRay ray) {
      return (this.geometry == null) ? PickingRay.NO_HIT : this.geometry.getIntersection(ray);
   }
   
   public void setWireframe(final boolean isWireframe) {
      this.isWireframe = isWireframe;
   }
   
   public boolean isWireframe() {
      return this.isWireframe;
   }
}
