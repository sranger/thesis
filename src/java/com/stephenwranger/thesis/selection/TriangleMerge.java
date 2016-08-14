package com.stephenwranger.thesis.selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activity.InvalidActivityException;

import com.stephenwranger.compgeo.algorithms.delaunay.DelaunayTriangulation;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Plane;
import com.stephenwranger.graphics.math.intersection.Triangle2d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.thesis.data.GridCell;

/**
 * Class used to project triangles onto a common plane and merge any that overlap.
 */
public class TriangleMerge {
   private final List<Triangle3d> triangles = new ArrayList<>();

   public TriangleMerge(final Collection<Triangle3d> inputTriangles) {
      this.triangles.addAll(inputTriangles);
   }

   public List<Triangle3d> process(final boolean doMerge, final boolean doDelaunay) {
      final List<Tuple3d> vertices = new ArrayList<>();
      final List<Triangle3d> output = new ArrayList<>();

      for (final Triangle3d triangle : this.triangles) {
         vertices.addAll(Arrays.asList(triangle.getCorners()));
      }

      final Tuple3d min = Tuple3d.getMin(vertices);
      final Vector3d normal = GridCell.getAverageNormal(vertices);
      final Plane plane = new Plane(min, normal);
      final Map<Tuple3d, Tuple2d> projectedPoints = new HashMap<>();
      final Map<Tuple2d, Tuple3d> unprojectedPoints = new HashMap<>();
      final Tuple2d min2d = new Tuple2d(Double.MAX_VALUE, Double.MAX_VALUE);
      final Tuple2d max2d = new Tuple2d(-Double.MAX_VALUE, -Double.MAX_VALUE);

      for (final Tuple3d vertex : vertices) {
         final Tuple2d projectedVertex = plane.getProjectedPoint(vertex);
         min2d.x = Math.min(min2d.x, projectedVertex.x);
         max2d.x = Math.max(max2d.x, projectedVertex.x);

         min2d.y = Math.min(min2d.y, projectedVertex.y);
         max2d.y = Math.max(max2d.y, projectedVertex.y);

         projectedPoints.put(vertex, projectedVertex);
         unprojectedPoints.put(projectedVertex, vertex);
      }

      // TODO
      //      if(doMerge) {
      //         Collections.sort(this.triangles, new Comparator<Triangle3d>() {
      //            @Override
      //            public int compare(final Triangle3d o1, final Triangle3d o2) {
      //               return Double.compare(o1.distanceToPoint(min), o2.distanceToPoint(min));
      //            }
      //         });
      //      }

      if (doDelaunay) {
         final double xDistance = max2d.x - min2d.x;
         final double yDistance = max2d.y - min2d.y;

         final double triangleBase = xDistance * 2.0;
         final double triangleHeight = yDistance * 2.0;

         // a triangle surrounding all the points is twice the length of the x- and y- ranges of the bounding box surrounding all the vertices
         final Tuple2d v0 = new Tuple2d(min2d.x - 1, min2d.y - 1);
         final Tuple2d v1 = new Tuple2d(min2d.x + triangleBase + 1, min2d.y - 1);
         final Tuple2d v2 = new Tuple2d(min2d.x - 1, min2d.y + triangleHeight + 1);
         final Triangle2d boundingTriangle = new Triangle2d(v0, v1, v2);
         final DelaunayTriangulation delaunay = new DelaunayTriangulation(boundingTriangle);

         for (final Triangle3d triangle : this.triangles) {
            final Tuple3d[] corners = triangle.getCorners();
            final Tuple2d c0 = projectedPoints.get(corners[0]);
            final Tuple2d c1 = projectedPoints.get(corners[1]);
            final Tuple2d c2 = projectedPoints.get(corners[2]);

            try {
               // TODO: this doesn't triangulate what's around the triangles; is that possible?
               //             delaunay.addTriangle(new Triangle2d(c0, c1, c2), false);
               delaunay.addVertex(c0, false);
               delaunay.addVertex(c1, false);
               delaunay.addVertex(c2, false);
            } catch (final InvalidActivityException e) {
               e.printStackTrace();
            }
         }

         for (final Triangle2d triangle : delaunay.getTriangles()) {
            final Tuple2d[] corners = triangle.getCorners();
            final Tuple3d c0 = TriangleMerge.get3d(corners[0], unprojectedPoints);
            final Tuple3d c1 = TriangleMerge.get3d(corners[1], unprojectedPoints);
            final Tuple3d c2 = TriangleMerge.get3d(corners[2], unprojectedPoints);

            if ((c0 != null) && (c1 != null) && (c2 != null)) {
               Triangle3d tri = new Triangle3d(c0, c1, c2);

               if (tri.getNormal().dot(plane.getNormal()) < 0) {
                  tri = new Triangle3d(c1, c0, c2);
               }
               output.add(tri);
            }
         }
      }

      System.out.println("triangle count: " + output.size());
      return output;
   }

   private static Tuple3d get3d(final Tuple2d point2d, final Map<Tuple2d, Tuple3d> map) {
      Tuple2d closestKey = null;
      double closest = Double.MAX_VALUE;

      for (final Tuple2d p : map.keySet()) {
         final double temp = p.distanceSquared(point2d);

         if (temp < closest) {
            closestKey = p;
            closest = temp;
         }
      }

      return (closest == 0) ? map.get(closestKey) : null;
   }
}
