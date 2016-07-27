package com.stephenwranger.thesis.icosatree;

import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Icosatet extends TreeCell {
   private static final int MAX_CHILDREN_ROOT = 20;
   private static final int MAX_CHILDREN_OTHER = 8;
   
   private final Tuple3d current = new Tuple3d();

   protected Icosatet(final TreeStructure tree, final String path) {
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
         // http://www.cut-the-knot.org/triangle/glasses.shtml
         final TrianglePrismVolume bounds = (TrianglePrismVolume) this.getBoundingVolume();
         final double[] uvwDepth = bounds.getBarycentricCoordinate(point.getXYZ(tree, current));
         final int[] splits = this.getCellSplit();
         
         // index along u
         final int u = (int)Math.floor(uvwDepth[0] / (1.0 / splits[0])); 
         
         // index along v
         final int v = (int)Math.floor(uvwDepth[1] / (1.0 / splits[1]));
         
         // index along depth
         final int d = (int)Math.floor(uvwDepth[3] / (1.0 / splits[2]));
         
         return u * splits[0] * splits[1] + v * splits[1] + d;
      }
   }
   
   @Override
   protected boolean swapPointCheck(final TreeStructure tree, final Point current, final Point pending) {
//    final Triangle3d topFace = bounds.getTopFace();
//    final Tuple3d origin = topFace.getBarycentricOrigin();
//    final Vector3d uAxis = topFace.getVectorU();
//    final Vector3d vAxis = topFace.getVectorV();
      
      // TODO: implement closest-to-center check
      return false;
   }
}
