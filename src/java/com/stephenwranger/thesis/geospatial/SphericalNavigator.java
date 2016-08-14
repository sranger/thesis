package com.stephenwranger.thesis.geospatial;

import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.DecimalFormat;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.PickingHit;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.SphericalCoordinate;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.renderables.PreRenderable;
import com.stephenwranger.graphics.renderables.TextRenderable;
import com.stephenwranger.graphics.utils.MathUtils;

public class SphericalNavigator implements PreRenderable, MouseListener, MouseMotionListener, MouseWheelListener {
   private enum EventType {
      MOUSE_DRAG_LEFT, MOUSE_DRAG_RIGHT, MOUSE_CLICK;
   }

   private static final Vector3d     RIGHT_VECTOR     = new Vector3d(0, 1, 0);

   private static final Vector3d     UP_VECTOR        = new Vector3d(0, 0, -1);

   private final DecimalFormat       formatter        = new DecimalFormat("0.000");
   private final Scene               scene;
   private final TextRenderable      textRenderer;
   private final Tuple3d             anchor           = WGS84.geodesicToCartesian(new Tuple3d());
   private final SphericalCoordinate cameraCoordinate = new SphericalCoordinate(0, 0, 2e7);

   private Tuple3d                   mouseXyz         = null;
   private Point                     previousEvent    = null;
   private Point                     currentEvent     = null;
   private EventType                 eventType        = null;
   private boolean                   update           = true;
   private Earth                     earth            = null;

   public SphericalNavigator(final Scene scene) {
      this.scene = scene;

      scene.addMouseListener(this);
      scene.addMouseMotionListener(this);
      scene.addMouseWheelListener(this);

      this.textRenderer = new TextRenderable(new Font("SansSerif", Font.PLAIN, 32));
      this.scene.addRenderableOrthographic(this.textRenderer);
   }

   public Tuple3d getAnchor() {
      return new Tuple3d(this.anchor);
   }

   public SphericalCoordinate getCameraCoordinate() {
      return new SphericalCoordinate(this.cameraCoordinate);
   }

   @Override
   public synchronized void mouseClicked(final MouseEvent event) {
      if (!event.isControlDown()) {
         if (event.getClickCount() == 2) {
            this.mouseXyz = null;
            this.previousEvent = null;
            this.currentEvent = event.getPoint();
            this.eventType = EventType.MOUSE_CLICK;

            this.update = true;
         }
      } else {
         this.mouseReleased(null);
      }
   }

   @Override
   public synchronized void mouseDragged(final MouseEvent event) {
      if (!event.isControlDown()) {
         if (SwingUtilities.isLeftMouseButton(event)) {
            this.currentEvent = event.getPoint();
            this.eventType = EventType.MOUSE_DRAG_LEFT;
         } else if (SwingUtilities.isRightMouseButton(event) && (this.previousEvent != null)) {
            this.mouseXyz = null;
            this.currentEvent = event.getPoint();
            this.eventType = EventType.MOUSE_DRAG_RIGHT;
         } else {
            this.mouseXyz = null;
            this.previousEvent = null;
            this.currentEvent = null;
            this.eventType = null;
         }

         this.update = true;
      } else {
         this.mouseReleased(null);
      }
   }

   @Override
   public void mouseEntered(final MouseEvent event) {
      // nothing atm

   }

   @Override
   public void mouseExited(final MouseEvent event) {
      // nothing atm

   }

   @Override
   public synchronized void mouseMoved(final MouseEvent event) {
      if (!event.isControlDown()) {
         this.updateText(event);
      } else {
         this.mouseReleased(null);
      }
   }

   @Override
   public synchronized void mousePressed(final MouseEvent event) {
      if (!event.isControlDown()) {
         this.mouseXyz = null;
         this.previousEvent = event.getPoint();
         this.currentEvent = null;
         this.eventType = null;
      } else {
         this.mouseReleased(null);
      }
   }

   @Override
   public synchronized void mouseReleased(final MouseEvent event) {
      this.mouseXyz = null;
      this.previousEvent = null;
      this.currentEvent = null;
      this.eventType = null;
   }

   @Override
   public void mouseWheelMoved(final MouseWheelEvent event) {
      final double count = event.getPreciseWheelRotation();

      this.cameraCoordinate.update(0, 0, this.cameraCoordinate.getRange() * (0.1 * count));
      this.update = true;
   }

   /**
    * Moves the {@link SphericalNavigator}'s anchor to the given geodetic coordinate and the spherical camera coordinate
    * to the given azimuth, elevation, and range.
    *
    * @param longitudeDegrees
    * @param latitudeDegrees
    * @param altitudeMeters
    * @param azimuthRadians
    * @param elevationRadians
    * @param rangeMeters
    */
   public synchronized void moveTo(final double longitudeDegrees, final double latitudeDegrees, final double altitudeMeters, final double azimuthRadians, final double elevationRadians, final double rangeMeters) {
      this.anchor.set(WGS84.geodesicToCartesian(new Tuple3d(longitudeDegrees, latitudeDegrees, altitudeMeters)));
      this.cameraCoordinate.setAzimuth(azimuthRadians);
      this.cameraCoordinate.setElevation(elevationRadians);
      this.cameraCoordinate.setRange(rangeMeters);
      this.update = true;
   }

