package com.stephenwranger.thesis.geospatial;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

public class DemViewer extends JPanel {
   private final BufferedImage image   = new BufferedImage(1201, 1201, BufferedImage.TYPE_INT_ARGB);
   private File                demfile = null;

   public DemViewer() {
      this.setPreferredSize(new Dimension(800, 800));
   }

   @Override
   public void paintComponent(final Graphics graphics) {
      final Graphics2D g = (Graphics2D) graphics;
      g.setBackground(Color.white);
      g.clearRect(0, 0, this.getWidth(), this.getHeight());
      g.drawImage(this.image, 0, 0, this.getWidth(), this.getHeight(), null);
      g.dispose();
   }

   public void setDemFile(final File demfile) {
      this.demfile = demfile;

      if (this.demfile != null) {
         DigitalElevationUtils.toImage(demfile, this.image);
      }

      this.repaint();
   }

   public static void main(final String[] args) {
      final DemViewer viewer = new DemViewer();

      final JFileChooser chooser = new JFileChooser(DigitalElevationUtils.DEM3_DIRECTORY);
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setFileFilter(new FileFilter() {
         @Override
         public boolean accept(final File f) {
            return f.isDirectory() || f.getAbsolutePath().toLowerCase().endsWith(".hgt");
         }

         @Override
         public String getDescription() {
            return "*.hgt files";
         }
      });

      final JButton button = new JButton("Select HGT File...");
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            final int choice = chooser.showOpenDialog(viewer);

            if (choice == JFileChooser.APPROVE_OPTION) {
               viewer.setDemFile(chooser.getSelectedFile());
            }
         }
      });
      final JFrame frame = new JFrame("DEM Viewer");

      SwingUtilities.invokeLater(() -> {
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         frame.setLocation(100, 100);
         frame.getContentPane().setLayout(new BorderLayout());
         frame.getContentPane().add(viewer, BorderLayout.CENTER);
         frame.getContentPane().add(button, BorderLayout.SOUTH);
         frame.pack();
         frame.setVisible(true);
      });
   }
}
