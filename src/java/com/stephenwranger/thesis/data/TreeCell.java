package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.utils.buffers.BufferUtils;
import com.stephenwranger.graphics.utils.buffers.SegmentObject;

public abstract class TreeCell implements Iterable<Point>, SegmentObject {
   public enum Status {
      EMPTY, PENDING, COMPLETE
   }
   
   public final String path;
   
   private final TreeStructure tree;
   private final BoundingVolume bounds;
   private final DataAttributes attributes;
   private final int stride;
   private final Map<Integer, BoundingVolume> childBounds = new HashMap<>();
   private final Tuple3d tempTuple = new Tuple3d();
   private int poolIndex = -1;
   private int bufferIndex = -1;
   
   // used only when building tree manually
   private final int[] cellSplit = new int[3];
   private final Map<Integer, Point> points = new HashMap<>(100, 0.95f);
   private final Map<String, Integer> pointsByChild = new HashMap<>();
   
   // used only when reading tree from filesystem or http
   private byte[] pointBuffer = null;
   private ByteBuffer gpuBuffer = null;
   private String[] children = null;
   private Status status = Status.EMPTY;
   
   protected TreeCell(final TreeStructure tree, final String path) {
      this.tree = tree;
      this.path = path;
      System.arraycopy(tree.getCellSplit(), 0, this.cellSplit, 0, 3);
      
      this.bounds = tree.getBoundingVolume(this.path);
      this.attributes = tree.getAttributes();
      this.stride = this.attributes.stride;
      
      for(int i = 0; i < this.getMaxChildren(); i++) {
         childBounds.put(i, tree.getBoundingVolume(this.path, i));
      }
   }
   
   @Override
   public Iterator<Point> iterator() {
      if(this.pointBuffer == null) {
         return this.points.values().iterator();
      } else {
         final int pointCount = this.getPointCount();
         
         return new Iterator<Point>() {
            private int index = 0;
            
            @Override
            public boolean hasNext() {
               return index < pointCount;
            }

            @Override
            public Point next() {
               final Point point = getPoint(index);
               index++;
               
               return point;
            }
         };
      }
   }
   
   public Point getPoint(final int index) {
      if(this.pointBuffer == null) {
         return this.points.get(index);
      } else {
         return new Point(attributes, Arrays.copyOfRange(pointBuffer, index * stride, stride));
      }
   }
   
   protected void addPoint(final int index, final Point point) {
      if(this.pointBuffer != null) {
         throw new RuntimeException("Cannot add points to a TreeCell initialized via byte array");
      } else if(this.points.containsKey(index)) {
         throw new RuntimeException("Cannot add new Point when existing Point resides at the given index; use TreeCell.getPoint(int) to determine whether an index is occupied and TreeCell.swapPoint(int, Point) to replace a Point at a given index.");
      }
      
      this.points.put(index, point);
   }
   
   protected Point swapPoint(final int index, final Point point) {
      if(this.pointBuffer != null) {
         throw new RuntimeException("Cannot modify points to a TreeCell initialized via byte array");
      } else if(!this.points.containsKey(index)) {
         throw new RuntimeException("Cannot swap new Point when existing Point does not exist at the given index; use TreeCell.getPoint(int) to determine whether an index is occupied and TreeCell.addPoint(int, Point) to add a new Point at a given index.");
      }
      
      return this.points.put(index, point);
   }
   
   public void setData(final byte[] buffer, final String[] children) {
      this.pointBuffer = buffer;
      this.children = children;
      this.points.clear();
      this.pointsByChild.clear();

      this.gpuBuffer = BufferUtils.newByteBuffer(32 * this.getPointCount());
      final ByteBuffer temp = ByteBuffer.wrap(this.pointBuffer).order(ByteOrder.LITTLE_ENDIAN);
      final int pointCount = this.getPointCount();
      
      for(int i = 0; i < pointCount; i++) {
         this.attributes.loadBuffer(this.gpuBuffer, temp, i);
      }
      
      this.gpuBuffer.rewind();
      this.status = Status.COMPLETE;
   }
   
