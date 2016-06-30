package com.stephenwranger.thesis.visualization;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.renderables.TriangleMesh;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.geospatial.Earth;
import com.stephenwranger.thesis.geospatial.SphericalNavigator;
import com.stephenwranger.thesis.icosatree.Icosatree;

public class ThesisVisualization extends JFrame {
   private static final long serialVersionUID = 545923577250987084L;
   
   private final Earth earth;
   private final Scene scene;

   public ThesisVisualization() {
      super("Thesis Visualization");
      
      this.earth = new Earth();
      this.earth.setWireframe(true);
      
      this.scene = new Scene(new Dimension(1600, 1000));
      this.scene.addRenderable(this.earth);
//      this.scene.setViewingVolume(new BoundingSphere(new Tuple3d(), WGS84.EQUATORIAL_RADIUS / 2.0));
      
      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      this.scene.addPreRenderable(navigator);
      
      final List<Attribute> attributes = new ArrayList<>();
      attributes.add(new Attribute("0,X,0,8,DOUBLE,8,-2671529.167771962,-2670791.4207160836,-2671206.533690431,148.32056658828276"));
      attributes.add(new Attribute("1,Y,8,8,DOUBLE,8,-4469545.061828081,-4468937.152562849,-4469280.47211661,27.343033140313597"));
      attributes.add(new Attribute("2,Z,16,8,DOUBLE,8,3671282.354341662,3671858.9230972063,3671551.800912032,105.13728902370654"));

      final Icosatree tree = new Icosatree(new DataAttributes(attributes), new int[] { 10, 10, 10 });
      final Color4f color01 = new Color4f(1f,0f,0f,1f);
      final Color4f color23 = new Color4f(0f,1f,0f,1f);
      final Color4f color45 = new Color4f(0f,0f,1f,1f);
      final Color4f colorTop = new Color4f(1f,1f,1f,1f);
      final Color4f colorBottom = new Color4f(0.3f,0.3f,0.3f,1f);
      
//      final int start = 16;
//      final int end = start + 1;
//      for(int i = start; i < end; i++) {
      
//      final int[] list = new int[] { 10, 11, 16 }; // over USA
      final int[] list = new int[] { 0 };//, 1, 2, 3, 4, 5, 6, 7 }; // all children
      final boolean drawNormals = false;
      
      for(final int i : list) {
         final TreeCell cell = tree.getCell("K", i);
         final TrianglePrismVolume volume = (TrianglePrismVolume) cell.getBoundingVolume();
         
         if(i == 11) {
            navigator.setViewingVolume(volume);
         }
         
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
         
         for(int j = 0; j < 8; j++) {
            final TreeCell cellIn = tree.getCell("K" + i, j);
            final TrianglePrismVolume volumeIn = (TrianglePrismVolume) cellIn.getBoundingVolume();
            final Triangle3d[] facesIn = volumeIn.getFaces();

            final TriangleMesh meshIn01 = new TriangleMesh(new Triangle3d[] { facesIn[0], facesIn[1] }, color01);
            meshIn01.setDrawNormals(drawNormals);
            final TriangleMesh meshIn23 = new TriangleMesh(new Triangle3d[] { facesIn[2], facesIn[3] }, color23);
            meshIn23.setDrawNormals(drawNormals);
            final TriangleMesh meshIn45 = new TriangleMesh(new Triangle3d[] { facesIn[4], facesIn[5] }, color45);
            meshIn45.setDrawNormals(drawNormals);
            final TriangleMesh meshInTop = new TriangleMesh(new Triangle3d[] { facesIn[6] }, colorTop);
            meshInTop.setDrawNormals(drawNormals);
            final TriangleMesh meshInBottom = new TriangleMesh(new Triangle3d[] { facesIn[7] }, colorBottom);
            meshInBottom.setDrawNormals(drawNormals);
            this.scene.addRenderable(meshIn01);
            this.scene.addRenderable(meshIn23);
            this.scene.addRenderable(meshIn45);
            this.scene.addRenderable(meshInTop);
            this.scene.addRenderable(meshInBottom);
         }
      }
      
      this.getContentPane().add(this.scene);
      
      SwingUtilities.invokeLater(() -> {
         setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         setLocation(200, 200);
         pack();
         scene.start();
         setVisible(true);
      });
   }
   
   public static void main(final String[] args) {
      new ThesisVisualization();
   }
}
