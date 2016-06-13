package com.stephenwranger.thesis.octree;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.Point;

public class Octet implements Iterable<Point> {
   private static final int MAX_CHILDREN = 8;
   
   public final String path;
   
   private final BoundingBox bounds;
   private final DataAttributes attributes;
   private final int[] cellSplit = new int[3];
   private final Map<Integer, Point> points = new HashMap<>();
   private final Map<String, BoundingBox> childBounds = new HashMap<>();
   
   Octet(final DataAttributes attributes, final int[] cellSplit, final String path) {
      this.path = path;
      System.arraycopy(cellSplit, 0, this.cellSplit, 0, 3);
      
      this.bounds = Octree.getOctetBoundingVolume(this.path);
      this.attributes = attributes;
      
      for(int i = 0; i < MAX_CHILDREN; i++) {
         final String childPath = this.path + Integer.toString(i);
         childBounds.put(childPath, Octree.getOctetBoundingVolume(childPath));
      }
   }
   
   @Override
   public Iterator<Point> iterator() {
      return this.points.values().iterator();
   }
   
   public void addPoint(final Octree octree, final Point point) {
      final Tuple3d min = bounds.getMin();
      final Tuple3d dimensions = bounds.getDimensions();
      final double xStep = dimensions.x / this.cellSplit[0];
      final double yStep = dimensions.y / this.cellSplit[1];
      final double zStep = dimensions.z / this.cellSplit[2];
      
      final Tuple3d xyz = new Tuple3d(point.getValue(octree.xAttribute).doubleValue(), point.getValue(octree.yAttribute).doubleValue(), point.getValue(octree.zAttribute).doubleValue());

      final int xIndex = (int)Math.floor((xyz.x - min.x) % xStep);
      final int yIndex = (int)Math.floor((xyz.y - min.y) % yStep);
      final int zIndex = (int)Math.floor((xyz.z - min.z) % zStep);
      final int index = xIndex + yIndex * cellSplit[0] + zIndex * cellSplit[0] * cellSplit[1];
      
      if(points.containsKey(index)) {
         final Point p = this.points.get(index);
         final Tuple3d center = new Tuple3d(xIndex * xStep + xStep * 0.5, yIndex * yStep + yStep * 0.5, zIndex * zStep + zStep * 0.5);
         final Tuple3d current = new Tuple3d(xyz);
         current.subtract(min);
         final Tuple3d stored = new Tuple3d(p.getValue(octree.xAttribute).doubleValue(), p.getValue(octree.yAttribute).doubleValue(), p.getValue(octree.zAttribute).doubleValue());
         stored.subtract(min);
         
         final Octet child = getChildOctet(octree, xyz);
         
         if(child == null) {
            throw new RuntimeException("Point given does not belong in this octet");
         }
         
         // if new point is closer to cell center than old point, swap them
         // then pass point to child
         if(center.distance(current) < center.distance(stored)) {
            this.points.put(index, point);
            child.addPoint(octree, p);
         } else {
            child.addPoint(octree, point);
         }
      } else {
         // add point to current octet
         this.points.put(index, point);
      }
   }
   
   public void setData(final ByteBuffer buffer) {
      // TODO: for use when not directly building octree from raw data but from reading pre-computed data structure files
   }
   
   private Octet getChildOctet(final Octree octree, final Tuple3d point) {
      Octet child = null;
      
      for(final Entry<String, BoundingBox> entry : this.childBounds.entrySet()) {
         final String childPath = entry.getKey();
         final BoundingBox childBounds = entry.getValue();
         
         if(childBounds.contains(point)) {
            child = octree.getOctet(childPath);
         }
      }
      
      return child;
   }
}
