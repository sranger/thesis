package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.stephenwranger.graphics.bounds.BoundingVolume;

public abstract class TreeStructure implements Iterable<TreeCell> {
   protected static final double MAX_RADIUS = 8388608.0;
   
   private final DataAttributes attributes;
   private final Map<String, TreeCell> treeCells = new HashMap<>();
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
   public final Attribute iAttribute;
   
   public TreeStructure(final DataAttributes attributes, final int[] cellSplit) {
      this.attributes = attributes;
      System.arraycopy(cellSplit, 0, this.cellSplit, 0, 3);
      
      // TODO: change hard-coded
      xAttribute = this.attributes.getAttribute("X");
      yAttribute = this.attributes.getAttribute("Y");
      zAttribute = this.attributes.getAttribute("Z");
      rAttribute = this.attributes.getAttribute("Red");
      gAttribute = this.attributes.getAttribute("Green");
      bAttribute = this.attributes.getAttribute("Blue");
      iAttribute = this.attributes.getAttribute("Intensity");
   }
   
   @Override
   public Iterator<TreeCell> iterator() {
      final List<TreeCell> octets = new ArrayList<>();
      octets.addAll(this.treeCells.values());
      Collections.sort(octets, this.pathLengthComparator);
      
      return octets.iterator();
   }
   
   public int getCellCount() {
      return this.treeCells.size();
   }
   
   /**
    * Will insert the given point into the root of the Octree which will then trickle down to the first un-occupied cell.
    * 
    * @param point the point to insert
    */
   public void addPoint(final Point point) {
      final TreeCell root = this.getCell("");
      root.addPoint(point);
   }
   
   public TreeCell getCell(final String path) {
      TreeCell octet = this.treeCells.get(path);
      
      if(octet == null) {
         octet = this.createTreeCell(this, path);
         this.treeCells.put(path, octet);
      }
      
      if(!path.equals(octet.getPath())) {
         System.err.println("octet path does not equal requested path: " + octet.getPath() + " != " + path);
      }
      
      return octet;
   }
   
   public DataAttributes getAttributes() {
      return this.attributes;
   }
   
   public int[] getCellSplit() {
      return this.cellSplit;
   }
   
   public abstract BoundingVolume getBoundingVolume(final String path);
   
   public abstract TreeCell createTreeCell(final TreeStructure tree, final String path);
}
