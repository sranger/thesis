package com.stephenwranger.thesis.geospatial;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.EllipticalSegment;
import com.stephenwranger.graphics.renderables.GeodesicVertex;
import com.stephenwranger.graphics.utils.MathUtils;
import com.stephenwranger.graphics.utils.buffers.Vertex;
import com.stephenwranger.graphics.utils.textures.Texture2d;

public class EarthImagery {
   private static final Map<String, Texture2d> CACHED_TEXTURES = createLeastRecentlyUsedMap(10000);
   private static String          OPEN_STREET_MAP_PROPERTY = "osm.server";
   private static String          OPEN_STREET_MAP_CACHE_PROPERTY = "osm.cache";
   private static String[]        OPEN_STREET_MAP_URL = getOpenStreetMapServers();
   private static final File      OPEN_STREET_MAP_CACHE_DIRECTORY = getOpenStreetMapCacheDirectory();
   private static final Texture2d BASE_EARTH_TEXTURE = EarthImagery.getBaseTexture();
   private static final TextureServer TEXTURE_SERVER = new TextureServer();
   
   private static int URL_INDEX = 0;

   private EarthImagery() {
      //statics only
   }

   /**
    * Retrieves the imagery tile from the OpenStreetMap server and updates the {@link EllipticalSegment} 
    * texture coordinates or sets the base imagery texture if no OpenStreetMap server is defined.<br/>
    * <br/>
    * http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    * 
    * @param segment
    */
   public static void setImagery(final EllipticalSegment segment) {
      // set base texture until server can get around to handling this
      segment.setTexture(new Texture2d[] { EarthImagery.BASE_EARTH_TEXTURE }, null);
      
      // TODO: need to support multiple textures; they're never at the same depth
      if (EarthImagery.OPEN_STREET_MAP_URL != null) {
         TEXTURE_SERVER.addPending(segment);
      }
   }
   
   private static int[][] computeZoomTile(final EllipticalSegment segment) {
      final Vertex[] vertices = segment.getVertices();
      final Tuple3d lla0 = WGS84.cartesianToGeodesic(vertices[0].getVertex());
      final Tuple3d lla1 = WGS84.cartesianToGeodesic(vertices[1].getVertex());
      final Tuple3d lla2 = WGS84.cartesianToGeodesic(vertices[2].getVertex());
      final int depth = Math.min(15, segment.getDepth());

      final int[] tile0 = tileXYZ(lla0.x, lla0.y, depth);
      final int[] tile1 = tileXYZ(lla1.x, lla1.y, depth);
      final int[] tile2 = tileXYZ(lla2.x, lla2.y, depth);

      final int minX = MathUtils.getMin(tile0[0], tile1[0], tile2[0]);
      final int maxX = MathUtils.getMax(tile0[0], tile1[0], tile2[0]);
      final int minY = MathUtils.getMin(tile0[1], tile1[1], tile2[1]);
      final int maxY = MathUtils.getMax(tile0[1], tile1[1], tile2[1]);
      final List<int[]> tiles = new ArrayList<>();
      
      final int n = (int) Math.pow(2, depth);
      
      for(int x = (int) MathUtils.clamp(0, n-1, minX); x <= (int) MathUtils.clamp(0, n-1, maxX); x++) {
         for(int y = (int) MathUtils.clamp(0, n-1, minY); y <= (int) MathUtils.clamp(0, n-1, maxY); y++) {
            tiles.add(new int[] { x, y, depth });
         }
      }
      
      return tiles.toArray(new int[tiles.size()][]);
   }
   
   public static int numTiles(final int zoom) {
      return (int) Math.pow(2, zoom);
   }
   
//   public static double[] lonlat2relativeXY(final double lon, final double lat) {
//      final double x = (lon + 180) / 360;
//      final double y = (1 - Math.log(Math.tan(Math.toRadians(lat)) + MathUtils.sec(Math.toRadians(lat))) / Math.PI) / 2;
//      
//      return new double[] { x, y };
//   }
//
//   public static double[] lonlat2xy(final double lon, final double lat, final int z) {
//      final double n = numTiles(z);
//      final double[] xy = lonlat2relativeXY(lon, lat);
//      xy[0] *= n;
//      xy[1] *= n;
//      
//      return xy;
//   }
//      
//   public static int[] tileXYZ(final double lon, final double lat, final int z) {
//      final double[] xy = lonlat2xy(lon,lat,z);
//      return new int[] { (int) Math.floor(xy[0]), (int) Math.floor(xy[1]), z };
//   }
//
//   public static double[] xy2lonlat(final int x, final int y, final int z) {
//      final double n = numTiles(z);
//      final double relY = (y) / n;
//      final double lat = mercatorToLat(Math.PI * (1 - 2 * relY));
//      final double lon = -180.0 + 360.0 * x / n;
//      
//      return new double[] { lon, lat };
//   }

