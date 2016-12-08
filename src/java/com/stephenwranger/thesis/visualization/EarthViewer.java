package com.stephenwranger.thesis.visualization;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.thesis.geospatial.Earth;
import com.stephenwranger.thesis.geospatial.SphericalNavigator;

public class EarthViewer extends JFrame {
   private static final long serialVersionUID = 545923577250987084L;
   private static final int  OPTIONS_WIDTH    = 200;

   private final Earth       earth;
   private final Scene       scene;

   public EarthViewer() {
      super("Earth Viewer");

      this.earth = new Earth();
      this.earth.setWireframe(false);
      this.earth.setLightingEnabled(false);
      this.earth.setLoadFactor(0.75);
      this.earth.setAltitudeOffset(-100);

      this.scene = new Scene(new Dimension(1200, 900));
      this.scene.addRenderable(this.earth);
      this.scene.setOriginEnabled(true);

      final SphericalNavigator navigator = new SphericalNavigator(this.scene);
      navigator.moveTo(-120.8566862, 35.3721688, 0, SphericalNavigator.AZIMUTH_EAST, SphericalNavigator.ELEVATION_ZENITH / 2.0, 400);
      navigator.setEarth(this.earth);
      this.scene.addPreRenderable(navigator);

      final JPanel options = new JPanel();
      options.setLayout(new BoxLayout(options, BoxLayout.PAGE_AXIS));
      options.setPreferredSize(new Dimension(EarthViewer.OPTIONS_WIDTH, 500));

      this.addSpinner(options, "Earth Altitude Offset", this.earth.getAltitudeOffset(), -Double.MAX_VALUE, Double.MAX_VALUE, 0.1, (value) -> {
         this.earth.setAltitudeOffset(value.doubleValue());
      });

      final JCheckBox wireframeCheckBox = this.addCheckBox(options, "Earth Wireframe", this.earth.isWireframe(), this.earth::setWireframe);

      this.getContentPane().setLayout(new BorderLayout());
      this.getContentPane().add(this.scene, BorderLayout.CENTER);
      this.getContentPane().add(options, BorderLayout.WEST);

      this.scene.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(final KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_W) {
               wireframeCheckBox.doClick();
            } else if (event.getKeyCode() == KeyEvent.VK_L) {
               EarthViewer.this.earth.setLightingEnabled(!EarthViewer.this.earth.isLightingEnabled());
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

   private JCheckBox addCheckBox(final JPanel parent, final String checkBoxLabel, final boolean initialValue, final Consumer<Boolean> eventListener) {
      final JLabel label = new JLabel(checkBoxLabel);
      final JCheckBox checkBox = new JCheckBox();
      checkBox.setSelected(initialValue);
      final JPanel panel = new JPanel();
      panel.setLayout(new GridLayout(1, 2));
      panel.setMaximumSize(new Dimension(EarthViewer.OPTIONS_WIDTH, 30));
      panel.add(label);
      panel.add(checkBox);
      parent.add(panel);

      checkBox.addActionListener((event) -> {
         eventListener.accept(checkBox.isSelected());
      });

      return checkBox;
   }

   private JSpinner addSpinner(final JPanel parent, final String spinnerLabel, final Number initialValue, final Comparable<? extends Number> min, final Comparable<? extends Number> max, final Number step,
         final Consumer<Number> eventListener) {
      final JLabel label = new JLabel(spinnerLabel);
      final JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialValue, min, max, step));
      final JPanel panel = new JPanel();
      panel.setLayout(new GridLayout(1, 2));
      panel.setMaximumSize(new Dimension(EarthViewer.OPTIONS_WIDTH, 30));
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
      new EarthViewer();
   }
}
