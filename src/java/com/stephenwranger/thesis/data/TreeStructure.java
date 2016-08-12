package com.stephenwranger.thesis.data;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import com.stephenwranger.graphics.bounds.BoundingVolume;

public abstract class TreeStructure implements Iterable<TreeCell> {
   protected static final double MAX_RADIUS = 8388608.0;
   
   private final Map<String, TreeCell> treeCells = new HashMap<>();
   private final DataAttributes attributes;
   private final Map<PointIndex, Point> pointsCache;
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
   
   /**
    * Creates a new {@link TreeStructure} object for use when building a new tree.
    * 
    * @param attributes the point attributes
    * @param cellSplit the xyz cell split values
    * @param maxPoints the maximum number of points to be loaded
    */
   public TreeStructure(final DataAttributes attributes, final int[] cellSplit, final int maxPoints) {
      this(attributes, cellSplit, maxPoints, TreeStructure.getMap(attributes));
   }

   private TreeStructure(final DataAttributes attributes, final int[] cellSplit, final int maxPoints, final Map<PointIndex, Point> pointsCache) {
      this.attributes = attributes;
      this.maxPoints = maxPoints;
      
      if(cellSplit != null) {
         System.arraycopy(cellSplit, 0, this.cellSplit, 0, 3);
      }
      
      // TODO: change hard-coded
      xAttribute = this.attributes.getAttribute("X");
      yAttribute = this.attributes.getAttribute("Y");
      zAttribute = this.attributes.getAttribute("Z");
      rAttribute = this.attributes.getAttribute("Red");
      gAttribute = this.attributes.getAttribute("Green");
      bAttribute = this.attributes.getAttribute("Blue");
      aAttribute = this.attributes.getAttribute("Altitude");
      iAttribute = this.attributes.getAttribute("Intensity");
      
      this.pointsCache = pointsCache;
   }
   
   /**
    * Creates a new {@link TreeStructure} object for use when loading from a pre-computed tree.
    * 
    * @param attributes the point attributes
    */
   public TreeStructure(final DataAttributes attributes, final int maxPoints) {
      this(attributes, null, maxPoints, null);
   }
   
   protected boolean containsKey(final PointIndex index) {
      return this.pointsCache.containsKey(index);
   }
   
   /**
    * Retrieves the {@link Point} at the given {@link PointIndex} in the points cache.
    * @param index the point index
    * @return the point at the given index or null if no mapping was found
    */
   protected Point getPoint(final PointIndex index) {
      return this.pointsCache.get(index);
   }
   
   /**
    * Inserts the given {@link Point} at the defined {@link PointIndex} and returns the old mapping, if any.
    * @param index the point index
    * @param point the point
    * @return the point previously mapped to the given index or null if no mapping was found
    */
   protected Point setPoint(final PointIndex index, final Point point) {
      final Point oldPoint = this.pointsCache.get(index);
      this.pointsCache.put(index, point);
      
      return oldPoint;
   }

   private static Map<PointIndex, Point> getMap(final DataAttributes attributes) {
      final boolean useMapDB = Boolean.valueOf(System.getProperty("mapdb.enable", "false"));

      if (useMapDB) {
         final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
         
         if(!tmpDir.exists()) {
            tmpDir.mkdirs();
         } else if(!tmpDir.isDirectory()) {
            throw new RuntimeException("defined temp directory is not a valid directory. (" + tmpDir.getAbsolutePath() + ")");
         }
//         final String name = (this.path.isEmpty()) ? "root" : this.path;
//         final DB diskDb = DBMaker.fileDB(new File(System.getProperty("java.io.tmpdir"), name)).closeOnJvmShutdown().fileDeleteAfterClose().make();
//         final DB memoryDb = DBMaker.memoryDB().make();
//
//         final HTreeMap<Integer, Point> diskMap = diskDb.hashMap("map" + name).keySerializer(Serializer.INTEGER).valueSerializer(new PointSerializer(this.attributes)).create();
//
//         final HTreeMap<Integer, Point> memoryMap = memoryDb.hashMap("map" + name).keySerializer(Serializer.INTEGER).valueSerializer(new PointSerializer(this.attributes)).expireAfterCreate().expireOverflow(diskMap).create();

         final DB db = DBMaker.tempFileDB()
                              .closeOnJvmShutdown()
                              .fileDeleteAfterClose()
                              .fileMmapEnable()
                              .make();
         HTreeMap<PointIndex, Point> diskMap = db.hashMap("map", new PointIndexSerializer(), new PointSerializer(attributes)).create();

         return diskMap;
      } else {
         return new HashMap<>();
      }
   }
   
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
      