   public static double mercatorToLat(final double mercatorY) {
      return Math.toDegrees(Math.atan(Math.sinh(mercatorY)));
   }
      
   public static double[] latEdges(final int y, final int z) {
      final double n = numTiles(z);
      final double unit = 1 / n;
      final double relY1 = y * unit;
      final double relY2 = relY1 + unit;
      final double lat1 = mercatorToLat(Math.PI * (1 - 2 * relY1));
      final double lat2 = mercatorToLat(Math.PI * (1 - 2 * relY2));
      return new double[] { lat1,lat2 };
   }

   public static double[] lonEdges(final int x, final int z) {
      final double n = numTiles(z);
      final double unit = 360 / n;
      final double lon1 = -180 + x * unit;
      final double lon2 = lon1 + unit;
      return new double[] { lon1, lon2 };
   }
   
   /**
    * Returns edges of requested tile S,W,N,E.
    * @param x
    * @param y
    * @param z
    * @return
    */
   public static double[] tileEdges(final int x, final int y, final int z) {
      final double[] lats = latEdges(y,z);
      final double[] lons = lonEdges(x,z);
      return new double[] { lats[1], lons[0], lats[0], lons[1] }; // S,W,N,E
   }

   
   /**
    * Computes x/y tile coordinate given specified location and zoom level.
    * 
    * <pre>
    * http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    *    n = 2 ^ zoom
    *    xtile = n * ((lon_deg + 180) / 360)
    *    ytile = n * (1 - (log(tan(lat_rad) + sec(lat_rad)) / π)) / 2
    * </pre>
    * 
    * @param lonDegrees
    * @param latDegrees
    * @param zoom
    * @return
    */
   public static int[] tileXYZ(final double lonDegrees, final double latDegrees, final int zoom) {
      final double[] xy = tileXYZDouble(lonDegrees, latDegrees, zoom);
      final double x = xy[0];
      final double y = xy[1];
      return new int[] { (int) Math.floor(x), (int) Math.floor(y), zoom };
   }
   
   public static double[] tileXYZDouble(final double lonDegrees, final double latDegrees, final int zoom) {
      final double latRadians = Math.toRadians(latDegrees);
      final double n = numTiles(zoom);
      final double x = (lonDegrees + 180.0) / 360.0;
      final double y = (1 - (Math.log(Math.tan(latRadians) + MathUtils.sec(latRadians)) / Math.PI)) / 2;
      
      return new double[] { x * n, y * n };
   }
   
