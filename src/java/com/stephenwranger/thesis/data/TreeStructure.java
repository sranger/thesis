package com.stephenwranger.thesis.data;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import com.stephenwranger.graphics.bounds.BoundingVolume;

public abstract class TreeStructure implements Iterable<TreeCell> {
   protected static final double MAX_RADIUS = 8388608.0;
   
   private final DB mapDB = DBMaker.fileDB("cache.db").make(); // TODO: update to have off-heap copy as well then move old cells (LRU) to file db?
   private final DataAttributes attributes;
   private final HTreeMap<String, TreeCell> treeCells = mapDB.hashMap("map", Serializer.STRING, new TreeCellSerializer()).create();
   private final Comparator<TreeCell> pathLengthComparator = new Comparator<TreeCell>() {
      @Override
      public int compare(final TreeCell o1, final TreeCell o2) {
         return Integer.compare(o1.getPath().length(), o2.getPath().length());
      }
   };
   private final int[] cellSplit = new int[3];

   // TODO: add way to change these
   public final Attribute xAttribute;
   public final Attribute yAttribute;
   public final Attribute zAttribute;
   public final Attribute rAttribute;
   public final Attribute gAttribute;
   public final Attribute bAttribute;
   public final Attribute aAttribute;
   public final Attribute iAttribute;
   public final int maxPoints;
   
   public TreeStructure(final DataAttributes attributes, final int[] cellSplit, final int maxPoints) {
      this.attributes = attributes;
      this.maxPoints = maxPoints;
      System.arraycopy(cellSplit, 0, this.cellSplit, 0, 3);
      
      // TODO: change hard-coded
      xAttribute = this.attributes.getAttribute("X");
      yAttribute = this.attributes.getAttribute("Y");
      zAttribute = this.attributes.getAttribute("Z");
      rAttribute = this.attributes.getAttribute("Red");
      gAttribute = this.attributes.getAttribute("Green");
      bAttribute = this.attributes.getAttribute("Blue");
      aAttribute = this.attributes.getAttribute("Altitude");
      iAttribute = this.attributes.getAttribute("Intensity");
   }
   
   @SuppressWarnings("unchecked")
   @Override
   public Iterator<TreeCell> iterator() {
      final List<TreeCell> cells = new ArrayList<>();
      cells.addAll(this.treeCells.values());
      Collections.sort(cells, this.pathLengthComparator);
      
      return cells.iterator();
   }
   
   public int getCellCount() {
      return this.treeCells.size();
   }
   
   /**
    * Will insert the given point into the root of the tree which will then trickle down to the first un-occupied cell.
    * 
    * @param point the point to insert
    */
   public void addPoint(final Point point) {
      final TreeCell root = this.getCell(null, 0);
      root.addPoint(this, point);
   }
   
   /**
    * Returns the {@link TreeCell} defined by the parent path and child index; if the parent path is null, the root node
    * will be returned.
    * 
    * @param parentPath
    * @param childIndex
    * @return
    */
   public TreeCell getCell(final String parentPath, final int childIndex) {
      return this.getCell(this.getPath(parentPath, childIndex));
   }
   
   /**
    * Returns the {@link TreeCell} defined by the given child path; if it has not been created it will be initialized 
    * before being returned. Note: this should only be called with a child string returned from a parent tree cell.
    * 
    * @param childPath
    * @return
    */
   public TreeCell getCell(final String childPath) {
      TreeCell cell = this.treeCells.get(childPath);
      
      if(cell == null) {
         cell = this.createTreeCell(this, childPath);
         this.treeCells.put(cell.getPath(), cell);
      }
      
      return cell;
   }
   
   /**
    * Returns the {@link TreeCell} defined by the given child path if exists; if it has not been created, null will be 
    * returned.
    * 
    * @param path
    * @return
    */
   public TreeCell containsCell(final String path) {
      TreeCell cell = null;
      
      if(this.treeCells.containsKey(path)) {
         cell = this.treeCells.get(path);
      }
      
      return cell;
   }
   
   public DataAttributes getAttributes() {
      return this.attributes;
   }
   
   public int[] getCellSplit() {
      return this.cellSplit;
   }

