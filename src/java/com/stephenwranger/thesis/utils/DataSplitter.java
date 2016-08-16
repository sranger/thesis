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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import com.stephenwranger.graphics.collections.Pair;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.utils.TimeUtils;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.geospatial.WGS84;

public class DataSplitter extends Thread {
   private static final int NUM_ARGS = 8;
   private static final String USAGE = "./dataSplitter.sh <pointsDat> <attributesCsv> <outputDir> <min_lon> <min_lat> <max_lon> <max_lat> <degree_step>";
   private static final int DAT_FILE_INDEX = 0;
   private static final int ATTRIBUTES_FILE_INDEX = 1;
   private static final int OUTPUT_DIR_INDEX = 2;
   private static final int MIN_LON_INDEX = 3;
   private static final int MIN_LAT_INDEX = 4;
   private static final int MAX_LON_INDEX = 5;
   private static final int MAX_LAT_INDEX = 6;
   private static final int DEGREE_STEP_INDEX = 7;
   
   private static final long ONE_SECOND_NANO = 1000_000_000;
   
   private final File datFile;
   private final File attributesFile;
   private final File outputDir;
   private final DataAttributes attributes;
   private final Tuple2d minLonLat;
   private final Tuple2d maxLonLat;
   private final double degreeStep;
   
   public DataSplitter(final File datFile, final File attributesFile, final File outputDir, final Tuple2d minLonLat, final Tuple2d maxLonLat, final double degreeStep) {
      this.datFile = datFile;
      this.attributesFile = attributesFile;
      this.outputDir = outputDir;
      this.attributes = new DataAttributes(readAttributes(this.attributesFile));
      this.minLonLat = new Tuple2d(minLonLat);
      this.maxLonLat = new Tuple2d(maxLonLat);
      this.degreeStep = Math.max(0.0001, degreeStep);
   }
   
   private static String getFormatter(final double step) {
      final String value = Double.toString(step);
      final int index = value.indexOf(".");
      final int count = value.length() - index - 1;
      
      if(index >= 0) {
         final StringBuilder sb = new StringBuilder("0.");
         for(int i = 0 ; i < count; i++) {
            sb.append("0");
         }
         
         return sb.toString();
      } else {
         return "0";
      }
   }
   