   public static double[] xy2lonlat(final int x, final int y, final int z) {
      final double n = numTiles(z);
      final double lon_deg = x / n * 360.0 - 180.0;
      final double lat_rad = Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n)));
      final double lat_deg = Math.toDegrees(lat_rad);
      return new double[] { lon_deg, lat_deg };
   }

   private static Texture2d getBaseTexture() {
      return Texture2d.getTexture(Earth.class.getResourceAsStream("world.topo.bathy.200401.3x5400x2700.png"), GL.GL_RGBA);
   }
   
   private static String[] getOpenStreetMapServers() {
      final String property = System.getProperty(OPEN_STREET_MAP_PROPERTY);
      String[] urls = null;
      
      if(property != null && !property.isEmpty()) {
         final String prefix = property.substring(0, property.indexOf("{"));
         final String[] servers = property.substring(property.indexOf("{") + 1, property.indexOf("}")).split(",");
         final String suffix = property.substring(property.indexOf("}") + 1);
         urls = new String[servers.length];
         
         for(int i = 0; i < servers.length; i++) {
            urls[i] = prefix + servers[i] + suffix;
         }
      }
      
      return urls;
   }
   
   private static File getOpenStreetMapCacheDirectory() {
      if(OPEN_STREET_MAP_URL != null && OPEN_STREET_MAP_URL.length > 0) {
         final String serverProperty = System.getProperty(OPEN_STREET_MAP_PROPERTY);
         final String suffix = serverProperty.substring(serverProperty.indexOf("}") + 2);
         final String property = System.getProperty(OPEN_STREET_MAP_CACHE_PROPERTY) + File.separator + suffix;
         
         if(property != null && !property.isEmpty()) {
            final File directory = new File(property);
            
            if(!directory.exists()) {
               directory.mkdirs();
            }
            
            if(directory.isDirectory()) {
               return directory;
            }
         }
      }
      
      return null;
   }
   
   private static synchronized String getNextServerUrl() {
      final String url = OPEN_STREET_MAP_URL[URL_INDEX];
      URL_INDEX = (URL_INDEX + 1) % OPEN_STREET_MAP_URL.length;
      
      return url;
   }
   
   private static Texture2d getOpenStreetMapTexture(final String urlString) {
      Texture2d texture = CACHED_TEXTURES.get(urlString);
      
      if(texture == null) {
         final File cachedFile = new File(OPEN_STREET_MAP_CACHE_DIRECTORY, urlString.substring(urlString.indexOf(".org/") + 5));
         
         if(cachedFile.exists()) {
            try {
               texture = Texture2d.getTexture(new FileInputStream(cachedFile), GL2.GL_RGBA);
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            }
         }
         
         // if cache didn't exist or reading image failed
         if(texture == null) {
            try {
               final URL url = new URL(urlString);
               final URLConnection connection = url.openConnection();
               
               try (final InputStream is = connection.getInputStream()) {
                  texture = Texture2d.getTexture(is, GL2.GL_RGBA);
                  final BufferedImage image = texture.getImage();
                  
                  if(image != null && !cachedFile.exists()) {
                     cachedFile.mkdirs();
                     ImageIO.write(image, "png", cachedFile);
                  }
               }
            } catch(final IOException e) {
               e.printStackTrace();
            }
         }
         
         if(texture != null) {
            CACHED_TEXTURES.put(urlString, texture);
         }
      }
      
      return texture;
   }
   
   private static <K, V> Map<K, V> createLeastRecentlyUsedMap(final int maxEntries) {
      return new LinkedHashMap<K, V>(maxEntries * 10 / 7, 0.7f, true) {
         private static final long serialVersionUID = 6027248153957687785L;

         @Override
         protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
             return size() > maxEntries;
         }
      };
   }

   static class TextureServer extends Thread {
      private final Queue<EllipticalSegment> pending = new LinkedBlockingQueue<>();
      
      public TextureServer() {
         this.start();
      }
      
      public synchronized void addPending(final EllipticalSegment segment) {
         pending.add(segment);
         this.notify();
      }
      
      @Override
      public void run() {
         while(true) {
            synchronized(this) {
                  try {
                     if(this.pending.isEmpty()) {
                        this.wait();
                     } else {
                        Thread.sleep(1);
                     }
                  } catch (final InterruptedException e) {
                     e.printStackTrace();
                  }
               
               EllipticalSegment segment = null;
               
               for(final EllipticalSegment es : this.pending) {
                  if(segment == null || segment.getDepth() > es.getDepth()) {
                     segment = es;
                  }
               }
               
               if(segment != null) {
                  this.pending.remove(segment);
                  // TODO: pull from open street map (no free high res satellite imagery i can find, unfortunately)
                  // maybe this? http://mike.teczno.com/notes/osm-us-terrain-layer/background.html
                  final int[][] tiles = computeZoomTile(segment);
                  final Texture2d[] textures = new Texture2d[tiles.length];
                  final Tuple2d[][] texCoords = new Tuple2d[tiles.length][];
                  
                  for(int i = 0; i < tiles.length; i++) {
                     final int[] tile = tiles[i];
                     
                     final String urlString = getNextServerUrl() + "/" + tile[2] + "/" + tile[0] + "/" + tile[1] + ".png";
                     final Texture2d texture = getOpenStreetMapTexture(urlString);
                        
                     if(texture == null) {
                        textures[i] = EarthImagery.BASE_EARTH_TEXTURE;
                        texCoords[i] = null;
                     } else {
                        final GeodesicVertex[] vertices = segment.getVertices();
                        textures[i] = texture;
                        texCoords[i] = new Tuple2d[vertices.length];
                        
                        for(int j = 0; j < vertices.length; j++) {
                           final Tuple3d lonLatAlt = vertices[j].getGeodesicVertex();
                           System.out.println(lonLatAlt);
                           final double[] tileDouble = tileXYZDouble(lonLatAlt.x, lonLatAlt.y, tile[2]);
                           
                           final Tuple2d texCoord = new Tuple2d();
                           texCoord.x = tileDouble[0] - Math.floor(tileDouble[0]);
                           texCoord.y = 1.0 - (tileDouble[1] - Math.floor(tileDouble[1]));
                           
                           texCoords[i][j] = texCoord;
                        }
                     }
                  }
                  
                  segment.setTexture(textures, texCoords);
               }
            }
         }
      }
   }
   
   public static void main(final String[] args) {
      System.out.println(Arrays.toString(EarthImagery.xy2lonlat(0, 0, 0)));
      for(int z = 0; z < 6; z++) {
         System.out.println("zoom: " + z);
         for(int x = 0; x < Math.pow(2, z); x++) {
            for(int y = 0; y < Math.pow(2, z); y++) {
               final double[] lonLat = xy2lonlat(x, y, z);
               final int[] tile = tileXYZ(lonLat[0], lonLat[1], z);

//               final double[] lonLat = new double[] { tile2lon(x, z), tile2lat(y, z) };
//               final int[] tile = getTileNumbers(lonLat[0], lonLat[1], z);
               
               if(!Arrays.equals(tile, new int[] { x, y, z })) {
                  System.err.println("not equal: " + x + ", " + y + ", " + z + " -> " + lonLat[0] + ", " + lonLat[1] + " -> " + Arrays.toString(tile));
               }
            }
         }
      }
      
      System.out.println("done!");
      System.exit(0);
   }
}