package com.stephenwranger.thesis.geospatial;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.EllipticalSegment;
import com.stephenwranger.graphics.renderables.GeodesicVertex;
import com.stephenwranger.graphics.utils.MathUtils;
import com.stephenwranger.graphics.utils.TupleMath;
import com.stephenwranger.graphics.utils.textures.Texture2d;

public class EarthImagery {
   public enum ImageryType {
      OPEN_STREET_MAP, STAMEN_TERRAIN;

      public File getCacheDirectory() {
         switch (this) {
            default:
            case OPEN_STREET_MAP:
               return EarthImagery.OPEN_STREET_MAP_CACHE_DIRECTORY;
            case STAMEN_TERRAIN:
               return EarthImagery.STAMEN_CACHE_DIRECTORY;
         }
      }

      public String getUrl(final int[] tileXyz) {
         switch (this) {
            default:
            case OPEN_STREET_MAP:
               return EarthImagery.getOpenStreetMapUrl(tileXyz);
            case STAMEN_TERRAIN:
               return EarthImagery.getStamenUrl(tileXyz);
         }
      }

      public boolean hasCacheDirectory() {
         switch (this) {
            default:
            case OPEN_STREET_MAP:
               return EarthImagery.OPEN_STREET_MAP_CACHE_DIRECTORY.exists();
            case STAMEN_TERRAIN:
               return EarthImagery.STAMEN_CACHE_DIRECTORY.exists();
         }
      }

      public boolean isValid() {
         switch (this) {
            default:
            case OPEN_STREET_MAP:
               return EarthImagery.OPEN_STREET_MAP_URL != null;
            case STAMEN_TERRAIN:
               return EarthImagery.STAMEN_URL != null;
         }
      }
   }

   static class TextureServer extends Thread {
      private final Map<EllipticalSegment, ImageryType> pending = new ConcurrentHashMap<>();

      public TextureServer() {
         this.start();
      }

      public synchronized void addPending(final EllipticalSegment segment, final ImageryType imageryType) {
         this.pending.put(segment, imageryType);
         this.notify();
      }

      @Override
      public void run() {
         EllipticalSegment segment = null;
         ImageryType imageryType = null;

         while (true) {
            segment = null;
            imageryType = null;
            synchronized (this) {
               try {
                  if (this.pending.isEmpty()) {
                     this.wait();
                  }
               } catch (final InterruptedException e) {
                  e.printStackTrace();
               }

               for (final Entry<EllipticalSegment, ImageryType> pair : this.pending.entrySet()) {
                  final EllipticalSegment es = pair.getKey();
                  if ((segment == null) || (segment.getDepth() > es.getDepth())) {
                     segment = es;
                     imageryType = pair.getValue();
                  }
               }
            }

            if (segment != null) {
               // TODO: pull from open street map (no free high res satellite imagery i can find, unfortunately)
               // maybe this? http://mike.teczno.com/notes/osm-us-terrain-layer/background.html
               final int[][] tiles = EarthImagery.computeZoomTile(segment);
               final Texture2d[] textures = new Texture2d[tiles.length];
               final Tuple2d[][] texCoords = new Tuple2d[tiles.length][];

               for (int i = 0; i < tiles.length; i++) {
                  final int[] tile = tiles[i];

                  final String urlString = imageryType.getUrl(tile);
                  final Texture2d texture = EarthImagery.getTexture(urlString, imageryType, tile);

                  if (texture == null) {
                     textures[i] = EarthImagery.BASE_EARTH_TEXTURE;
                     texCoords[i] = null;
                  } else {
                     final GeodesicVertex[] vertices = segment.getVertices();
                     textures[i] = texture;
                     texCoords[i] = new Tuple2d[vertices.length];

                     for (int j = 0; j < vertices.length; j++) {
                        final Tuple3d lonLatAlt = vertices[j].getGeodesicVertex();
                        final double[] tileDouble = EarthImagery.tileXYZDouble(lonLatAlt.x, lonLatAlt.y, tile[2]);

                        final Tuple2d texCoord = new Tuple2d();
                        texCoord.x = tileDouble[0] - tile[0];
                        texCoord.y = 1.0 - (tileDouble[1] - tile[1]);

                        texCoords[i][j] = texCoord;
                     }
                  }
               }

               segment.setTexture(textures, texCoords);

               synchronized (this) {
                  if (segment != null) {
                     this.pending.remove(segment);
                  }
               }
            }
         }
      }
   }

