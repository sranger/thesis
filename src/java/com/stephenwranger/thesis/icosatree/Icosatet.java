package com.stephenwranger.thesis.icosatree;

import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.Triangle2d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Icosatet extends TreeCell {
   private static final int MAX_CHILDREN_ROOT = 20;
   private static final int MAX_CHILDREN_OTHER = 8;

   private final Tuple3d pending = new Tuple3d();
   private final Tuple3d current = new Tuple3d();

   public Icosatet(final TreeStructure tree, final String path) {
      super(tree, path);
   }

   @Override
   public int getMaxChildren() {
      // the first level is an icosahedron so it has 20 faces
      // after that, each face is split into 4 triangles and 2 layers
      // TODO: is two layers sufficient?
      // TODO: should I split into regular three-dimensional shapes?
      return (this.path.isEmpty()) ? MAX_CHILDREN_ROOT : MAX_CHILDREN_OTHER;
   }

   @Override
   protected int getIndex(final TreeStructure tree, final Point point) {
      if(this.getPath().isEmpty()) {
         // since the root node is technically square; only store a single point in it to get things going
         return 0;
      } else {
         point.getXYZ(tree, this.pending);
         final TrianglePrismVolume bounds = (TrianglePrismVolume) this.getBoundingVolume();
         final Triangle3d top = bounds.getTopFace();
         final Tuple3d dimensions = top.getTriangularCoordinatesRange();
         final Tuple3d stu = top.getTriangularCoordinates(this.pending);
         final double depth = top.distanceToPoint(this.pending) / bounds.getDepth();
//         System.out.println("stu: " + stu + ", depth = " + depth);
         final int[] cellSplit = tree.getCellSplit();
         int index = -1;
         
         if(stu != null) {
            final double maxDepth = bounds.getDepth();
            final double xStep = dimensions.x / cellSplit[0];
            final double yStep = dimensions.y / cellSplit[0];
            final double zStep = dimensions.z / cellSplit[0];
            final double wStep = maxDepth / cellSplit[2];
            final int xIndex = (int) Math.floor(stu.x / xStep);
            final int yIndex = (int) Math.floor(stu.y / yStep);
            final int zIndex = (int) Math.floor(stu.z / zStep);
            final int wIndex = (int) Math.floor(depth / wStep);
//            System.out.println("index: " + xIndex + ", " + yIndex + ", " + zIndex);
            index = xIndex * cellSplit[0] * cellSplit[0] * cellSplit[0] + yIndex * cellSplit[0] * cellSplit[0] + zIndex * cellSplit[0] + wIndex;
         }
         
         return index;
      }
   }
   
   @Override
   protected boolean swapPointCheck(final TreeStructure tree, final Point current, final Point pending) {
      if(this.path.length() == 0) {
         return false;
      } else {
         final TrianglePrismVolume bounds = (TrianglePrismVolume) this.getBoundingVolume();
         final Triangle3d top = bounds.getTopFace();
         final Tuple3d dimensions = top.getTriangularCoordinatesRange();
         final int[] cellSplit = this.getCellSplit();
         
         final double xStep = dimensions.x / cellSplit[0];
         final double yStep = dimensions.y / cellSplit[0];
         final double zStep = dimensions.z / cellSplit[0];

         final Tuple3d pendingLocal = top.getTriangularCoordinates(pending.getXYZ(tree, this.pending));
         final Tuple3d currentLocal = top.getTriangularCoordinates(current.getXYZ(tree, this.current));
         
         final int xIndex = (int)Math.floor((pendingLocal.x) / xStep);
         final int yIndex = (int)Math.floor((pendingLocal.y) / yStep);
         final int zIndex = (int)Math.floor((pendingLocal.z) / zStep);
         
         final Tuple3d center = new Tuple3d(xIndex * xStep + xStep * 0.5, yIndex * yStep + yStep * 0.5, zIndex * zStep + zStep * 0.5);
         
         // if new point is closer to cell center than old point, swap them then pass point to child
         // we can ignore the depth at this point as they're both in the same indexed cell already
         return center.distanceSquared(pendingLocal) < center.distanceSquared(currentLocal);
      }
   }
   
   @Override
   protected Class<? extends TreeStructure> getTreeType() {
      return Icosatree.class;
   }
   
   // won't work as a+b+c == k but grid must have axis values not dependent on the others
   public static void main(final String[] args) {
      final JFrame frame = new JFrame("Triangular Graph");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setLocation(100, 100);
      
      final JPanel panel = new JPanel() {
         private static final long serialVersionUID = -296430685666931141L;

         @Override
         public void paintComponent(final Graphics g) {
            final double side = Math.min(this.getWidth(), this.getHeight()) * 0.8;
            final double halfWidth = side / 2.0;
            final double toEdge = Math.sqrt(3.0) * halfWidth;
            final Tuple2d center = new Tuple2d(this.getWidth() / 2.0, this.getHeight() / 2.0);
            final double halfHeight = toEdge / 2.0;
            
            final double minX = center.x - halfWidth;
            final double minY = center.y - halfHeight;
            
            final Tuple2d c1 = new Tuple2d(minX, minY);
            final Tuple2d c2 = new Tuple2d(minX + side, minY);
            final Tuple2d c3 = new Tuple2d(center.x, minY + toEdge);
            final Triangle2d triangle = new Triangle2d(c1, c2, c3, true);

            g.drawLine((int) c1.x, (int) c1.y, (int) c2.x, (int) c2.y);
            g.drawLine((int) c2.x, (int) c2.y, (int) c3.x, (int) c3.y);
            g.drawLine((int) c3.x, (int) c3.y, (int) c1.x, (int) c1.y);

            final int k = 5;
            
            for(int a = 0; a <= k; a++) {
               for(int b = 0; b <= k; b++) {
                  final double baryX = a / (double) k;
                  final double baryY = b / (double) k;
                  final double baryZ = 1.0 - baryX - baryY;
                  final Tuple2d xy = triangle.getCartesianCoordinate(new Tuple3d(baryX, baryY, baryZ));

                  if(triangle.contains(xy)) {
                     // index along u
                     final int u = (int)Math.floor(baryX * k);
                     
                     // index along v
                     final int v = (int)Math.floor(baryY * k);
                     
                     final int index = u * k + v;
                     
                     if(u != a || v != b) {
                        System.out.println("a, b == xy:    " + a + ", " + b + " == " + xy);
                        System.out.println("u, v == index: " + u + ", " + v + " == " + index);
                     }
                     
                     g.fillOval((int) (xy.x-3), (int) (xy.y-3), 6, 6);
                  }
               }
            }
            
            g.dispose();
            
         }
      };
      
      panel.setPreferredSize(new Dimension(1000, 1000));
      frame.getContentPane().add(panel);
      
      
      SwingUtilities.invokeLater(() -> {
         frame.pack();
         frame.setVisible(true);
      });
   }
}
