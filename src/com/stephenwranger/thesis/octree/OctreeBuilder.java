package com.stephenwranger.thesis.octree;

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

import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.Point;

public class OctreeBuilder {
   private static final String USAGE = "OctreeBuilder <input directory> <output directory> <cell split x> [<cell split y> <cell split z>]";
   
   private final File inputDirectory;
   private final int[] cellSplit;
   
   private final List<Point> points = new ArrayList<>();
   private Octree octree = null;
   private DataAttributes attributes = null;
   
   public OctreeBuilder(final File inputDirectory, final int[] cellSplit) {
      this.inputDirectory = inputDirectory;
      this.cellSplit = cellSplit;
      
      this.importData();
   }
   
   private void importData() {
      System.out.println("importing data from " + this.inputDirectory);
      System.out.println("\treading attributes...");
      this.attributes = new DataAttributes(readAttributes(this.inputDirectory, "root.csv"));
      this.octree = new Octree(this.attributes, this.cellSplit);
      System.out.println("\t\tattributes read: " + attributes.getAttributeNames());
      System.out.println("\treading data...");
      readDirectory(this.inputDirectory);
      System.out.println("\t\tpoint data read: " + points.size());
   }
   
   private void readDirectory(final File directory) {
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
                  points.add(new Point(this.attributes, buffer));
               }
            } catch(final IOException e) {
               e.printStackTrace();
            }
         }
      }
   }
   
   public void build() {
      if(octree == null) {
         throw new RuntimeException("Cannot build Octree before initialization is complete");
      } else {
         System.out.println("building octree...");
         for(final Point point : this.points) {
            this.octree.addPoint(point);
         }
         
         System.out.println("octree built: " + this.octree.getOctetCount());
      }
   }
   
   public void export(final File outputDirectory) {
      if(octree == null) {
         throw new RuntimeException("Cannot build Octree before initialization is complete");
      } else {
         System.out.println("exporting octree to " + outputDirectory);
         
         for(final Octet octet : this.octree) {
            final String[] split = octet.path.substring(0, octet.path.length()-1).split("");
            final char childIndex = octet.path.charAt(octet.path.length()-1);
            final String dirPath = String.join("/", split);
            final String fileName = childIndex + ".dat";
            final File datFile = new File(outputDirectory, dirPath + "/" + fileName);
            
            try(final BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(datFile))) {
               for(final Point p : octet) {
                  fout.write(p.getRawData());
               }
            } catch(final IOException e) {
               throw new RuntimeException("Could not write octet point data: " + datFile.getAbsolutePath(), e);
            }
         }
      }
   }
   
   public void exportFlat(final File outputDirectory) {
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
            fout.write(p.getRawData());
         }
      } catch(final IOException e) {
         throw new RuntimeException("Could not export points.dat", e);
      }
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
      if(args.length != 3 && args.length != 5) {
         throw new IllegalArgumentException(USAGE);
      }
      
      final File inputDirectory = new File(args[0]);
      final File outputDirectory = new File(args[1]);
      final int[] cellSplit = new int[] { Integer.parseInt(args[2]), 0, 0 };
      
      if(args.length == 5) {
         cellSplit[1] = Integer.parseInt(args[3]);
         cellSplit[2] = Integer.parseInt(args[4]);
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
      
      final OctreeBuilder builder = new OctreeBuilder(inputDirectory, cellSplit);
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
         } else {
            file.delete();
         }
      }
   }
}
