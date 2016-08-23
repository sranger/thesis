package com.stephenwranger.thesis.visualization;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
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
import javax.swing.filechooser.FileFilter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.Tuple3d;
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
      this.earth.setAltitudeOffset(-100);

      this.scene = new Scene(new Dimension(1200, 750));
      this.scene.addRenderable(this.earth);
      this.scene.setOriginEnabled(true);

      //      this.addFrustumRenderable();

      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      navigator.moveTo(-120.8643, 35.371, 0, SphericalNavigator.AZIMUTH_SOUTH, SphericalNavigator.ELEVATION_ZENITH, 2000);
      navigator.setEarth(this.earth);
      this.scene.addPreRenderable(navigator);

      // this.loadIcosatreeBounds("");

      final File attributeFile = new File(basePath, "attributes.csv");
      final TreeRenderable tree = (attributeFile.isFile()) ? new TreeRenderable(basePath, connectionType) : null;
      tree.setLevelOfDetail(1);
      this.scene.addRenderable(tree);

      final ContextAwarePointSelection pointSelector = new ContextAwarePointSelection(this.scene, tree);
      this.scene.addPostProcessor(pointSelector);

      // this.addTimingDisplay(scene, renderer);

      final JPanel options = new JPanel();
      options.setLayout(new BoxLayout(options, BoxLayout.PAGE_AXIS));
      options.setPreferredSize(new Dimension(300, 500));

      if (tree != null) {
         this.addSpinner(options, "Split Ratio (screen area)", tree.getLevelOfDetail(), 0.1, Double.MAX_VALUE, 0.1, (value) -> {
            tree.setLevelOfDetail(value.doubleValue());
         });
      }

      this.addSpinner(options, "Grid Size (meters)", pointSelector.getMinDensity(), 0.1, Double.MAX_VALUE, 0.1, (value) -> {
         pointSelector.setGridSizeMeters(value.doubleValue());
      });

      this.addSpinner(options, "k-Nearest Count", pointSelector.getNeighborCount(), 3, Integer.MAX_VALUE, 1, (value) -> {
         pointSelector.setNeighborCount(value.intValue());
      });

      this.addSpinner(options, "Normal Offset", pointSelector.getNormalOffset(), 0.0, 1.0, 0.01, (value) -> {
         pointSelector.setNormalOffset(value.doubleValue());
      });

      this.addSpinner(options, "Min Density", pointSelector.getMinDensity(), 0.0, Double.MAX_VALUE, 0.1, (value) -> {
         pointSelector.setMinDensity(value.doubleValue());
      });

      this.addCheckBox(options, "Draw Points", pointSelector.isDrawPoints(), pointSelector::setDrawPoints);
      this.addCheckBox(options, "Draw Triangles", pointSelector.isDrawTriangles(), pointSelector::setDrawTriangles);

      final JCheckBox drawEarthCheckBox = this.addCheckBox(options, "Draw Earth", this.earth.inScene(), (isSelected) -> {
         if (isSelected && !ThesisVisualization.this.earth.inScene()) {
            ThesisVisualization.this.scene.addRenderable(ThesisVisualization.this.earth);
         } else if (!isSelected && ThesisVisualization.this.earth.inScene()) {
            ThesisVisualization.this.earth.remove();
         }
      });

      this.addSpinner(options, "Earth Altitude Offset", this.earth.getAltitudeOffset(), -Double.MAX_VALUE, Double.MAX_VALUE, 0.1, (value) -> {
         this.earth.setAltitudeOffset(value.doubleValue());
      });

      this.addCheckBox(options, "Enable Point Pruning", pointSelector.isPruningEnabled(), pointSelector::setPruningEnabled);
      this.addCheckBox(options, "Pruning Orthonormal", pointSelector.isPruningOrthonormal(), pointSelector::setPruningOrthonormal);
      this.addCheckBox(options, "Pruning Altitude", pointSelector.isPruningAltitude(), pointSelector::setPruningAltitude);
      this.addSpinner(options, "Altitude ratio removal", pointSelector.getGroundDistanceRatio(), 0.0, 1.0, 0.1, (value) -> {
         pointSelector.setGroundDistanceRatio(value.doubleValue());
      });

      final JCheckBox wireframeCheckBox = this.addCheckBox(options, "Earth Wireframe", this.earth.isWireframe(), this.earth::setWireframe);

      final JFileChooser chooser = new JFileChooser();
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setFileFilter(new FileFilter() {
         @Override
         public boolean accept(final File f) {
            return (!f.exists() && f.getName().toLowerCase().endsWith("*.csv")) || f.isDirectory();
         }

         @Override
         public String getDescription() {
            return "*.csv";
         }
      });

      this.addButton(options, "Export Selected Points", () -> {
         final Collection<Tuple3d> points = pointSelector.getSelectedPoints();
         final int choice = chooser.showSaveDialog(this.scene);

         if (choice == JFileChooser.APPROVE_OPTION) {
            try (final BufferedWriter fout = new BufferedWriter(new FileWriter(chooser.getSelectedFile()))) {
               fout.write("id,x,y,z");
               int i = 0;

               for (final Tuple3d point : points) {
                  fout.write("\n" + i + "," + point.x + "," + point.y + "," + point.z);
                  i++;
               }
            } catch (final IOException e) {
               e.printStackTrace();
            }
         }
      });

      this.addButton(options, "Export Triangulation", () -> {
         final Collection<Triangle3d> triangles = pointSelector.getTriangulation();
         final int choice = chooser.showSaveDialog(this.scene);

         if (choice == JFileChooser.APPROVE_OPTION) {
            try (final BufferedWriter fout = new BufferedWriter(new FileWriter(chooser.getSelectedFile()))) {
               fout.write("id,order,x,y,z");
               int i = 0;

               for (final Triangle3d triangle : triangles) {
                  final Tuple3d[] corners = triangle.getCorners();

                  for (int j = 0; j < corners.length; j++) {
                     fout.write("\n" + i + "," + j + "," + corners[j].x + "," + corners[j].y + "," + corners[j].z);
                  }
                  i++;
               }
            } catch (final IOException e) {
               e.printStackTrace();
            }
         }
      });

      if (tree != null) {
         final Timings timings = tree.getTimings();
         final JTextArea timingsArea = new JTextArea();
         final JScrollPane timingsScroll = new JScrollPane(timingsArea);
         timingsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
         timingsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
         options.add(timingsScroll);

         final long[] last = new long[] { 0 };

         this.scene.addPostProcessor((gl, glu, scene) -> {
            final long time = System.nanoTime();

            if ((last[0] + 1000000000) > time) {
               last[0] = time;
               timingsArea.setText("Point Count: " + tree.getPointsRendered() + "\n" + timings.toString());
            }
         });
      }

      this.getContentPane().setLayout(new BorderLayout());
      this.getContentPane().add(this.scene, BorderLayout.CENTER);
      this.getContentPane().add(options, BorderLayout.WEST);

      this.scene.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(final KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_W) {
               wireframeCheckBox.doClick();
            } else if (event.getKeyCode() == KeyEvent.VK_E) {
               drawEarthCheckBox.doClick();
            } else if (event.getKeyCode() == KeyEvent.VK_L) {
               ThesisVisualization.this.earth.setLightingEnabled(!ThesisVisualization.this.earth.isLightingEnabled());
            }
         }
      });

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
               System.out.println("frustum: inScene? " + frustum.inScene() + ", isPaused? " + frustum.isPaused());
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

   private JButton addButton(final JPanel parent, final String label, final Runnable runnable) {
      final JButton button = new JButton(label);
      final JPanel panel = new JPanel(new GridLayout(1, 1));
      panel.add(button);
      panel.setMaximumSize(new Dimension(300, 30));
      panel.add(button);
      parent.add(panel);

      button.addActionListener((event) -> {
         runnable.run();
      });

      return button;
   }

   private JCheckBox addCheckBox(final JPanel parent, final String checkBoxLabel, final boolean initialValue, final Consumer<Boolean> eventListener) {
      final JLabel label = new JLabel(checkBoxLabel);
      final JCheckBox checkBox = new JCheckBox();
      checkBox.setSelected(initialValue);
      final JPanel panel = new JPanel();
      panel.setLayout(new GridLayout(1, 2));
      panel.setMaximumSize(new Dimension(300, 30));
      panel.add(label);
      panel.add(checkBox);
      parent.add(panel);

      checkBox.addActionListener((event) -> {
         eventListener.accept(checkBox.isSelected());
      });

      return checkBox;
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

   private JSpinner addSpinner(final JPanel parent, final String spinnerLabel, final Number initialValue, final Comparable<? extends Number> min, final Comparable<? extends Number> max, final Number step,
         final Consumer<Number> eventListener) {
      final JLabel label = new JLabel(spinnerLabel);
      final JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialValue, min, max, step));
      final JPanel panel = new JPanel();
      panel.setLayout(new GridLayout(1, 2));
      panel.setMaximumSize(new Dimension(300, 30));
      panel.add(label);
      panel.add(spinner);
      parent.add(panel);

      spinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(final ChangeEvent e) {
            eventListener.accept(((Number) spinner.getValue()));
         }
      });

      return spinner;
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