   public abstract BoundingVolume getBoundingVolume(final String path);
   
   public abstract BoundingVolume getBoundingVolume(final String parentPath, final int childIndex);
   
   /**
    * Creates a new TreeCell with the given path. Note: this should only be called with a child string returned from a parent tree cell.
    * 
    * @param tree
    * @param childPath
    * @return
    */
   public abstract TreeCell createTreeCell(final TreeStructure tree, final String childPath);
   
   public abstract String getPath(final String parentPath, final int childIndex);
   
   private static class TreeCellSerializer implements Serializer<TreeCell> {

      @SuppressWarnings("unchecked")
      @Override
      public TreeCell deserialize(final DataInput2 in, final int available) throws IOException {
         try {
            final Class<? extends TreeCell> cellType = (Class<? extends TreeCell>) Class.forName(in.readUTF());
            final Class<? extends TreeStructure> treeType = (Class<? extends TreeStructure>) Class.forName(in.readUTF());
            final String path = in.readUTF();
            final List<Attribute> attributes = new ArrayList<>();
            final int attributeCount = in.readInt();
            
            for(int i = 0; i < attributeCount; i++) {
               attributes.add(new Attribute(in.readUTF()));
            }
            
            final DataAttributes dataAttributes = new DataAttributes(attributes);
            final int[] cellSplit = new int[] { in.readInt(), in.readInt(), in.readInt() };
            final BoundingVolume bounds = (BoundingVolume) treeType.getMethod("getIcosatetBoundingVolume", String.class).invoke(null, path);
            final Map<Integer, BoundingVolume> childBounds = new HashMap<>();
            final int childCount = in.readInt();
            
            for(int i = 0; i < childCount; i++) {
               final String childPath = in.readUTF();
               final BoundingVolume childBound = (BoundingVolume) treeType.getMethod("getIcosatetBoundingVolume", String.class).invoke(null, childPath);
               final int childIndex = (int) treeType.getMethod("getIndex", String.class, int.class).invoke(null, childPath, childPath.length() - 1);
               childBounds.put(childIndex, childBound);
            }
            
            final Constructor<? extends TreeCell> cellConstructor = cellType.getConstructor(String.class, BoundingVolume.class, DataAttributes.class, int[].class, Map.class);
            final TreeCell cell = cellConstructor.newInstance(path, bounds, dataAttributes, cellSplit, childBounds);
            
            final byte[] buffer = new byte[in.readInt()];
            final int pointCount = in.readInt();
            
            for(int i = 0; i < pointCount; i++) {
               final int index = in.readInt();
               in.readFully(buffer);
               cell.addPoint(index, new Point(dataAttributes, buffer));
            }
            
            return cell;
         } catch(final Exception e) {
            e.printStackTrace();
         }
         return null;
      }

      @Override
      public void serialize(final DataOutput2 out, final TreeCell value) throws IOException {
         final Class<? extends TreeCell> cellType = value.getClass();
         final Class<? extends TreeStructure> treeType = value.getTreeType();
         
         // constructor requirements
         // final String path, final BoundingVolume bounds, final DataAttributes attributes, final int[] cellSplit, final Map<Integer, BoundingVolume> childBounds
         final String path = value.path;
         final DataAttributes attributes = value.getDataAttributes();
         final int[] cellSplit = value.getCellSplit();
         final String[] childList = value.getChildList();

         out.writeUTF(cellType.getName());
         out.writeUTF(treeType.getName());
         out.writeUTF(path);
         out.writeInt(attributes.size());
         
         for(final Attribute attribute : attributes) {
            out.writeUTF(attribute.toString());
         }
         
         for(final int split : cellSplit) {
            out.writeInt(split);
         }
         
         out.writeInt(childList.length);
         
         for(final String child : childList) {
            out.writeUTF(child);
         }
         
         out.writeInt(attributes.stride);
         out.writeInt(value.getPointCount());
         
         for(final Entry<Integer, Point> entry : value.points().entrySet()) {
            out.writeInt(entry.getKey());
            out.write(entry.getValue().getRawData().array());
         }
      }
   }
}
