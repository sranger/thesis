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
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;
import com.stephenwranger.thesis.icosatree.Icosatree;
import com.stephenwranger.thesis.octree.Octree;

public class TreeBuilder {
   private static final String USAGE = "TreeBuilder "
         + "<type [octree|icosatree; default octree]> "  // 0
         + "<input directory> "                          // 1
         + "<output directory> "                         // 2
         + "<read depth> "                               // 3
         + "<export csv [true|false]> "                  // 4
         + "<build/export tree [true|false]> "           // 5
         + "<cell split x> "                             // 6
         + "[<cell split y> <cell split z>]";            // 7-8
   private static final int TREE_TYPE_INDEX = 0;
   private static final int INPUT_DIRECTORY_INDEX = 1;
   private static final int OUTPUT_DIRECTORY_INDEX = 2;
   private static final int READ_DEPTH_INDEX = 3;
   private static final int EXPORT_CSV_INDEX = 4;
   private static final int BUILD_EXPORT_TREE_INDEX = 5;
   private static final int X_CELL_SPLIT_INDEX = 6;
   private static final int Y_CELL_SPLIT_INDEX = 7;
   private static final int Z_CELL_SPLIT_INDEX = 8;
   
   public enum TreeTypes {
      OCTREE, ICOSATREE;
   }
   
   private final TreeTypes type;
   private final File inputDirectory;
   private final int maxDepth;
   private final int[] cellSplit;
   
   private final List<Point> points = new ArrayList<>();
   private TreeStructure tree = null;
   private DataAttributes attributes = null;
   
   public TreeBuilder(final String type, final File inputDirectory, final int maxDepth, final int[] cellSplit) {
      final TreeTypes temp = TreeTypes.valueOf(type.toUpperCase());
      this.type = (temp == null) ? TreeTypes.OCTREE : temp;
      this.inputDirectory = inputDirectory;
      this.maxDepth = maxDepth;
      this.cellSplit = cellSplit;
      
      this.importData();
   }
   
   private void importData() {
      final long startTime = System.nanoTime();
      System.out.println("importing data from " + this.inputDirectory);
      System.out.println("\treading attributes...");
      this.attributes = new DataAttributes(readAttributes(this.inputDirectory, "attributes.csv"));
      
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
   
   public void build() {
      if(tree == null) {
         throw new RuntimeException("Cannot build tree before initialization is complete");
      } else {
         final long startTime = System.nanoTime();
         int count = 0;
         double currentPrintPercentage = 0;
         System.out.println("building tree...");
         
         for(final Point point : this.points) {
            // TODO: not sure why some points don't get added correctly
            try {
               this.tree.addPoint(point);
            } catch(final Exception e) {
               System.err.println("Could not add point: " + point);
               e.printStackTrace();
            }
            
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
         final int cellCount = this.tree.getCellCount();
         double currentPrintPercentage = 0;
         int count = 0;
         
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
               datFile = new File(dir, datName);
               metaFile = new File(dir, metaName);
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
            
            count++;
            
            double percentage = (count / (double) cellCount);
            percentage *= 10000.0;
            percentage = ((int) percentage) / 100.0;
            
            if(percentage >= currentPrintPercentage + 0.1) {
               final long elapsed = (System.nanoTime() - startTime);
               final long eta = (long) (((100.0 - percentage) * elapsed) / percentage);
               System.out.println("[" + percentage + "%]: " + count + " of " + cellCount + " completed. Elapsed: " + TimeUtils.formatNanoseconds(elapsed) + ", ETA: " + TimeUtils.formatNanoseconds(eta));
               currentPrintPercentage += 0.1;
            }
         }
         
         final long endTime = System.nanoTime();
         System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
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
      if(args.length != 7 && args.length != 9) {
         throw new IllegalArgumentException(USAGE);
      }
      
      final String type = args[TREE_TYPE_INDEX];
      final File inputDirectory = new File(args[INPUT_DIRECTORY_INDEX]);
      final File outputDirectory = new File(args[OUTPUT_DIRECTORY_INDEX]);
      final int maxDepth = Integer.parseInt(args[READ_DEPTH_INDEX]);
      final int[] cellSplit = new int[] { Integer.parseInt(args[X_CELL_SPLIT_INDEX]), 0, 0 };
      
      if(args.length == 6) {
         cellSplit[1] = Integer.parseInt(args[Y_CELL_SPLIT_INDEX]);
         cellSplit[2] = Integer.parseInt(args[Z_CELL_SPLIT_INDEX]);
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
      
      final TreeBuilder builder = new TreeBuilder(type, inputDirectory, maxDepth, cellSplit);
      
      if(Boolean.valueOf(args[BUILD_EXPORT_TREE_INDEX])) {
         builder.build();
         builder.export(outputDirectory);
      }
      
      if(Boolean.valueOf(args[EXPORT_CSV_INDEX])) {
         builder.exportFlat(outputDirectory);
      }
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
