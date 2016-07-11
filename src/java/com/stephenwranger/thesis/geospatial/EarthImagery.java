package com.stephenwranger.thesis.geospatial;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.EllipticalSegment;
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
//   private static final TextureServer TEXTURE_SERVER = new TextureServer();
   
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
      segment.setTexture(EarthImagery.BASE_EARTH_TEXTURE, null);
      
      // TODO: need to support multiple textures; they're never at the same depth
//      if (EarthImagery.OPEN_STREET_MAP_URL != null) {
//         TEXTURE_SERVER.addPending(segment);
//      }
   }
   
   private static int[] computeZoomTile(final EllipticalSegment segment) {
      final Vertex[] vertices = segment.getVertices();
      final Tuple3d lla0 = WGS84.cartesianToGeodesic(vertices[0].getVertex());
      final Tuple3d lla1 = WGS84.cartesianToGeodesic(vertices[1].getVertex());
      final Tuple3d lla2 = WGS84.cartesianToGeodesic(vertices[2].getVertex());
      final double lon = MathUtils.sum(lla0.x, lla1.x, lla2.x);
      final double lat = MathUtils.sum(lla0.y, lla1.y, lla2.y);
      
      return getTileNumbers(lon, lat, segment.getDepth());
            
            
//      final Vertex[] vertices = segment.getVertices();
//      final Tuple3d lla0 = WGS84.cartesianToGeodesic(vertices[0].getVertex());
//      final Tuple3d lla1 = WGS84.cartesianToGeodesic(vertices[1].getVertex());
//      final Tuple3d lla2 = WGS84.cartesianToGeodesic(vertices[2].getVertex());
//
//      final double minLon = MathUtils.getMin(lla0.x, lla1.x, lla2.x);
//      final double maxLon = MathUtils.getMax(lla0.x, lla1.x, lla2.x);
//      
//      final double minLat = MathUtils.getMin(lla0.y, lla1.y, lla2.y);
//      final double maxLat = MathUtils.getMax(lla0.y, lla1.y, lla2.y);
//      
//      for(int i = 19; i >= 0; i--) {
//         final int[] minTile = getTileNumbers(minLon, minLat, i);
//         final int[] maxTile = getTileNumbers(maxLon, maxLat, i);
//         
//         if(minTile[1] == maxTile[1] && minTile[2] == maxTile[2]) {
//            return minTile;
//         }
//      }
//      
//      return new int[] { 0, 0, 0 };
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
   public static int[] getTileNumbers(final double lonDegrees, final double latDegrees, final int zoom) {
      int xtile = (int) Math.floor((lonDegrees + 180) / 360 * (1 << zoom));
      int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(latDegrees)) + 1 / Math.cos(Math.toRadians(latDegrees))) / Math.PI) / 2 * (1 << zoom));
      if (xtile < 0)
         xtile = 0;
      if (xtile >= (1 << zoom))
         xtile = ((1 << zoom) - 1);
      if (ytile < 0)
         ytile = 0;
      if (ytile >= (1 << zoom))
         ytile = ((1 << zoom) - 1);
      return new int[] { zoom, xtile, ytile };
   }
   
   public static double tile2lon(final int x, final int z) {
      return x / Math.pow(2.0, z) * 360.0 - 180;
   }

   public static double tile2lat(final int y, final int z) {
     double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
     return Math.toDegrees(Math.atan(Math.sinh(n)));
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
               System.out.println(directory);
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
                  this.wait();
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
                  final int[] zoomXY = computeZoomTile(segment);
                  final String urlString = getNextServerUrl() + "/" + zoomXY[0] + "/" + zoomXY[1] + "/" + zoomXY[2] + ".png";
                  
                  final Texture2d texture = getOpenStreetMapTexture(urlString);
                     
                  if(texture == null) {
                     segment.setTexture(EarthImagery.BASE_EARTH_TEXTURE, null);
                  } else {
                     final Vertex[] vertices = segment.getVertices();
                     final Tuple2d[] texCoords = new Tuple2d[vertices.length];
                     
                     for(int i = 0; i < vertices.length; i++) {
                        final Tuple3d lonLatAlt = WGS84.cartesianToGeodesic(vertices[i].getVertex());
                        final double tileMinLon = tile2lon(zoomXY[1], zoomXY[0]);      // west
                        final double tileMaxLon = tile2lon(zoomXY[1] + 1, zoomXY[0]);  // east
                        final double tileMaxLat = tile2lat(zoomXY[2], zoomXY[0]);      // north
                        final double tileMinLat = tile2lat(zoomXY[2] + 1, zoomXY[0]);  // south
                        
                        final Tuple2d texCoord = new Tuple2d();
                        texCoord.x = (lonLatAlt.x - tileMinLon) / (tileMaxLon - tileMinLon);
                        texCoord.y = (lonLatAlt.y - tileMinLat) / (tileMaxLat - tileMinLat);
//                        System.out.println("location: " + lonLatAlt);
//                        System.out.println("zoom: " + zoomXY[0]);
//                        System.out.println("tile: " + zoomXY[1] + ", " + zoomXY[2]);
//                        System.out.println("tile min: " + tileMinLon + ", " + tileMinLat);
//                        System.out.println("tile max: " + tileMaxLon + ", " + tileMaxLat);
//                        System.out.println("texture coordinate: " + texCoord);
                        
                        texCoords[i] = texCoord;
                     }
                     
                     segment.setTexture(texture, texCoords);
                  }
               }
            }
         }
      }
   }
}