      try {
         root.addPoint(this, point);
      } catch(final Exception e) {
         e.printStackTrace();
         System.exit(1);
      }
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
   
//   private static class TreeCellSerializer implements Serializer<TreeCell> {
//
//      @SuppressWarnings("unchecked")
//      @Override
//      public TreeCell deserialize(final DataInput2 in, final int available) throws IOException {
//         try {
//            final Class<? extends TreeCell> cellType = (Class<? extends TreeCell>) Class.forName(in.readUTF());
//            final Class<? extends TreeStructure> treeType = (Class<? extends TreeStructure>) Class.forName(in.readUTF());
//            final String path = in.readUTF();
//            final List<Attribute> attributes = new ArrayList<>();
//            final int attributeCount = in.readInt();
//            
//            for(int i = 0; i < attributeCount; i++) {
//               attributes.add(new Attribute(in.readUTF()));
//            }
//            
//            final DataAttributes dataAttributes = new DataAttributes(attributes);
//            final int[] cellSplit = new int[] { in.readInt(), in.readInt(), in.readInt() };
//            final BoundingVolume bounds = (BoundingVolume) treeType.getMethod("getCellBoundingVolume", String.class).invoke(null, path);
//            final Map<Integer, BoundingVolume> childBounds = new HashMap<>();
//            final int childCount = in.readInt();
//            
//            for(int i = 0; i < childCount; i++) {
//               final int childIndex = in.readInt();
//               final String childPath = (String) treeType.getMethod("getCellPath", String.class, int.class).invoke(null, path, childIndex);
//               final BoundingVolume childBound = (BoundingVolume) treeType.getMethod("getCellBoundingVolume", String.class).invoke(null, childPath);
//               childBounds.put(childIndex, childBound);
//            }
//            
//            final Constructor<? extends TreeCell> cellConstructor = cellType.getConstructor(String.class, BoundingVolume.class, DataAttributes.class, int[].class, Map.class);
//            final TreeCell cell = cellConstructor.newInstance(path, bounds, dataAttributes, cellSplit, childBounds);
//            
//            final byte[] buffer = new byte[in.readInt()];
//            final int pointCount = in.readInt();
//            
//            for(int i = 0; i < pointCount; i++) {
//               final int index = in.readInt();
//               in.readFully(buffer);
//               cell.addPoint(index, new Point(dataAttributes, buffer));
//            }
//            
//            return cell;
//         } catch(final Exception e) {
//            e.printStackTrace();
//         }
//         return null;
//      }
//
//      @Override
//      public void serialize(final DataOutput2 out, final TreeCell value) throws IOException {
//         final Class<? extends TreeCell> cellType = value.getClass();
//         final Class<? extends TreeStructure> treeType = value.getTreeType();
//         
//         // constructor requirements
//         // final String path, final BoundingVolume bounds, final DataAttributes attributes, final int[] cellSplit, final Map<Integer, BoundingVolume> childBounds
//         final String path = value.path;
//         final DataAttributes attributes = value.getDataAttributes();
//         final int[] cellSplit = value.getCellSplit();
//         final Map<Integer, BoundingVolume> childBounds = value.getChildBounds();
//
//         out.writeUTF(cellType.getName());
//         out.writeUTF(treeType.getName());
//         out.writeUTF(path);
//         out.writeInt(attributes.size());
//         
//         for(final Attribute attribute : attributes) {
//            out.writeUTF(attribute.toString());
//         }
//         
//         for(final int split : cellSplit) {
//            out.writeInt(split);
//         }
//         out.writeInt(childBounds.size());
//         
//         for(final Integer childIndex : childBounds.keySet()) {
//            out.writeInt(childIndex);
//         }
//         
//         out.writeInt(attributes.stride);
//         out.writeInt(value.getPointCount());
//         
//         for(final Entry<Integer, Point> entry : value.points().entrySet()) {
//            out.writeInt(entry.getKey());
//            out.write(entry.getValue().getRawData().array());
//         }
//      }
//   }
}
