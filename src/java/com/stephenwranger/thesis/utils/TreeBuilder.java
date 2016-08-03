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
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
         + "<input csv file> "                           // 1
         + "<input attributes file> "                    // 2
         + "<output directory> "                         // 3
         + "<cell split x> "                             // 4
         + "[<cell split y> <cell split z>]";            // 5-6
   private static final int TREE_TYPE_INDEX = 0;
   private static final int INPUT_CSV_INDEX = 1;
   private static final int INPUT_ATTRIBUTES_INDEX = 2;
   private static final int OUTPUT_DIRECTORY_INDEX = 3;
   private static final int X_CELL_SPLIT_INDEX = 4;
   private static final int Y_CELL_SPLIT_INDEX = 5;
   private static final int Z_CELL_SPLIT_INDEX = 6;
   
   public enum TreeTypes {
      OCTREE, ICOSATREE;
   }
   
   private final TreeTypes type;
   private final File inputDatFile;
   private final int[] cellSplit;
   private final DataAttributes attributes;
   private final TreeStructure tree;
   
   public TreeBuilder(final String type, final File inputDatFile, final File inputAttributesFile, final int[] cellSplit) {
      final TreeTypes temp = TreeTypes.valueOf(type.toUpperCase());
      this.type = (temp == null) ? TreeTypes.OCTREE : temp;
      this.inputDatFile = inputDatFile;
      this.cellSplit = cellSplit;
      
      this.attributes = new DataAttributes(readAttributes(inputAttributesFile));
      this.tree = (this.type == TreeTypes.OCTREE) ? new Octree(this.attributes, this.cellSplit) : new Icosatree(this.attributes, this.cellSplit); 
   }
   
   public void build() {
      final long startTime = System.nanoTime();
      int count = 0;
//      double currentPrintPercentage = 0;
      System.out.println("building tree...");
      System.out.println();
      final long pointCount = this.inputDatFile.length() / this.attributes.stride;
      
      
      try (final BufferedInputStream fin = new BufferedInputStream(new FileInputStream(this.inputDatFile))) {
         final byte[] buffer = new byte[this.attributes.stride];
         
         while(fin.read(buffer) > -1) {
            final Point point = new Point(this.attributes, buffer);
            
            try {
               // TODO: not sure why some points don't get added correctly
               this.tree.addPoint(point);
            } catch(final Exception e) {
               System.err.println("Could not add point: " + point);
               e.printStackTrace();
            }
            
            double percentage = (count / (double) pointCount);
            percentage *= 10000.0;
            percentage = ((int) percentage) / 100.0;
            
//            if(percentage >= currentPrintPercentage + 0.1) {
               final long elapsed = (System.nanoTime() - startTime);
               final long eta = (long) (((100.0 - percentage) * elapsed) / percentage);
               System.out.print("\33[1A\33[2K"); // in linux, moves up a line in console and erases it (note: doesn't work when console wraps lines)
               System.out.println("[" + percentage + "%]: " + count + " of " + pointCount + " completed. Elapsed: " + TimeUtils.formatNanoseconds(elapsed) + ", ETA: " + TimeUtils.formatNanoseconds(eta));
//               currentPrintPercentage += 0.1;
//            }
            
            count++;
         }
      } catch (final IOException e1) {
         e1.printStackTrace();
      }
      
      System.out.println("tree built: " + this.tree.getCellCount());
      final long endTime = System.nanoTime();
      System.out.println(TimeUtils.formatNanoseconds(endTime - startTime));
   }
   
   public void export(final File outputDirectory) {
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
   
   private int getMaxPointCount() {
      int maxCount = 0;
      
      for(final TreeCell treeCell : this.tree) {
         maxCount = Math.max(maxCount, treeCell.getPointCount());
      }
      
      return maxCount;
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
      if(args.length != 5 && args.length != 7) {
         throw new IllegalArgumentException(USAGE);
      }
      
      final String type = args[TREE_TYPE_INDEX];
      final File inputCsvFile = new File(args[INPUT_CSV_INDEX]);
      final File inputAttributesFile = new File(args[INPUT_ATTRIBUTES_INDEX]);
      final File outputDirectory = new File(args[OUTPUT_DIRECTORY_INDEX]);
      final int[] cellSplit = new int[] { Integer.parseInt(args[X_CELL_SPLIT_INDEX]), 0, 0 };
      
      if(args.length == 6) {
         cellSplit[1] = Integer.parseInt(args[Y_CELL_SPLIT_INDEX]);
         cellSplit[2] = Integer.parseInt(args[Z_CELL_SPLIT_INDEX]);
      } else {
         cellSplit[1] = cellSplit[0];
         cellSplit[2] = cellSplit[0];
      }
      
      if(!inputCsvFile.isFile() || !inputAttributesFile.isFile()) {
         throw new IllegalArgumentException("Input CSV and Attribute files must exist and be a directory.");
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
      
      final TreeBuilder builder = new TreeBuilder(type, inputCsvFile, inputAttributesFile, cellSplit);
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
