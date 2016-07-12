package com.stephenwranger.thesis.geospatial;

import java.awt.Point;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
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
import com.stephenwranger.graphics.math.intersection.Ellipsoid;
import com.stephenwranger.graphics.math.intersection.Plane;
import com.stephenwranger.graphics.renderables.EllipticalGeometry;
import com.stephenwranger.graphics.renderables.Renderable;

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
   private static final double coronaHeight = 8000.0;

   private final Ellipsoid ellipsoid = WGS84.ELLIPSOID;
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
      final double distance = cameraPosition.distance(scene.getLookAt());
      double far = distance * 1.618;
      double near = far / 3000.0;

      if (distance <= closeDistance) {
         return this.getNearFarDistances(scene);
//         // TODO: far plane is near origin of earth for some reason...
//         System.out.println("\nearth distance: " + distance + " <= " + closeDistance);
//         // find intersection of ellipsoid and corners of screen
//         // get plane bisecting ellipsoid at that point
//         // use as far plane
//         final double w = scene.getSurfaceWidth();
//         final double h = scene.getSurfaceHeight();
//
//         final Tuple3d tL = CameraUtils.gluUnProject(scene, new Tuple3d(0, 0, 0.1));
//         final Tuple3d tR = CameraUtils.gluUnProject(scene, new Tuple3d(w, 0, 0.1));
//         final Tuple3d bL = CameraUtils.gluUnProject(scene, new Tuple3d(0, h, 0.1));
//
//         // TODO: require 3 of 4 aren't null
//         if ((tL != null) && (tR != null) && (bL != null)) {
//            final Vector3d vectorTL = new Vector3d(TupleMath.sub(tL, cameraPosition)).normalize();
//            final Vector3d vectorTR = new Vector3d(TupleMath.sub(tR, cameraPosition)).normalize();
//            final Vector3d vectorBL = new Vector3d(TupleMath.sub(bL, cameraPosition)).normalize();
//
//            final Tuple3d intersectTL = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorTL), 0, false);
//            final Tuple3d intersectTR = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorTR), 0, false);
//            final Tuple3d intersectBL = WGS84.getNearIntersection(new PickingRay(cameraPosition, vectorBL), 0, false);
//            
//            if ((intersectTL != null) && (intersectTR != null) && (intersectBL != null)) {
//               final Plane farPlane = new Plane(intersectTL, intersectTR, intersectBL);
//               System.out.println("tl distance: " + intersectTL.distance(cameraPosition));
//               System.out.println("tr distance: " + intersectTR.distance(cameraPosition));
//               System.out.println("bl distance: " + intersectBL.distance(cameraPosition));
//               final Tuple3d intersection = IntersectionUtils.rayPlaneIntersection(farPlane, cameraPosition, scene.getViewVector());
//               far = intersection.distance(cameraPosition);
//               
//               near = far / 3000.0;
//               System.out.println("earth near/far: " + near + " / " + far + ", sin = " + sin);// + ", plane distance: " + farPlane.getDistance());
//            }
//         }
      }

      return new double[] { near, far };
   }
   
   private double[] getNearFarDistances(final Scene scene) {
      final Vector3d view = scene.getViewVector();
      final Vector3d up = scene.getUpVector();
      final Tuple3d position = scene.getCameraPosition();

      final boolean useAltitude = false;
      final float altScale = 1.0f;

      final double coronaHeight = ((useAltitude && altScale > 1) ? altScale : 1) * this.coronaHeight;
      /*
       * Calculate the distance to a corner of the near plane (from the camera), and use ratio: (nearCorner /
       * distToSurface) = (distToNear / desiredNearPlane)
       */
      final double[] frustum = scene.getFrustumPerspective();
      final double halfNearPlaneHeight = ((frustum[3] - frustum[2]) / 2.);
      final double halfNearPlaneWidth = -((frustum[1] - frustum[0]) / 2.);
      final double distToNear = frustum[4];

      /*
       * The vector component of each of the frustum rays will be calculated by summing the scaled camera.view vector
       * (scaled to near plane distance), the scaled up vector (scaled by the halfNearPlaneHeight calculated above), and
       * a scaled 'left' vector (left is the cross of up and camera.view, scaled by halfNearPlaneWidth calculated
       * above).
       */
      final double scaledViewX = view.x * distToNear;
      final double scaledViewY = view.y * distToNear;
      final double scaledViewZ = view.z * distToNear;

      final double scaledUpX = up.x * halfNearPlaneHeight;
      final double scaledUpY = up.y * halfNearPlaneHeight;
      final double scaledUpZ = up.z * halfNearPlaneHeight;

      /*
       * (up _cross_ camera.view) halfNearPlaneWidth == scaled left vector
       */
      final double scaledLeftX = (up.y * view.z - up.z * view.y) * halfNearPlaneWidth;
      final double scaledLeftY = (up.z * view.x - up.x * view.z) * halfNearPlaneWidth;
      final double scaledLeftZ = (up.x * view.y - up.y * view.x) * halfNearPlaneWidth;

      /*
       * Get the geodetic coordinate of the camera relative to the surface
       */
      final Tuple3d cameraGeodesic = WGS84.cartesianToGeodesic(position);

      /*
       * Find the cartesian location of the nearest point on the surface to the camera (camera geodetic location with
       * altitude of 0)
       */
      final Tuple3d surfacePt = WGS84.geodesicToCartesian(new Tuple3d(cameraGeodesic.x, cameraGeodesic.y, coronaHeight));

      /*
       * Vector components used for calculation of various lengths throughout this method
       */
      final double lengthVecX = surfacePt.x - position.x;
      final double lengthVecY = surfacePt.y - position.y;
      final double lengthVecZ = surfacePt.z - position.z;

      /*
       * SurfacePt is a point on the plane tangent to the world surface (at the corona height) under the camera.
       * lengthVec contains a vector normal to this plane.
       * 
       * Find the intersection of each of the camera-corner frustum rays with this plane, and compute the distance from
       * the camera.viewing plane to each intersection point. The minimum value is the distance to the near clipping
       * plane.
       */
      final Plane plane = new Plane(new Vector3d(lengthVecX, lengthVecY, lengthVecZ), (-lengthVecX * surfacePt.x) - (lengthVecY * surfacePt.y) - (lengthVecZ * surfacePt.z));

      /*
       * All of our intersection tests will use the camera position as a point on the line.
       */
      final Tuple3d targetp = new Tuple3d();
      targetp.x = position.x;
      targetp.y = position.y;
      targetp.z = position.z;

      /*
       * Calculate the distance to the near plane by computing the intersection of each of the corner frustum rays with
       * the tangent plane, and finding the distance from each intersection with the camera.viewing plane.
       */
      double nearPlane = Double.MAX_VALUE;

      /*
       * The far clipping plane is bounded (maximally) by the greatest distance to the intersection of each of the 4
       * corner frustum from the camera with the surface.
       * 
       * If there is no intersection between a ray and the surface then we assume that the far plane is the maximum
       * possible distance (calculated above)
       */
      double farPlane = 0;

      /*
       * An upper limit for the far clipping plane, the distance to the closest point on the surface (from the camera)
       * plus the maximum possible distance before the surface is occluded by itself (e.g. due to curvature)
       */
      final double maxFar = surface.getMaximumVisibleDistance(position.x, position.y, position.z);

      /*
       * Upper-Right frustum corner ray...
       */
      final Vector3d targetv = new Vector3d();
      targetv.x = scaledViewX + scaledUpX - scaledLeftX;
      targetv.y = scaledViewY + scaledUpY - scaledLeftY;
      targetv.z = scaledViewZ + scaledUpZ - scaledLeftZ;

      Point point;
      /*
       * Near Calculation
       */
      if (cameraGeodetic[2] > coronaHeight) {
         plane.intersects(target, answer);
         point = answer.getPointIntersection();
         if (point != null) {
            /*
             * Ensure that the intersection is in front of the camera
             */
            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
               if (dist < nearPlane)
                  nearPlane = dist;
            }
         }
      }
      /*
       * Far Calculation
       */
      double[] intersect = surface.getNearIntersection(target, minAlt);
      if (intersect == null) {
         farPlane = maxFar;
      } else {
         /*
          * If there was an intersection, find the distance between the camera and the intersection point.
          */
         surface.lonLatToXYZ(surfacePt, intersect[0], intersect[1], minAlt);
         farPlane = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
      }

      /*
       * Upper-Left frustum corner ray...
       */
      target.v.x = scaledViewX + scaledUpX + scaledLeftX;
      target.v.y = scaledViewY + scaledUpY + scaledLeftY;
      target.v.z = scaledViewZ + scaledUpZ + scaledLeftZ;

      /*
       * Near Calculation
       */
      if (cameraGeodetic[2] > coronaHeight) {
         plane.intersects(target, answer);
         point = answer.getPointIntersection();
         if (point != null) {
            /*
             * Ensure that the intersection is in front of the camera
             */
            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
               if (dist < nearPlane)
                  nearPlane = dist;
            }
         }
      }
      /*
       * Far Calculation
       */
      if (farPlane < maxFar && (intersect = surface.getNearIntersection(target, minAlt)) != null) {
         surface.lonLatToXYZ(surfacePt, intersect[0], intersect[1], minAlt);
         final double newFar = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
         if (newFar > farPlane)
            farPlane = newFar;
      } else
         farPlane = maxFar;

      /*
       * Lower-Left frustum corner ray...
       */
      target.v.x = scaledViewX - scaledUpX + scaledLeftX;
      target.v.y = scaledViewY - scaledUpY + scaledLeftY;
      target.v.z = scaledViewZ - scaledUpZ + scaledLeftZ;

      /*
       * Near Calculation
       */
      if (cameraGeodetic[2] > coronaHeight) {
         plane.intersects(target, answer);
         point = answer.getPointIntersection();
         if (point != null) {
            /*
             * Ensure that the intersection is in front of the camera
             */
            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
               if (dist < nearPlane)
                  nearPlane = dist;
            }
         }
      }

      /*
       * Far calculation
       */
      if (farPlane < maxFar && (intersect = surface.getNearIntersection(target, minAlt)) != null) {
         surface.lonLatToXYZ(surfacePt, intersect[0], intersect[1], minAlt);
         final double newFar = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
         if (newFar > farPlane)
            farPlane = newFar;
      } else
         farPlane = maxFar;

      /*
       * Lower-Right frustum corner ray...
       */
      target.v.x = scaledViewX - scaledUpX - scaledLeftX;
      target.v.y = scaledViewY - scaledUpY - scaledLeftY;
      target.v.z = scaledViewZ - scaledUpZ - scaledLeftZ;

      /*
       * Near Calculation
       */
      if (cameraGeodetic[2] > coronaHeight) {
         plane.intersects(target, answer);
         point = answer.getPointIntersection();
         if (point != null) {
            /*
             * Ensure that the intersection is in front of the camera
             */
            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
               if (dist < nearPlane)
                  nearPlane = dist;
            }
         }
      }

      /*
       * Far Calculation
       */
      if (farPlane < maxFar && (intersect = surface.getNearIntersection(target, minAlt)) != null) {
         surface.lonLatToXYZ(surfacePt, intersect[0], intersect[1], minAlt);
         final double newFar = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
         if (newFar > farPlane)
            farPlane = newFar;
      } else
         farPlane = maxFar;

      /*
       * Camera is inside corona?
       */
      if (cameraGeodetic[2] < coronaHeight) {
         /* Clamp near plane to a minimum distance */
         // depthQuery.setMaxNear(3);
         // depthQuery.setMinNear(3);
         nearPlane = 0;// depthQuery.getMinNear();
      }

      if (farPlane < maxFar) {

         /*
          * When the world is at a very coarse node resolution, it is possible that the entire geometry that is in the
          * FOV is below the elliposid. If the far plane is not far enough to include the geometry, the world will never
          * refine, and will never show up in the scene. To fix this, we check the distance from the camera to a point
          * on the world geometry along the camera.view vector, and make sure that the far plane is at least as large as
          * the distance between the camera and this intersection.
          */
         target.v.x = scaledViewX;
         target.v.y = scaledViewY;
         target.v.z = scaledViewZ;
         try {
            final double[] latLonAlt = world.getLatLonAlt(target);
            if (latLonAlt == null) {
               farPlane = maxFar;
            } else {
               /*
                * Nothing will be visible beyond the world terrain, so we clamp the far clip distance
                */
               depthQuery.setMinFar(farPlane);
               depthQuery.setMaxFar(farPlane);
               depthQuery.setFarDistance(farPlane);

               depthQuery.setNearDistance(nearPlane);
               if (cameraGeodetic[2] <= coronaHeight) {
                  /*
                   * Clamp the near clipping plane to avoid 'bouncing' issues that arise with the
                   * TerrainCameraPositionConstraint
                   * when the near plane changes dramatically between successive frames.
                   */
                  depthQuery.setMinNear(coronaHeight / DepthRangeQuery.CLIP_RATIO_MAX);
                  depthQuery.setMaxNear(Math.max(nearPlane, coronaHeight / DepthRangeQuery.CLIP_RATIO_MAX));
               }

               return;
            }
         } catch (final IllegalStateException ise) {
            farPlane = maxFar;
         }
      }

      if (nearPlane < farPlane) {
         // depthQuery.setNearDistance(nearPlane);
         depthQuery.setFarDistance(farPlane);
         depthQuery.setMinFar(farPlane);
         if (cameraGeodetic[2] <= coronaHeight) {
            /*
             * Clamp the near clipping plane to avoid 'bouncing' issues that arise with the
             * TerrainCameraPositionConstraint
             * when the near plane changes dramatically between successive frames.
             */
            depthQuery.setMinNear(coronaHeight / DepthRangeQuery.CLIP_RATIO_MAX);
            depthQuery.setMaxNear(Math.max(nearPlane, coronaHeight / DepthRangeQuery.CLIP_RATIO_MAX));
         } else {
            depthQuery.setNearDistance(nearPlane);
         }
      }
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
         this.geometry = new EllipticalGeometry(gl, this.ellipsoid, WGS84.EQUATORIAL_RADIUS, 2, this::getAltitude, EarthImagery::setImagery);
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

   private double getAltitude(final double longitudeDegrees, final double latitudeDegrees) {
      return WGS84.getEllipsoidHeight(longitudeDegrees, latitudeDegrees);
   }
}
