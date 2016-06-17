package com.stephenwranger.thesis.geospatial;

import java.util.List;

import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.Ellipsoid;
import com.stephenwranger.graphics.utils.MathUtils;

/**
 * Reference frame for WGS84 (World Geodesic System 1984) where X-Axis exits at 0 deg longitude, 0 deg latitude, Y-Axis
 * exits at 90 deg longitude, 0 deg latitude, and Z-Axis exits 90 deg latitude (longitude undefined at poles); Certesian
 * (0,0,0) is at the Earth's center of mass.<br/><br/>
 * 
 * https://en.wikipedia.org/wiki/World_Geodetic_System<br/>
 * https://en.wikipedia.org/wiki/Geodetic_datum
 * 
 * @author rangers
 *
 */
public class WGS84 {
   public static final double EQUATORIAL_RADIUS = 6378137.0;
   public static final double FLATTENING = 1.0 / 298.257223563;
   public static final double SEMI_MINOR_RADIUS = EQUATORIAL_RADIUS * (1.0 - FLATTENING); // a(1 − f), 6356752.3142
   public static final double MASS = 5.97219e24; // kg
   public static final double ANGULAR_ECCENTRICITY = Math.acos((SEMI_MINOR_RADIUS / EQUATORIAL_RADIUS));
   public static final double FIRST_ECCENTRICITY_SQUARED = (2.0 * FLATTENING) - (FLATTENING - FLATTENING); // 2f − f^2
   public static final double SECOND_ECCENTRICITY_SQUARED = (FLATTENING * (2.0 - FLATTENING)) / ((1.0 - FLATTENING) * (1.0 - FLATTENING)); //f(2 − f)/(1 − f)^2;
   public static final double REDUCED_LATITUDE = Math.sqrt(1.0 - FIRST_ECCENTRICITY_SQUARED * FIRST_ECCENTRICITY_SQUARED);
   
   private static final Ellipsoid ELLIPSOID = new Ellipsoid(new Tuple3d(), EQUATORIAL_RADIUS, FLATTENING, FIRST_ECCENTRICITY_SQUARED, SECOND_ECCENTRICITY_SQUARED);
   
   /**
    * Return the cartesian coordinates for the given longitude, latitude and altitude (in degrees and meters).<br/><br/>
    * https://en.wikipedia.org/wiki/Geographic_coordinate_conversion#From_geodetic_to_ECEF_coordinates
    * 
    * @param lonLatAltDeg the longitude (in decimal degrees), latitude (in decimal degrees) and altitude (in meters)
    * @return the cartesian coordinate conversion of the given geodesic coordinates
    */
   public static Tuple3d geodesicToCartesian(final Tuple3d lonLatAltDeg) {
      double lon = Math.toRadians(lonLatAltDeg.x);
      double lat = Math.toRadians(lonLatAltDeg.y);
      double alt = lonLatAltDeg.z;
      
      if (lon > Math.PI) {
         lon -= MathUtils.TWO_PI;
      }
      final double sinLat = Math.sin(lat);
      final double cosLat = Math.cos(lat);
      final double sin2Lat = sinLat * sinLat;
      final double Rn = EQUATORIAL_RADIUS / Math.sqrt(1 - FIRST_ECCENTRICITY_SQUARED * sin2Lat);  // earth radius at location

      final Tuple3d result = new Tuple3d();
      
      result.x = (Rn + alt) * cosLat * Math.cos(lon);
      result.y = (Rn + alt) * cosLat * Math.sin(lon);
      result.z = ((Rn * (1 - FIRST_ECCENTRICITY_SQUARED)) + alt) * sinLat;
      
      return result;
   }

   /**
    * Return the geodesic coordinates (in degrees and meters) for the given longitude, latitude and altitude (in degrees and meters).<br/><br/>
    * https://en.wikipedia.org/wiki/Geographic_coordinate_conversion#From_geodetic_to_ECEF_coordinates<br/>
    * 
    * @param xyz the cartesian coordinates
    * @return the geodesic coordinate conversion of the given cartesian coordinates as longitude (in decimal degrees), latitude (in decimal degrees) and altitude (in meters)
    */
   public static Tuple3d cartesianToGeodesic(final Tuple3d cartesian) {
      return ELLIPSOID.toLonLatAlt(cartesian);
   }
   
