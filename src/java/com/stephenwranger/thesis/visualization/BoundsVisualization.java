package com.stephenwranger.thesis.visualization;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.renderables.Line;
import com.stephenwranger.thesis.geospatial.Earth;
import com.stephenwranger.thesis.geospatial.EarthImagery.ImageryType;
import com.stephenwranger.thesis.geospatial.SphericalNavigator;
import com.stephenwranger.thesis.icosatree.Icosatree;

public class BoundsVisualization extends JFrame {
   private static final long   serialVersionUID = 545923577250987084L;
   private static final String USAGE            = "java BoundsVisualization";

   private final Earth         earth;
   private final Scene         scene;

   public BoundsVisualization() {
      super("Bounds Visualization");

      this.earth = new Earth(ImageryType.OPEN_STREET_MAP);
      this.earth.setWireframe(false);
      this.earth.setLightingEnabled(false);
      this.earth.setLoadFactor(0.75);

      this.scene = new Scene(new Dimension(1200, 900));
      //      this.scene.addRenderable(this.earth);
      this.scene.setOriginEnabled(false);

      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      navigator.moveTo(-120.8566862, 35.3721688, 0, SphericalNavigator.AZIMUTH_EAST, SphericalNavigator.ELEVATION_ZENITH / 2.0, 1e7);
      //      navigator.setEarth(this.earth);
      this.scene.addPreRenderable(navigator);

      for (int i = 0; i < 1; i++) {
         final String path = Icosatree.getCellPath("", i);
         this.loadIcosatreeBounds(path, 0);
      }

      this.getContentPane().setLayout(new BorderLayout());
      this.getContentPane().add(this.scene, BorderLayout.CENTER);

      SwingUtilities.invokeLater(() -> {
         this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         this.setLocation(100, 100);
         this.pack();
         this.scene.start();
         this.setVisible(true);
      });
   }

   public void loadIcosatreeBounds(final String path, final int maxDepth) {
      final TrianglePrismVolume bounds = (TrianglePrismVolume) Icosatree.getCellBoundingVolume(path);
      this.addRenderables(bounds);

      if (maxDepth > path.length()) {
         for (int i = 0; i < 20; i++) {
            final String childPath = Icosatree.getCellPath(path, i);
            final TrianglePrismVolume childbounds = (TrianglePrismVolume) Icosatree.getCellBoundingVolume(childPath);

            this.addRenderables(childbounds);

            if (maxDepth > childPath.length()) {
               this.loadIcosatreeBounds(childPath, maxDepth);
            }
         }
      }
   }

   private void addRenderables(final TrianglePrismVolume bounds) {
      final float lineWidth = 6f;
      final Color4f color = new Color4f((float) Math.random(), (float) Math.random(), (float) Math.random(), 1f);
      final Triangle3d top = bounds.getTopFace();
      final Triangle3d bottom = bounds.getBottomFace();

      final Tuple3d[] topCorners = top.getCorners();
      final Tuple3d[] bottomCorners = bottom.getCorners();

      final Line side1 = new Line(topCorners[0], bottomCorners[1]);
      side1.setColor(color);
      side1.setLineWidth(lineWidth);
      final Line side2 = new Line(topCorners[1], bottomCorners[0]);
      side2.setColor(color);
      side2.setLineWidth(lineWidth);
      final Line side3 = new Line(topCorners[2], bottomCorners[2]);
      side3.setColor(color);
      side3.setLineWidth(lineWidth);

      this.scene.addRenderable(side1);
      this.scene.addRenderable(side2);
      this.scene.addRenderable(side3);

      for(int i = 0; i < topCorners.length; i++) {
         final Line topEdge = new Line(topCorners[i], topCorners[(i+1) % topCorners.length]);
         final Line bottomEdge = new Line(bottomCorners[i], bottomCorners[(i+1) % bottomCorners.length]);

         topEdge.setColor(color);
         topEdge.setLineWidth(lineWidth);
         bottomEdge.setColor(color);
         bottomEdge.setLineWidth(lineWidth);

         this.scene.addRenderable(topEdge);
         this.scene.addRenderable(bottomEdge);
      }
   }

   public static void main(final String[] args) {
      try {
         new BoundsVisualization();
      } catch (final Exception e) {
         System.err.println(BoundsVisualization.USAGE);
         e.printStackTrace();
      }
   }
}
