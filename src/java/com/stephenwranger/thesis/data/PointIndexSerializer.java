package com.stephenwranger.thesis.data;

import java.io.IOException;

import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

public class PointIndexSerializer implements Serializer<PointIndex> {
   
   public PointIndexSerializer() {
      // nothing
   }

   @Override
   public PointIndex deserialize(final DataInput2 in, final int available) throws IOException {
      final String path = in.readUTF();
      final int index = in.readInt();
      
      return new PointIndex(path, index);
   }

   @Override
   public void serialize(final DataOutput2 out, final PointIndex pointIndex) throws IOException {
      out.writeUTF(pointIndex.path);
      out.writeInt(pointIndex.index);
   }

}
