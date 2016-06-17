package com.stephenwranger.thesis.geospatial;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.SwingUtilities;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.SphericalCoordinate;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.utils.MathUtils;

public class SphericalNavigator implements MouseListener, MouseMotionListener, MouseWheelListener {
   private static final Vector3d RIGHT_VECTOR = new Vector3d(0,1,0);
   private static final Vector3d UP_VECTOR = new Vector3d(-1,0,0);
   private static final Vector3d AZIMUTH_ROTATION_AXIS = new Vector3d(0,1,0);
   private static final Vector3d ELEVATION_ROTATION_AXIS = new Vector3d(0,0,-1);
   
   private final Scene scene;
   private final Tuple3d anchor = WGS84.geodesicToCartesian(new Tuple3d());
   private final SphericalCoordinate cameraCoordinate = new SphericalCoordinate(0, 0, 1e7);

   public SphericalNavigator(final Scene scene) {
      this.scene = scene;

      this.scene.setLookAt(this.anchor, new Vector3d(0,-1,0));
      this.scene.setCameraPosition(WGS84.geodesicToCartesian(new Tuple3d(0,0,1e7)));

      scene.addMouseListener(this);
      scene.addMouseMotionListener(this);
      scene.addMouseWheelListener(this);
   }

   public void removeListeners() {
      this.scene.removeMouseListener(this);
      this.scene.removeMouseMotionListener(this);
      this.scene.removeMouseWheelListener(this);
   }

   @Override
   public void mouseWheelMoved(final MouseWheelEvent event) {
      final double count = event.getPreciseWheelRotation();

      final SphericalCoordinate coordinate = new SphericalCoordinate(this.cameraCoordinate);
      coordinate.range = Math.max(0.0, coordinate.range * (1.0 + (0.1 * count)));

      this.setCameraPosition(coordinate);
   }
   
   public void setCameraPosition(final SphericalCoordinate cameraCoordinate) {
      this.cameraCoordinate.set(cameraCoordinate);
      
      // get geodetic coordinate of anchor
      final Tuple3d lonLatAlt = WGS84.cartesianToGeodesic(this.anchor);
      System.out.println("anchor xyz: " + this.anchor);
      System.out.println("anchor lla: " + lonLatAlt);
      // get orientation of anchor in relation to local surface reference frame (up is surface normal at anchor)
      final Quat4d orientation = WGS84.getOrientation(lonLatAlt.x, lonLatAlt.y);

      // create rotation that converts local reference vector to point towards local camera position
      final Quat4d azimuthRotation = new Quat4d(AZIMUTH_ROTATION_AXIS, Math.toDegrees(cameraCoordinate.azimuth));
      final Quat4d elevationRotation = new Quat4d(ELEVATION_ROTATION_AXIS, Math.toDegrees(cameraCoordinate.elevation));
      final Quat4d sphericalRotation = new Quat4d();
      sphericalRotation.mult(azimuthRotation);
      sphericalRotation.mult(elevationRotation);
      
      final Vector3d toCamera = new Vector3d(RIGHT_VECTOR);
      sphericalRotation.mult(toCamera);
      toCamera.scale(cameraCoordinate.range);
      orientation.mult(toCamera);
      
      final Tuple3d cameraPosition = new Tuple3d(toCamera);
      cameraPosition.add(this.anchor);
      
      final Vector3d up = new Vector3d(UP_VECTOR);
      sphericalRotation.mult(up);
      orientation.mult(up);
      
      this.scene.setLookAt(this.anchor, up);
      this.scene.setCameraPosition(cameraPosition);
   }
   
   public void moveAnchor(final Tuple3d anchor) {
      this.anchor.set(anchor);
      System.out.println("new anchor xyz: " + anchor);
      System.out.println("new anchor lla: " + WGS84.cartesianToGeodesic(anchor));
      
      this.scene.setLookAt(this.anchor, this.scene.getUp());
   }
   
   private Point previousEvent = null;

   @Override
   public void mouseDragged(final MouseEvent event) {      
      if(SwingUtilities.isLeftMouseButton(event)) {
         final Vector3d currentVector = SphericalNavigator.getMouseVector(this.scene, event);
         final Tuple3d lonLatAltIntersection = WGS84.getNearIntersection(new PickingRay(this.scene.getCameraPosition(), currentVector), 0, true);
         
         if(lonLatAltIntersection != null) {
            this.moveAnchor(WGS84.geodesicToCartesian(lonLatAltIntersection));
         }
      } else if(SwingUtilities.isRightMouseButton(event) && previousEvent != null) {
         final int x = event.getX() - previousEvent.x;
         final int y = event.getY() - previousEvent.y;
         
         if(x != 0 || y != 0) {
            final SphericalCoordinate coordinates = new SphericalCoordinate(this.cameraCoordinate);
            final double azimuth = -((x / (double) this.scene.getWidth()) * Math.PI);
            final double elevation = ((y / (double) this.scene.getHeight()) * Math.PI);
            coordinates.azimuth += azimuth;
            coordinates.elevation += elevation;
            
            coordinates.elevation = MathUtils.clamp(0, MathUtils.HALF_PI, coordinates.elevation);
            
            this.setCameraPosition(coordinates);
            this.previousEvent = event.getPoint();
         }
      } else {
         this.previousEvent = null;
      }
   }

   @Override
   public void mouseMoved(final MouseEvent event) {
      // nothing atm

   }

   @Override
   public void mouseClicked(final MouseEvent event) {
      if(event.getClickCount() == 2) {
         final Vector3d mouseVector = SphericalNavigator.getMouseVector(this.scene, event);
         final PickingRay ray = new PickingRay(this.scene.getCameraPosition(), mouseVector);
         final Tuple3d anchor = WGS84.getNearIntersection(ray, 0, false);

         this.moveAnchor(anchor);
      }
   }

   @Override
   public void mousePressed(final MouseEvent event) {
      this.previousEvent = event.getPoint();
   }

   @Override
   public void mouseReleased(final MouseEvent event) {
      this.previousEvent = null;
   }

   @Override
   public void mouseEntered(final MouseEvent event) {
      // nothing atm

   }

   @Override
   public void mouseExited(final MouseEvent event) {
      // nothing atm

   }
   
   private static Vector3d getMouseVector(final Scene scene, final MouseEvent event) {
      final Vector3d vector = new Vector3d();
      final Tuple3d cameraWorld = scene.getCameraPosition();
      final Tuple3d cameraScreen = CameraUtils.getScreenCoordinates(scene, cameraWorld);
      final Tuple3d screenCoordinates = new Tuple3d(event.getX(), scene.getHeight() - event.getY(), cameraScreen.z + 0.2);
      vector.subtract(CameraUtils.getWorldCoordinates(scene, screenCoordinates), cameraWorld);
      vector.normalize();
      
      return vector;
   }
}
