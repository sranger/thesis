package com.stephenwranger.thesis.octree;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Octree extends TreeStructure {
   
   public Octree(final DataAttributes attributes, final int maxPoints) {
      super(attributes, new int[3], maxPoints);
   }
   
   public Octree(final DataAttributes attributes, final int[] cellSplit) {
      super(attributes, cellSplit, -1);
   }

   @Override
   public TreeCell createTreeCell(final TreeStructure tree, final String childPath) {
      return new Octet(tree, childPath);
   }
   
   @Override
   public BoundingVolume getBoundingVolume(final String parentPath, final int childIndex) {
      final String path = this.getPath(parentPath, childIndex);
      
      return this.getBoundingVolume(path);
   }
   
   @Override
   public BoundingVolume getBoundingVolume(final String path) {
      return Octree.getCellBoundingVolume(path);
   }
   
   public static BoundingVolume getCellBoundingVolume(final String path) {
      final Tuple3d childmin = new Tuple3d(-MAX_RADIUS, -MAX_RADIUS, -MAX_RADIUS);
      final Tuple3d childmax = new Tuple3d(MAX_RADIUS, MAX_RADIUS, MAX_RADIUS);
      final Tuple3d size = new Tuple3d();
      size.subtract(childmax, childmin);
      
      for(int i = 0; i < path.length(); i++) {
         final int index = Integer.parseInt(Character.toString(path.charAt(i)));
         
         if ((index & 0b0001) > 0) {
             childmin.z += size.z / 2.0;
         } else {
             childmax.z -= size.z / 2.0;
         }
   
         if ((index & 0b0010) > 0)
             childmin.y += size.y / 2.0;
         else
             childmax.y -= size.y / 2.0;
   
         if ((index & 0b0100) > 0)
             childmin.x += size.x / 2.0;
         else
             childmax.x -= size.x / 2.0;

         size.subtract(childmax, childmin);
      }
         
      return new BoundingBox(childmin.x, childmin.y, childmin.z, childmax.x, childmax.y, childmax.z);
   }
   
   @Override
   public String getPath(final String parentPath, final int childIndex) {
      return Octree.getCellPath(parentPath, childIndex);
   }
   
   public static String getCellPath(final String parentPath, final int childIndex) {
      return (parentPath == null) ? "" : parentPath + childIndex;
   }
   
   public static int getIndex(final String path, final int charIndex) {
      final char ch = path.charAt(charIndex);
      return Integer.parseInt(Character.toString(ch));
   }
}
