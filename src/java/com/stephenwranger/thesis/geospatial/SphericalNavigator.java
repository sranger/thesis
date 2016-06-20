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

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.SphericalCoordinate;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.renderables.TextRenderable;
import com.stephenwranger.graphics.utils.MathUtils;

public class SphericalNavigator implements MouseListener, MouseMotionListener, MouseWheelListener {
   private static final Vector3d RIGHT_VECTOR = new Vector3d(0,1,0);
   private static final Vector3d UP_VECTOR = new Vector3d(0,0,-1);
   private static final Vector3d AZIMUTH_ROTATION_AXIS = new Vector3d(0,-1,0);
   private static final Vector3d ELEVATION_ROTATION_AXIS = new Vector3d(1,0,0);
   
   private final DecimalFormat formatter = new DecimalFormat("0.000");
   private final Scene scene;
   private final TextRenderable textRenderer;
   private final Tuple3d anchor = WGS84.geodesicToCartesian(new Tuple3d());
   private final SphericalCoordinate cameraCoordinate = new SphericalCoordinate(0, 0, 1e7);

   public SphericalNavigator(final Scene scene) {
      this.scene = scene;

      this.scene.setCameraPosition(WGS84.geodesicToCartesian(new Tuple3d(0,0,1e7)), this.anchor, new Vector3d(0,0,1));

      scene.addMouseListener(this);
      scene.addMouseMotionListener(this);
      scene.addMouseWheelListener(this);
      
      this.textRenderer = new TextRenderable(new Font("SansSerif", Font.PLAIN, 32));
      this.scene.addRenderableOrthographic(this.textRenderer);
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

      this.setCameraPosition(this.anchor, coordinate);
   }
   
   public void setCameraPosition(final Tuple3d anchor, final SphericalCoordinate cameraCoordinate) {
      if(MathUtils.isFinite(anchor)) {
         this.anchor.set(anchor);
         this.cameraCoordinate.set(cameraCoordinate);
         
         // get geodetic coordinate of anchor
         final Tuple3d lonLatAlt = WGS84.cartesianToGeodesic(this.anchor);
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
         
         if(MathUtils.isFinite(cameraPosition) && MathUtils.isFinite(this.anchor) && MathUtils.isFinite(up)) {
            this.scene.setCameraPosition(cameraPosition, this.anchor, up);
         }
         System.out.println("\neyeLLA:    " + WGS84.cartesianToGeodesic(cameraPosition));
         System.out.println("anchorLLA: " + WGS84.cartesianToGeodesic(anchor));
   //      System.out.println();
   //      System.out.println("eye: " + cameraPosition);
   //      System.out.println("lookAt: " + this.anchor);
   //      System.out.println("up: " + up);
   //      final double[] current = this.scene.getModelViewMatrix();
   //      final double[] expected = CameraUtils.gluLookAt(cameraPosition, this.anchor, up).get();
   //      
   //      for(int i = 0; i < 16; i++) {
   //         System.out.println("[" + String.format("%2d", i) + "] " + String.format("%7.5f", current[i]) + " : " + String.format("%7.5f", expected[i]));
   //      }
      }
   }
   
   private Point previousEvent = null;
   private Tuple3d mouseLonLatAlt = null;
   
   private Tuple3d updateText(final MouseEvent event) {
      final Vector3d currentVector = SphericalNavigator.getMouseVector(this.scene, event);
      final Tuple3d lonLatAltIntersection = WGS84.getNearIntersection(new PickingRay(this.scene.getCameraPosition(), currentVector), 0, true);

      this.textRenderer.clearText();
      
      if(lonLatAltIntersection != null) {
         final Tuple3d xyz = WGS84.geodesicToCartesian(lonLatAltIntersection);
         this.textRenderer.addText(xyz.toString(), new Point(100, 50));
         this.textRenderer.addText("lon: " + formatter.format(lonLatAltIntersection.x) + "\n"
               + "lat: " + formatter.format(lonLatAltIntersection.y) + "\n"
               + "alt: " + formatter.format(lonLatAltIntersection.z), new Point(100, 100));
      }
      
      return lonLatAltIntersection;
   }

   @Override
   public void mouseDragged(final MouseEvent event) {
      final Tuple3d lonLatAltIntersection = updateText(event);
      
      if(SwingUtilities.isLeftMouseButton(event)) {
         if(this.mouseLonLatAlt != null && lonLatAltIntersection != null) {
            final Tuple3d currentXyz = WGS84.geodesicToCartesian(lonLatAltIntersection);
            final Tuple3d previousXyz = WGS84.geodesicToCartesian(this.mouseLonLatAlt);
            final Vector3d toCurrent = new Vector3d(currentXyz);
            toCurrent.scale(-1);
            toCurrent.normalize();
            
            final Vector3d toPrevious = new Vector3d(previousXyz);
            toPrevious.scale(-1);
            toPrevious.normalize();
            
            final Vector3d axis = new Vector3d();
            axis.cross(toCurrent, toPrevious);
            
            final double angle = Math.toDegrees(toCurrent.angle(toPrevious));
            final Tuple3d anchor = new Tuple3d(this.anchor);
            final Quat4d rotation = new Quat4d(axis, angle);
            
            rotation.mult(anchor);
            this.setCameraPosition(anchor, this.cameraCoordinate);
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
            
            this.setCameraPosition(this.anchor, coordinates);
            this.previousEvent = event.getPoint();
         }
      } else {
         this.previousEvent = null;
         this.mouseLonLatAlt = null;
      }
   }

   @Override
   public void mouseMoved(final MouseEvent event) {
      final Tuple3d lonLatAltIntersection = updateText(event);

      this.textRenderer.clearText();
      
      if(lonLatAltIntersection != null) {
         final Tuple3d xyz = WGS84.geodesicToCartesian(lonLatAltIntersection);
         this.textRenderer.addText(xyz.toString(), new Point(100, 50));
         this.textRenderer.addText("lon: " + formatter.format(lonLatAltIntersection.x) + "\n"
               + "lat: " + formatter.format(lonLatAltIntersection.y) + "\n"
               + "alt: " + formatter.format(lonLatAltIntersection.z), new Point(100, 100));
      }
   }

   @Override
   public void mouseClicked(final MouseEvent event) {
      if(event.getClickCount() == 2) {
         final Vector3d mouseVector = SphericalNavigator.getMouseVector(this.scene, event);
         final PickingRay ray = new PickingRay(this.scene.getCameraPosition(), mouseVector);
         final Tuple3d anchor = WGS84.getNearIntersection(ray, 0, false);

         if(anchor != null) {
            this.setCameraPosition(anchor, this.cameraCoordinate);
         }
      }
   }

   @Override
   public void mousePressed(final MouseEvent event) {
      this.previousEvent = event.getPoint();
      this.mouseLonLatAlt = updateText(event);
   }

   @Override
   public void mouseReleased(final MouseEvent event) {
      this.previousEvent = null;
      this.mouseLonLatAlt = null;
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
//      System.out.println("camera screen coordinates: " + CameraUtils.getScreenCoordinates(scene, cameraWorld));
      final Tuple3d screenCoordinates = new Tuple3d(event.getX(), scene.getHeight() - event.getY(), 0.0);
      final Tuple3d mouseWorld = CameraUtils.getWorldCoordinates(scene, screenCoordinates);
//      final Tuple3d mouseScreen = CameraUtils.getScreenCoordinates(scene, mouseWorld);
//      System.out.println("mouse screen coordinates: " + mouseScreen);
      vector.subtract(mouseWorld, cameraWorld);
      vector.normalize();
      
      return vector;
   }
}
