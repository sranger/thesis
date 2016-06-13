package com.stephenwranger.thesis.octree;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.geospatial.WGS84;

public class Octree implements Iterable<Octet> {
   private static final double MAX_RADIUS = WGS84.EQUATORIAL_RADIUS * 2.0;
   
   private final DataAttributes attributes;
   private final Map<String, Octet> octets = new TreeMap<>(new Comparator<String>() {
      @Override
      public int compare(final String o1, final String o2) {
         return Integer.compare(o1.length(), o2.length());
      }
   });
   private final int[] cellSplit = new int[3];

   // TODO: add way to change these
   public final Attribute xAttribute;
   public final Attribute yAttribute;
   public final Attribute zAttribute;
   public final Attribute rAttribute;
   public final Attribute gAttribute;
   public final Attribute bAttribute;
   public final Attribute iAttribute;
   
   public Octree(final DataAttributes attributes, final int[] cellSplit) {
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
   public Iterator<Octet> iterator() {
      return this.octets.values().iterator();
   }
   
   public int getOctetCount() {
      return this.octets.size();
   }

   public Octet getOctet(final String path) {
      Octet octet = this.octets.get(path);
      
      if(octet == null) {
         octet = new Octet(this.attributes, cellSplit, path);
         this.octets.put(path, octet);
      }
      
      return octet;
   }
   
   /**
    * Will insert the given point into the root of the Octree which will then trickle down to the first un-occupied cell.
    * 
    * @param point the point to insert
    */
   public void addPoint(final Point point) {
      final Octet root = this.getOctet("root");
      root.addPoint(this, point);
   }
   
   public static BoundingBox getOctetBoundingVolume(final String octetPath) {
      final Tuple3d childmin = new Tuple3d(-MAX_RADIUS, -MAX_RADIUS, -MAX_RADIUS);
      final Tuple3d childmax = new Tuple3d(MAX_RADIUS, MAX_RADIUS, MAX_RADIUS);
      final Tuple3d size = new Tuple3d();
      size.subtract(childmax, childmin);
      
      for(int i = 0; i < octetPath.length(); i++) {
         final int index = octetPath.charAt(i) - 48;
         if ((index & 0b0001) > 0)
             childmin.z += size.z / 2;
         else
             childmax.z -= size.z / 2;
   
         if ((index & 0b0010) > 0)
             childmin.y += size.y / 2;
         else
             childmax.y -= size.y / 2;
   
         if ((index & 0b0100) > 0)
             childmin.x += size.x / 2;
         else
             childmax.x -= size.x / 2;

         size.subtract(childmax, childmin);
      }
         
      return new BoundingBox(childmin, childmax);
   }
}
