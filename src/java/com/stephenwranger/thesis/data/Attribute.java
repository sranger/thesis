package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;

public class Attribute {
   public static final String HEADER = "index,name,offset,size,type,typeBytes,min,max,mean,variance";
   
   public final int index;
   public final String name;
   public final int offset;
   public final int size;
   public final AttributeType type;
   public final int typeBytes;
   public final double min;
   public final double max;
   public final double mean;
   public final double variance;
   
   public Attribute(final String csvLine) {
      final String[] split = csvLine.split(",");
      
      this.index = Integer.parseInt(split[0]);
      this.name = split[1];
      this.offset = Integer.parseInt(split[2]);
      this.size = Integer.parseInt(split[3]);
      this.type = AttributeType.valueOf(split[4]);
      this.typeBytes = Integer.parseInt(split[5]);
      this.min = Double.parseDouble(split[6]);
      this.max = Double.parseDouble(split[7]);
      this.mean = Double.parseDouble(split[8]);
      this.variance = Double.parseDouble(split[9]);
   }
   
   public Number getValue(final ByteBuffer buffer, final int index, final int stride) {
      return this.type.get(buffer, index * stride + this.offset);
   }
   
   public byte[] getBytes(final ByteBuffer buffer, final int index, final int stride) {
      return this.type.getBytes(buffer, index * stride + this.offset, this.size);
   }
   
   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder();

      sb.append(index).append(",");
      sb.append(name).append(",");
      sb.append(offset).append(",");
      sb.append(size).append(",");
      sb.append(type).append(",");
      sb.append(typeBytes).append(",");
      sb.append(min).append(",");
      sb.append(max).append(",");
      sb.append(mean).append(",");
      sb.append(variance);
      
      return sb.toString();
   }
}
