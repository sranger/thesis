package com.stephenwranger.thesis.geospatial;

import com.stephenwranger.graphics.math.Tuple3d;

/**
 * Reference frame for WGS84 (World Geodesic System 1984) where X-Axis exits at 0 deg longitude, 0 deg latitude, Y-Axis
 * exits at 90 deg longitude, 0 deg latitude, and Z-Axis exits 90 deg latitude (longitude undefined at poles); Certesian
 * (0,0,0) is at the Earth's center of mass.<br/><br/>
 * 
 * https://en.wikipedia.org/wiki/World_Geodetic_System
 * 
 * @author rangers
 *
 */
public class WGS84 {
   public static final double EQUATORIAL_RADIUS = 6378137.0;
   public static final double FLATTENING = 1.0 / 298.257223563;
   public static final double SEMI_MINOR_RADIUS = EQUATORIAL_RADIUS * (1.0 - FLATTENING); // 6356752.3142
   
   /**
    * Return the cartesian coordinates for the given longitude, latitude and altitude (in degrees and meters).
    * 
    * @param lonLatAltDeg the longitude (in decimal degrees), latitude (in decimal degrees) and altitude (in meters)
    * @return the cartesian coordinate conversion of the given geodesic coordinates
    */
   public Tuple3d geodesicToCartesian(final Tuple3d lonLatAltDeg) {
      // TODO
      return null;
   }

   /**
    * Return the geodesic coordinates (in degrees and meters) for the given longitude, latitude and altitude (in degrees and meters).
    * 
    * @param xyz the cartesian coordinates
    * @return the geodesic coordinate conversion of the given cartesian coordinates as longitude (in decimal degrees), latitude (in decimal degrees) and altitude (in meters)
    */
   public Tuple3d cartesianToGeodesic(final Tuple3d xyz) {
      // TODO
      return null;
   }
}
