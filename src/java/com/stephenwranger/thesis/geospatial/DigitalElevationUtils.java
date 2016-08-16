package com.stephenwranger.thesis.geospatial;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stephenwranger.graphics.collections.Pair;

public class DigitalElevationUtils {
   public static final String                              NORTH                   = "N";
   public static final String                              SOUTH                   = "S";
   public static final String                              EAST                    = "E";
   public static final String                              WEST                    = "w";
   public static final String                              DEM3_EXTENSION          = ".HGT";
   public static final String                              ZIP_EXTENSION           = ".ZIP";
   public static final String                              ARCHIVE_SEPARATOR       = "/";
   public static final String                              COMMA                   = ",";
   public static final String                              NEWLINE                 = "\n";

   public static final String                              DEM3_DIRECTORY_PROPERTY = "dem3.directory";
   public static final String                              DEM3_DIRECTORY          = System.getProperty(DigitalElevationUtils.DEM3_DIRECTORY_PROPERTY);
   public static final String                              CATALOG_FILENAME        = "catalog.dem3";
   public static final Map<Pair<Integer, Integer>, String> CATALOG_MAP             = DigitalElevationUtils.readCatalogMap();

   /** Each DEM3 tile is split into 1201x1201 cells. */
   public static final int                                 CELL_DIMENSIONS         = 1201;
   /** Each DEM3 cell is 1.0 / 1201.0 degrees square. */
   public static final double                              CELL_SIZE               = 1.0 / DigitalElevationUtils.CELL_DIMENSIONS;
   /** Each DEM3 elevation value in the 3 arc second dataset is a 16-bit short. */
   public static final int                                 SIZEOF_VALUE_BYTES      = 2;

   private DigitalElevationUtils() {
      // statics
   }

   public static void buildCatalog() {
      if(DigitalElevationUtils.DEM3_DIRECTORY != null) {
         final File catalogDirectory = new File(DigitalElevationUtils.DEM3_DIRECTORY);
         final File catalog = new File(catalogDirectory, DigitalElevationUtils.CATALOG_FILENAME);
         
         final Map<Pair<Integer, Integer>, String> files = DigitalElevationUtils.getFileBounds(catalogDirectory);
         final StringBuilder sb = new StringBuilder();
         final List<Pair<Integer, Integer>> keys = new ArrayList<>();
         keys.addAll(files.keySet());
   
         Collections.sort(keys);
   
         for (final Pair<Integer, Integer> key : keys) {
            final String value = files.get(key);
            sb.append(key.left).append(DigitalElevationUtils.COMMA).append(key.right).append(DigitalElevationUtils.COMMA).append(value).append(DigitalElevationUtils.NEWLINE);
         }
   
         try (final BufferedWriter writer = new BufferedWriter(new FileWriter(catalog))) {
            writer.write(sb.toString());
         } catch (final IOException e) {
            e.printStackTrace();
         }
      }
   }

   /**
    * Returns the elevation in meters at, or as close to, the given geodetic coordinate. This function will search the
    * given directory for the DEM3 filename that contains the requested point recursively and within compressed
    * archives. This function only supports the 3" (arc second) data from
    * <a href="viewfinderpanoramas.org">viewfinderpanoramas.org</a><br/>
    * <br/>
    * HGT format information: http://wiki.openstreetmap.org/wiki/SRTM<br/>
    * DEM Data: http://viewfinderpanoramas.org/dem3.html
    *
    * <pre>
    *    HGT Format
    *       - files 1deg x 1deg tiles
    *       - south/west to north/east
    *       - NW = index 0
    *       - NE = index 1200
    *       - SE = index 1201 * 1201
    *       - west-east columns then south-north (ie. cell = latIndex * 1201 + lonIndex)
    *       - 16 bit short per cell
    *       - 1201 x 1201 cells per tile
    *       - outermost rows/cols overlap neighbor tiles
    * </pre>
    *
    * @param dem3
    * @param longitudeDegrees
    * @param latitudeDegrees
    * @return
    */
   public static double getElevation(final double longitudeDegrees, final double latitudeDegrees) {
      final int lonBase = (int) Math.floor(longitudeDegrees);
      final int latBase = (int) Math.floor(latitudeDegrees);
      final int lonIndex = (int) Math.floor((longitudeDegrees - lonBase) / DigitalElevationUtils.CELL_SIZE);
      final int latIndex = (int) Math.floor((latitudeDegrees - latBase) / DigitalElevationUtils.CELL_SIZE);
      final int cellIndex = ((1200 - latIndex) * DigitalElevationUtils.CELL_DIMENSIONS) + lonIndex;
      final int byteOffset = cellIndex * DigitalElevationUtils.SIZEOF_VALUE_BYTES;

      final String filePath = DigitalElevationUtils.CATALOG_MAP.get(Pair.getInstance(lonBase, latBase));

      double elevation = 0;

      // TODO: get weighted value using neighbors
      if (filePath != null) {
         elevation = DigitalElevationUtils.getDigitalElevationModelValue(filePath, byteOffset);
      }

      return (Double.isNaN(elevation)) ? 0 : elevation;
   }

