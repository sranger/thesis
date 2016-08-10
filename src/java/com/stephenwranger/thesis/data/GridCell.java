package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.collections.Pair;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.utils.MathUtils;

public class GridCell {
   private static final int[][]  GRID_OFFSETS    = new int[][] { { -1, -1, -1 }, { 1, -1, -1 }, { 1, 1, -1 }, { -1, 1, -1 }, { -1, -1, 1 }, { 1, -1, 1 }, { 1, 1, 1 }, { -1, 1, 1 } };
   private static final int      ABOVE           = 1;
   private static final int      BELOW           = -1;

   public final Tuple3d[]        p;
   public final double[]         val;
   public final List<Tuple3d>    points          = new ArrayList<>();
   public List<Vector3d>         normals         = null;
   public final List<Triangle3d> triangles       = new ArrayList<>();

   private final Tuple3d         min;
   private final Tuple3d         max;
   private final int             x;
   private final int             y;
   private final int             z;

   public GridCell(final Tuple3d min, final Tuple3d max, final int x, final int y, final int z) {
      this.min = min;
      this.max = max;
      this.x = x;
      this.y = y;
      this.z = z;

      this.p = new Tuple3d[8];
      this.val = new double[8];
      Arrays.fill(this.val, Double.NaN);

      this.p[0] = new Tuple3d(min);
      this.p[1] = new Tuple3d(max.x, min.y, min.z);
      this.p[2] = new Tuple3d(max.x, max.y, min.z);
      this.p[3] = new Tuple3d(min.x, max.y, min.z);
      this.p[4] = new Tuple3d(min.x, min.y, max.z);
      this.p[5] = new Tuple3d(max.x, min.y, max.z);
      this.p[6] = new Tuple3d(max);
      this.p[7] = new Tuple3d(min.x, max.y, max.z);
   }

   public void checkNeighborCells(final Scene scene, final GridCell[][][] cells) {
      for(int i = 0; i < 8; i++) {
         final Tuple3d corner = this.p[i];
         final Pair<Tuple3d, Vector3d> pair = this.getClosest(corner, cells, 2);
         
         if(pair != null) {
            final Vector3d toPoint = Vector3d.getVector(this.p[i], pair.left, false);
            this.val[i] = -(toPoint.dot(pair.right));
         }
      }
      
      this.finish(scene);
   }

   public void computeNormals(final Scene scene, final GridCell[][][] cells, final int k) {
      if (!this.points.isEmpty()) {
         this.normals = this.computePointNormals(scene, cells, k);
      }
   }

   public void finish(final Scene scene) {
      if (this.normals != null) {
         this.updateNormals(scene);
         
         for (int i = 0; i < 8; i++) {
            final int index = this.getClosestPointIndex(this.p[i]);
            final Tuple3d point = this.points.get(index);
            final Vector3d pointNormal = this.normals.get(index);
            final Vector3d toPoint = Vector3d.getVector(this.p[i], point, false);
            
            this.val[i] = -(toPoint.dot(pointNormal));// >= 0) ? BELOW : ABOVE;
         }

         MarchingCubes.polygonise(this, 0, this.triangles);
      }
   }
   
   // TODO: handle faces behind others?
   private void updateNormals(final Scene scene) {
      final Vector3d viewVector = scene.getViewVector();
      
      for(final Vector3d normal : this.normals) {
         if(normal != null && normal.dot(viewVector) < 0) {
            normal.scale(-1);
         }
      }
   }
   
   private int getClosestPointIndex(final Tuple3d origin) {
      double distanceSquared = Double.MAX_VALUE;
      int index = -1;
      
      for(int i = 0; i < this.points.size(); i++) {
         final Tuple3d point = this.points.get(i);
         final Vector3d normal = this.normals.get(i);
         
         if(point != null && normal != null) {
            final double dsq = origin.distanceSquared(point);
            
            if(dsq < distanceSquared) {
               distanceSquared = dsq;
               index = i;
            }
         }
      }
      
      return index;
   }

   private List<Vector3d> computePointNormals(final Scene scene, final GridCell[][][] cells, final int neighborCount) {
      final List<Vector3d> normals = new ArrayList<>();

      for(final Tuple3d currentPoint : this.points) {
         final Vector3d normal = computePointNormal(currentPoint, cells, neighborCount);
         normals.add(normal);
      }

      // no neighbors
      return normals;
   }
   
   private Vector3d computePointNormal(final Tuple3d currentPoint, final GridCell[][][] cells, final int neighborCount) {
      final TreeMap<Double, Tuple3d> neighbors = new TreeMap<>();
      final int k = Math.max(3, neighborCount);
      int offset = 0;
      
      while ((neighbors.size() < k) && (offset < MathUtils.getMax(cells.length, cells[0].length, cells[0][0].length))) {
         neighbors.clear();
         for (int xi = this.x - offset; xi <= (this.x + offset); xi++) {
            for (int yi = this.y - offset; yi <= (this.y + offset); yi++) {
               for (int zi = this.z - offset; zi <= (this.z + offset); zi++) {
                  if ((xi >= 0) && (xi < cells.length) && (yi >= 0) && (yi < cells[0].length) && (zi >= 0) && (zi < cells[0][0].length)) {
                     final GridCell cell = cells[xi][yi][zi];

                     for(final Tuple3d point : cell.points) {
                        final double distanceSquared = point.distanceSquared(currentPoint);
                        
                        if(distanceSquared > 0) {
                           if(neighbors.size() == k && neighbors.lastKey() > distanceSquared) {
                              neighbors.remove(neighbors.lastKey());
                              neighbors.put(distanceSquared, point);
                           } else if(neighbors.size() < k) {
                              neighbors.put(distanceSquared, point);
                           }
                        }
                     }
                  }
               }
            }
         }

         offset++;
      }

      if (neighbors.size() > 2) {
         return GridCell.getAverageNormal(neighbors.values());
      } else {
         return null;
      }
   }
   
