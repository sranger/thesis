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
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.SphericalCoordinate;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.renderables.PreRenderable;
import com.stephenwranger.graphics.renderables.TextRenderable;
import com.stephenwranger.graphics.utils.MathUtils;

public class SphericalNavigator implements PreRenderable, MouseListener, MouseMotionListener, MouseWheelListener {
   private static final Vector3d RIGHT_VECTOR = new Vector3d(0,1,0);
   private static final Vector3d UP_VECTOR = new Vector3d(0,0,-1);
   
   private enum EventType {
      MOUSE_DRAG_LEFT, MOUSE_DRAG_RIGHT, MOUSE_CLICK;
   }
   
   private final DecimalFormat formatter = new DecimalFormat("0.000");
   private final Scene scene;
   private final TextRenderable textRenderer;
   private final Tuple3d anchor = WGS84.geodesicToCartesian(new Tuple3d());
   private final SphericalCoordinate cameraCoordinate = new SphericalCoordinate(0, 0, 2e7);

   private Tuple3d mouseLonLatAlt = null;
   private Point previousEvent = null;
   private Point currentEvent = null;
   private EventType eventType = null;
   private boolean update = true;

   public SphericalNavigator(final Scene scene) {
      this.scene = scene;

      scene.addMouseListener(this);
      scene.addMouseMotionListener(this);
      scene.addMouseWheelListener(this);
      
      this.textRenderer = new TextRenderable(new Font("SansSerif", Font.PLAIN, 32));
      this.scene.addRenderableOrthographic(this.textRenderer);
   }
   
   public synchronized void setViewingVolume(final BoundingVolume boundingVolume) {
      final Tuple3d center = boundingVolume.getCenter();
      final Tuple3d centerLonLatAlt = WGS84.cartesianToGeodesic(center);
      centerLonLatAlt.z = 0.0;
      this.anchor.set(WGS84.geodesicToCartesian(centerLonLatAlt));
      this.cameraCoordinate.setRange(boundingVolume.getSpannedDistance(null) / 2.0);
      this.update = true;
   }

   public void removeListeners() {
      this.scene.removeMouseListener(this);
      this.scene.removeMouseMotionListener(this);
      this.scene.removeMouseWheelListener(this);
   }

   @Override
   public void mouseWheelMoved(final MouseWheelEvent event) {
      final double count = event.getPreciseWheelRotation();

      this.cameraCoordinate.update(0, 0, this.cameraCoordinate.getRange() * (0.1 * count));
      this.update = true;
   }
   
