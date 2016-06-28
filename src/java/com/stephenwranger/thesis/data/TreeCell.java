package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.Tuple3d;

public abstract class TreeCell implements Iterable<Point> {
   
   public final String path;
   
   private final TreeStructure tree;
   private final BoundingVolume bounds;
   private final DataAttributes attributes;
   private final int[] cellSplit = new int[3];
   private final Map<Integer, Point> points = new HashMap<>(100, 0.95f);
   private final Map<Integer, BoundingVolume> childBounds = new HashMap<>();
   private final Map<String, Integer> pointsByChild = new HashMap<>();
   
   protected TreeCell(final TreeStructure tree, final String path) {
      this.tree = tree;
      this.path = path;
      System.arraycopy(tree.getCellSplit(), 0, this.cellSplit, 0, 3);
      
      this.bounds = (BoundingBox) tree.getBoundingVolume(this.path);
      this.attributes = tree.getAttributes();
      
      for(int i = 0; i < this.getMaxChildren(); i++) {
         childBounds.put(i, tree.getBoundingVolume(this.path, i));
      }
   }
   
   @Override
   public Iterator<Point> iterator() {
      return this.points.values().iterator();
   }
   
   protected Point getPoint(final int index) {
      return this.points.get(index);
   }
   
   protected void addPoint(final int index, final Point point) {
      if(this.points.containsKey(index)) {
         throw new RuntimeException("Cannot add new Point when existing Point resides at the given index; use TreeCell.getPoint(int) to determine whether an index is occupied and TreeCell.swapPoint(int, Point) to replace a Point at a given index.");
      }
      
      this.points.put(index, point);
   }
   
   protected Point swapPoint(final int index, final Point point) {
      if(!this.points.containsKey(index)) {
         throw new RuntimeException("Cannot swap new Point when existing Point does not exist at the given index; use TreeCell.getPoint(int) to determine whether an index is occupied and TreeCell.addPoint(int, Point) to add a new Point at a given index.");
      }
      
      return this.points.put(index, point);
   }
   
   public void setData(final ByteBuffer buffer) {
      // TODO: for use when not directly building tree from raw data but from reading pre-computed data structure files
   }
   
   protected TreeCell getChildCell(final TreeStructure tree, final Tuple3d point) {
      TreeCell child = null;
      System.out.println("\n\n" + point);
      
      for(final Entry<Integer, BoundingVolume> entry : this.childBounds.entrySet()) {
         final int childIndex = entry.getKey();
         final BoundingVolume childBounds = entry.getValue();
         
         if(childBounds.contains(point)) {
            child = tree.getCell(this.path, childIndex);
      
            if(!child.getBoundingVolume().equals(childBounds)) {
               System.err.println("child bounds dont match: " + child.getPath());
               System.err.println("\tin octet = " + child.getBoundingVolume());
               System.err.println("\tcached   = " + childBounds);
            }
            break;
         }
      }
      
      return child;
   }

   /**
    * Returns a list of octet paths for any child octets of this octet with point data.
    * 
    * @return
    */
   public String[] getChildList() {
      return this.pointsByChild.keySet().toArray(new String[this.pointsByChild.size()]);
   }

   public String getPath() {
      return this.path;
   }

   public int getPointCount() {
      return this.points.size();
   }

   public BoundingVolume getBoundingVolume() {
      return this.bounds;
   }
   
   public int[] getCellSplit() {
      return this.cellSplit;
   }
   
   public void addPoint(final Point point) {
      final TreeCell insertedInto = this.addPoint(this.tree, point);
      
      if(insertedInto == null) {
         throw new NullPointerException("Point was not inserted into a TreeCell");
      }
      
      if(insertedInto != this) {
         int count = 1;
         
         if(pointsByChild.containsKey(insertedInto.getPath())) {
            count += pointsByChild.get(insertedInto.getPath());
         }
         
         pointsByChild.put(insertedInto.getPath(), count);
      }
   }
   
   protected abstract TreeCell addPoint(final TreeStructure tree, final Point point);
   public abstract int getMaxChildren();
}
