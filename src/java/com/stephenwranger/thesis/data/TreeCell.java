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
   private final Tuple3d tempTuple = new Tuple3d();
   
   protected TreeCell(final TreeStructure tree, final String path) {
      this.tree = tree;
      this.path = path;
      System.arraycopy(tree.getCellSplit(), 0, this.cellSplit, 0, 3);
      
      this.bounds = tree.getBoundingVolume(this.path);
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
   
   /*
      TreeCell insertedInto = this;
      
      if(this.getPointCount() >= this.maxPoints) {
         insertedInto = this.getChildCell(tree, point.getXYZ(tree, current));
         insertedInto.addPoint(point);
      } else {
         super.addPoint(this.getPointCount(), point);
      }
      
      return insertedInto;
    */
   
   public void addPoint(final Point point) {
      final int index = this.getIndex(this.tree, point);
      TreeCell insertedInto = this;
      
      if(this.points.containsKey(index)) {
         insertedInto = this.getChildCell(this.tree, point.getXYZ(this.tree, this.tempTuple));
         final Point current = this.getPoint(index);
         final boolean swap = this.swapPointCheck(this.tree, current, point);
         Point toInsert = point;
         
         if(swap) {
            // this swap point should be the same as current
            toInsert = this.swapPoint(index, point);
         }
         
         insertedInto.addPoint(toInsert);
      } else {
         this.addPoint(index, point);
      }
      
      if(insertedInto != this) {
         int count = 1;
         
         if(pointsByChild.containsKey(insertedInto.getPath())) {
            count += pointsByChild.get(insertedInto.getPath());
         }
         
         pointsByChild.put(insertedInto.getPath(), count);
      }
   }
   
   @Override
   public String toString() {
      return "[TreeCell: " + this.path + ", point count: " + this.points.size() + "]";
   }

   /**
    * Returns the index that the given {@link Point} should be stored in.
    * 
    * @param tree the TreeStructure this cell belongs to
    * @param point
    * @return
    */
   protected abstract int getIndex(final TreeStructure tree, final Point point);
   
   /**
    * Called when a cell is currently full; return true to swap pending point with current point. 
    * 
    * @param tree the TreeStructure this cell belongs to
    * @param current point currently in index cell
    * @param pending point overlapping with current point
    * @return true to swap points; false to send pending point to child node
    */
   protected abstract boolean swapPointCheck(final TreeStructure tree, final Point current, final Point pending);
   
   /**
    * Returns the max number of child nodes this cell will split into.
    * 
    * @return
    */
   public abstract int getMaxChildren();
}
