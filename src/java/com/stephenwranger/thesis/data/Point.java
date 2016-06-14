package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.stephenwranger.graphics.math.Tuple3d;

public class Point {
   private final Map<String, Number> values = new HashMap<>();
   private final ByteBuffer rawData;
   private final double x;
   private final double y;
   private final double z;
   
   public Point(final TreeStructure tree, final DataAttributes attributes, final byte[] buffer) {
      this.rawData = ByteBuffer.allocate(buffer.length).order(ByteOrder.LITTLE_ENDIAN);
      this.rawData.put(buffer);
      this.rawData.rewind();
      
      for(final Attribute attribute : attributes) {
         values.put(attribute.name, attribute.getValue(this.rawData));
      }
      
      this.x = tree.xAttribute.getValue(this.rawData).doubleValue();
      this.y = tree.yAttribute.getValue(this.rawData).doubleValue();
      this.z = tree.zAttribute.getValue(this.rawData).doubleValue();
   }
   
   public ByteBuffer getRawData() {
      return this.rawData;
   }
   
   public Number getValue(final Attribute attribute) {
      return this.values.get(attribute.name);
   }
   
   public Tuple3d getXYZ() {
      return new Tuple3d(x,y,z);
   }
   
   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder();
      
      for(final Entry<String, Number> entry : values.entrySet()) {
         sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
      }
      
      return sb.toString();
   }
}
