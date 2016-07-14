package com.stephenwranger.thesis.geospatial;

import java.util.HashMap;
import java.util.Map;

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
import com.stephenwranger.graphics.math.intersection.Ellipsoid;
import com.stephenwranger.graphics.renderables.EllipticalGeometry;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.MathUtils;
import com.stephenwranger.graphics.utils.shader.ShaderKernel;
import com.stephenwranger.graphics.utils.shader.ShaderProgram;
import com.stephenwranger.graphics.utils.shader.ShaderStage;

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
   private static final double         MAX_DEPTH_RATIO   = 3000.0;

   private final ShaderProgram         shader;
   private final Ellipsoid             ellipsoid         = WGS84.ELLIPSOID;
   private EllipticalGeometry          geometry          = null;
   private boolean                     isWireframe       = false;
   private boolean                     isLightingEnabled = false;
   private double                      loadFactor        = 0.75;

   public Earth() {
      super(new Tuple3d(), new Quat4d());
      
      final Map<String, Integer> requestedAttributeLocations = new HashMap<>();
      final ShaderKernel vert = new ShaderKernel("earth.vert", Earth.class.getResourceAsStream("earth.vert"), ShaderStage.VERTEX);
      final ShaderKernel frag = new ShaderKernel("earth.frag", Earth.class.getResourceAsStream("earth.frag"), ShaderStage.FRAGMENT);
      this.shader = new ShaderProgram("Earth Texture Shader", requestedAttributeLocations, vert, frag);
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
      double near = far / MAX_DEPTH_RATIO;

      if (distance <= closeDistance) {
         final double[] nearFar = this.getNearFarDistances(scene);
         
         if(nearFar[0] != Double.MAX_VALUE && nearFar[1] != -Double.MAX_VALUE) {
            return nearFar;
         }
      }

      return new double[] { near, far };
   }
   
   private double[] getNearFarDistances(final Scene scene) {
      final Tuple3d cameraOrigin = scene.getCameraPosition();
      final double maxFar = WGS84.EQUATORIAL_RADIUS;
      final double w = scene.getWidth();
      final double h = scene.getHeight();
      final Tuple3d[] screenCorners = new Tuple3d[] {
            new Tuple3d(0, 0, 0.25),
            new Tuple3d(w, 0, 0.25),
            new Tuple3d(w, h, 0.25),
            new Tuple3d(0, h, 0.25)
      };
      
      double far = cameraOrigin.distance(scene.getLookAt()) * 1.618;
      double near = far / MAX_DEPTH_RATIO;
      
      for(final Tuple3d screenCorner : screenCorners) {
         final Tuple3d worldXyz = CameraUtils.gluUnProject(scene, screenCorner);
         
         if(worldXyz != null) {
            final Vector3d worldVector = new Vector3d(worldXyz);
            worldVector.subtract(cameraOrigin);
            worldVector.normalize();
            
            final PickingRay ray = new PickingRay(cameraOrigin, worldVector);
            double distance = Double.NaN;
            
            if(this.geometry == null) {
               final double[] hits = this.ellipsoid.getIntersection(ray);
               
               if(hits != null && hits.length > 0) {
                  distance = MathUtils.getMin(hits);
               } else {
                  distance = maxFar;
               }
            } else {
               final PickingHit hit =  this.geometry.getIntersection(ray);
               
               if(hit != PickingRay.NO_HIT) {
                  distance = hit.getDistance();
               } else {
                  distance = maxFar;
               }
            }
   
            if(Double.isFinite(distance)) {
               near = Math.max(0.01, Math.min(near, distance - MAX_DEPTH_RATIO));
               far = Math.max(far, distance + MAX_DEPTH_RATIO);
            }
         }
      }
      
      if(near > far / MAX_DEPTH_RATIO) {
         near = far / MAX_DEPTH_RATIO;
      }
      
      return new double[] { near, far };
   }
   
