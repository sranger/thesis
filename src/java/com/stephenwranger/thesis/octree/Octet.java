package com.stephenwranger.thesis.octree;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Octet extends TreeCell {
   private static final int MAX_CHILDREN = 8;
   
   Octet(final TreeStructure tree, final String path) {
      super(tree, path);
   }
   
   @Override
   protected TreeCell addPoint(final TreeStructure octree, final Point point) {
      final BoundingBox bounds = (BoundingBox) this.getBoundingVolume();
      final int[] cellSplit = this.getCellSplit();
      if(!bounds.contains(point.getXYZ())) {
         System.err.println("point put in wrong octet: " + point.getXYZ() + ", " + bounds);
      }
      
      final Tuple3d min = bounds.getMin();
      final Tuple3d dimensions = bounds.getDimensions();
      final double xStep = dimensions.x / cellSplit[0];
      final double yStep = dimensions.y / cellSplit[1];
      final double zStep = dimensions.z / cellSplit[2];
      
      final Tuple3d xyz = point.getXYZ();

      final int xIndex = (int)Math.floor((xyz.x - min.x) / xStep);
      final int yIndex = (int)Math.floor((xyz.y - min.y) / yStep);
      final int zIndex = (int)Math.floor((xyz.z - min.z) / zStep);
      final int index = xIndex + yIndex * cellSplit[0] + zIndex * cellSplit[0] * cellSplit[1];
      
      final Point p = this.getPoint(index);
      TreeCell insertedInto = this;
      
      if(p == null) {
         this.addPoint(index, point);
      } else {
         final Tuple3d center = new Tuple3d(xIndex * xStep + xStep * 0.5, yIndex * yStep + yStep * 0.5, zIndex * zStep + zStep * 0.5);
         final Tuple3d current = new Tuple3d(xyz);
         current.subtract(min);
         final Tuple3d stored = p.getXYZ();
         stored.subtract(min);
         
         final TreeCell child = getChildOctet(octree, xyz);
         
         if(child == null) {
            System.out.println("error point: " + point);
            throw new RuntimeException("Point given (" + xyz + ") does not belong in this octet (" + bounds + ")");
         }
         
         insertedInto = child;
         
         // if new point is closer to cell center than old point, swap them
         // then pass point to child
         if(center.distance(current) < center.distance(stored)) {
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
