package com.stephenwranger.thesis.octree;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Octet extends TreeCell {
   private static final int MAX_CHILDREN = 8;
   private final Tuple3d current = new Tuple3d();
   private final Tuple3d previous = new Tuple3d();
   
   Octet(final TreeStructure tree, final String path) {
      super(tree, path);
   }
   
   @Override
   protected TreeCell addPoint(final TreeStructure tree, final Point point) {
      final BoundingBox bounds = (BoundingBox) this.getBoundingVolume();
      final int[] cellSplit = this.getCellSplit();
      point.getXYZ(tree, this.current);
      
      if(!bounds.contains(this.current)) {
         System.err.println("\npoint put in wrong octet: " + this.current);
         System.out.println("current octet: " + this.path);
         for(int i = this.path.length(); i > 0; i--) {
            final String parent = this.path.substring(0, i);
            final BoundingVolume parentBounds = tree.getBoundingVolume(parent);
            System.out.println("\tin '" + parent + "'? " + parentBounds.contains(this.current) + ", " + parentBounds);
         }
         final BoundingVolume parentBounds = tree.getBoundingVolume("");
         System.out.println("\tin ''? " + parentBounds.contains(this.current) + ", " + parentBounds);
      }
      
      final Tuple3d min = bounds.getMin();
      final Tuple3d dimensions = bounds.getDimensions();
      final double xStep = dimensions.x / cellSplit[0];
      final double yStep = dimensions.y / cellSplit[1];
      final double zStep = dimensions.z / cellSplit[2];
      
      final int xIndex = (int)Math.floor((this.current.x - min.x) / xStep);
      final int yIndex = (int)Math.floor((this.current.y - min.y) / yStep);
      final int zIndex = (int)Math.floor((this.current.z - min.z) / zStep);
      final int index = xIndex + yIndex * cellSplit[0] + zIndex * cellSplit[0] * cellSplit[1];
      
      final Point p = this.getPoint(index);
      TreeCell insertedInto = this;
      
      if(p == null) {
         this.addPoint(index, point);
      } else {
         final Tuple3d center = new Tuple3d(xIndex * xStep + xStep * 0.5, yIndex * yStep + yStep * 0.5, zIndex * zStep + zStep * 0.5);
         final Tuple3d currentLocal = new Tuple3d(this.current);
         currentLocal.subtract(min);
         final Tuple3d storedLocal = p.getXYZ(tree, previous);
         storedLocal.subtract(min);
         
         final TreeCell child = getChildCell(tree, this.current);
         
         if(child == null) {
            System.out.println("error point: " + point);
            throw new RuntimeException("Point given (" + this.current + ") does not belong in this octet (" + bounds + ")");
         }
         
         insertedInto = child;
         
         // if new point is closer to cell center than old point, swap them
         // then pass point to child
         if(center.distance(currentLocal) < center.distance(storedLocal)) {
            final Point oldPoint = this.swapPoint(index, point);
            child.addPoint(oldPoint);
         } else {
            child.addPoint(point);
         }
      }
      
      return insertedInto;
   }

   @Override
   public int getMaxChildren() {
      return Octet.MAX_CHILDREN;
   }
}
