package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class Point {
   private final Map<String, Number> values = new HashMap<>();
   private final byte[] rawData;
   
   public Point(final DataAttributes attributes, final byte[] buffer) {
      this.rawData = new byte[buffer.length];
      System.arraycopy(buffer, 0, this.rawData, 0, buffer.length);
      
      final ByteBuffer bb = ByteBuffer.wrap(this.rawData);
      
      for(final Attribute attribute : attributes) {
         values.put(attribute.name, attribute.getValue(bb));
      }
   }
   
   public byte[] getRawData() {
      return this.rawData;
   }
   
   public Number getValue(final Attribute attribute) {
      return this.values.get(attribute.name);
   }
}
