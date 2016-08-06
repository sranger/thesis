package com.stephenwranger.thesis.selection;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.IntersectionUtils;
import com.stephenwranger.graphics.math.intersection.LineSegment;
import com.stephenwranger.graphics.renderables.PointRenderable;
import com.stephenwranger.graphics.renderables.PostProcessor;
import com.stephenwranger.thesis.renderables.TreeRenderable;

public class ContextAwarePointSelection implements PostProcessor, MouseListener, MouseMotionListener, MouseWheelListener {
   private final Scene           scene;
   private final TreeRenderable  tree;
   private final PointRenderable pointRenderer;

   //   private final TriangleMesh    selectionTriangles = null;
   private Volume                selection      = null;
   private final List<Tuple2d>   mouseSelection = new ArrayList<>();

   public ContextAwarePointSelection(final Scene scene, final TreeRenderable tree) {
      this.scene = scene;
      this.tree = tree;

      this.scene.addMouseListener(this);
      this.scene.addMouseMotionListener(this);
      this.scene.addMouseWheelListener(this);

      this.pointRenderer = new PointRenderable();
      this.pointRenderer.setPointSize(10f);
      this.scene.addRenderable(this.pointRenderer);
   }

   @Override
   public void mouseClicked(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mouseDragged(final MouseEvent event) {
      if (event.isControlDown()) {
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
      } else {
         this.mouseSelection.clear();
         this.selection = null;
      }
   }

   @Override
   public void mouseEntered(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mouseExited(final MouseEvent e) {
      // nothing

   }

   @Override
   public void mouseMoved(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mousePressed(final MouseEvent event) {
      if (event.isControlDown() && SwingUtilities.isLeftMouseButton(event)) {
         this.mouseSelection.clear();
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
      } else {
         this.mouseSelection.clear();
         this.pointRenderer.setPoints(null);
         this.selection = null;
      }
   }

   @Override
   public void mouseReleased(final MouseEvent event) {
      if (event.isControlDown() && SwingUtilities.isLeftMouseButton(event)) {
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
         final int length = this.mouseSelection.size();
         final List<LineSegment> temp = new ArrayList<>();
         final List<Tuple2d> polygon = new ArrayList<>();

         for (int i = 0; i < length; i++) {
            final LineSegment line = new LineSegment(this.mouseSelection.get(i), this.mouseSelection.get((i + 1) % length));
            final Tuple2d intersection = ContextAwarePointSelection.getIntersections(line, temp);
            polygon.add(this.mouseSelection.get(i));

            if (intersection == null) {
               temp.add(line);

               if (i == (length - 1)) {
                  // end of loop so add the first point again to close the polygon
                  polygon.add(this.mouseSelection.get((i + 1) % length));
               }
            } else {
               // we're ending the loop so add the intersection point
               temp.add(new LineSegment(this.mouseSelection.get(i), intersection));
               polygon.add(intersection);
               break;
            }
         }

         final Tuple2d[] polygonVertices = polygon.toArray(new Tuple2d[polygon.size()]);
         this.selection = new Volume(this.scene, this.scene.getCameraPosition(), polygonVertices);
      } else {
         this.mouseSelection.clear();
         this.pointRenderer.setPoints(null);
         this.selection = null;
      }
   }

   @Override
   public void mouseWheelMoved(final MouseWheelEvent e) {
      this.mouseSelection.clear();
   }

   @Override
   public void process(final GL2 gl, final GLU glu, final Scene scene) {
      final Volume selection = this.selection;
      this.selection = null;

      if (selection != null) {
         final Collection<Tuple3d> selectedPoints = this.tree.getVolumeIntersection(selection);
         System.out.println("selected points count: " + selectedPoints.size());
         this.pointRenderer.setPoints(selectedPoints);

         //         if (this.selectionTriangles != null) {
         //            this.selectionTriangles.remove();
         //            this.selectionTriangles = null;
         //         }
         //
         //         this.selectionTriangles = new TriangleMesh(selection.getTriangles(), Color4f.red());
         //         this.selectionTriangles.setPolygonMode(GL.GL_FRONT_AND_BACK);
         //         this.selectionTriangles.setDrawNormals(true);
         //         this.scene.addRenderable(this.selectionTriangles);
      } else if (!this.mouseSelection.isEmpty()) {
         final int size = this.mouseSelection.size();
         gl.glPushMatrix();
         gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
         gl.glLoadIdentity();
         glu.gluOrtho2D(0, this.scene.getWidth(), 0, this.scene.getHeight());
         gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
         gl.glLoadIdentity();

         gl.glPushAttrib(GL2.GL_LIGHTING_BIT | GL2.GL_LINE_BIT);
         gl.glDisable(GLLightingFunc.GL_LIGHTING);

         gl.glLineWidth(3f);
         gl.glBegin(GL.GL_LINE_STRIP);
         gl.glColor4f(0f, 0.6f, 0f, 1f);

         for (int i = 0; i <= size; i++) {
            final Tuple2d point = this.mouseSelection.get(i % size);
            gl.glVertex2f((float) point.x, (float) (this.scene.getHeight() - point.y));
         }

         gl.glEnd();
         gl.glPopAttrib();
         gl.glPopMatrix();
      }
   }

   private static Tuple2d getIntersections(final LineSegment toCheck, final List<LineSegment> existing) {
      final Tuple2d intersection = new Tuple2d();

      for (final LineSegment segment : existing) {
         if (IntersectionUtils.lineSegmentsIntersect(toCheck, segment, intersection)) {
            // make sure the intersection isn't on the endpoints
            if (!IntersectionUtils.isZero(segment.min.distance(intersection)) && !IntersectionUtils.isZero(segment.max.distance(intersection))) {
               return intersection;
            }
         }
      }

      return null;
   }
}
