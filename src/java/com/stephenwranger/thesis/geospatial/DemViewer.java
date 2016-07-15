package com.stephenwranger.thesis.geospatial;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel.MapMode;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
         final short[][] values = new short[1201][1201];
         short max = Short.MIN_VALUE;

         try (final RandomAccessFile raf = new RandomAccessFile(this.demfile, "r")) {
            final ShortBuffer buffer = raf.getChannel().map(MapMode.READ_ONLY, 0, this.demfile.length()).asShortBuffer();

            for (int x = 0; x < 1201; x++) {
               for (int y = 0; y < 1201; y++) {
                  values[x][y] = buffer.get((y * 1201) + x);
                  max = max < values[x][y] ? values[x][y] : max;
               }
            }
         } catch (final IOException e) {
            e.printStackTrace();
         }

         for (int x = 0; x < 1201; x++) {
            for (int y = 0; y < 1201; y++) {
               final float value = values[x][y] / (float) max;
               this.image.setRGB(x, y, new Color((y <= 10) ? 0f : value, value, (y <= 10) ? 0f : value, 1f).getRGB());
            }
         }
      }

      this.repaint();
   }

   public static void main(final String[] args) {
      if (args.length != 1) {
         System.err.println("USAGE: java DemViewer <input file>");
         System.exit(1);
      } else {
         final File file = new File(args[0]);

         final DemViewer viewer = new DemViewer();

         if (file.exists()) {
            viewer.setDemFile(file);
         }

         final JFrame frame = new JFrame("DEM Viewer");

         SwingUtilities.invokeLater(() -> {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocation(100, 100);
            frame.getContentPane().add(viewer);
            frame.pack();
            frame.setVisible(true);
         });
      }
   }
}
