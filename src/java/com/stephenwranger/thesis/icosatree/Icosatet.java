package com.stephenwranger.thesis.icosatree;

import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Icosatet extends TreeCell {
   
   private final int maxPoints;
   
   private final Tuple3d current = new Tuple3d();

   protected Icosatet(final TreeStructure tree, final String path) {
      super(tree, path);
      
      final int[] cellCount = tree.getCellSplit();
      this.maxPoints = cellCount[0] * cellCount[1] * cellCount[2];
   }

   @Override
   public int getMaxChildren() {
      // the first level is an icosahedron so it has 20 faces
      // after that, each face is split into 4 triangles and 2 layers
      // TODO: is two layers sufficient?
      // TODO: should I split into regular three-dimensional shapes?
      return (this.path.isEmpty()) ? 20 : 8;
   }

   @Override
   protected TreeCell addPoint(final TreeStructure tree, final Point point) {
      // TODO: implement triangular prism subcells
      TreeCell insertedInto = this;
      
      if(this.getPointCount() >= this.maxPoints) {
         insertedInto = this.getChildCell(tree, point.getXYZ(tree, current));
         insertedInto.addPoint(point);
      } else {
         super.addPoint(this.getPointCount(), point);
      }
      
      return insertedInto;
   }
}
