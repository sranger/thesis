package com.stephenwranger.thesis.visualization;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.renderables.FrustumRenderable;
import com.stephenwranger.graphics.renderables.TextRenderable;
import com.stephenwranger.graphics.renderables.TriangleMesh;
import com.stephenwranger.graphics.utils.Timings;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeServerProcessor.ConnectionType;
import com.stephenwranger.thesis.data.TreeStructure;
import com.stephenwranger.thesis.geospatial.Earth;
import com.stephenwranger.thesis.geospatial.EarthImagery.ImageryType;
import com.stephenwranger.thesis.geospatial.SphericalNavigator;
import com.stephenwranger.thesis.icosatree.Icosatree;
import com.stephenwranger.thesis.renderables.TreeRenderable;
import com.stephenwranger.thesis.selection.ContextAwarePointSelection;

public class ThesisVisualization extends JFrame {
   private static final long   serialVersionUID = 545923577250987084L;
   private static final String USAGE            = "java ThesisVisualization <tree_path> <FILESYSTEM|HTTP>";

   private final Earth         earth;
   private final Scene         scene;

   public ThesisVisualization(final String basePath, final ConnectionType connectionType) {
      super("Thesis Visualization");

      this.earth = new Earth(ImageryType.OPEN_STREET_MAP);
      this.earth.setWireframe(false);
      this.earth.setLightingEnabled(false);
      this.earth.setLoadFactor(0.75);
      this.earth.setAltitudeOffset(0);

      this.scene = new Scene(new Dimension(1200, 750));
      this.scene.addRenderable(this.earth);
      this.scene.setOriginEnabled(true);

      this.scene.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(final KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_W) {
               ThesisVisualization.this.earth.setWireframe(!ThesisVisualization.this.earth.isWireframe());
            } else if (event.getKeyCode() == KeyEvent.VK_E) {
               if (ThesisVisualization.this.earth.inScene()) {
                  ThesisVisualization.this.earth.remove();
               } else {
                  ThesisVisualization.this.scene.addRenderable(ThesisVisualization.this.earth);
               }
            } else if (event.getKeyCode() == KeyEvent.VK_L) {
               ThesisVisualization.this.earth.setLightingEnabled(!ThesisVisualization.this.earth.isLightingEnabled());
            } else if (event.getKeyCode() == KeyEvent.VK_LEFT) {
               ThesisVisualization.this.earth.setLoadFactor(Math.max(0.01, ThesisVisualization.this.earth.getLoadFactor() - 0.01));
            } else if (event.getKeyCode() == KeyEvent.VK_RIGHT) {
               ThesisVisualization.this.earth.setLoadFactor(ThesisVisualization.this.earth.getLoadFactor() + 0.01);
            }
         }
      });

      //      this.addFrustumRenderable();

      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      navigator.moveTo(-120.8643, 35.371, 0, 0, 0, 100);
      //      navigator.setEarth(this.earth);
      this.scene.addPreRenderable(navigator);

      // this.loadIcosatreeBounds("");

      final TreeRenderable tree = new TreeRenderable(basePath, connectionType);
      tree.setLevelOfDetail(0.1);
      this.scene.addRenderable(tree);

      final ContextAwarePointSelection pointSelector = new ContextAwarePointSelection(this.scene, tree);
      this.scene.addPostProcessor(pointSelector);

      // this.addTimingDisplay(scene, renderer);

      final JPanel options = new JPanel();
      options.setLayout(new BoxLayout(options, BoxLayout.PAGE_AXIS));
      options.setPreferredSize(new Dimension(300, 500));

      final JLabel ratioLabel = new JLabel("Split Ratio (screen area)");
      final JSpinner ratioSpinner = new JSpinner(new SpinnerNumberModel(tree.getLevelOfDetail(), 0.1, Double.MAX_VALUE, 0.1));
      final JPanel ratioSpinnerPanel = new JPanel();
      ratioSpinnerPanel.setLayout(new GridLayout(1, 2));
      ratioSpinnerPanel.setMaximumSize(new Dimension(300, 30));
      ratioSpinnerPanel.add(ratioLabel);
      ratioSpinnerPanel.add(ratioSpinner);
      options.add(ratioSpinnerPanel);

      ratioSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            tree.setLevelOfDetail(((Number) ratioSpinner.getValue()).doubleValue());
         }
      });

      final JLabel gridLabel = new JLabel("Grid Size (meters)");
      final JSpinner gridSpinner = new JSpinner(new SpinnerNumberModel(pointSelector.getGridSizeMeters(), 0.1, Double.MAX_VALUE, 0.1));
      final JPanel gridSpinnerPanel = new JPanel();
      gridSpinnerPanel.setLayout(new GridLayout(1, 2));
      gridSpinnerPanel.setMaximumSize(new Dimension(300, 30));
      gridSpinnerPanel.add(gridLabel);
      gridSpinnerPanel.add(gridSpinner);
      options.add(gridSpinnerPanel);

      gridSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            pointSelector.setGridSizeMeters(((Number) gridSpinner.getValue()).doubleValue());
         }
      });

      final JLabel nearestLabel = new JLabel("k-Nearest Count");
      final JSpinner nearestSpinner = new JSpinner(new SpinnerNumberModel(pointSelector.getNeighborCount(), 3, Integer.MAX_VALUE, 1));
      final JPanel nearestSpinnerPanel = new JPanel();
      nearestSpinnerPanel.setLayout(new GridLayout(1, 2));
      nearestSpinnerPanel.setMaximumSize(new Dimension(300, 30));
      nearestSpinnerPanel.add(nearestLabel);
      nearestSpinnerPanel.add(nearestSpinner);
      options.add(nearestSpinnerPanel);

      nearestSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            pointSelector.setNeighborCount(((Number) nearestSpinner.getValue()).intValue());
         }
      });

      final JLabel normalLabel = new JLabel("Normal Offset");
      final JSpinner normalSpinner = new JSpinner(new SpinnerNumberModel(pointSelector.getNormalOffset(), 0.0, 1.0, 0.01));
      final JPanel normalSpinnerPanel = new JPanel();
      normalSpinnerPanel.setLayout(new GridLayout(1, 2));
      normalSpinnerPanel.setMaximumSize(new Dimension(300, 30));
      normalSpinnerPanel.add(normalLabel);
      normalSpinnerPanel.add(normalSpinner);
      options.add(normalSpinnerPanel);

      normalSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            pointSelector.setNormalOffset(((Number) normalSpinner.getValue()).doubleValue());
         }
      });

      final JLabel minDensityLabel = new JLabel("Min Density");
      final JSpinner minDensitySpinner = new JSpinner(new SpinnerNumberModel(pointSelector.getMinDensity(), 0.0, Double.MAX_VALUE, 0.1));
      final JPanel minDensityPanel = new JPanel();
      minDensityPanel.setLayout(new GridLayout(1, 2));
      minDensityPanel.setMaximumSize(new Dimension(300, 30));
      minDensityPanel.add(minDensityLabel);
      minDensityPanel.add(minDensitySpinner);
      options.add(minDensityPanel);

      minDensitySpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            pointSelector.setMinDensity(((Number) minDensitySpinner.getValue()).doubleValue());
         }
      });

      final JLabel drawPointsLabel = new JLabel("Draw Points");
      final JCheckBox drawPointsCheckBox = new JCheckBox();
      if (pointSelector.isDrawPoints()) {
         drawPointsCheckBox.setSelected(true);
      }
      final JPanel drawPointsPanel = new JPanel();
      drawPointsPanel.setLayout(new GridLayout(1, 2));
      drawPointsPanel.setMaximumSize(new Dimension(300, 30));
      drawPointsPanel.add(drawPointsLabel);
      drawPointsPanel.add(drawPointsCheckBox);
      options.add(drawPointsPanel);

      drawPointsCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            pointSelector.setDrawPoints(drawPointsCheckBox.isSelected());
         }
      });

      final JLabel drawTrianglesLabel = new JLabel("Draw Triangles");
      final JCheckBox drawTrianglesCheckBox = new JCheckBox();
      if (pointSelector.isDrawTriangles()) {
         drawTrianglesCheckBox.setSelected(true);
      }
      final JPanel drawTrianglesPanel = new JPanel();
      drawTrianglesPanel.setLayout(new GridLayout(1, 2));
      drawTrianglesPanel.setMaximumSize(new Dimension(300, 30));
      drawTrianglesPanel.add(drawTrianglesLabel);
      drawTrianglesPanel.add(drawTrianglesCheckBox);
      options.add(drawTrianglesPanel);

      drawTrianglesCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            pointSelector.setDrawTriangles(drawTrianglesCheckBox.isSelected());
         }
      });

      final JLabel drawEarthLabel = new JLabel("Draw Earth");
      final JCheckBox drawEarthCheckBox = new JCheckBox();
      drawEarthCheckBox.setSelected(this.earth.inScene());
      final JPanel drawEarthPanel = new JPanel();
      drawEarthPanel.setLayout(new GridLayout(1, 2));
      drawEarthPanel.setMaximumSize(new Dimension(300, 30));
      drawEarthPanel.add(drawEarthLabel);
      drawEarthPanel.add(drawEarthCheckBox);
      options.add(drawEarthPanel);

      drawEarthCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            if (drawEarthCheckBox.isSelected() && !ThesisVisualization.this.earth.inScene()) {
               ThesisVisualization.this.scene.addRenderable(ThesisVisualization.this.earth);
            } else if (!drawEarthCheckBox.isSelected() && ThesisVisualization.this.earth.inScene()) {
               ThesisVisualization.this.earth.remove();
            }
         }
      });

      final JLabel earthElevationLabel = new JLabel("Earth Altitude Offset");
      final JSpinner earthElevationSpinner = new JSpinner(new SpinnerNumberModel(this.earth.getAltitudeOffset(), -Double.MAX_VALUE, Double.MAX_VALUE, 0.1));
      final JPanel earthElevationPanel = new JPanel();
      earthElevationPanel.setLayout(new GridLayout(1, 2));
      earthElevationPanel.setMaximumSize(new Dimension(300, 30));
      earthElevationPanel.add(earthElevationLabel);
      earthElevationPanel.add(earthElevationSpinner);
      options.add(earthElevationPanel);

      earthElevationSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            ThesisVisualization.this.earth.setAltitudeOffset(((Number) earthElevationSpinner.getValue()).doubleValue());
         }
      });

      final JLabel wireframeLabel = new JLabel("Earth Wireframe");
      final JCheckBox wireframeCheckBox = new JCheckBox();
      wireframeCheckBox.setSelected(this.earth.isWireframe());
      final JPanel wireframePanel = new JPanel();
      wireframePanel.setLayout(new GridLayout(1, 2));
      wireframePanel.setMaximumSize(new Dimension(300, 30));
      wireframePanel.add(wireframeLabel);
      wireframePanel.add(wireframeCheckBox);
      options.add(wireframePanel);

      wireframeCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            ThesisVisualization.this.earth.setWireframe(wireframeCheckBox.isSelected());
         }
      });

      final Timings timings = tree.getTimings();
      final JTextArea timingsArea = new JTextArea();
      final JScrollPane timingsScroll = new JScrollPane(timingsArea);
      timingsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      timingsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      options.add(timingsScroll);

      this.scene.addPostProcessor((gl, glu, scene) -> {
         timingsArea.setText("Point Count: " + tree.getPointsRendered() + "\n" + timings.toString());
      });

      this.getContentPane().setLayout(new BorderLayout());
      this.getContentPane().add(this.scene, BorderLayout.CENTER);
      this.getContentPane().add(options, BorderLayout.WEST);

      SwingUtilities.invokeLater(() -> {
         this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         this.setLocation(100, 100);
         this.pack();
         this.scene.start();
         this.setVisible(true);
      });
   }

   public void addFrustumRenderable() {
      final FrustumRenderable frustum = new FrustumRenderable();
      this.scene.addRenderable(frustum);

      this.scene.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(final KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_F) {
               frustum.setPaused(!frustum.isPaused());
            }
         }
      });
   }

   public void addTimingDisplay(final Scene scene, final TreeRenderable renderer) {
      final TextRenderable timingRenderable = new TextRenderable(new Font("Monospaced", Font.PLAIN, 14)) {

         @Override
         public synchronized void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
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
         System.err.println(ThesisVisualization.USAGE);
         e.printStackTrace();
      }
   }
}
