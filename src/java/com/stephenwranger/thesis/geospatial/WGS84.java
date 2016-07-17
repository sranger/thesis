package com.stephenwranger.thesis.geospatial;

import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Ellipsoid;
import com.stephenwranger.graphics.utils.MathUtils;

/**
 * Reference frame for WGS84 (World Geodesic System 1984) where X-Axis exits at 0 deg longitude, 0 deg latitude, Y-Axis
 * exits at 90 deg longitude, 0 deg latitude, and Z-Axis exits 90 deg latitude (longitude undefined at poles); Certesian
 * (0,0,0) is at the Earth's center of mass.<br/>
 * <br/>
 *
 * https://en.wikipedia.org/wiki/World_Geodetic_System<br/>
 * https://en.wikipedia.org/wiki/Geodetic_datum
 *
 * @author rangers
 *
 */
public class WGS84 {
   public static final double    EQUATORIAL_RADIUS           = 6378137.0;
   public static final double    FLATTENING                  = 1.0 / 298.257223563;
   public static final double    SEMI_MINOR_RADIUS           = WGS84.EQUATORIAL_RADIUS * (1.0 - WGS84.FLATTENING);                                                                                                                                                                                                                                                                               // a(1 − f), 6356752.3142
   public static final double    MASS                        = 5.97219e24;                                                                                                                                                                                                                                                                                                                                                                                                       // kg
   public static final double    ANGULAR_ECCENTRICITY        = Math.acos((WGS84.SEMI_MINOR_RADIUS / WGS84.EQUATORIAL_RADIUS));
   public static final double    FIRST_ECCENTRICITY_SQUARED  = (2.0 * WGS84.FLATTENING) - (WGS84.FLATTENING * WGS84.FLATTENING);                                                                                                                                                                                                                                     // 2f − f^2
   public static final double    SECOND_ECCENTRICITY_SQUARED = (WGS84.FLATTENING * (2.0 - WGS84.FLATTENING)) / ((1.0 - WGS84.FLATTENING) * (1.0 - WGS84.FLATTENING));                                                                                                                      //f(2 − f)/(1 − f)^2;
   public static final double    REDUCED_LATITUDE            = Math.sqrt(1.0 - (WGS84.FIRST_ECCENTRICITY_SQUARED * WGS84.FIRST_ECCENTRICITY_SQUARED));

   public static final Ellipsoid ELLIPSOID                   = new Ellipsoid(new Tuple3d(), WGS84.EQUATORIAL_RADIUS, WGS84.FLATTENING, WGS84.FIRST_ECCENTRICITY_SQUARED, WGS84.SECOND_ECCENTRICITY_SQUARED);

   /**
    * Return the geodesic coordinates (in degrees and meters) for the given longitude, latitude and altitude (in degrees
    * and meters).<br/>
    * <br/>
    * https://en.wikipedia.org/wiki/Geographic_coordinate_conversion#From_geodetic_to_ECEF_coordinates<br/>
    *
    * @param xyz
    *           the cartesian coordinates
    * @return the geodesic coordinate conversion of the given cartesian coordinates as longitude (in decimal degrees),
    *         latitude (in decimal degrees) and altitude (in meters)
    */
   public static Tuple3d cartesianToGeodesic(final Tuple3d cartesian) {
      return WGS84.ELLIPSOID.toLonLatAlt(cartesian);
   }

   /**
    * Return the cartesian coordinates for the given longitude, latitude and altitude (in degrees and meters).<br/>
    * <br/>
    * https://en.wikipedia.org/wiki/Geographic_coordinate_conversion#From_geodetic_to_ECEF_coordinates
    *
    * @param lonLatAltDeg
    *           the longitude (in decimal degrees), latitude (in decimal degrees) and altitude (in meters)
    * @return the cartesian coordinate conversion of the given geodesic coordinates
    */
   public static Tuple3d geodesicToCartesian(final Tuple3d lonLatAltDeg) {
      return WGS84.ELLIPSOID.toXYZ(lonLatAltDeg);
   }