   @Override
   public synchronized void preRender(final GL2 gl, final GLU glu, final GLDrawable drawable, final Scene scene) {
      if (this.update) {
         if (this.eventType != null) {
            switch (this.eventType) {
               case MOUSE_CLICK:
                  this.click(gl, scene);
                  break;
               case MOUSE_DRAG_LEFT:
                  this.dragLeft(gl, scene);
                  break;
               case MOUSE_DRAG_RIGHT:
                  this.dragRight(gl, scene);
                  break;
            }
         }

         // get geodetic coordinate of anchor
         final Tuple3d lonLatAlt = WGS84.cartesianToGeodesic(this.anchor);
         //         System.out.println("lla: " + lonLatAlt);
         // get orientation of anchor in relation to local surface reference frame (up is surface normal at anchor)
         final Quat4d orientation = WGS84.getOrientation(lonLatAlt.x, lonLatAlt.y);
         final Quat4d sphericalRotation = this.cameraCoordinate.getOrientation();

         // create rotation that converts local reference vector to point towards local camera position
         final Vector3d toCamera = new Vector3d(SphericalNavigator.RIGHT_VECTOR);
         sphericalRotation.mult(toCamera);
         orientation.mult(toCamera);
         //         System.out.println("range: " + this.cameraCoordinate.getRange());
         toCamera.scale(this.cameraCoordinate.getRange());

         final Tuple3d cameraPosition = new Tuple3d(toCamera);
         cameraPosition.add(this.anchor);

         final Vector3d up = new Vector3d(SphericalNavigator.UP_VECTOR);
         sphericalRotation.mult(up);
         orientation.mult(up);

         //         // make sure the camera doesn't go below the earth
         //         if(this.earth != null) {
         //            final PickingHit toEarth = this.earth.getIntersection(new PickingRay(cameraPosition, new Vector3d(-up.x, -up.y, -up.z)));
         //
         //            if(toEarth != null) {
         //               final Tuple3d intersection = toEarth.getHitLocation();
         //               final Tuple3d cameraGeodesic = WGS84.cartesianToGeodesic(cameraPosition);
         //               final Tuple3d intersectionGeodesic = WGS84.cartesianToGeodesic(intersection);
         //
         //               if(cameraGeodesic.z < intersectionGeodesic.z) {
         //                  cameraGeodesic.z = intersectionGeodesic.z + 5;
         //
         //                  cameraPosition.set(WGS84.geodesicToCartesian(cameraGeodesic));
         //               }
         //            }
         //         }

         if (MathUtils.isFinite(cameraPosition) && MathUtils.isFinite(this.anchor) && MathUtils.isFinite(up)) {
            //            System.out.println("cam to origin:    " + cameraPosition.distance(scene.getOrigin()) + ", " + cameraPosition);
            //            System.out.println("anchor to origin: " + this.anchor.distance(scene.getOrigin()) + ", " + this.anchor);
            this.scene.setCameraPosition(cameraPosition, this.anchor, up);
         } else {
            System.err.println("scene position not updating; invalid");
            System.err.println("cam pos:    " + cameraPosition + ", " + MathUtils.isFinite(cameraPosition));
            System.err.println("anchor pos: " + this.anchor + ", " + MathUtils.isFinite(this.anchor));
            System.err.println("up vector:  " + up + ", " + MathUtils.isFinite(up));
         }
         this.update = false;
      }
   }

   public void removeListeners() {
      this.scene.removeMouseListener(this);
      this.scene.removeMouseMotionListener(this);
      this.scene.removeMouseWheelListener(this);
   }

   public void setEarth(final Earth earth) {
      this.earth = earth;
   }

   public synchronized void setViewingVolume(final BoundingVolume boundingVolume) {
      final Tuple3d center = boundingVolume.getCenter();
      final Tuple3d centerLonLatAlt = WGS84.cartesianToGeodesic(center);
      centerLonLatAlt.z = 0.0;
      this.anchor.set(WGS84.geodesicToCartesian(centerLonLatAlt));
      this.cameraCoordinate.setRange(boundingVolume.getSpannedDistance(null) / 2.0);
      this.update = true;
   }

   private void click(final GL2 gl, final Scene scene) {
      if (this.currentEvent != null) {
         final Tuple3d xyz = this.getIntersection(this.currentEvent);

         if (xyz != null) {
            this.anchor.set(xyz);

            //            if (this.earth != null) {
            //               final Tuple3d lla = WGS84.cartesianToGeodesic(this.anchor);
            //               final double elevation = DigitalElevationUtils.getElevation(lla.x, lla.y);
            //               lla.z = elevation;
            //               this.anchor.set(WGS84.geodesicToCartesian(lla));
            //            }
         }
      }
   }

