package com.stephenwranger.thesis.icosatree;

import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Icosatet extends TreeCell {

   protected Icosatet(final TreeStructure tree, final String path) {
      super(tree, path);
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
      // TODO Auto-generated method stub
      return null;
   }
}
