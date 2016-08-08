package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.utils.TupleMath;

public class GridCell {
   private static final int[][]  GRID_OFFSETS = new int[][] { { -1, -1, -1 }, { 1, -1, -1 }, { 1, 1, -1 }, { -1, 1, -1 }, { -1, -1, 1 }, { 1, -1, 1 }, { 1, 1, 1 }, { -1, 1, 1 } };
   private static final int      ABOVE        = 1;
   private static final int      BELOW        = -1;

   public final Tuple3d[]        p;
   public final double[]         val;
   public final List<Tuple3d>    points       = new ArrayList<>();
   public final List<Triangle3d> triangles    = new ArrayList<>();

   private final Tuple3d         min;
   private final Tuple3d         max;

   public GridCell(final Tuple3d min, final Tuple3d max) {
      this.min = min;
      this.max = max;
      this.p = new Tuple3d[8];
      this.val = new double[8];
      Arrays.fill(this.val, GridCell.ABOVE);

      this.p[0] = new Tuple3d(min);
      this.p[1] = new Tuple3d(max.x, min.y, min.z);
      this.p[2] = new Tuple3d(max.x, max.y, min.z);
      this.p[3] = new Tuple3d(min.x, max.y, min.z);
      this.p[4] = new Tuple3d(min.x, min.y, max.z);
      this.p[5] = new Tuple3d(max.x, min.y, max.z);
      this.p[6] = new Tuple3d(max);
      this.p[7] = new Tuple3d(min.x, max.y, max.z);
   }

   public void finish(final GridCell[][][] cells, final int x, final int y, final int z) {
      // this.p[0] = new Tuple3d(min);
      // this.p[1] = new Tuple3d(max.x, min.y, min.z);
      // this.p[2] = new Tuple3d(max.x, max.y, min.z);
      // this.p[3] = new Tuple3d(min.x, max.y, min.z);
      // this.p[4] = new Tuple3d(min.x, min.y, max.z);
      // this.p[5] = new Tuple3d(max.x, min.y, max.z);
      // this.p[6] = new Tuple3d(max);
      // this.p[7] = new Tuple3d(min.x, max.y, max.z);
      main: for (int i = 0; i < 8; i++) {
         final int minX = Math.min(x, x + GridCell.GRID_OFFSETS[i][0]);
         final int maxX = Math.max(x, x + GridCell.GRID_OFFSETS[i][0]);
         final int minY = Math.min(y, y + GridCell.GRID_OFFSETS[i][1]);
         final int maxY = Math.max(y, y + GridCell.GRID_OFFSETS[i][1]);
         final int minZ = Math.min(z, z + GridCell.GRID_OFFSETS[i][2]);
         final int maxZ = Math.max(z, z + GridCell.GRID_OFFSETS[i][2]);

         for (int xi = minX; xi <= maxX; xi++) {
            for (int yi = minY; yi <= maxY; yi++) {
               for (int zi = minZ; zi <= maxZ; zi++) {
                  if ((this.val[i] != GridCell.BELOW) && (cells[xi][yi][zi] != null)) {
                     final int index = GridCell.getIndex(i, x - xi, y - yi, z - zi);
                     if ((index >= 0) && (index <= 7) && (cells[xi][yi][zi].val[index] == GridCell.BELOW)) {
                        this.val[i] = GridCell.BELOW;
                        continue main;
                     }
                  }
               }
            }
         }
      }
   }

   public void finish(final Scene scene, final GridCell[][][] cells, final int x, final int y, final int z, final int neighborDistance) {
      if (!this.points.isEmpty()) {
         final Vector3d average = this.computeAverageNormal(scene, cells, x, y, z, neighborDistance);

         if (average != null) {
            final Tuple3d center = TupleMath.average(this.min, this.max);

            for (int i = 0; i < 8; i++) {
               final Vector3d toCenter = new Vector3d();
               toCenter.subtract(center, this.p[i]);
               toCenter.normalize();

               this.val[i] = (toCenter.dot(average) < 0) ? GridCell.ABOVE : GridCell.BELOW;
            }

            MarchingCubes.polygonise(this, 0, this.triangles);
         }
      }
   }

   private Vector3d computeAverageNormal(final Scene scene, final GridCell[][][] cells, final int x, final int y, final int z, final int neighborDistance) {
      final List<Tuple3d> neighbors = new ArrayList<>();
      //      final Tuple3d min = new Tuple3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
      //      final Tuple3d max = new Tuple3d(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
      //      final Tuple3d center = new Tuple3d();

      for (int xi = Math.max(0, x - neighborDistance); xi <= Math.min(cells.length - 1, x + neighborDistance); xi++) {
         for (int yi = Math.max(0, y - neighborDistance); yi <= Math.min(cells[xi].length - 1, y + neighborDistance); yi++) {
            for (int zi = Math.max(0, z - neighborDistance); zi <= Math.min(cells[xi][yi].length - 1, z + neighborDistance); zi++) {
               final GridCell cell = cells[xi][yi][zi];

               if (cell != null) {
                  neighbors.addAll(cell.points);
                  //                  for (final Tuple3d p : cell.points) {
                  //                     count++;
                  //                     this.min.x = Math.min(this.min.x, p.x);
                  //                     this.min.y = Math.min(this.min.y, p.y);
                  //                     this.min.z = Math.min(this.min.z, p.z);
                  //
                  //                     this.max.x = Math.max(this.max.x, p.x);
                  //                     this.max.y = Math.max(this.max.y, p.y);
                  //                     this.max.z = Math.max(this.max.z, p.z);
                  //
                  //                     center.add(p);
                  //                  }
               }
            }
         }
      }

      if (!neighbors.isEmpty()) {
         //         center.x /= count;
         //         center.y /= count;
         //         center.z /= count;

         return GridCell.getAverageNormal(neighbors);
      }

      // no neighbors
      return null;
   }