//   private double[] getNearFarDistances(final Scene scene) {
//      final Vector3d view = scene.getViewVector();
//      final Vector3d up = scene.getUpVector();
//      final Tuple3d position = scene.getCameraPosition();
//
//      final double coronaHeight = Earth.CORONA_HEIGHT;
//
//      /*
//       * Calculate the distance to a corner of the near plane (from the camera), and use ratio: (nearCorner /
//       * distToSurface) = (distToNear / desiredNearPlane)
//       */
//      final double[] frustum = scene.getFrustumPerspective();
//      final double halfNearPlaneHeight = ((frustum[3] - frustum[2]) / 2.);
//      final double halfNearPlaneWidth = -((frustum[1] - frustum[0]) / 2.);
//      final double distToNear = frustum[4];
////      System.out.println("l: " + frustum[CameraUtils.LEFT_PLANE]);
////      System.out.println("r: " + frustum[CameraUtils.RIGHT_PLANE]);
////      System.out.println("b: " + frustum[CameraUtils.BOTTOM_PLANE]);
////      System.out.println("t: " + frustum[CameraUtils.TOP_PLANE]);
////      System.out.println("n: " + frustum[CameraUtils.NEAR_PLANE]);
////      System.out.println("f: " + frustum[CameraUtils.FAR_PLANE]);
////      System.out.println("half near dim: " + halfNearPlaneWidth + ", " + halfNearPlaneHeight);
////      System.out.println("dist to near: " + distToNear);
//
//      /*
//       * The vector component of each of the frustum rays will be calculated by summing the scaled camera.view vector
//       * (scaled to near plane distance), the scaled up vector (scaled by the halfNearPlaneHeight calculated above), and
//       * a scaled 'left' vector (left is the cross of up and camera.view, scaled by halfNearPlaneWidth calculated
//       * above).
//       */
//      final double scaledViewX = view.x * distToNear;
//      final double scaledViewY = view.y * distToNear;
//      final double scaledViewZ = view.z * distToNear;
//
//      final double scaledUpX = up.x * halfNearPlaneHeight;
//      final double scaledUpY = up.y * halfNearPlaneHeight;
//      final double scaledUpZ = up.z * halfNearPlaneHeight;
//
//      /*
//       * (up _cross_ camera.view) halfNearPlaneWidth == scaled left vector
//       */
//      final double scaledLeftX = (up.y * view.z - up.z * view.y) * halfNearPlaneWidth;
//      final double scaledLeftY = (up.z * view.x - up.x * view.z) * halfNearPlaneWidth;
//      final double scaledLeftZ = (up.x * view.y - up.y * view.x) * halfNearPlaneWidth;
//
//      /*
//       * Get the geodetic coordinate of the camera relative to the surface
//       */
//      final Tuple3d cameraGeodesic = WGS84.cartesianToGeodesic(position);
//
//      /*
//       * Find the cartesian location of the nearest point on the surface to the camera (camera geodetic location with
//       * altitude of 0)
//       */
//      Tuple3d surfacePt = WGS84.geodesicToCartesian(new Tuple3d(cameraGeodesic.x, cameraGeodesic.y, coronaHeight));
//
//      /*
//       * Vector components used for calculation of various lengths throughout this method
//       */
//      final double lengthVecX = surfacePt.x - position.x;
//      final double lengthVecY = surfacePt.y - position.y;
//      final double lengthVecZ = surfacePt.z - position.z;
//
//      /*
//       * SurfacePt is a point on the plane tangent to the world surface (at the corona height) under the camera.
//       * lengthVec contains a vector normal to this plane.
//       * 
//       * Find the intersection of each of the camera-corner frustum rays with this plane, and compute the distance from
//       * the camera.viewing plane to each intersection point. The minimum value is the distance to the near clipping
//       * plane.
//       */
//      final Plane plane = new Plane(new Vector3d(lengthVecX, lengthVecY, lengthVecZ), (-lengthVecX * surfacePt.x) - (lengthVecY * surfacePt.y) - (lengthVecZ * surfacePt.z));
//
//      /*
//       * All of our intersection tests will use the camera position as a point on the line.
//       */
//      final Tuple3d targetp = new Tuple3d();
//      targetp.x = position.x;
//      targetp.y = position.y;
//      targetp.z = position.z;
//
//      /*
//       * Calculate the distance to the near plane by computing the intersection of each of the corner frustum rays with
//       * the tangent plane, and finding the distance from each intersection with the camera.viewing plane.
//       */
//      double nearPlane = Double.MAX_VALUE;
//      System.out.println("\n");
//      System.out.println("1. nearPlane: " + nearPlane);
//
//      /*
//       * The far clipping plane is bounded (maximally) by the greatest distance to the intersection of each of the 4
//       * corner frustum from the camera with the surface.
//       * 
//       * If there is no intersection between a ray and the surface then we assume that the far plane is the maximum
//       * possible distance (calculated above)
//       */
//      double farPlane = 0;
//      System.out.println("1. farPlane: " + farPlane);
//
//      /*
//       * An upper limit for the far clipping plane, the distance to the closest point on the surface (from the camera)
//       * plus the maximum possible distance before the surface is occluded by itself (e.g. due to curvature)
//       */
//      final double maxFar = WGS84.getMaximumVisibleDistance(position.x, position.y, position.z);
//
//      /*
//       * Upper-Right frustum corner ray...
//       */
//      final Vector3d targetv = new Vector3d();
//      targetv.x = scaledViewX + scaledUpX - scaledLeftX;
//      targetv.y = scaledViewY + scaledUpY - scaledLeftY;
//      targetv.z = scaledViewZ + scaledUpZ - scaledLeftZ;
//
//      Tuple3d point;
//      /*
//       * Near Calculation
//       */
//      if (cameraGeodesic.z > coronaHeight) {
//         point = IntersectionUtils.rayPlaneIntersection(plane, targetp, targetv);
//
//         if (point != null) {
//            /*
//             * Ensure that the intersection is in front of the camera
//             */
//            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
//               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
//               
//               if (dist < nearPlane) {
//                  nearPlane = dist;
//                  System.out.println("2. nearPlane: " + nearPlane);
//               }
//            }
//         }
//      }
//      /*
//       * Far Calculation
//       */
//      Tuple3d intersect = WGS84.getNearIntersection(new PickingRay(targetp, targetv), MINIMUM_ALTITUDE, true);
//      if (intersect == null) {
//         farPlane = maxFar;
//         System.out.println("2a. farPlane: " + farPlane);
//      } else {
//         /*
//          * If there was an intersection, find the distance between the camera and the intersection point.
//          */
//         surfacePt = WGS84.geodesicToCartesian(new Tuple3d(intersect.x, intersect.y, MINIMUM_ALTITUDE));
//         farPlane = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
//         System.out.println("2b. farPlane: " + farPlane);
//      }
//
//      /*
//       * Upper-Left frustum corner ray...
//       */
//      targetv.x = scaledViewX + scaledUpX + scaledLeftX;
//      targetv.y = scaledViewY + scaledUpY + scaledLeftY;
//      targetv.z = scaledViewZ + scaledUpZ + scaledLeftZ;
//
//      /*
//       * Near Calculation
//       */
//      if (cameraGeodesic.z > coronaHeight) {
//         point = IntersectionUtils.rayPlaneIntersection(plane, targetp, targetv);
//         
//         if (point != null) {
//            /*
//             * Ensure that the intersection is in front of the camera
//             */
//            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
//               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
//               
//               if (dist < nearPlane) {
//                  nearPlane = dist;
//                  System.out.println("3. nearPlane: " + nearPlane);
//               }
//            }
//         }
//      }
//      /*
//       * Far Calculation
//       */
//      if (farPlane < maxFar && (intersect = WGS84.getNearIntersection(new PickingRay(targetp, targetv), MINIMUM_ALTITUDE, true)) != null) {
//         surfacePt = WGS84.geodesicToCartesian(new Tuple3d(intersect.x, intersect.y, MINIMUM_ALTITUDE));
//         final double newFar = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
//         
//         if (newFar > farPlane) {
//            farPlane = newFar;
//            System.out.println("3a. farPlane: " + farPlane);
//         }
//      } else {
//         farPlane = maxFar;
//         System.out.println("3b. farPlane: " + farPlane);
//      }
//
//      /*
//       * Lower-Left frustum corner ray...
//       */
//      targetv.x = scaledViewX - scaledUpX + scaledLeftX;
//      targetv.y = scaledViewY - scaledUpY + scaledLeftY;
//      targetv.z = scaledViewZ - scaledUpZ + scaledLeftZ;
//
//      /*
//       * Near Calculation
//       */
//      if (cameraGeodesic.z > coronaHeight) {
//         point = IntersectionUtils.rayPlaneIntersection(plane, targetp, targetv);
//         
//         if (point != null) {
//            /*
//             * Ensure that the intersection is in front of the camera
//             */
//            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
//               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
//               
//               if (dist < nearPlane) {
//                  nearPlane = dist;
//                  System.out.println("4. nearPlane: " + nearPlane);
//               }
//            }
//         }
//      }
//
//      /*
//       * Far calculation
//       */
//      if (farPlane < maxFar && (intersect = WGS84.getNearIntersection(new PickingRay(targetp, targetv), MINIMUM_ALTITUDE, true)) != null) {
//         surfacePt = WGS84.geodesicToCartesian(new Tuple3d(intersect.x, intersect.y, MINIMUM_ALTITUDE));
//         final double newFar = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
//         
//         if (newFar > farPlane) {
//            farPlane = newFar;
//            System.out.println("4a. farPlane: " + farPlane);
//         }
//      } else {
//         farPlane = maxFar;
//         System.out.println("4b. farPlane: " + farPlane);
//      }
//
//      /*
//       * Lower-Right frustum corner ray...
//       */
//      targetv.x = scaledViewX - scaledUpX - scaledLeftX;
//      targetv.y = scaledViewY - scaledUpY - scaledLeftY;
//      targetv.z = scaledViewZ - scaledUpZ - scaledLeftZ;
//
//      /*
//       * Near Calculation
//       */
//      if (cameraGeodesic.z > coronaHeight) {
//         point = IntersectionUtils.rayPlaneIntersection(plane, targetp, targetv);
//         
//         if (point != null) {
//            /*
//             * Ensure that the intersection is in front of the camera
//             */
//            if ((point.x - position.x) * view.x + (point.y - position.y) * view.y + (point.z - position.z) * view.z >= 0) {
//               final double dist = Plane.distanceToPlane(view, position, point.x, point.y, point.z);
//               
//               if (dist < nearPlane) {
//                  nearPlane = dist;
//                  System.out.println("5. nearPlane: " + nearPlane);
//               }
//            }
//         }
//      }
//
//      /*
//       * Far Calculation
//       */
//      if (farPlane < maxFar && (intersect = WGS84.getNearIntersection(new PickingRay(targetp, targetv), MINIMUM_ALTITUDE, true)) != null) {
//         surfacePt = WGS84.geodesicToCartesian(new Tuple3d(intersect.x, intersect.y, MINIMUM_ALTITUDE));
//         final double newFar = Plane.distanceToPlane(view, position, surfacePt.x, surfacePt.y, surfacePt.z);
//         
//         if (newFar > farPlane) {
//            farPlane = newFar;
//            System.out.println("5a. farPlane: " + farPlane);
//         }
//      } else {
//         farPlane = maxFar;
//         System.out.println("5b. farPlane: " + farPlane);
//      }
//
//      /*
//       * Camera is inside corona?
//       */
//      if (cameraGeodesic.z < coronaHeight) {
//         /* Clamp near plane to a minimum distance */
//         nearPlane = 0.01;
//         System.out.println("6. nearPlane: " + nearPlane);
//      }
//
//      if (farPlane < maxFar) {
//         /*
//          * When the world is at a very coarse node resolution, it is possible that the entire geometry that is in the
//          * FOV is below the elliposid. If the far plane is not far enough to include the geometry, the world will never
//          * refine, and will never show up in the scene. To fix this, we check the distance from the camera to a point
//          * on the world geometry along the camera.view vector, and make sure that the far plane is at least as large as
//          * the distance between the camera and this intersection.
//          */
//         targetv.x = scaledViewX;
//         targetv.y = scaledViewY;
//         targetv.z = scaledViewZ;
//         
//         try {
//            final double[] intersection = WGS84.ELLIPSOID.getIntersection(new PickingRay(targetp, targetv));
//            if (intersection == null) {
//               farPlane = maxFar;
//               System.out.println("6. farPlane: " + farPlane);
//            }
//         } catch (final IllegalStateException ise) {
//            farPlane = maxFar;
//            System.out.println("6ex. farPlane: " + farPlane);
//         }
//      } else if (nearPlane < farPlane) {
//         nearPlane = Math.max(nearPlane, coronaHeight / 3000.0);
//         System.out.println("7. nearPlane: " + nearPlane);
//      }
//      
//      if(nearPlane > farPlane / 3000.0) {
//         nearPlane = farPlane / 3000.0;
//         System.out.println("8. nearPlane: " + nearPlane);
//      }
//
//      System.out.println("final: " + nearPlane + ", " + farPlane);
//      return new double[] { nearPlane, farPlane };
//   }

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

      gl.glPushAttrib(GL2.GL_POLYGON_BIT);
      gl.glPolygonMode(GL.GL_FRONT_AND_BACK, (this.isWireframe) ? GL2GL3.GL_LINE : GL2GL3.GL_FILL);
      
      this.shader.enable(gl);
      this.geometry.render(gl, glu, glDrawable, scene);
      this.shader.disable(gl);
      gl.glPopAttrib();
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