   private Pair<Tuple3d, Vector3d> getClosest(final Tuple3d currentPoint, final GridCell[][][] cells, final int maxOffset) {
      final int xSize = cells.length;
      final int ySize = cells[0].length;
      final int zSize = cells[0][0].length;
      final int maxSize = Math.min(maxOffset, MathUtils.getMax(xSize, ySize, zSize));

      Pair<Tuple3d, Vector3d> pair = null;
      double distanceSquared = Double.NaN;
      int offset = 0;
      
      while (pair == null && offset < maxSize) {
         for (int xi = this.x - offset; xi <= (this.x + offset); xi++) {
            for (int yi = this.y - offset; yi <= (this.y + offset); yi++) {
               for (int zi = this.z - offset; zi <= (this.z + offset); zi++) {
                  if ((xi >= 0) && (xi < xSize) && (yi >= 0) && (yi < ySize) && (zi >= 0) && (zi < zSize)) {
                     final GridCell cell = cells[xi][yi][zi];

                     for(int i = 0; i < cell.points.size(); i++) {
                        final Tuple3d point = cell.points.get(i);
                        final Vector3d normal = cell.normals.get(i);
                        
                        if(point != null && normal != null) {
                           final double temp = point.distanceSquared(currentPoint);
                           
                           if(pair == null || temp < distanceSquared) {
                              pair = Pair.getInstance(point, normal);
                           }
                        }
                     }
                  }
               }
            }
         }

         offset++;
      }

      return pair;
   }

   public static void main(final String[] args) {
      final Vector3d v1 = new Vector3d(0,1,0);
      final Vector3d v2 = new Vector3d(0.1, 0.8, 0.2);
      final Vector3d v3 = new Vector3d(-0.1,-0.3,0.4);
      final Vector3d v4 = new Vector3d(1,0,0);

      System.out.println("(equal)         v1.dot(v1) == " + v1.dot(v1));
      System.out.println("(same dir)      v1.dot(v2) == " + v1.dot(v2));
      System.out.println("(opposite dir)  v1.dot(v3) == " + v1.dot(v3));
      System.out.println("(perpendicular) v1.dot(v4) == " + v1.dot(v4));
      
//      final List<Tuple3d> points = new ArrayList<>();
//
//      for (int x = -10; x <= 10; x++) {
//         for (int z = -10; z <= 10; z++) {
//            points.add(new Tuple3d(x, 0, z));
//         }
//      }
//
//      //    for (int i = 0; i < 1000; i++) {
//      //       points.add(new Tuple3d(Math.random() - 0.5, (Math.random() - 0.5) * 0.0, Math.random() - 0.5));
//      //    }
//
//      final Vector3d average = GridCell.getAverageNormal(points);
//      System.out.println("\n\naverage: " + average);
//      System.out.println("-1,-1,-1: " + new Vector3d(-1, -1, -1).dot(average));
//      System.out.println("-1,-1, 1: " + new Vector3d(-1, -1, 1).dot(average));
//      System.out.println("-1, 1,-1: " + new Vector3d(-1, 1, -1).dot(average));
//      System.out.println("-1, 1, 1: " + new Vector3d(-1, 1, 1).dot(average));
//      System.out.println(" 1,-1,-1: " + new Vector3d(1, -1, -1).dot(average));
//      System.out.println(" 1,-1, 1: " + new Vector3d(1, -1, 1).dot(average));
//      System.out.println(" 1, 1,-1: " + new Vector3d(1, 1, -1).dot(average));
//      System.out.println(" 1, 1, 1: " + new Vector3d(1, 1, 1).dot(average));
//      System.out.println(" 1, 0, 0: " + new Vector3d(1, 0, 0).dot(average));
//      System.out.println(" 0, 1, 0: " + new Vector3d(0, 1, 0).dot(average));
//      System.out.println(" 0, 0, 1: " + new Vector3d(0, 0, 1).dot(average));
//      System.out.println("-1, 0, 0: " + new Vector3d(-1, 0, 0).dot(average));
//      System.out.println(" 0,-1, 0: " + new Vector3d(0, -1, 0).dot(average));
//      System.out.println(" 0, 0,-1: " + new Vector3d(0, 0, -1).dot(average));
   }

   private static Vector3d getAverageNormal(final Collection<Tuple3d> neighbors) {
      return GridCell.getAverageNormalCommonsMath3(neighbors);
      //      return getAverageNormalCommonsMkobos(points);
   }

   /**
    * http://commons.apache.org/proper/commons-math/userguide/linear.html
    *
    * @param points
    * @return
    */
   private static Vector3d getAverageNormalCommonsMath3(final Collection<Tuple3d> points) {
      final Tuple3d centroid = Tuple3d.getAverage(points);
      final double[][] samples = new double[points.size()][3];
      int i = 0;
      
      for (final Tuple3d neighbor : points) {
         samples[i][0] = neighbor.x - centroid.x;
         samples[i][1] = neighbor.y - centroid.y;
         samples[i][2] = neighbor.z - centroid.z;
         i++;
      }

      final RealMatrix matrix = MatrixUtils.createRealMatrix(samples);
      final Covariance covariance = new Covariance(matrix);
      final RealMatrix cm = covariance.getCovarianceMatrix();
      final double[][] cmarr = cm.getData();

      final Vector3d v0 = new Vector3d(cmarr[0]);
      final Vector3d v1 = new Vector3d(cmarr[1]);

      final Vector3d n = new Vector3d();
      n.cross(v0, v1);

      return n;
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