   private void dragLeft(final GL2 gl, final Scene scene) {
      if ((this.previousEvent != null) && (this.currentEvent != null)) {
         final Tuple3d fromXyz = (this.mouseXyz == null) ? this.getIntersection(this.previousEvent) : new Tuple3d(this.mouseXyz);
         final Tuple3d toXyz = this.getIntersection(this.currentEvent);

         if ((fromXyz != null) && (toXyz != null)) {
            if (this.mouseXyz == null) {
               this.mouseXyz = new Tuple3d(fromXyz);
            }

            final Tuple3d cameraPos = scene.getCameraPosition();
            final Vector3d fromVector = Vector3d.getVector(cameraPos, fromXyz, true);
            final Vector3d toVector = Vector3d.getVector(cameraPos, toXyz, true);

            final Vector3d axis = new Vector3d();
            axis.cross(fromVector, toVector);
            final double angleDegrees = fromVector.angleDegrees(toVector);
            // negative as we're rotating from camera point of view
            final Quat4d rotation = new Quat4d(axis, -angleDegrees);

            final Vector3d toAnchor = Vector3d.getVector(cameraPos, this.anchor, true);
            rotation.rotateVector(toAnchor);
            final PickingRay ray = new PickingRay(cameraPos, toAnchor);
            Tuple3d newAnchor = null;

            if (this.earth == null) {
               newAnchor = WGS84.getNearIntersection(ray, 0, false);
            } else {
               final PickingHit hit = this.earth.getIntersection(ray);

               if (hit != PickingRay.NO_HIT) {
                  newAnchor = hit.getHitLocation();
               }
            }

            if (newAnchor != null) {
               this.anchor.set(newAnchor);
               this.cameraCoordinate.setRange(this.anchor.distance(cameraPos));
            }
         }
      }
   }

   private void dragRight(final GL2 gl, final Scene scene) {
      if ((this.previousEvent != null) && (this.currentEvent != null)) {
         final int x = this.currentEvent.x - this.previousEvent.x;
         final int y = this.currentEvent.y - this.previousEvent.y;

         if ((x != 0) || (y != 0)) {
            final double azimuth = ((x / (double) scene.getWidth()) * Math.PI);
            final double elevation = ((y / (double) scene.getHeight()) * Math.PI);

            this.cameraCoordinate.update(azimuth, elevation, 0);
         }

         this.previousEvent = this.currentEvent;
      }
   }

   /**
    * Returns cartesian intersection point with Earth (if set) or Ellipsoid.
    *
    * @param point
    * @return
    */
   private Tuple3d getIntersection(final Point point) {
      final Vector3d currentVector = this.getMouseVector(this.scene, point);
      Tuple3d hitLocation = null;

      if (currentVector != null) {
         final Tuple3d cameraScreen = new Tuple3d(this.scene.getWidth() / 2.0, this.scene.getHeight() / 2.0, 0.0);
         final Tuple3d cameraWorld = CameraUtils.gluUnProject(this.scene, cameraScreen);
         cameraWorld.add(this.scene.getOrigin());

         final PickingRay ray = new PickingRay(cameraWorld, currentVector);

         if (this.earth != null) {
            final PickingHit hit = this.earth.getIntersection(ray);

            if (hit != null) {
               hitLocation = hit.getHitLocation();
            }
         }

         if (hitLocation == null) {
            hitLocation = WGS84.getNearIntersection(ray, 0, false);
         }
      }

      return hitLocation;
   }

   private Vector3d getMouseVector(final Scene scene, final Point point) {
      final Vector3d vector = new Vector3d();
      final Tuple3d cameraScreen = new Tuple3d(scene.getWidth() / 2.0, scene.getHeight() / 2.0, 0.0);
      final Tuple3d cameraWorld = CameraUtils.gluUnProject(scene, cameraScreen);
      final Tuple3d mouseScreen = new Tuple3d(point.x, scene.getHeight() - point.y, 1.0);
      final Tuple3d mouseWorld = CameraUtils.gluUnProject(scene, mouseScreen);

      if (mouseWorld != null) {
         vector.subtract(mouseWorld, cameraWorld);
         vector.normalize();

         return vector;
      } else {
         System.out.println("screen: " + mouseScreen);
         System.out.println("world: " + mouseWorld);
         return null;
      }
   }

   private void updateText(final MouseEvent event) {
      final Tuple3d xyzIntersection = this.getIntersection(event.getPoint());

      this.textRenderer.clearText();

      if (xyzIntersection != null) {
         final Tuple3d lonLatAltIntersection = WGS84.cartesianToGeodesic(xyzIntersection);
         this.textRenderer.addText(xyzIntersection.toString(), new Point(100, 50));
         this.textRenderer.addText("lon: " + this.formatter.format(lonLatAltIntersection.x) + ", lat: " + this.formatter.format(lonLatAltIntersection.y) + ", alt: " + this.formatter.format(lonLatAltIntersection.z), new Point(100, 100));
         this.textRenderer.addText("Expected Altitude: " + DigitalElevationUtils.getElevation(lonLatAltIntersection.x, lonLatAltIntersection.y), new Point(100, 150));
      }
   }
}
