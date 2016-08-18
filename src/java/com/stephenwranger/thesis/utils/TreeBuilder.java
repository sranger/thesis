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
         + "<type [octree|icosatree]> "                  // 0
         + "<input directory> "                          // 1
         + "<output directory> "                         // 2
         + "<cell split x> "                             // 3
         + "[<cell split y> <cell split z>]";            // 4-5
   private static final int TREE_TYPE_INDEX = 0;
   private static final int INPUT_DIR_INDEX = 1;
   private static final int OUTPUT_DIRECTORY_INDEX = 2;
   private static final int X_CELL_SPLIT_INDEX = 3;
   private static final int Y_CELL_SPLIT_INDEX = 4;
   private static final int Z_CELL_SPLIT_INDEX = 5;
   
   private static final long ONE_SECOND_NANO = 1000_000_000;
   
   public enum TreeTypes {
      OCTREE, ICOSATREE;
   }
   
   private final TreeTypes type;
   private final File inputDir;
   private final int[] cellSplit;
   private final DataAttributes attributes;
   private final TreeStructure tree;
   
   public TreeBuilder(final String type, final File inputDir, final int[] cellSplit) {
      final TreeTypes temp = TreeTypes.valueOf(type.toUpperCase());
      this.type = (temp == null) ? TreeTypes.OCTREE : temp;
      this.inputDir = inputDir;
      this.cellSplit = cellSplit;
      
      final File attributesFile = new File(this.inputDir, "attributes.csv");

      if(!attributesFile.exists()) {
         throw new RuntimeException("Could not find attributes.csv in input directory");
      }
      
      this.attributes = new DataAttributes(readAttributes(attributesFile));
      this.tree = (this.type == TreeTypes.OCTREE) ? new Octree(this.attributes, this.cellSplit) : new Icosatree(this.attributes, this.cellSplit); 
   }
   
   public void build() {
      final long startTime = System.nanoTime();
      System.out.println("building tree...");
      System.out.println();
      long length = 0;
      
      for(final File file : this.inputDir.listFiles()) {
         if(file.getName().endsWith(".dat")) {
            length += file.length();
         }
      }
      
      final long pointCount = length / this.attributes.stride;
      final byte[] buffer = new byte[this.attributes.stride];
      long lastPrint = 0;
      long count = 0;
      
      for(final File file : this.inputDir.listFiles()) {
         if(!file.getName().endsWith(".dat")) {
            continue;
         }
         System.out.println("Reading:" + file.getName() + "\n");
         
         try (final BufferedInputStream fin = new BufferedInputStream(new FileInputStream(file))) {
            while(fin.read(buffer) > -1) {
               final Point point = new Point(this.attributes, buffer);
               
               try {
                  // TODO: not sure why some points don't get added correctly
                  this.tree.addPoint(point);
               } catch(final Exception e) {
                  System.err.println("Could not add point: " + point);
                  e.printStackTrace();
               }
   
               final long elapsed = (System.nanoTime() - startTime);
               
               if(elapsed - lastPrint > ONE_SECOND_NANO) {
                  lastPrint = elapsed;
      
                  printStats(count, pointCount, elapsed);
               }
               
               count++;
            }
         } catch (final IOException e1) {
            e1.printStackTrace();
         }
         
         printStats(count, pointCount, (System.nanoTime() - startTime));
      }
      
      System.out.println("tree built: " + this.tree.getCellCount());
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   private static void printStats(final long count, final long pointCount, final long elapsed) {
      final double exactPercentage = (count / (double) pointCount);
      double percentage = (count / (double) pointCount);
      percentage *= 10000.0;
      percentage = ((int) percentage) / 100.0;
   
      final long eta = (long) (((1.0 - exactPercentage) * elapsed) / exactPercentage);
      System.out.print("\33[1A\33[2K"); // in linux, moves up a line in console and erases it (note: doesn't work when console wraps lines)
      System.out.println("[" + percentage + "%]: " + count + " of " + pointCount + " completed. Elapsed: " + TimeUtils.formatNanoseconds(elapsed) + ", ETA: " + TimeUtils.formatNanoseconds(eta));
   }
   
   public void export(final File outputDirectory) {
      final long startTime = System.nanoTime();
      final int cellCount = this.tree.getCellCount();
      long lastPrint = 0;
      long count = 0;
      
      System.out.println("exporting tree to " + outputDirectory + "\n");
      
      for(final TreeCell treeCell : this.tree) {
         final String path = treeCell.getPath();
         File datFile = null;
         File metaFile = null;
         
         if(path.isEmpty()) {
            datFile = new File(outputDirectory, "/root.dat");
            metaFile = new File(outputDirectory, "/root.txt");
            
            TreeImporter.exportAttributes(this.attributes, outputDirectory);
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
               final int[] countStats = this.getPointCountStats();
               fout.write("\n");
               fout.write("min: " + Integer.toString(countStats[0]));
               fout.write("max: " + Integer.toString(countStats[1]));
               fout.write("avg: " + Integer.toString(countStats[2]));
               fout.write("cells: " + Integer.toString(countStats[3]));
               fout.write("maxDepth: " + Integer.toString(countStats[4]));
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

         final long elapsed = (System.nanoTime() - startTime);
         
         if(elapsed - lastPrint > ONE_SECOND_NANO) {
            lastPrint = elapsed;

            printStats(count, cellCount, elapsed);
         }
      }

      final long elapsed = (System.nanoTime() - startTime);
      printStats(count, cellCount, elapsed);
      
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   private int[] getPointCountStats() {
      int total = 0;
      int minCount = Integer.MAX_VALUE;
      int maxCount = 0;
      int cellCount = 0;
      int maxDepth = 0;
      
      for(final TreeCell treeCell : this.tree) {
         final int count = treeCell.getPointCount();
         minCount = Math.min(minCount, count);
         maxCount = Math.max(maxCount, count);
         total += count;
         cellCount++;
         maxDepth = Math.max(maxDepth, treeCell.path.length());
      }
      
      return new int[] { minCount, maxCount, (int) Math.ceil(total / (double) cellCount), cellCount };
   }
   
   private static List<Attribute> readAttributes(final File attributesFile) {
      final List<Attribute> attributes = new ArrayList<>();
      
      try(final BufferedReader reader = new BufferedReader(new FileReader(attributesFile))) {
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
      
      final String type = args[TREE_TYPE_INDEX];
      final File inputDir = new File(args[INPUT_DIR_INDEX]);
      final File outputDirectory = new File(args[OUTPUT_DIRECTORY_INDEX]);
      final int[] cellSplit = new int[] { Integer.parseInt(args[X_CELL_SPLIT_INDEX]), 0, 0 };
      
      if(args.length == 6) {
         cellSplit[1] = Integer.parseInt(args[Y_CELL_SPLIT_INDEX]);
         cellSplit[2] = Integer.parseInt(args[Z_CELL_SPLIT_INDEX]);
      } else {
         cellSplit[1] = cellSplit[0];
         cellSplit[2] = cellSplit[0];
      }
      
      if(!inputDir.isDirectory()) {
         throw new IllegalArgumentException("Input directory must exist and be a directory.");
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
      
      final TreeBuilder builder = new TreeBuilder(type, inputDir, cellSplit);
      builder.build();
      builder.export(outputDirectory);
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
