package com.stephenwranger.thesis.visualization;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingSphere;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.renderables.TriangleMesh;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.geospatial.Earth;
import com.stephenwranger.thesis.geospatial.SphericalNavigator;
import com.stephenwranger.thesis.geospatial.WGS84;
import com.stephenwranger.thesis.icosatree.Icosatree;

public class ThesisVisualization extends JFrame {
   private static final long serialVersionUID = 545923577250987084L;
   
   private final Earth earth;
   private final Scene scene;

   public ThesisVisualization() {
      super("Thesis Visualization");
      
      this.earth = new Earth();
      
      this.scene = new Scene(new Dimension(1600, 1000));
      this.scene.addRenderable(this.earth);
      this.scene.setViewingVolume(new BoundingSphere(new Tuple3d(), WGS84.EQUATORIAL_RADIUS / 2.0));
      
      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      
      final List<Attribute> attributes = new ArrayList<>();
      attributes.add(new Attribute("0,X,0,8,DOUBLE,8,-2671529.167771962,-2670791.4207160836,-2671206.533690431,148.32056658828276"));
      attributes.add(new Attribute("1,Y,8,8,DOUBLE,8,-4469545.061828081,-4468937.152562849,-4469280.47211661,27.343033140313597"));
      attributes.add(new Attribute("2,Z,16,8,DOUBLE,8,3671282.354341662,3671858.9230972063,3671551.800912032,105.13728902370654"));

      final Icosatree tree = new Icosatree(new DataAttributes(attributes), new int[] { 10, 10, 10 });
      
      for(int i = 0; i < 1; i++) {
         final TrianglePrismVolume volume = (TrianglePrismVolume) tree.getBoundingVolume(Character.toString((char)(i + 65)));
         final Triangle3d[] faces = volume.getFaces();
         final Color4f color = new Color4f((float) Math.max(0.5, Math.random()), (float) Math.max(0.5, Math.random()), (float) Math.max(0.5, Math.random()), 1f);
         final TriangleMesh mesh = new TriangleMesh(faces, color);
         this.scene.addRenderable(mesh);
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
      final ThesisVisualization visualization = new ThesisVisualization();
   }
}