   public static String pad(final String original, final char padChar, final int length) {
      final StringBuilder sb = new StringBuilder();

      for (int i = original.length(); i < length; i++) {
         sb.append(padChar);
      }

      sb.append(original);

      return sb.toString();
   }

   public static BufferedImage toImage(final File demFile) {
      return DigitalElevationUtils.toImage(demFile, null);
   }

   public static BufferedImage toImage(final File demFile, final BufferedImage outputImage) {
      final short[][] values = new short[1201][1201];
      short max = Short.MIN_VALUE;

      try (final RandomAccessFile raf = new RandomAccessFile(demFile, "r")) {
         final ShortBuffer buffer = raf.getChannel().map(MapMode.READ_ONLY, 0, demFile.length()).asShortBuffer();

         for (int x = 0; x < 1201; x++) {
            for (int y = 0; y < 1201; y++) {
               values[x][y] = buffer.get((y * 1201) + x);
               max = max < values[x][y] ? values[x][y] : max;
            }
         }
      } catch (final IOException e) {
         e.printStackTrace();
      }

      final BufferedImage image = (outputImage == null) ? new BufferedImage(1201, 1201, BufferedImage.TYPE_INT_ARGB) : outputImage;

      for (int x = 0; x < 1201; x++) {
         for (int y = 0; y < 1201; y++) {
            final float value = values[x][y] / (float) max;
            image.setRGB(x, y, new Color((y <= 10) ? 0f : value, value, (y <= 10) ? 0f : value, 1f).getRGB());
         }
      }

      return image;
   }

   private static File getCatalog() {
      final File catalog = new File(DigitalElevationUtils.DEM3_DIRECTORY, DigitalElevationUtils.CATALOG_FILENAME);

      if (catalog.isDirectory()) {
         System.err.println("DEM3 Catalog is directory; invalid.");
         return null;
      } else if (!catalog.exists()) {
         DigitalElevationUtils.buildCatalog();
      }

      return catalog;
   }

   private static double getDigitalElevationModelValue(final String path, final int byteOffset) {
      double value = 0;

      if (path.toUpperCase().endsWith(DigitalElevationUtils.DEM3_EXTENSION)) {
         final File file = new File(path);

         try (final RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            final MappedByteBuffer mappedFile = raf.getChannel().map(MapMode.READ_ONLY, 0, file.length());
            value = mappedFile.getShort(byteOffset);
         } catch (final Exception e) {
            System.err.println("path.length = " + path.length());
            System.err.println("byte offset = " + byteOffset);
            e.printStackTrace();
         }
      }

      return value;
   }

   private static Map<Pair<Integer, Integer>, String> getFileBounds(final File basePath) {
      final Map<Pair<Integer, Integer>, String> files = new HashMap<>();

      if (basePath.isDirectory()) {
         for (final File path : basePath.listFiles()) {
            files.putAll(DigitalElevationUtils.getFileBounds(path));
         }
      } else {
         String nameUppercase = basePath.getName().toUpperCase();

         if (nameUppercase.contains("/")) {
            nameUppercase = nameUppercase.substring(nameUppercase.lastIndexOf("/") + 1);
         }

         if (nameUppercase.endsWith(DigitalElevationUtils.DEM3_EXTENSION)) {
            files.put(DigitalElevationUtils.getIndex(nameUppercase), basePath.getAbsolutePath());
         }
      }

      return files;
   }

   private static Pair<Integer, Integer> getIndex(final String filename) {
      try {
         final int latIndex = ((filename.substring(0, 1).equals(DigitalElevationUtils.NORTH)) ? 1 : -1) * Integer.parseInt(filename.substring(1, 3));
         final int lonIndex = ((filename.substring(3, 4).equals(DigitalElevationUtils.EAST)) ? 1 : -1) * Integer.parseInt(filename.substring(4, 7));

         return Pair.getInstance(lonIndex, latIndex);
      } catch (final Exception e) {
         System.err.println("name = '" + filename + "'");
         e.printStackTrace();
      }

      return null;
   }

   private static Map<Pair<Integer, Integer>, String> readCatalogMap() {
      final Map<Pair<Integer, Integer>, String> catalog = new HashMap<>();
      final File catalogFile = DigitalElevationUtils.getCatalog();

      if ((catalogFile != null) && catalogFile.exists()) {
         try (final BufferedReader reader = new BufferedReader(new FileReader(catalogFile))) {
            String line = null;

            while ((line = reader.readLine()) != null) {
               final String[] split = line.split(DigitalElevationUtils.COMMA);
               final int lonIndex = Integer.parseInt(split[0]);
               final int latIndex = Integer.parseInt(split[1]);
               final String path = split[2];
               catalog.put(Pair.getInstance(lonIndex, latIndex), path);
            }
         } catch (final IOException e) {
            e.printStackTrace();
         }
      }

      return catalog;
   }
}