   /**
    * Computes Ellipsoid height at given lon/lat<br/>
    * https://www.physicsforums.com/threads/radius-of-ellipsoid.251321/ (their major/minor axis variables are swapped it
    * looks like)
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
   public static double getEllipsoidHeight(final double lonDeg, final double latDeg) {
      final double a = WGS84.EQUATORIAL_RADIUS;
      final double c = WGS84.SEMI_MINOR_RADIUS;
      final double a2 = a * a;
      final double c2 = c * c;

      final double phi = Math.toRadians(latDeg);
      final double sinPhi = Math.sin(phi);
      final double sin2Phi = sinPhi * sinPhi;
      final double cosPhi = Math.cos(phi);
      final double cos2Phi = cosPhi * cosPhi;
      final double numerator = a2 * c2;
      final double denominator = (a2 * sin2Phi) + (c2 * cos2Phi);
      final double r = Math.sqrt(numerator / denominator);

      return r + DigitalElevationUtils.getElevation(lonDeg, latDeg);
   }

   public static double getMaximumVisibleDistance(final double x, final double y, final double z) {
      return Math.sqrt((x * x) + (y * y) + (z * z));
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
      final Ellipsoid ellipsoid = (altitude == 0) ? WGS84.ELLIPSOID : new Ellipsoid(new Tuple3d(), WGS84.EQUATORIAL_RADIUS + altitude, WGS84.FLATTENING, WGS84.FIRST_ECCENTRICITY_SQUARED, WGS84.SECOND_ECCENTRICITY_SQUARED);
      final double[] intersections = ellipsoid.getIntersection(ray);
      Tuple3d solution = null;

      if (intersections.length == 0) {
         return null;
      }

      if (intersections.length == 1) {
         solution = ellipsoid.intersectionToLonLat(ray, intersections[0]);
      } else if (Math.signum(intersections[0]) != Math.signum(intersections[1])) {
         /*
          * Opposite signs means we are inside the ellipsoid. In this case we return the intersection point with the
          * smallest magnitude.
          */
         if (Math.abs(intersections[0]) < Math.abs(intersections[1])) {
            solution = ellipsoid.intersectionToLonLat(ray, intersections[0]);
         } else {
            solution = ellipsoid.intersectionToLonLat(ray, intersections[1]);
         }
      } else if ((intersections[0] >= 0) && ((intersections[1] < 0) || (intersections[0] < intersections[1]))) {
         solution = ellipsoid.intersectionToLonLat(ray, intersections[0]);
      } else if (intersections[1] >= 0) {
         solution = ellipsoid.intersectionToLonLat(ray, intersections[1]);
      } else {
         return null;
      }
      if (Double.isNaN(solution.x) || Double.isNaN(solution.y)) {
         return null;
      }

      return solution;
   }

   public static Quat4d getOrientation(final double lonDeg, final double latDeg) {
      final Quat4d qOrientation = new Quat4d();
      final double lon = Math.toRadians(lonDeg);
      double lat = Math.toRadians(latDeg);

      if (lat != 0) {
         lat = Math.atan(WGS84.REDUCED_LATITUDE * Math.tan(lat));
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
      double qMagnitude = (x * x) + (y * y) + (z * z) + (w * w);

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

   public static RotationTransformation getRotationTransformation(final Tuple3d lonLatAlt1, final Tuple3d lonLatAlt2) {
      final Vector3d xyz1 = new Vector3d(WGS84.geodesicToCartesian(lonLatAlt1));
      final Vector3d xyz2 = new Vector3d(WGS84.geodesicToCartesian(lonLatAlt2));

      final double angle = xyz2.angleRadians(xyz1);

      if (angle == 0) {
         return null;
      }

      final Vector3d axis = new Vector3d();
      axis.cross(xyz2, xyz1);
      axis.normalize();

      return new RotationTransformation(axis, angle);
   }
}