   private static final Map<String, Texture2d> CACHED_TEXTURES                 = EarthImagery.createLeastRecentlyUsedMap(10000);
   private static String                       IMAGE_CACHE_PROPERTY            = "image.cache";
   // OpenStreetMap Map Tiles
   private static String                       OPEN_STREET_MAP_PROPERTY        = "osm.server";

   private static String[]                     OPEN_STREET_MAP_URL             = EarthImagery.getOpenStreetMapServers();
   private static final File                   OPEN_STREET_MAP_CACHE_DIRECTORY = EarthImagery.getOpenStreetMapCacheDirectory();
   // Stamen Terrain Tiles
   private static String                       STAMEN_PROPERTY                 = "stamen.server";

   private static String[]                     STAMEN_URL                      = EarthImagery.getStamenServers();
   private static final File                   STAMEN_CACHE_DIRECTORY          = EarthImagery.getStamenCacheDirectory();

   private static final Texture2d              BASE_EARTH_TEXTURE              = EarthImagery.getBaseTexture();
   private static final TextureServer          TEXTURE_SERVER                  = new TextureServer();

   private static int                          OPEN_STREET_MAP_URL_INDEX       = 0;

   private static int                          STAMEN_URL_INDEX                = 0;

   static {
      EarthImagery.disableSslValidation();
   }

   private EarthImagery() {
      //statics only
   }

