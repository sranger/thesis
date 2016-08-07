package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.List;

import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;

public class GridCell {
   private static final double[][] VERTEX_OFFSET = { { 0.0, 0.0, 0.0 }, { 1.0, 0.0, 0.0 }, { 1.0, 1.0, 0.0 }, { 0.0, 1.0, 0.0 }, { 0.0, 0.0, 1.0 }, { 1.0, 0.0, 1.0 }, { 1.0, 1.0, 1.0 }, { 0.0, 1.0, 1.0 } };
   public final Tuple3d[]          p;
   public final double[]           val;
   public final List<Tuple3d>      points        = new ArrayList<>();
   public final List<Triangle3d>   triangles     = new ArrayList<>();

   private final Tuple3d           min;
   private final Tuple3d           max;

   public GridCell(final Tuple3d min, final Tuple3d max) {
      this.min = min;
      this.max = max;
      this.p = new Tuple3d[8];
      this.val = new double[8];

      this.p[0] = new Tuple3d(min);
      this.p[1] = new Tuple3d(max.x, min.y, min.z);
      this.p[2] = new Tuple3d(max.x, max.y, min.z);
      this.p[3] = new Tuple3d(min.x, max.y, min.z);
      this.p[4] = new Tuple3d(min.x, min.y, max.z);
      this.p[5] = new Tuple3d(max.x, min.y, max.z);
      this.p[6] = new Tuple3d(max);
      this.p[7] = new Tuple3d(min.x, max.y, max.z);
   }

   public void finish() {
      if (!this.points.isEmpty()) {
         double maxDistance = this.max.distance(this.min);

         for (int i = 0; i < 8; i++) {
            double averageDistance = 0;

            for (final Tuple3d point : this.points) {
               averageDistance += point.distance(this.p[i]);
            }

            averageDistance /= this.points.size();

            // weighted average of distances from all points to the corner
            // TODO: no idea if this is what to do...
            this.val[i] = (averageDistance) / maxDistance;

            this.val[i] = (this.val[i] < 0.5) ? -1 : 1;
         }

         MarchingCubes.polygonise(this, 0, this.triangles);
      }
   }
}