   public static void main(final String[] args) {
      final List<Tuple3d> points = new ArrayList<>();

      for (int i = 0; i < 1000; i++) {
         points.add(new Tuple3d(Math.random() - 0.5, (Math.random() - 0.5) * 0.1, Math.random() - 0.5));
      }

      final Vector3d average = GridCell.getAverageNormal(points);
      System.out.println(average);
      System.out.println("-1,-1,-1: " + new Vector3d(-1, -1, -1).dot(average));
      System.out.println("-1,-1, 1: " + new Vector3d(-1, -1, 1).dot(average));
      System.out.println("-1, 1,-1: " + new Vector3d(-1, 1, -1).dot(average));
      System.out.println("-1, 1, 1: " + new Vector3d(-1, 1, 1).dot(average));
      System.out.println(" 1,-1,-1: " + new Vector3d(1, -1, -1).dot(average));
      System.out.println(" 1,-1, 1: " + new Vector3d(1, -1, 1).dot(average));
      System.out.println(" 1, 1,-1: " + new Vector3d(1, 1, -1).dot(average));
      System.out.println(" 1, 1, 1: " + new Vector3d(1, 1, 1).dot(average));
   }

   private static Vector3d getAverageNormal(final List<Tuple3d> neighbors) {
      final int count = neighbors.size();
      Vector3d averageToNeighbor = null;

      for (int i = 0; i < (neighbors.size() - 1); i++) {
         final Tuple3d current = neighbors.get(i);

         for (int j = i + 1; j < neighbors.size(); j++) {
            final Tuple3d test = neighbors.get(j);
            final Vector3d tempVector = new Vector3d();
            if (test.distanceSquared(current) > 0) {
               tempVector.subtract(test, current);
               tempVector.normalize();

               if (averageToNeighbor == null) {
                  averageToNeighbor = tempVector;
               } else {
                  if (tempVector.dot(averageToNeighbor) < 0) {
                     tempVector.scale(-1);
                  }

                  averageToNeighbor.add(tempVector);
               }
            }
         }
      }

      averageToNeighbor.x /= count;
      averageToNeighbor.y /= count;
      averageToNeighbor.z /= count;

      averageToNeighbor.cross(new Vector3d(averageToNeighbor.x, -averageToNeighbor.y, -averageToNeighbor.z));
      averageToNeighbor.normalize();

      return averageToNeighbor;
   }

   /**
    * Looks up index into val array for adjacent cell. TODO: make this a lookup table?
    *
    * @param index
    *           current val array index
    * @param x
    *           current x offset
    * @param y
    *           current y offset
    * @param z
    *           current z offset
    * @return the val array index or -1 if no offset valid
    */
   private static int getIndex(final int index, final int x, final int y, final int z) {
      // @formatter:off
      if((x != 0) && (y != 0) && (z != 0)) {
         switch(index) {
            case 0: return 6;
            case 1: return 7;
            case 2: return 4;
            case 3: return 5;
            case 4: return 2;
            case 5: return 3;
            case 6: return 0;
            case 7: return 1;
         }
      } else if((x != 0) && (y != 0) && (z == 0)) {
         switch(index) {
            case 0: return 2;
            case 1: return 3;
            case 2: return 0;
            case 3: return 1;
            case 4: return 6;
            case 5: return 7;
            case 6: return 4;
            case 7: return 5;
         }
      } else if((x != 0) && (y == 0) && (z != 0)) {
         switch(index) {
            case 0: return 5;
            case 1: return 4;
            case 2: return 7;
            case 3: return 6;
            case 4: return 1;
            case 5: return 0;
            case 6: return 3;
            case 7: return 2;
         }
      } else if((x != 0) && (y == 0) && (z == 0)) {
         switch(index) {
            case 0: return 1;
            case 1: return 0;
            case 2: return 3;
            case 3: return 2;
            case 4: return 5;
            case 5: return 4;
            case 6: return 7;
            case 7: return 6;
         }
      } else if((x == 0) && (y != 0) && (z != 0)) {
         switch(index) {
            case 0: return 4;
            case 1: return 5;
            case 2: return 6;
            case 3: return 7;
            case 4: return 0;
            case 5: return 1;
            case 6: return 2;
            case 7: return 3;
         }
      } else if((x == 0) && (y != 0) && (z == 0)) {
         switch(index) {
            case 0: return 3;
            case 1: return 2;
            case 2: return 1;
            case 3: return 0;
            case 4: return 7;
            case 5: return 6;
            case 6: return 5;
            case 7: return 4;
         }
      } else if((x == 0) && (y == 0) && (z != 0)) {
         switch(index) {
            case 0: return 4;
            case 1: return 5;
            case 2: return 6;
            case 3: return 7;
            case 4: return 0;
            case 5: return 1;
            case 6: return 2;
            case 7: return 3;
         }
      }

      return -1;
      // @formatter:on
   }
}