   public static void disableSslValidation() {
      try {
         // Create a trust manager that does not validate certificate chains
         final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
               return null;
            }
         } };

         SSLContext sc = SSLContext.getInstance("TLS");
         sc.init(null, trustAllCerts, new java.security.SecureRandom());
         HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
         HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(final String string, final SSLSession ssls) {
               return true;
            }
         });
      } catch (final Exception e) {
      }
   }

   public static void main(final String[] args) {
      final Tuple3d minLonLatAlt = new Tuple3d(-120.0, 30.0, 0.0);
      final Tuple3d maxLonLatAlt = new Tuple3d(-115.0, 40.0, 0.0);
      final int depth = 5;
      final int[][] tiles = EarthImagery.computeZoomTile(minLonLatAlt, maxLonLatAlt, depth);
      final Texture2d[] textures = new Texture2d[tiles.length];
      final Tuple2d[][] texCoords = new Tuple2d[tiles.length][];

      for (int i = 0; i < tiles.length; i++) {
         final int[] tile = tiles[i];

         final String urlString = EarthImagery.getNextOpenStreetMapServerUrl() + "/" + tile[2] + "/" + tile[0] + "/" + tile[1] + ".png";
         final Texture2d texture = EarthImagery.getTexture(urlString, ImageryType.OPEN_STREET_MAP, tile);

         if (texture == null) {
            textures[i] = EarthImagery.BASE_EARTH_TEXTURE;
            texCoords[i] = null;
         } else {
            final Tuple3d[] vertices = new Tuple3d[] { minLonLatAlt, maxLonLatAlt };
            textures[i] = texture;
            texCoords[i] = new Tuple2d[vertices.length];

            for (int j = 0; j < vertices.length; j++) {
               final Tuple3d lonLatAlt = vertices[j];
               final double[] tileDouble = EarthImagery.tileXYZDouble(lonLatAlt.x, lonLatAlt.y, tile[2]);

               final Tuple2d texCoord = new Tuple2d();
               texCoord.x = tileDouble[0] - tile[0];
               texCoord.y = tileDouble[1] - tile[1];

               texCoords[i][j] = texCoord;
            }
         }
      }

      final JFrame frame = new JFrame("Texture Test");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setLocation(100, 100);

      final JTabbedPane tabPane = new JTabbedPane();

      for (int i = 0; i < textures.length; i++) {
         final BufferedImage image = textures[i].getImage();
         final Tuple2d[] tex = texCoords[i];

         final JPanel panel = new JPanel() {
            private static final long serialVersionUID = 2576079064574101275L;
            private final Color[]     colors           = new Color[] { Color.RED, Color.GREEN, Color.BLUE };

            @Override
            public void paintComponent(final Graphics g) {
               super.paintComponent(g);

               final Graphics2D g2 = (Graphics2D) g;
               final int width = image.getWidth();
               final int height = image.getHeight();
               final int[] x = new int[tex.length];
               final int[] y = new int[tex.length];

               g2.drawImage(image, 0, 0, null);
               g.setColor(Color.black);

               for (int j = 0; j < x.length; j++) {
                  x[j] = (int) (width * tex[j].x);
                  y[j] = (int) (height * tex[j].y);
               }

               g.drawPolygon(x, y, x.length);

               for (int j = 0; j < x.length; j++) {
                  g.setColor(this.colors[j]);
                  g.fillOval(x[j] - 5, y[j] - 5, 10, 10);
               }
            }
         };

         panel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
         tabPane.insertTab("Texture " + i, null, panel, "", i);
      }

      SwingUtilities.invokeLater(() -> {
         frame.getContentPane().add(tabPane);
         frame.pack();
         frame.setVisible(true);
      });
   }

   /**
    * Retrieves the imagery tile from the OpenStreetMap server and updates the {@link EllipticalSegment}
    * texture coordinates or sets the base imagery texture if no OpenStreetMap server is defined.<br/>
    * <br/>
    * http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    *
    * @param segment
    */
   public static void setImagery(final EllipticalSegment segment, final ImageryType imageryType) {
      // set base texture until server can get around to handling this
      segment.setTexture(null, null);

      // TODO: need to support multiple textures; they're never at the same depth
      if ((imageryType != null) && imageryType.isValid()) {
         EarthImagery.TEXTURE_SERVER.addPending(segment, imageryType);
      } else {
         // set base texture if no imagery is available
         segment.setBaseTexture(EarthImagery.BASE_EARTH_TEXTURE);
      }
   }

   private static int[][] computeZoomTile(final EllipticalSegment segment) {
      final Tuple3d[] vertices = segment.getGeodesicVertices();
      final Tuple3d minLonLatAlt = TupleMath.getMin(vertices);
      final Tuple3d maxLonLatAlt = TupleMath.getMax(vertices);

      return EarthImagery.computeZoomTile(minLonLatAlt, maxLonLatAlt, segment.getDepth() + 2);
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

   private static int[][] computeZoomTile(final Tuple3d minLonLatAlt, final Tuple3d maxLonLatAlt, final int depth) {
      final int[] minTile = EarthImagery.tileXYZ(minLonLatAlt.x, minLonLatAlt.y, depth);
      final int[] maxTile = EarthImagery.tileXYZ(maxLonLatAlt.x, maxLonLatAlt.y, depth);

      final int minX = Math.min(minTile[0], maxTile[0]);
      final int maxX = Math.max(minTile[0], maxTile[0]);
      final int minY = Math.min(minTile[1], maxTile[1]);
      final int maxY = Math.max(minTile[1], maxTile[1]);

      final List<int[]> tiles = new ArrayList<>();
      final int n = (int) Math.pow(2, depth);

      for (int x = (int) MathUtils.clamp(0, n - 1, minX); x <= (int) MathUtils.clamp(0, n - 1, maxX); x++) {
         for (int y = (int) MathUtils.clamp(0, n - 1, minY); y <= (int) MathUtils.clamp(0, n - 1, maxY); y++) {
            tiles.add(new int[] { x, y, depth });
         }
      }

      return tiles.toArray(new int[tiles.size()][]);
   }

   private static <K, V> Map<K, V> createLeastRecentlyUsedMap(final int maxEntries) {
      return new LinkedHashMap<K, V>((maxEntries * 10) / 7, 0.7f, true) {
         private static final long serialVersionUID = 6027248153957687785L;

         @Override
         protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
            return this.size() > maxEntries;
         }
      };
   }

   private static Texture2d getBaseTexture() {
      return Texture2d.getTexture(Earth.class.getResourceAsStream("world.topo.bathy.200401.3x5400x2700.png"), GL.GL_RGBA);
   }

   private static synchronized String getNextOpenStreetMapServerUrl() {
      final String url = EarthImagery.OPEN_STREET_MAP_URL[EarthImagery.OPEN_STREET_MAP_URL_INDEX];
      EarthImagery.OPEN_STREET_MAP_URL_INDEX = (EarthImagery.OPEN_STREET_MAP_URL_INDEX + 1) % EarthImagery.OPEN_STREET_MAP_URL.length;

      return url;
   }

   private static synchronized String getNextStamenServerUrl() {
      final String url = EarthImagery.STAMEN_URL[EarthImagery.STAMEN_URL_INDEX];
      EarthImagery.STAMEN_URL_INDEX = (EarthImagery.STAMEN_URL_INDEX + 1) % EarthImagery.STAMEN_URL.length;

      return url;
   }

   private static File getOpenStreetMapCacheDirectory() {
      if ((EarthImagery.OPEN_STREET_MAP_URL != null) && (EarthImagery.OPEN_STREET_MAP_URL.length > 0)) {
         final String serverProperty = System.getProperty(EarthImagery.OPEN_STREET_MAP_PROPERTY);
         final String suffix = serverProperty.substring(serverProperty.indexOf("}") + 2);
         final String property = System.getProperty(EarthImagery.IMAGE_CACHE_PROPERTY) + File.separator + suffix;

         if ((property != null) && !property.isEmpty()) {
            final File directory = new File(property);

            if (!directory.exists()) {
               directory.mkdirs();
            }

            if (directory.isDirectory()) {
               return directory;
            }
         }
      }

      return null;
   }

   private static String[] getOpenStreetMapServers() {
      final String property = System.getProperty(EarthImagery.OPEN_STREET_MAP_PROPERTY);
      String[] urls = null;

      if ((property != null) && !property.isEmpty()) {
         final String prefix = property.substring(0, property.indexOf("{"));
         final String[] servers = property.substring(property.indexOf("{") + 1, property.indexOf("}")).split(",");
         final String suffix = property.substring(property.indexOf("}") + 1);
         urls = new String[servers.length];

         for (int i = 0; i < servers.length; i++) {
            urls[i] = prefix + servers[i] + suffix;
         }
      }

      return urls;
   }

   private static String getOpenStreetMapUrl(final int[] tileXyz) {
      return EarthImagery.getNextOpenStreetMapServerUrl() + "/" + tileXyz[2] + "/" + tileXyz[0] + "/" + tileXyz[1] + ".png";
   }

   private static File getStamenCacheDirectory() {
      if ((EarthImagery.STAMEN_URL != null) && (EarthImagery.STAMEN_URL.length > 0)) {
         final String serverProperty = System.getProperty(EarthImagery.STAMEN_PROPERTY);
         String suffix = serverProperty.substring(serverProperty.indexOf("//") + 2);

         if (suffix.contains("}.")) {
            suffix = serverProperty.substring(serverProperty.indexOf("}") + 2);
         }
         final String property = System.getProperty(EarthImagery.IMAGE_CACHE_PROPERTY) + File.separator + suffix;

         if ((property != null) && !property.isEmpty()) {
            final File directory = new File(property);

            if (!directory.exists()) {
               directory.mkdirs();
            }

            if (directory.isDirectory()) {
               return directory;
            }
         }
      }

      return null;
   }

   private static String[] getStamenServers() {
      final String property = System.getProperty(EarthImagery.STAMEN_PROPERTY);
      String[] urls = null;

      if ((property != null) && !property.isEmpty()) {
         if (property.contains("{")) {
            final String prefix = property.substring(0, property.indexOf("{"));
            final String[] servers = property.substring(property.indexOf("{") + 1, property.indexOf("}")).split(",");
            final String suffix = property.substring(property.indexOf("}") + 1);
            urls = new String[servers.length];

            for (int i = 0; i < servers.length; i++) {
               urls[i] = prefix + servers[i] + suffix;
            }
         } else {
            urls = new String[] { property };
         }
      }

      return urls;
   }

   private static String getStamenUrl(final int[] tileXyz) {
      return EarthImagery.getNextStamenServerUrl() + "/" + tileXyz[2] + "/" + tileXyz[0] + "/" + tileXyz[1] + ".jpg";
   }

   private static Texture2d getTexture(final String urlString, final ImageryType imageryType, final int[] tileXyz) {
      return EarthImagery.getTexture(urlString, imageryType, tileXyz, 0);
   }

   private static Texture2d getTexture(final String urlString, final ImageryType imageryType, final int[] tileXyz, final int redirectCount) {
      final String tileKey = imageryType.name() + File.separator + tileXyz[2] + File.separator + tileXyz[0] + File.separator + tileXyz[1];
      File cachedFile = null;
      Texture2d texture = EarthImagery.CACHED_TEXTURES.get(tileKey);

      if (texture == null) {
         if (imageryType.hasCacheDirectory()) {
            cachedFile = new File(imageryType.getCacheDirectory(), tileXyz[2] + File.separator + tileXyz[0] + File.separator + tileXyz[1] + ".png");

            if (cachedFile.exists()) {
               try {
                  texture = Texture2d.getTexture(new FileInputStream(cachedFile), GL.GL_RGBA);
               } catch (FileNotFoundException e) {
                  e.printStackTrace();
               }
            }
         }

         // if cache didn't exist or reading image failed
         if (texture == null) {
            try {
               final URL url = new URL(urlString);
               final URLConnection connection = url.openConnection();

               if ((connection instanceof HttpURLConnection) && (redirectCount < 5)) {
                  final int status = ((HttpURLConnection) connection).getResponseCode();

                  // if the URL is redirecting traffic; return the new url's value
                  if ((status == HttpURLConnection.HTTP_MOVED_TEMP) || (status == HttpURLConnection.HTTP_MOVED_PERM)) {
                     ((HttpURLConnection) connection).disconnect();
                     return EarthImagery.getTexture(connection.getHeaderField("Location"), imageryType, tileXyz, redirectCount + 1);
                  }
               }

               try (final InputStream is = connection.getInputStream()) {
                  texture = Texture2d.getTexture(is, GL.GL_RGBA);
                  final BufferedImage image = texture.getImage();

                  if ((cachedFile != null) && (image != null) && !cachedFile.exists()) {
                     cachedFile.mkdirs();
                     ImageIO.write(image, "png", cachedFile);

                     if (texture != null) {
                        EarthImagery.CACHED_TEXTURES.put(tileKey, texture);
                     }
                  }
               }
            } catch (final Exception e) {
               System.err.println(urlString);
               e.printStackTrace();
            }
         }
      }

      return texture;
   }

   private static double[] latEdges(final int y, final int z) {
      final double n = EarthImagery.numTiles(z);
      final double unit = 1 / n;
      final double relY1 = y * unit;
      final double relY2 = relY1 + unit;
      final double lat1 = EarthImagery.mercatorToLat(Math.PI * (1 - (2 * relY1)));
      final double lat2 = EarthImagery.mercatorToLat(Math.PI * (1 - (2 * relY2)));
      return new double[] { lat1, lat2 };
   }

   private static double[] lonEdges(final int x, final int z) {
      final double n = EarthImagery.numTiles(z);
      final double unit = 360 / n;
      final double lon1 = -180 + (x * unit);
      final double lon2 = lon1 + unit;
      return new double[] { lon1, lon2 };
   }

   private static double mercatorToLat(final double mercatorY) {
      return Math.toDegrees(Math.atan(Math.sinh(mercatorY)));
   }

   private static int numTiles(final int zoom) {
      return (int) Math.pow(2, zoom);
   }

   /**
    * Returns edges of requested tile S,W,N,E.
    *
    * @param x
    * @param y
    * @param z
    * @return
    */
   private static double[] tileEdges(final int x, final int y, final int z) {
      final double[] lats = EarthImagery.latEdges(y, z);
      final double[] lons = EarthImagery.lonEdges(x, z);
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
   private static int[] tileXYZ(final double lonDegrees, final double latDegrees, final int zoom) {
      final double[] xy = EarthImagery.tileXYZDouble(lonDegrees, latDegrees, zoom);
      final double x = xy[0];
      final double y = xy[1];
      return new int[] { (int) Math.floor(x), (int) Math.floor(y), zoom };
   }

   private static double[] tileXYZDouble(final double lonDegrees, final double latDegrees, final int zoom) {
      final double latRadians = Math.toRadians(latDegrees);
      final double n = EarthImagery.numTiles(zoom);
      final double x = (lonDegrees + 180.0) / 360.0;
      final double y = (1 - (Math.log(Math.tan(latRadians) + MathUtils.sec(latRadians)) / Math.PI)) / 2;

      return new double[] { x * n, y * n };
   }

   private static double[] xy2lonlat(final int x, final int y, final int z) {
      final double n = EarthImagery.numTiles(z);
      final double lon_deg = ((x / n) * 360.0) - 180.0;
      final double lat_rad = Math.atan(Math.sinh(Math.PI * (1 - ((2 * y) / n))));
      final double lat_deg = Math.toDegrees(lat_rad);
      return new double[] { lon_deg, lat_deg };
   }
}