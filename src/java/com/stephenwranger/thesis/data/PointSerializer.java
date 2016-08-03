package com.stephenwranger.thesis.data;

import java.io.IOException;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class PointSerializer implements Serializer<Point> {
   private final DataAttributes attributes;
   
   public PointSerializer(final DataAttributes pointAttributes) {
      this.attributes = pointAttributes;
   }

   @Override
   public Point deserialize(final DataInput2 in, final int available) throws IOException {
      final byte[] data = new byte[in.readInt()];
      in.readFully(data);
      
      return new Point(this.attributes, data);
   }

   @Override
   public void serialize(final DataOutput2 out, final Point point) throws IOException {
      final byte[] data = point.getRawData().array();
      out.writeInt(data.length);
      out.write(data);
   }

}
