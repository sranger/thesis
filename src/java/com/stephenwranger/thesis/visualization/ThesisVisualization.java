package com.stephenwranger.thesis.visualization;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.renderables.TextRenderable;
import com.stephenwranger.graphics.renderables.TriangleMesh;
import com.stephenwranger.graphics.utils.Timings;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeServerProcessor.ConnectionType;
import com.stephenwranger.thesis.data.TreeStructure;
import com.stephenwranger.thesis.geospatial.Earth;
import com.stephenwranger.thesis.geospatial.SphericalNavigator;
import com.stephenwranger.thesis.icosatree.Icosatree;
import com.stephenwranger.thesis.renderables.TreeRenderable;

public class ThesisVisualization extends JFrame {
   private static final long serialVersionUID = 545923577250987084L;
   private static final String USAGE = "java ThesisVisualization <tree_path> <FILESYSTEM|HTTP>";

   private final Earth earth;
   private final Scene scene;

   public ThesisVisualization(final String basePath, final ConnectionType connectionType) {
      super("Thesis Visualization");

      this.earth = new Earth();
      this.earth.setWireframe(false);

      this.scene = new Scene(new Dimension(1600, 1000));
      this.scene.addRenderable(this.earth);
      this.scene.setOriginEnabled(true);

      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      navigator.moveTo(-120.8687371531015, 35.368949194257716, 81.32168056350201, 0, 0, 200000);
      this.scene.addPreRenderable(navigator);

      // this.loadIcosatreeBounds("");

      // final TreeRenderable renderer = new TreeRenderable(basePath, connectionType);
      // this.scene.addRenderable(renderer);

      // this.addTimingDisplay(scene, renderer);

      this.getContentPane().add(this.scene);

      SwingUtilities.invokeLater(() -> {
         setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         setLocation(2000, 100);
         pack();
         scene.start();
         setVisible(true);
      });
   }

   public void addTimingDisplay(final Scene scene, final TreeRenderable renderer) {
      final TextRenderable timingRenderable = new TextRenderable(new Font("Monospaced", Font.PLAIN, 14)) {

         @Override
         public synchronized void render(GL2 gl, GLU glu, GLAutoDrawable glDrawable, Scene scene) {
            final Timings timings = renderer.getTimings();
            final String[] text = timings.toString().split("\n");

            this.clearText();
            int y = scene.getHeight() - 10;
            for (final String t : text) {
               this.addText(t, new Point(10, y));
               y -= 15;
            }

            super.render(gl, glu, glDrawable, scene);
         }

      };
      this.scene.addRenderableOrthographic(timingRenderable);
   }

   public void loadIcosatreeBounds(final String path) {
      final List<Attribute> attributes = new ArrayList<>();
      attributes.add(new Attribute("0,X,0,8,DOUBLE,8,-2671529.167771962,-2670791.4207160836,-2671206.533690431,148.32056658828276"));
      attributes.add(new Attribute("1,Y,8,8,DOUBLE,8,-4469545.061828081,-4468937.152562849,-4469280.47211661,27.343033140313597"));
      attributes.add(new Attribute("2,Z,16,8,DOUBLE,8,3671282.354341662,3671858.9230972063,3671551.800912032,105.13728902370654"));

      final Icosatree tree = new Icosatree(new DataAttributes(attributes), new int[] { 10, 10, 10 });

      // final int[] list = new int[] { 10, 11, 16 }; // over USA
      final int[] list = new int[] { 11 };
      final boolean drawNormals = false;

      for (final int i : list) {
         final TreeCell cell = tree.getCell("", i);
         this.addRenderables(tree, cell, drawNormals, 3);
      }
   }

   private void addRenderables(final TreeStructure tree, final TreeCell cell, final boolean drawNormals, final int maxDepth) {
      final Color4f color01 = new Color4f(1f, 0f, 0f, 1f);
      final Color4f color23 = new Color4f(0f, 1f, 0f, 1f);
      final Color4f color45 = new Color4f(0f, 0f, 1f, 1f);
      final Color4f colorTop = new Color4f(1f, 1f, 1f, 1f);
      final Color4f colorBottom = new Color4f(0.3f, 0.3f, 0.3f, 1f);
      final TrianglePrismVolume volume = (TrianglePrismVolume) cell.getBoundingVolume();

      final Triangle3d[] faces = volume.getFaces();

      final TriangleMesh mesh01 = new TriangleMesh(new Triangle3d[] { faces[0], faces[1] }, color01);
      mesh01.setDrawNormals(drawNormals);
      final TriangleMesh mesh23 = new TriangleMesh(new Triangle3d[] { faces[2], faces[3] }, color23);
      mesh23.setDrawNormals(drawNormals);
      final TriangleMesh mesh45 = new TriangleMesh(new Triangle3d[] { faces[4], faces[5] }, color45);
      mesh45.setDrawNormals(drawNormals);
      final TriangleMesh meshTop = new TriangleMesh(new Triangle3d[] { faces[6] }, colorTop);
      meshTop.setDrawNormals(drawNormals);
      final TriangleMesh meshBottom = new TriangleMesh(new Triangle3d[] { faces[7] }, colorBottom);
      meshBottom.setDrawNormals(drawNormals);
      this.scene.addRenderable(mesh01);
      this.scene.addRenderable(mesh23);
      this.scene.addRenderable(mesh45);
      this.scene.addRenderable(meshTop);
      this.scene.addRenderable(meshBottom);

      final String path = cell.getPath();

      if (path.length() <= maxDepth) {
         for (int j = 0; j < 8; j++) {
            final TreeCell cellIn = tree.getCell(path, j);
            this.addRenderables(tree, cellIn, drawNormals, maxDepth);
         }
      }
   }

   public static void main(final String[] args) {
      try {
         final String basePath = args[0];
         final ConnectionType connectionType = ConnectionType.valueOf(args[1]);

         new ThesisVisualization(basePath, connectionType);
      } catch (final Exception e) {
         System.err.println(USAGE);
         e.printStackTrace();
      }
   }
}