   public void clearData() {
      this.pointBuffer = null;
      this.children = null;
      this.status = Status.EMPTY;
   }
   
   public void setPending() {
      this.status = Status.PENDING;
   }
   
   public boolean isEmpty() {
      return this.status == Status.EMPTY;
   }
   
   public boolean isComplete() {
      return this.status == Status.COMPLETE;
   }
   
   public Status getStatus() {
      return this.status;
   }
   
   /**
    * Will load into buffer X,Y,Z,R,G,B,Altitude,Intensity as float values.
    * 
    * @param buffer
    */
   @Override
   public void loadBuffer(final ByteBuffer buffer) {
      buffer.put(this.gpuBuffer);
      this.gpuBuffer.rewind();
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

      if(child == null) {
         final StringBuilder sb = new StringBuilder();
         
         if(this.bounds instanceof TrianglePrismVolume) {
            final TrianglePrismVolume tpv = (TrianglePrismVolume) this.bounds;
            final Tuple3d[] topCorners = tpv.getTopFace().getCorners();
            final Tuple3d[] botCorners = tpv.getBottomFace().getCorners();
            
            for(final Tuple3d corner : topCorners) {
               sb.append("\n").append(corner.x).append(",").append(corner.y).append(",").append(corner.z).append(",-1");
            }
            
            for(final Tuple3d corner : botCorners) {
               sb.append("\n").append(corner.x).append(",").append(corner.y).append(",").append(corner.z).append(",-1");
            }
         }
         
         for(final Entry<Integer, BoundingVolume> entry : this.childBounds.entrySet()) {
            if(entry.getValue() instanceof TrianglePrismVolume) {
               final TrianglePrismVolume tpv = (TrianglePrismVolume) entry.getValue();
               final Tuple3d[] topCorners = tpv.getTopFace().getCorners();
               final Tuple3d[] botCorners = tpv.getBottomFace().getCorners();
               
               for(final Tuple3d corner : topCorners) {
                  sb.append("\n").append(corner.x).append(",").append(corner.y).append(",").append(corner.z).append(",").append(entry.getKey());
               }
               
               for(final Tuple3d corner : botCorners) {
                  sb.append("\n").append(corner.x).append(",").append(corner.y).append(",").append(corner.z).append(",").append(entry.getKey());
               }
            }
         }
         throw new RuntimeException("Cannot find child node in parent '" + this.path + "' for point\n" + point.x + "," + point.y + "," + point.z + ",8" + sb.toString());
      }
      
      return child;
   }

   /**
    * Returns a list of octet paths for any child octets of this octet with point data.
    * 
    * @return
    */
   public String[] getChildList() {
      if(this.children == null) {
         return this.pointsByChild.keySet().toArray(new String[this.pointsByChild.size()]);
      } else {
         return this.children.clone();
      }
   }
   
   public boolean hasChildren() {
      return this.children != null && this.children.length > 0;
   }

   public String getPath() {
      return this.path;
   }

   public int getPointCount() {
      return (this.pointBuffer == null) ? this.points.size() : this.pointBuffer.length / stride;
   }

   public BoundingVolume getBoundingVolume() {
      return this.bounds;
   }
   
   public int[] getCellSplit() {
      return this.cellSplit;
   }
   
   public void addPoint(final Point point) {
      if(this.pointBuffer != null) {
         throw new RuntimeException("Cannot add points to a TreeCell initialized via byte array");
      }
      
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
   public void setSegmentLocation(final int poolIndex, final int bufferIndex) {
      this.poolIndex = poolIndex;
      this.bufferIndex = bufferIndex;
   }

   @Override
   public int getSegmentPoolIndex() {
      return this.poolIndex;
   }

   @Override
   public int getBufferIndex() {
      return this.bufferIndex;
   }

   @Override
   public int getVertexCount() {
      return this.getPointCount();
   }

   @Override
   public String toString() {
      return "[TreeCell: " + this.path + ", point count: " + this.getPointCount() + "]";
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
