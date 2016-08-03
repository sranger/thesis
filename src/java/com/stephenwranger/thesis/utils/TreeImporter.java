package com.stephenwranger.thesis.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import com.stephenwranger.graphics.utils.TimeUtils;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.Point;

public class TreeImporter {
   private static final String USAGE = "TreeBuilder "
         + "<input directory> "                          // 0
         + "<output directory> "                         // 1
         + "<read depth> ";                              // 2
   private static final int INPUT_DIRECTORY_INDEX = 0;
   private static final int OUTPUT_DIRECTORY_INDEX = 1;
   private static final int READ_DEPTH_INDEX = 2;
   
   public enum TreeTypes {
      OCTREE, ICOSATREE;
   }
   
   private final File inputDirectory;
   private final int maxDepth;
   private final List<Point> points = new ArrayList<>();
   
   private DataAttributes attributes = null;
   
   public TreeImporter(final File inputDirectory, final int maxDepth) {
      this.inputDirectory = inputDirectory;
      this.maxDepth = maxDepth;
      
      this.importData();
   }
   
   private void importData() {
      final long startTime = System.nanoTime();
      System.out.println("importing data from " + this.inputDirectory);
      System.out.println("\treading attributes...");
      this.attributes = new DataAttributes(readAttributes(this.inputDirectory, "attributes.csv"));
      
      System.out.println("\t\tattributes read: " + attributes.getAttributeNames());
      System.out.println("\treading data...");
      readDirectory(this.inputDirectory, 0);
      System.out.println("\t\tpoint data read: " + points.size());
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   private void readDirectory(final File directory, final int depth) {
      try {
         for(final String name : directory.list()) {
            final File path = new File(directory, name);
            
            if(path.isDirectory()) {
               if(depth < this.maxDepth || this.maxDepth == -1) {
                  readDirectory(path, depth + 1);
               }
            } else if(name.endsWith(".dat")) {
               try(final BufferedInputStream fin = new BufferedInputStream(new FileInputStream(path))) {
                  final byte[] buffer = new byte[this.attributes.stride];
                  int index = -1;
                  
                  // read points until file is empty
                  while((index = fin.read(buffer)) != -1) {
                     if(index == buffer.length) {
                        final Point point = new Point(this.attributes, buffer);
                        points.add(point);
                        
                        if(points.size() % 1000000 == 0) {
                           System.out.println("completed " + (points.size() / 1000000) + " million");
                        }
                     } else {
                        throw new IOException("Could not read full buffer");
                     }
                  }
               } catch(final IOException e) {
                  e.printStackTrace();
               }
            }
         }
      } catch(final OutOfMemoryError e) {
         System.err.println("OutOfMemory: " + this.points.size() + " completed.");
         throw e;
      }
   }
   
   public void exportFlat(final File outputDirectory) {
      final long startTime = System.nanoTime();
      double currentPrintPercentage = 0;
      int count = 0;
      
      System.out.println("exporting flat file points to " + outputDirectory + " at attributes.csv and points.data");
      
      final StringBuilder sb = new StringBuilder();
      sb.append(Attribute.HEADER);
      
      for(final Attribute attribute : this.attributes) {
         sb.append("\n");
         sb.append(attribute.toString());
      }
      
      try(final BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputDirectory, "attributes.csv")))) {
         writer.write(sb.toString());
      } catch(final IOException e) {
         throw new RuntimeException("Could not export attributes.csv", e);
      }
      
      try(final BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(new File(outputDirectory, "points.dat")))) {
         for(final Point p : this.points) {
            fout.write(p.getRawData().array());
            
            double percentage = (count / (double) points.size());
            percentage *= 10000.0;
            percentage = ((int) percentage) / 100.0;
            
            if(percentage >= currentPrintPercentage + 0.1) {
               final long elapsed = (System.nanoTime() - startTime);
               final long eta = (long) (((100.0 - percentage) * elapsed) / percentage);
               System.out.println("[" + percentage + "%]: " + count + " of " + points.size() + " completed. Elapsed: " + TimeUtils.formatNanoseconds(elapsed) + ", ETA: " + TimeUtils.formatNanoseconds(eta));
               currentPrintPercentage += 0.1;
            }
            
            count++;
         }
      } catch(final IOException e) {
         throw new RuntimeException("Could not export points.dat", e);
      }
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   private static List<Attribute> readAttributes(final File directory, final String name) {
      final List<Attribute> attributes = new ArrayList<>();
      
      try(final BufferedReader reader = new BufferedReader(new FileReader(new File(directory, name)))) {
         final String header = reader.readLine();
         
         if(header.equals(Attribute.HEADER)) {
            String line = null;
            
            while((line = reader.readLine()) != null) {
               attributes.add(new Attribute(line));
            }
         } else {
            throw new RuntimeException("root.csv unknown format; header not recognized: " + header);
         }
      } catch (final IOException e) {
         throw new RuntimeException("Could not read input attributes", e);
      }
      
      return attributes;
   }
   
   public static void main(final String[] args) throws IOException {
      if(args.length != 7 && args.length != 9) {
         throw new IllegalArgumentException(USAGE);
      }
      
      final File inputDirectory = new File(args[INPUT_DIRECTORY_INDEX]);
      final File outputDirectory = new File(args[OUTPUT_DIRECTORY_INDEX]);
      final int maxDepth = Integer.parseInt(args[READ_DEPTH_INDEX]);
      
      if(!inputDirectory.exists() || !inputDirectory.isDirectory()) {
         throw new IllegalArgumentException("Input Directory must exist and be a directory.");
      }
      
      if(!outputDirectory.exists()) {
         System.out.println("Creating output directory...");
         outputDirectory.mkdirs();
         
         if(outputDirectory.exists()) {
            System.out.println("Success!");
         } else {
            throw new IOException("Could not create output directory.");
         }
      } else if(!outputDirectory.isDirectory()) {
         throw new IllegalArgumentException("Output Directory must exist and be a directory.");
      }
      
      if(outputDirectory.list().length != 0) {
         final int value = JOptionPane.showConfirmDialog(null, "Output Directory is not empty; would you like to delete its contents?", "Confirm Output Directory Purge", JOptionPane.YES_NO_OPTION);
         
         if(value != JOptionPane.OK_OPTION) {
            throw new RuntimeException("Output directory not empty; user requested it be left in tact. Please change the output directory before proceeding. Exiting.");
         }
         
         System.out.println("Purging contents of output directory...");
         
         purgeContents(outputDirectory);
      }
      
      final TreeImporter builder = new TreeImporter(inputDirectory, maxDepth);
      builder.exportFlat(outputDirectory);
   }
   
   /**
    * Recursively deletes contents of given directory.
    * 
    * @param directory the directory to purge
    */
   private static void purgeContents(final File directory) {
      for(final String name : directory.list()) {
         final File file = new File(directory, name);
         
         if(file.isDirectory()) {
            purgeContents(file);
         }
         
         file.delete();
      }
   }
}
