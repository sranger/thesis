package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.stephenwranger.graphics.math.Tuple3d;

public class Point {
   private final DataAttributes attributes;
   private final ByteBuffer rawData;
   private final int stride;
   
   public Point(final DataAttributes attributes, final byte[] buffer) {
      this.attributes = attributes;
      this.stride = buffer.length;
      this.rawData = ByteBuffer.allocate(buffer.length).order(ByteOrder.LITTLE_ENDIAN);
      this.rawData.put(buffer);
      this.rawData.rewind();
   }
   
   public ByteBuffer getRawData() {
      return this.rawData;
   }
   
   public Number getValue(final Attribute attribute) {
      return attribute.getValue(this.rawData, 0, stride);
   }
   
   public Tuple3d getXYZ(final TreeStructure tree, final Tuple3d output) {
      final Tuple3d outValue = (output == null) ? new Tuple3d() : output;
      outValue.x = tree.xAttribute.getValue(this.rawData, 0, stride).doubleValue();
      outValue.y = tree.yAttribute.getValue(this.rawData, 0, stride).doubleValue();
      outValue.z = tree.zAttribute.getValue(this.rawData, 0, stride).doubleValue();
      
      return output;
   }
   
   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder();
      
      for(final Attribute attribute : this.attributes) {
         sb.append(attribute.name).append(" = ").append(attribute.getValue(this.rawData, 0, stride)).append("\n");
      }
      
      return sb.toString();
   }
}
