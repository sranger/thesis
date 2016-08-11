package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.stephenwranger.graphics.math.Tuple3d;

public class DataAttributes implements Iterable<Attribute> {
   private static final int            BYTES_PER_POINT      = 32;
   public static final String         X_ATTRIBUTE_NAME     = "X";
   public static final String         Y_ATTRIBUTE_NAME     = "Y";
   public static final String         Z_ATTRIBUTE_NAME     = "Z";
   public static final String         R_ATTRIBUTE_NAME     = "Red";
   public static final String         G_ATTRIBUTE_NAME     = "Green";
   public static final String         B_ATTRIBUTE_NAME     = "Blue";
   public static final String         A_ATTRIBUTE_NAME     = "Altitude";
   public static final String         I_ATTRIBUTE_NAME     = "Intensity";

   private static final String[]       USED_ATTRIBUTE_NAMES = new String[] { DataAttributes.X_ATTRIBUTE_NAME, DataAttributes.Y_ATTRIBUTE_NAME, DataAttributes.Z_ATTRIBUTE_NAME, DataAttributes.R_ATTRIBUTE_NAME,
         DataAttributes.G_ATTRIBUTE_NAME, DataAttributes.B_ATTRIBUTE_NAME, DataAttributes.A_ATTRIBUTE_NAME, DataAttributes.I_ATTRIBUTE_NAME };

   private static final boolean[]      normalize            = new boolean[] { false, false, false, true, true, true, false, true };

   private final List<Attribute>       attributes           = new ArrayList<>();
   private final Comparator<Attribute> indexComparator      = new Comparator<Attribute>() {
                                                               @Override
                                                               public int compare(final Attribute o1, final Attribute o2) {
                                                                  return Integer.compare(o1.offset, o2.offset);
                                                               }
                                                            };

   private final Attribute[]           usedAttributes       = new Attribute[DataAttributes.USED_ATTRIBUTE_NAMES.length];
   public final int                    stride;

   public DataAttributes(final List<Attribute> attributes) {
      this.attributes.addAll(attributes);
      this.attributes.sort(this.indexComparator);

      final Attribute last = this.attributes.get(this.attributes.size() - 1);
      this.stride = last.offset + last.size;

      for (int i = 0; i < DataAttributes.USED_ATTRIBUTE_NAMES.length; i++) {
         // sets to null if unavailable
         this.usedAttributes[i] = this.getAttribute(DataAttributes.USED_ATTRIBUTE_NAMES[i]);
      }
   }

   public Attribute getAttribute(final String name) {
      Attribute foundAttribute = null;
      for (final Attribute attribute : this.attributes) {
         if (attribute.name.equals(name)) {
            foundAttribute = attribute;
            break;
         }
      }

      return foundAttribute;
   }

   public String getAttributeNames() {
      final StringBuilder sb = new StringBuilder();

      for (int i = 0; i < this.attributes.size(); i++) {
         if (i != 0) {
            sb.append(",");
         }
         sb.append(this.attributes.get(i).name);
      }

      return sb.toString();
   }

   public int getGpuSize() {
      return DataAttributes.BYTES_PER_POINT;
   }

   @Override
   public Iterator<Attribute> iterator() {
      return this.attributes.iterator();
   }

   /**
    * Will load into buffer X,Y,Z,R,G,B,Altitude,Intensity as float values.
    *
    * @param buffer
    * @param pointIndex
    */
   public void loadBuffer(final Tuple3d origin, final ByteBuffer buffer, final ByteBuffer pointData, final int pointIndex, final Tuple3d outXyz) {
      for (int i = 0; i < this.usedAttributes.length; i++) {
         final Attribute attribute = this.usedAttributes[i];
         final boolean normalize = DataAttributes.normalize[i];

         if (attribute == null) {
            throw new RuntimeException("Attribute cannot be null");
         } else {
            double value = attribute.getValue(pointData, pointIndex, this.stride).doubleValue();

            if (i == 0) {
               if (outXyz != null) {
                  outXyz.x = value;
               }
               value -= origin.x;
            } else if (i == 1) {
               if (outXyz != null) {
                  outXyz.y = value;
               }
               value -= origin.y;
            } else if (i == 2) {
               if (outXyz != null) {
                  outXyz.z = value;
               }
               value -= origin.z;
            }

            if (normalize) {
               value /= Math.pow(2, attribute.size * 8); // 2^numBits
            }

            buffer.putFloat((float) value);
         }
      }
   }

   public int size() {
      return this.attributes.size();
   }
}