   /**
    * Computes Ellipsoid height at given lon/lat<br/>
    * https://www.physicsforums.com/threads/radius-of-ellipsoid.251321/ (their major/minor axis variables are swapped it looks like)
    * 
    * <pre>
    * http://www.wolframalpha.com/input/?i=(r%5E2+*+cos%5E2(t)+*+sin%5E2(p))+%2F+a%5E2+%2B+(r%5E2+*+sin%5E2(t)+*+sin%5E2(p))+%2F+b%5E2+%2B+(r%5E2+*+cos%5E2(p))+%2F+c%5E2+%3D+1,+solve+for+r
    * 
    * r = ±(a b c)/sqrt(c^2 sin^2(p) (a^2 sin^2(t)+b^2 cos^2(t))+a^2 b^2 cos^2(p))
    *    OR
    * r = sqrt(c^2 sin^2(p) (a^2 sin^2(t)+b^2 cos^2(t))+a^2 b^2 cos^2(p))!=0 and a b c!=0
    * </pre>
    * 
    * @param lonDeg
    * @param latDeg
    * @return
    */
   public static double getAltitude(final double lonDeg, final double latDeg) {
      // TODO: add DTED
      final double a = EQUATORIAL_RADIUS;
      final double c = SEMI_MINOR_RADIUS;
      final double a2 = a * a;
      final double c2 = c * c;
      
      final double phi = Math.toRadians(latDeg);
      final double sinPhi = Math.sin(phi);
      final double sin2Phi = sinPhi * sinPhi;
      final double cosPhi = Math.cos(phi);
      final double cos2Phi = cosPhi * cosPhi;
      final double numerator = a2 * c2;
      final double denominator = a2 * sin2Phi + c2 * cos2Phi;
      final double r = Math.sqrt(numerator / denominator);
      
      return r;
   }
   
   public static Quat4d getOrientation(final double lonDeg, final double latDeg) {
      final Quat4d qOrientation = new Quat4d();
      final double lon = Math.toRadians(lonDeg);
      double lat = Math.toRadians(latDeg);
      
      if (lat != 0) {
         lat = Math.atan(REDUCED_LATITUDE * Math.tan(lat));
      }

      // Highly optimized multiplication of three quaternions for yaw, pitch, and roll
      final double sinHalfAzimuth = Math.sin((lon - MathUtils.HALF_PI) / 2.);
      final double cosHalfAzimuth = Math.cos((lon - MathUtils.HALF_PI) / 2.);

      final double sinHalfElevation = Math.sin(lat / 2.);
      final double cosHalfElevation = Math.cos(lat / 2.);

      double x = -sinHalfAzimuth * cosHalfElevation;
      double y = cosHalfAzimuth * cosHalfElevation;
      double z = cosHalfAzimuth * sinHalfElevation;
      double w = -sinHalfAzimuth * sinHalfElevation;
      
      /*
       * Normalize if necessary
       */
      double qMagnitude = x * x + y * y + z * z + w * w;
      
      if (qMagnitude != 1.0) {
         qMagnitude = Math.sqrt(qMagnitude);
         x /= qMagnitude;
         y /= qMagnitude;
         z /= qMagnitude;
         w /= qMagnitude;
      }
      
      qOrientation.set(x, y, z, w);
      
      return qOrientation;
   }
   
   /**
    * Returns the closest intersection to the WGS84 Ellipsoid at the given altitude; if isGeodetic, returns as 
    * lon/lat/alt in degrees and meters and cartesian coordinates otherwise.
    * 
    * @param ray
    * @param altitude
    * @param geodetic
    * @return
    */
   public static Tuple3d getNearIntersection(final PickingRay ray, final double altitude, final boolean isGeodetic) {
      final Ellipsoid ellipsoid = (altitude == 0) ? WGS84.ELLIPSOID : new Ellipsoid(new Tuple3d(), altitude, FLATTENING, FIRST_ECCENTRICITY_SQUARED, SECOND_ECCENTRICITY_SQUARED);
      final List<Tuple3d> intersections = ellipsoid.getIntersection(ray);
      Tuple3d solution = null;
      
      if(intersections.size() == 1) {
         solution = intersections.get(0);
      } else if(intersections.size() == 2) {
         final Tuple3d origin = ray.getOrigin();
         final Tuple3d a = intersections.get(0);
         final Tuple3d b = intersections.get(1);
         final double aDist = a.distance(origin);
         final double bDist = b.distance(origin);
         
         solution = (aDist <= bDist) ? a : b;
      }
      
      if(solution != null && isGeodetic) {
         solution = ellipsoid.toLonLatAlt(solution);
      }
      
      return solution;
   }
}
