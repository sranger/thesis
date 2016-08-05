package com.stephenwranger.thesis.selection;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.PostProcessor;
import com.stephenwranger.thesis.renderables.TreeRenderable;

public class ContextAwarePointSelection implements PostProcessor, MouseListener, MouseMotionListener, MouseWheelListener {
   private final Scene scene;
   private final TreeRenderable tree;
   
   private Volume selection = null;
   private List<Tuple2d> mouseSelection = new ArrayList<>();
   
   public ContextAwarePointSelection(final Scene scene, final TreeRenderable tree) {
      this.scene = scene;
      this.tree = tree;
      
      this.scene.addMouseListener(this);
      this.scene.addMouseMotionListener(this);
      this.scene.addMouseWheelListener(this);
   }

   @Override
   public void process(final GL2 gl, final GLU glu, final Scene scene) {
      final Volume selection = this.selection;
      this.selection = null;
      
      if(selection != null) {
         final Collection<Tuple3d> selectedPoints = this.tree.getVolumeIntersection(selection);
         System.out.println("selected points count: " + selectedPoints.size());
      } else if(!this.mouseSelection.isEmpty()) {
         final int size = this.mouseSelection.size();
         gl.glPushMatrix();
         gl.glMatrixMode(GL2.GL_PROJECTION);
         gl.glLoadIdentity();
         glu.gluOrtho2D(0, this.scene.getWidth(), 0, this.scene.getHeight());
         gl.glMatrixMode(GL2.GL_MODELVIEW);
         gl.glLoadIdentity();
         
         gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
         gl.glDisable(GL2.GL_LIGHTING);
         
         gl.glLineWidth(3f);
         gl.glBegin(GL2.GL_LINE_STRIP);
         gl.glColor4f(0f, 0.6f, 0f, 1f);
         
         for(int i = 0; i <= size; i++) {
            final Tuple2d point = this.mouseSelection.get(i % size);
            gl.glVertex2f((float) point.x, (float) (this.scene.getHeight() - point.y));
         }
         
         gl.glEnd();
         gl.glPopAttrib();
         gl.glPopMatrix();
      }
   }

   @Override
   public void mousePressed(final MouseEvent event) {
      if(event.isControlDown()) {
         this.mouseSelection.clear();
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
      } else {
         this.mouseSelection.clear();
         this.selection = null;
      }
   }

   @Override
   public void mouseReleased(final MouseEvent event) {
      if(event.isControlDown()) {
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
         this.selection = new Volume(this.scene, this.scene.getCameraPosition(), this.mouseSelection.toArray(new Tuple2d[this.mouseSelection.size()]));
      } else {
         this.mouseSelection.clear();
         this.selection = null;
      }
   }

   @Override
   public void mouseDragged(final MouseEvent event) {
      if(event.isControlDown()) {
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
      } else {
         this.mouseSelection.clear();
         this.selection = null;
      }
   }

   @Override
   public void mouseMoved(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mouseClicked(final MouseEvent e) {
      // nothing
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
   public void mouseWheelMoved(final MouseWheelEvent e) {
      this.mouseSelection.clear();
   }
}
