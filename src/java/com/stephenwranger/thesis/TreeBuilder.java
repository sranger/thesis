package com.stephenwranger.thesis;

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
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;
import com.stephenwranger.thesis.icosatree.Icosatree;
import com.stephenwranger.thesis.octree.Octree;

public class TreeBuilder {
   private static final String USAGE = "TreeBuilder <type [octree|icosatree; default octree]> <input directory> <output directory> <cell split x> [<cell split y> <cell split z>]";
   
   public enum TreeTypes {
      OCTREE, ICOSATREE;
   }
   
   private final TreeTypes type;
   private final File inputDirectory;
   private final int[] cellSplit;
   
   private final List<Point> points = new ArrayList<>();
   private TreeStructure tree = null;
   private DataAttributes attributes = null;
   
   public TreeBuilder(final String type, final File inputDirectory, final int[] cellSplit) {
      final TreeTypes temp = TreeTypes.valueOf(type.toUpperCase());
      this.type = (temp == null) ? TreeTypes.OCTREE : temp;
      this.inputDirectory = inputDirectory;
      this.cellSplit = cellSplit;
      
      this.importData();
   }
   
   private void importData() {
      final long startTime = System.nanoTime();
      System.out.println("importing data from " + this.inputDirectory);
      System.out.println("\treading attributes...");
      this.attributes = new DataAttributes(readAttributes(this.inputDirectory, "root.csv"));
      
      switch(this.type) {
         case OCTREE:
            this.tree = new Octree(this.attributes, this.cellSplit);
            break;
         case ICOSATREE:
            this.tree = new Icosatree(this.attributes, this.cellSplit);
            break;
      }
      System.out.println("\t\tattributes read: " + attributes.getAttributeNames());
      System.out.println("\treading data...");
      readDirectory(this.inputDirectory);
      System.out.println("\t\tpoint data read: " + points.size());
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   private void readDirectory(final File directory) {
      try {
         for(final String name : directory.list()) {
            final File path = new File(directory, name);
            
            if(path.isDirectory()) {
               readDirectory(path);
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
   
   public void build() {
      if(tree == null) {
         throw new RuntimeException("Cannot build tree before initialization is complete");
      } else {
         final long startTime = System.nanoTime();
         int count = 0;
         System.out.println("building tree...");
         for(final Point point : this.points) {
            this.tree.addPoint(point);
            count++;
            
            if(count % 1000000 == 0) {
               double percentage = (count / (double) points.size());
               percentage *= 10000.0;
               percentage = ((int) percentage) / 100.0;
               System.out.println("completed " + count + " of " + points.size() + " (" + percentage + " %)");
            }
         }
         
         System.out.println("tree built: " + this.tree.getCellCount());
         final long endTime = System.nanoTime();
         System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
      }
   }
   
   public void export(final File outputDirectory) {
      if(tree == null) {
         throw new RuntimeException("Cannot build tree before initialization is complete");
      } else {
         final long startTime = System.nanoTime();
         System.out.println("exporting tree to " + outputDirectory);
         
         for(final TreeCell treeCell : this.tree) {
            final String path = treeCell.getPath();
            File datFile = null;
            File metaFile = null;
            
            if(path.isEmpty()) {
               datFile = new File(outputDirectory, "/root.dat");
               metaFile = new File(outputDirectory, "/root.txt");
            } else {
               final String[] split = treeCell.getPath().substring(0, treeCell.getPath().length()).split("");
               final char childIndex = treeCell.getPath().charAt(treeCell.getPath().length()-1);
               final String dirPath = String.join("/", split);
               final String datName = childIndex + ".dat";
               final String metaName = childIndex + ".txt";
               final File dir = new File(outputDirectory, dirPath);
               dir.mkdirs();
               datFile = new File(dir, "/" + datName);
               metaFile = new File(dir, "/" + metaName);
            }
            
            try(final BufferedWriter fout = new BufferedWriter(new FileWriter(metaFile))) {
               fout.write(String.join(",", treeCell.getChildList()));
               
               if(path.isEmpty()) {
                  fout.write("\n");
                  fout.write(Integer.toString(this.getMaxPointCount()));
               }
            } catch(final IOException e) {
               throw new RuntimeException("Could not write tree cell metadata: " + metaFile.getAbsolutePath(), e);
            }
            
            try(final BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(datFile))) {
               for(final Point p : treeCell) {
                  fout.write(p.getRawData().array());
               }
            } catch(final IOException e) {
               throw new RuntimeException("Could not write tree cell point data: " + datFile.getAbsolutePath(), e);
            }
         }
         
         final long endTime = System.nanoTime();
         System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
      }
   }
   
   public void exportFlat(final File outputDirectory) {
      final long startTime = System.nanoTime();
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
         }
      } catch(final IOException e) {
         throw new RuntimeException("Could not export points.dat", e);
      }
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   private int getMaxPointCount() {
      int maxCount = 0;
      
      for(final TreeCell treeCell : this.tree) {
         maxCount = Math.max(maxCount, treeCell.getPointCount());
      }
      
      return maxCount;
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
      if(args.length != 4 && args.length != 6) {
         throw new IllegalArgumentException(USAGE);
      }
      
      final String type = args[0];
      final File inputDirectory = new File(args[1]);
      final File outputDirectory = new File(args[2]);
      final int[] cellSplit = new int[] { Integer.parseInt(args[3]), 0, 0 };
      
      if(args.length == 6) {
         cellSplit[1] = Integer.parseInt(args[4]);
         cellSplit[2] = Integer.parseInt(args[5]);
      } else {
         cellSplit[1] = cellSplit[0];
         cellSplit[2] = cellSplit[0];
      }
      
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
      
      final TreeBuilder builder = new TreeBuilder(type, inputDirectory, cellSplit);
      builder.build();
      builder.export(outputDirectory);
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