   @Override
   public synchronized void preRender(final GL2 gl, final GLU glu, final GLDrawable drawable, final Scene scene) {
      if(this.update) {
         if(this.eventType != null) {
            switch(this.eventType) {
               case MOUSE_CLICK:
                  click(gl, scene);
                  break;
               case MOUSE_DRAG_LEFT:
                  dragLeft(gl, scene);
                  break;
               case MOUSE_DRAG_RIGHT:
                  dragRight(gl, scene);
                  break;
            }
         }
         
         // get geodetic coordinate of anchor
         final Tuple3d lonLatAlt = WGS84.cartesianToGeodesic(this.anchor);
         // get orientation of anchor in relation to local surface reference frame (up is surface normal at anchor)
         final Quat4d orientation = WGS84.getOrientation(lonLatAlt.x, lonLatAlt.y);
         final Quat4d sphericalRotation = cameraCoordinate.getOrientation();
         
         // create rotation that converts local reference vector to point towards local camera position
         final Vector3d toCamera = new Vector3d(RIGHT_VECTOR);
         sphericalRotation.mult(toCamera);
         orientation.mult(toCamera);
         toCamera.scale(cameraCoordinate.getRange());
         
         final Tuple3d cameraPosition = new Tuple3d(toCamera);
         cameraPosition.add(this.anchor);
         
         final Vector3d up = new Vector3d(UP_VECTOR);
         sphericalRotation.mult(up);
         orientation.mult(up);
         
         if(MathUtils.isFinite(cameraPosition) && MathUtils.isFinite(this.anchor) && MathUtils.isFinite(up)) {
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
   
   private void click(final GL2 gl, final Scene scene) {
      if(this.currentEvent != null) {
         final Tuple3d lonLatAlt = this.getIntersection(this.currentEvent);
         this.anchor.set(WGS84.geodesicToCartesian(lonLatAlt));
      }
   }
   
   private void dragLeft(final GL2 gl, final Scene scene) {
      if(this.previousEvent != null && this.currentEvent != null) {
         final Tuple3d from = (this.mouseLonLatAlt == null) ? this.getIntersection(this.previousEvent) : new Tuple3d(this.mouseLonLatAlt);
         final Tuple3d to = this.getIntersection(this.currentEvent);
         
         if(from != null && to != null) {
            if(this.mouseLonLatAlt == null) {
               this.mouseLonLatAlt = new Tuple3d(from);
            }
            
            final RotationTransformation transformation = WGS84.getRotationTransformation(from, to);
            transformation.apply(this.anchor);
         }
      }
   }
   
   private void dragRight(final GL2 gl, final Scene scene) {
      if(previousEvent != null && currentEvent != null) {
         final int x = currentEvent.x - previousEvent.x;
         final int y = currentEvent.y - previousEvent.y;
         
         if(x != 0 || y != 0) {
            final double azimuth = ((x / (double) scene.getWidth()) * Math.PI);
            final double elevation = ((y / (double) scene.getHeight()) * Math.PI);
            
            this.cameraCoordinate.update(azimuth, elevation, 0);
         }
         
         this.previousEvent = this.currentEvent;
      }
   }
   
   private Tuple3d getIntersection(final Point point) {
      final Vector3d currentVector = this.getMouseVector(scene, point);
      
      if(currentVector != null) {
         final Tuple3d cameraScreen = new Tuple3d(scene.getWidth() / 2.0, scene.getHeight() / 2.0, 0.0);
         final Tuple3d cameraWorld = CameraUtils.gluUnProject(scene, cameraScreen);
         
         return WGS84.getNearIntersection(new PickingRay(cameraWorld, currentVector), 0, true);
      }
      
      return null;
   }
   
   private void updateText(final MouseEvent event) {      
      final Tuple3d lonLatAltIntersection = this.getIntersection(event.getPoint());

      this.textRenderer.clearText();
      
      if(lonLatAltIntersection != null) {
         final Tuple3d xyz = WGS84.geodesicToCartesian(lonLatAltIntersection);
         this.textRenderer.addText(xyz.toString(), new Point(100, 50));
         this.textRenderer.addText("lon: " + formatter.format(lonLatAltIntersection.x)
               + ", lat: " + formatter.format(lonLatAltIntersection.y)
               + ", alt: " + formatter.format(lonLatAltIntersection.z), new Point(100, 100));
      }
   }

   @Override
   public synchronized void mouseDragged(final MouseEvent event) {
      if(SwingUtilities.isLeftMouseButton(event)) {
         this.currentEvent = event.getPoint();
         this.eventType = EventType.MOUSE_DRAG_LEFT;
      } else if(SwingUtilities.isRightMouseButton(event) && previousEvent != null) {
         this.mouseLonLatAlt = null;
         this.currentEvent = event.getPoint();
         this.eventType = EventType.MOUSE_DRAG_RIGHT;
      } else {
         this.mouseLonLatAlt = null;
         this.previousEvent = null;
         this.currentEvent = null;
         this.eventType = null;
      }
      
      this.update = true;
   }

   @Override
   public synchronized void mouseMoved(final MouseEvent event) {
      this.updateText(event);
   }

   @Override
   public synchronized void mouseClicked(final MouseEvent event) {
      if(event.getClickCount() == 2) {
         this.mouseLonLatAlt = null;
         this.previousEvent = null;
         this.currentEvent = event.getPoint();
         this.eventType = EventType.MOUSE_CLICK;
         
         this.update = true;
      }
   }

   @Override
   public synchronized void mousePressed(final MouseEvent event) {
      this.mouseLonLatAlt = null;
      this.previousEvent = event.getPoint();
      this.currentEvent = null;
      this.eventType = null;
   }

   @Override
   public synchronized void mouseReleased(final MouseEvent event) {
      this.mouseLonLatAlt = null;
      this.previousEvent = null;
      this.currentEvent = null;
      this.eventType = null;
   }

   @Override
   public void mouseEntered(final MouseEvent event) {
      // nothing atm

   }

   @Override
   public void mouseExited(final MouseEvent event) {
      // nothing atm

   }
   
   public SphericalCoordinate getCameraCoordinate() {
      return new SphericalCoordinate(this.cameraCoordinate);
   }
   
   public Tuple3d getAnchor() {
      return new Tuple3d(this.anchor);
   }
   
   private Vector3d getMouseVector(final Scene scene, final Point point) {
      final Vector3d vector = new Vector3d();
      final Tuple3d cameraScreen = new Tuple3d(scene.getWidth() / 2.0, scene.getHeight() / 2.0, 0.0);
      final Tuple3d cameraWorld = CameraUtils.gluUnProject(scene, cameraScreen);
      final Tuple3d mouseScreen = new Tuple3d(point.x, scene.getHeight() - point.y, 1.0);
      final Tuple3d mouseWorld = CameraUtils.gluUnProject(scene, mouseScreen);
      
      if(mouseWorld != null) {
         vector.subtract(mouseWorld, cameraWorld);
         vector.normalize();
         
//         final Vector3d view = new Vector3d();
//         view.subtract(this.anchor, scene.getCameraPosition());
//         view.normalize();
//         
//         if(view.angleDegrees(vector) > 90) {
//            System.err.println("camera: " + cameraWorld);
//            System.err.println("mouse:  " + mouseWorld);
//            System.err.println("view:   " + view);
//            System.err.println("vector: " + vector);
//         } else {
//            System.out.println("camera: " + cameraWorld);
//            System.out.println("mouse:  " + mouseWorld);
//            System.out.println("view:   " + view);
//            System.out.println("vector: " + vector);
//         }
         
         return vector;
      } else {
         System.out.println("screen: " + mouseScreen);
         System.out.println("world: " + mouseWorld);
         return null;
      }
   }
}
