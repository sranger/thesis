package com.stephenwranger.thesis.geospatial;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingSphere;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.PickingHit;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Plane;
import com.stephenwranger.graphics.renderables.EllipticalGeometry;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.TupleMath;

/**
 * {@link Earth} is a {@link Renderable} that creates an {@link EllipticalGeometry} based on the {@link WGS84} surface
 * projection. All images taken from The Celestial Motherlode (http://www.celestiamotherlode.net/catalog/earth.php) or
 * Visible Earth (http://visibleearth.nasa.gov/view.php?id=73580)
 *
 * @author rangers
 *
 */
public class Earth extends Renderable {
   private static final BoundingSphere BOUNDS            = new BoundingSphere(new Tuple3d(), WGS84.EQUATORIAL_RADIUS);

   private EllipticalGeometry          geometry          = null;
   private boolean                     isWireframe       = false;
   private boolean                     isLightingEnabled = false;
   private double                      loadFactor        = 0.75;

   public Earth() {
      super(new Tuple3d(), new Quat4d());
   }

   @Override
   public BoundingVolume getBoundingVolume() {
      return Earth.BOUNDS;
   }

   @Override
   public PickingHit getIntersection(final PickingRay ray) {
      return (this.geometry == null) ? PickingRay.NO_HIT : this.geometry.getIntersection(ray);
   }

   public double getLoadFactor() {
      return this.loadFactor;
   }

   @Override
   public double[] getNearFar(final Scene scene) {
      final double fovy = scene.getFOV();
      final double aspect = scene.getSurfaceWidth() / scene.getSurfaceHeight();
      final double fovx = fovy * aspect;

      final double sin = Math.sin(Math.min(fovx, fovy));
      final double closeDistance = (WGS84.EQUATORIAL_RADIUS / sin);

      final Tuple3d cameraPosition = scene.getCameraPosition();
      // distance from camera to center of earth
      //      final double distance = TupleMath.length(TupleMath.add(cameraPosition, scene.getOrigin()));
      final double distance = cameraPosition.distance(scene.getLookAt()) + WGS84.EQUATORIAL_RADIUS;
      double near = distance / 3000.0;
      double far = distance;

      if (distance <= closeDistance) {
         // find intersection of ellipsoid and corners of screen
         // get plane bisecting ellipsoid at that point
         // use as far plane
         final double w = scene.getSurfaceWidth();
         final double h = scene.getSurfaceHeight();

         final Tuple3d tL = CameraUtils.gluUnProject(scene, new Tuple3d(0, 0, 0.1));
         final Tuple3d tR = CameraUtils.gluUnProject(scene, new Tuple3d(w, 0, 0.1));
         final Tuple3d bR = CameraUtils.gluUnProject(scene, new Tuple3d(w, h, 0.1));
         final Tuple3d bL = CameraUtils.gluUnProject(scene, new Tuple3d(0, h, 0.1));

         // TODO: require 3 of 4 aren't null
         if ((tL != null) && (tR != null) && (bR != null) && (bL != null)) {
            final Vector3d vectorTL = new Vector3d(TupleMath.sub(tL, cameraPosition)).normalize();
            final Vector3d vectorTR = new Vector3d(TupleMath.sub(tR, cameraPosition)).normalize();
            //            final Vector3d vectorBR = new Vector3d(TupleMath.sub(bR, cameraPosition)).normalize();
            final Vector3d vectorBL = new Vector3d(TupleMath.sub(bL, cameraPosition)).normalize();

            final Tuple3d intersectTL = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorTL), 0, false);
            final Tuple3d intersectTR = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorTR), 0, false);
            //            final Tuple3d intersectBR = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorBR), 0, false);
            final Tuple3d intersectBL = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorBL), 0, false);

            if ((intersectTL != null) && (intersectTR != null) && (intersectBL != null)) {
               final Plane farPlane = new Plane(intersectTL, intersectTR, intersectBL);
               //               far = Math.min(cameraPosition.distance(scene.getLookAt()) * 2.0, farPlane.distanceToPoint(cameraPosition));
               far = farPlane.distanceToPoint(cameraPosition);
               near = far / 3000.0;
            }
         }
      }

      return new double[] { near, far };
   }

   public boolean isLightingEnabled() {
      return this.isLightingEnabled;
   }

   public boolean isWireframe() {
      return this.isWireframe;
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      if (this.geometry == null) {
         this.geometry = new EllipticalGeometry(gl, WGS84.EQUATORIAL_RADIUS, 2, this::getAltitude, EarthImagery::setImagery);
         this.geometry.setLightingEnabled(this.isLightingEnabled);
         this.geometry.setLoadFactor(this.loadFactor);
      }

      gl.glPolygonMode(GL.GL_FRONT_AND_BACK, (this.isWireframe) ? GL2GL3.GL_LINE : GL2GL3.GL_FILL);
      this.geometry.render(gl, glu, glDrawable, scene);
   }

   public void setLightingEnabled(final boolean isLightingEnabled) {
      this.isLightingEnabled = isLightingEnabled;

      if (this.geometry != null) {
         this.geometry.setLightingEnabled(this.isLightingEnabled);
      }
   }

   public void setLoadFactor(final double loadFactor) {
      this.loadFactor = loadFactor;

      if (this.geometry != null) {
         this.geometry.setLoadFactor(this.loadFactor);
      }
   }

   public void setWireframe(final boolean isWireframe) {
      this.isWireframe = isWireframe;
   }

   private double getAltitude(final double azimuth, final double elevation) {
      final double lonDeg = Math.toDegrees(azimuth);
      final double latDeg = Math.toDegrees(elevation);

      return WGS84.getEllipsoidHeight(lonDeg, latDeg);
   }
}
