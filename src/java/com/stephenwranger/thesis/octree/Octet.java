package com.stephenwranger.thesis.octree;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Octet extends TreeCell {
   private static final int MAX_CHILDREN = 8;
   private final Tuple3d pending = new Tuple3d();
   private final Tuple3d current = new Tuple3d();
   
   public Octet(final TreeStructure tree, final String path) {
      super(tree, path);
   }

   @Override
   protected int getIndex(final TreeStructure tree, final Point point) {
      final BoundingBox bounds = (BoundingBox) this.getBoundingVolume();
      final int[] cellSplit = this.getCellSplit();
      point.getXYZ(tree, this.pending);

      final Tuple3d min = bounds.getMin();
      final Tuple3d dimensions = bounds.getDimensions();
      final double xStep = dimensions.x / cellSplit[0];
      final double yStep = dimensions.y / cellSplit[1];
      final double zStep = dimensions.z / cellSplit[2];
      
      final int xIndex = (int)Math.floor((this.pending.x - min.x) / xStep);
      final int yIndex = (int)Math.floor((this.pending.y - min.y) / yStep);
      final int zIndex = (int)Math.floor((this.pending.z - min.z) / zStep);
      final int index = xIndex + yIndex * cellSplit[0] + zIndex * cellSplit[0] * cellSplit[1];
      
      return index;
   }
   
   @Override
   protected boolean swapPointCheck(final TreeStructure tree, final Point current, final Point pending) {
      final BoundingBox bounds = (BoundingBox) this.getBoundingVolume();
      final int[] cellSplit = this.getCellSplit();

      final Tuple3d min = bounds.getMin();
      final Tuple3d dimensions = bounds.getDimensions();
      final double xStep = dimensions.x / cellSplit[0];
      final double yStep = dimensions.y / cellSplit[1];
      final double zStep = dimensions.z / cellSplit[2];
      
      final Tuple3d pendingLocal = pending.getXYZ(tree, this.pending);
      pendingLocal.subtract(min);
      final Tuple3d currentLocal = current.getXYZ(tree, this.current);
      currentLocal.subtract(min);
      
      final int xIndex = (int)Math.floor((pendingLocal.x) / xStep);
      final int yIndex = (int)Math.floor((pendingLocal.y) / yStep);
      final int zIndex = (int)Math.floor((pendingLocal.z) / zStep);
      
      final Tuple3d center = new Tuple3d(xIndex * xStep + xStep * 0.5, yIndex * yStep + yStep * 0.5, zIndex * zStep + zStep * 0.5);
      
      // if new point is closer to cell center than old point, swap them then pass point to child
      return center.distance(pendingLocal) < center.distance(currentLocal);
   }

   @Override
   public int getMaxChildren() {
      return Octet.MAX_CHILDREN;
   }
   
   @Override
   protected Class<? extends TreeStructure> getTreeType() {
      return Octree.class;
   }
}