   @Override
   public void run() {
      final DecimalFormat formatter = new DecimalFormat(getFormatter(this.degreeStep));
      final Map<Pair<Integer, Integer>, BufferedOutputStream> writers = new HashMap<>();
      final Attribute x = this.attributes.getAttribute(DataAttributes.X_ATTRIBUTE_NAME);
      final Attribute y = this.attributes.getAttribute(DataAttributes.Y_ATTRIBUTE_NAME);
      final Attribute z = this.attributes.getAttribute(DataAttributes.Z_ATTRIBUTE_NAME);
      final byte[] buffer = new byte[this.attributes.stride];
      final ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
      final Tuple3d xyz = new Tuple3d();
      final long pointCount = this.datFile.length() / this.attributes.stride;
      final long startTime = System.nanoTime();
      long writtenCount = 0;
      long count = 0;
      long lastPrint = 0;

      try (final BufferedInputStream fin = new BufferedInputStream(new FileInputStream(this.datFile))) {
         while(fin.read(buffer) != -1) {
            xyz.x = x.getValue(bb, 0, this.attributes.stride).doubleValue();
            xyz.y = y.getValue(bb, 0, this.attributes.stride).doubleValue();
            xyz.z = z.getValue(bb, 0, this.attributes.stride).doubleValue();
            
            final Tuple3d lla = WGS84.cartesianToGeodesic(xyz);
            
            if(lla.x >= this.minLonLat.x && lla.x <= this.maxLonLat.x && lla.y >= this.minLonLat.y && lla.y <= this.maxLonLat.y) {
               final int lonIndex = (int) Math.floor(lla.x / degreeStep);
               final int latIndex = (int) Math.floor(lla.y / degreeStep);
               final Pair<Integer, Integer> key = Pair.getInstance(lonIndex, latIndex);
               BufferedOutputStream writer = writers.get(key);
               
               if(writer == null) {
                  writer = new BufferedOutputStream(new FileOutputStream(new File(this.outputDir, formatter.format(lonIndex*degreeStep) + "_" + formatter.format(latIndex*degreeStep) + "_to_" + formatter.format((lonIndex+1)*degreeStep) + "_" + formatter.format((latIndex+1)*degreeStep) + ".dat")));
                  writers.put(key, writer);
               }
               
               writer.write(buffer);
               writtenCount++;
            }
            
            count++;
            
            final long elapsed = (System.nanoTime() - startTime);
            
            if(elapsed - lastPrint > ONE_SECOND_NANO) {
               lastPrint = elapsed;

               final double exactPercentage = (count / (double) pointCount);
               double percentage = (count / (double) pointCount);
               percentage *= 10000.0;
               percentage = ((int) percentage) / 100.0;
               final long eta = (long) (((1.0 - exactPercentage) * elapsed) / exactPercentage);
               System.out.println("\33[1A\33[2K" + // in linux, moves up a line in console and erases it (note: doesn't work when console wraps lines)
                                  "\33[1A\33[2K" +
                                  "exported (passed geodesic bounds): " + writtenCount + "\n" +
                                  "[" + percentage + "%]: " + count + " of " + pointCount + " completed. Elapsed: " + TimeUtils.formatNanoseconds(elapsed) + ", ETA: " + TimeUtils.formatNanoseconds(eta));
               System.out.flush();
            }
         }
      } catch (final IOException e) {
         e.printStackTrace();
      } finally {
         for(final BufferedOutputStream writer : writers.values()) {
            try {
               writer.close();
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
         
         try (final BufferedWriter fout = new BufferedWriter(new FileWriter(new File(this.outputDir, "bounds.csv")))) {
            fout.write("bounds_id,corner_id,lon_deg,lat_deg\n");
            int ctr = 0;
            
            for(final Pair<Integer, Integer> bounds : writers.keySet()) {
               final int lonIndex = bounds.left;
               final int latIndex = bounds.right;
               final double minLon = lonIndex*degreeStep;
               final double minLat = latIndex*degreeStep;
               final double centerLon = minLon + (degreeStep / 2.0);
               final double centerLat = minLat + (degreeStep / 2.0);
               final double maxLon = (lonIndex+1)*degreeStep;
               final double maxLat = (latIndex+1)*degreeStep;
               fout.write(ctr + ",-1," + centerLon + "," + centerLat + "\n"); // center point for labeling
               fout.write(ctr + ",0," + minLon + "," + minLat + "\n");        // bottom-left
               fout.write(ctr + ",1," + maxLon + "," + minLat + "\n");        // bottom-right
               fout.write(ctr + ",2," + maxLon + "," + maxLat + "\n");        // top-right
               fout.write(ctr + ",3," + minLon + "," + maxLat + "\n");        // top-left
               fout.write(ctr + ",4," + minLon + "," + minLat + "\n");        // bottom-left (closing loop)
               ctr++;
            }
         } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
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
   
   public static void main(final String[] args) {
      if(args.length != NUM_ARGS) {
         throw new RuntimeException(USAGE);
      }

      final File datFile = new File(args[DAT_FILE_INDEX]);
      final File attributesFile = new File(args[ATTRIBUTES_FILE_INDEX]);
      final File outputDir = new File(args[OUTPUT_DIR_INDEX]);
      final double minLon = Double.parseDouble(args[MIN_LON_INDEX]);
      final double minLat = Double.parseDouble(args[MIN_LAT_INDEX]);
      final double maxLon = Double.parseDouble(args[MAX_LON_INDEX]);
      final double maxLat = Double.parseDouble(args[MAX_LAT_INDEX]);
      final double degreeStep = Double.parseDouble(args[DEGREE_STEP_INDEX]);
      
      if(!outputDir.exists()) {
         System.out.println("Creating output directory...");
         outputDir.mkdirs();
         
         if(outputDir.exists()) {
            System.out.println("Success!");
         } else {
            throw new RuntimeException("Could not create output directory.");
         }
      } else if(!outputDir.isDirectory()) {
         throw new IllegalArgumentException("Output Directory must exist and be a directory.");
      }
      
      if(outputDir.list().length > 0) {
         final int value = JOptionPane.showConfirmDialog(null, "Output Directory is not empty; would you like to delete its contents?", "Confirm Output Directory Purge", JOptionPane.YES_NO_OPTION);
         
         if(value != JOptionPane.OK_OPTION) {
            throw new RuntimeException("Output directory not empty; user requested it be left in tact. Please change the output directory before proceeding. Exiting.");
         }
         
         System.out.println("Purging contents of output directory...");
         
         purgeContents(outputDir);
      }
      
      final DataSplitter splitter = new DataSplitter(datFile, attributesFile, outputDir, new Tuple2d(minLon, minLat), new Tuple2d(maxLon, maxLat), degreeStep);
      
      try {
         splitter.start();
      } finally {
         try {
            splitter.join();
         } catch (final InterruptedException e) {
            e.printStackTrace();
         }
      }
      
      System.exit(0);
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
