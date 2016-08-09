package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mkobos.pca_transform.PCA;
import com.mkobos.pca_transform.PCA.TransformationType;
import com.mkobos.pca_transform.covmatrixevd.CovarianceMatrixEVDCalculator;
import com.mkobos.pca_transform.covmatrixevd.SVDBased;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.utils.TupleMath;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

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

   public void finish(final Scene scene, final GridCell[][][] cells, final int x, final int y, final int z, final int k) {
      if (!this.points.isEmpty()) {
         final Vector3d average = this.computeAverageNormal(scene, cells, x, y, z, k);

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

   private Vector3d computeAverageNormal(final Scene scene, final GridCell[][][] cells, final int x, final int y, final int z, final int k) {
      final List<Tuple3d> neighbors = new ArrayList<>();

      // TODO: per point normal with k-nearest?
      neighbors.addAll(this.points);
      int offset = 1;
      
      while(neighbors.size() < Math.max(3, k) && offset < 5) {
         System.out.println("neighbors.size: " + neighbors.size() + ", k = " + k);
         System.out.println(x + ", " + y + ", " + z + ", " + offset);
         if(x >= offset && y >= offset && z >= offset && cells[x-offset][y-offset][z-offset] != null) {
            neighbors.addAll(cells[x-offset][y-offset][z-offset].points);
         }
         if(x >= offset && y >= offset && z < cells[0][0].length - offset && cells[x-offset][y-offset][z+offset] != null) {
            neighbors.addAll(cells[x-offset][y-offset][z+offset].points);
         }
         if(x >= offset && y < cells[0].length - offset && z >= offset && cells[x-offset][y+offset][z-offset] != null) {
            neighbors.addAll(cells[x-offset][y+offset][z-offset].points);
         }
         if(x >= offset && y < cells[0].length - offset && z < cells[0][0].length - offset && cells[x-offset][y+offset][z+offset] != null) {
            neighbors.addAll(cells[x-offset][y+offset][z+offset].points);
         }
         if(x < cells.length - offset && y >= offset && z >= offset && cells[x+offset][y-offset][z-offset] != null) {
            neighbors.addAll(cells[x+offset][y-offset][z-offset].points);
         }
         if(x < cells.length - offset && y >= offset && z < cells[0][0].length - offset && cells[x+offset][y-offset][z+offset] != null) {
            neighbors.addAll(cells[x+offset][y-offset][z+offset].points);
         }
         if(x < cells.length - offset && y < cells[0].length - offset && z >= offset && cells[x+offset][y+offset][z-offset] != null) {
            neighbors.addAll(cells[x+offset][y+offset][z-offset].points);
         }
         if(x < cells.length - offset && y < cells[0].length - offset && z < cells[0][0].length - offset && cells[x+offset][y+offset][z+offset] != null) {
            neighbors.addAll(cells[x+offset][y+offset][z+offset].points);
         }
         
         offset++;
      }

      if (!neighbors.isEmpty()) {
         return GridCell.getAverageNormal(neighbors);
      }

      // no neighbors
      return null;
   }

   public static void main(final String[] args) {
      final List<Tuple3d> points = new ArrayList<>();

      for(int x = -10; x <= 10; x++) {
         for(int z = -10; z <= 10; z++) {
            points.add(new Tuple3d(x,0,z));
         }
      }

//    for (int i = 0; i < 1000; i++) {
//       points.add(new Tuple3d(Math.random() - 0.5, (Math.random() - 0.5) * 0.0, Math.random() - 0.5));
//    }

      final Vector3d average = GridCell.getAverageNormal(points);
      System.out.println("\n\naverage: " + average);
      System.out.println("-1,-1,-1: " + new Vector3d(-1, -1, -1).dot(average));
      System.out.println("-1,-1, 1: " + new Vector3d(-1, -1, 1).dot(average));
      System.out.println("-1, 1,-1: " + new Vector3d(-1, 1, -1).dot(average));
      System.out.println("-1, 1, 1: " + new Vector3d(-1, 1, 1).dot(average));
      System.out.println(" 1,-1,-1: " + new Vector3d(1, -1, -1).dot(average));
      System.out.println(" 1,-1, 1: " + new Vector3d(1, -1, 1).dot(average));
      System.out.println(" 1, 1,-1: " + new Vector3d(1, 1, -1).dot(average));
      System.out.println(" 1, 1, 1: " + new Vector3d(1, 1, 1).dot(average));
   }

   // TODO: look at the following
   // http://www.gamedev.net/topic/494852-eigenvector-of-a-3-x-3-matrix-for-plane-fitting/
   // https://github.com/mkobos/pca_transform/issues/3
   // http://stats.stackexchange.com/questions/163356/fitting-a-plane-to-a-set-of-points-in-3d-using-pca
   private static Vector3d getAverageNormal(final List<Tuple3d> neighbors) {
      final Tuple3d centroid = Tuple3d.getAverage(neighbors);
      final Tuple3d min = Tuple3d.getMin(neighbors);
      final Tuple3d max = Tuple3d.getMax(neighbors);
      System.out.println("centroid: " + centroid);
      System.out.println("x range: " + (max.x - min.x));
      System.out.println("y range: " + (max.y - min.y));
      System.out.println("z range: " + (max.z - min.z));
      final double[][] samples = new double[neighbors.size()][3];
      
      for(int i = 0; i < neighbors.size(); i++) {
         final Tuple3d tuple = neighbors.get(i);
         samples[i][0] = tuple.x - centroid.x;
         samples[i][1] = tuple.y - centroid.y;
         samples[i][2] = tuple.z - centroid.z;
      }
      
      final Matrix sampleMatrix = new Matrix(samples);
      final SVDBased svd = new SVDBased();
      final PCA pca = new PCA(sampleMatrix, svd, false);
      final Matrix eigenMatrix = pca.getEigenvectorsMatrix();
      final EigenvalueDecomposition ed = eigenMatrix.eig();
      System.out.println("ed.d: " + ed.getD().getRowDimension() + ", " + ed.getD().getColumnDimension());
      System.out.println("ed.v: " + ed.getV().getRowDimension() + ", " + ed.getV().getColumnDimension());
      
      for(int i = 0; i < pca.getOutputDimsNo(); i++) {
         System.out.println("eigenvalue " + i + ": " + pca.getEigenvalue(i));
      }
      
      final double[][] vectors = eigenMatrix.getArray();
      for(int i = 0; i < vectors.length; i++) {
         System.out.println("eigenvector " + i + ": " + Arrays.toString(vectors[i]));
      }
      
      final Vector3d n = new Vector3d(eigenMatrix.getArray()[2]);
      
      
//      System.out.println("rotation: " + rotationMatrix.getRowDimension() + ", " + rotationMatrix.getColumnDimension());
//      final Matrix eigenVectors = pca.getEigenvectorsMatrix();
//      System.out.println("dims: " + pca.getInputDimsNo() + ", " + pca.getOutputDimsNo());
//      final EigenvalueDecomposition ed = eigenVectors.eig();
//      System.out.println("vectors rank: " + eigenVectors.rank());
//      System.out.println("imag values: " + Arrays.toString(ed.getImagEigenvalues()));
//      System.out.println("real values: " + Arrays.toString(ed.getRealEigenvalues()));
//      System.out.println("ed.v: " + ed.getV().getRowDimension() + ", " + ed.getV().getColumnDimension());
//
//      System.out.println("e0: " + pca.getEigenvalue(0));
//      System.out.println("e1: " + pca.getEigenvalue(1));
//      System.out.println("e2: " + pca.getEigenvalue(2));
//
//      int ctr = 0;
//      for(final double[] vector : eigenVectors.getArray()) {
//            System.out.println("eigenVectors.v["+(ctr++)+"] = " + Arrays.toString(vector));
//      }
//      final Vector3d n = new Vector3d(ed.getV().getArray()[2]);
//      n.normalize();
      
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
