package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;

public enum AttributeType {
   BYTE(1), UNSIGNED_BYTE(1), SHORT(2), UNSIGNED_SHORT(2), INT(4), UNSIGNED_INT(4), LONG(8), UNSIGNED_LONG(8), FLOAT(4), DOUBLE(8), BYTE_ARRAY(-1);
   
   public final int byteSize;
   
   private AttributeType(final int byteSize) {
      this.byteSize = byteSize;
   }
   
   public void put(final ByteBuffer buffer, final int byteIndex, final Number value) {
      switch(this) {
         case BYTE:
            buffer.put(byteIndex, value.byteValue());
            break;
         case SHORT:
            buffer.putShort(byteIndex, value.shortValue());
            break;
         case INT:
            buffer.putInt(byteIndex, value.intValue());
            break;
         case FLOAT:
            buffer.putFloat(byteIndex, value.floatValue());
            break;
         case LONG:
            buffer.putLong(byteIndex, value.longValue());
            break;
         case DOUBLE:
            buffer.putDouble(byteIndex, value.doubleValue());
            break;
         case UNSIGNED_BYTE:
            buffer.put((byte) (value.shortValue() & 0xff));
            break;
         case UNSIGNED_SHORT:
            buffer.putShort((short) (value.intValue() & 0xffff));
            break;
         case UNSIGNED_INT:
            buffer.putInt((int) (value.longValue() & 0xffffffffL));
            break;
         case UNSIGNED_LONG:
            buffer.putLong(value.longValue() & 0xffffffffffffffffL); // TODO: probably doesn't change the value
            break;
         case BYTE_ARRAY:
            throw new UnsupportedOperationException("Use AttributeType.putBytes() for BYTE_ARRAY type");
      }
   }
   
   public void putBytes(final ByteBuffer buffer, final int byteIndex, final byte[] bytes) {
      buffer.put(bytes);
   }
   
   public Number get(final ByteBuffer buffer, final int byteIndex) {
      switch(this) {
         case BYTE:
            return buffer.get(byteIndex);
         case SHORT:
            return buffer.getShort(byteIndex);
         case INT:
            return buffer.getInt(byteIndex);
         case FLOAT:
            return buffer.getFloat(byteIndex);
         case LONG:
            return buffer.getLong(byteIndex);
         case DOUBLE:
            return buffer.getDouble(byteIndex);
         case UNSIGNED_BYTE:
            return ((short) (buffer.get(byteIndex) & 0xff));
         case UNSIGNED_SHORT:
            return (buffer.getShort(byteIndex) & 0xffff);
         case UNSIGNED_INT:
            return ((long) buffer.getInt(byteIndex) & 0xffffffffL);
         case UNSIGNED_LONG:
            return (buffer.getLong(byteIndex) & 0xffffffffffffffffL); // TODO: probably doesn't change the value
         case BYTE_ARRAY:
            throw new UnsupportedOperationException("Use AttributeType.getBytes() for BYTE_ARRAY type");
      }
      
      return Double.NaN;
   }
   
   public byte[] getBytes(final ByteBuffer buffer, final int byteIndex, final int count) {
      final byte[] bytes = new byte[count];
      buffer.position(byteIndex);
      buffer.get(bytes);
      
      return bytes;
   }
   
   public Class<?> getJavaType() {
      switch(this) {
         case BYTE:
         case UNSIGNED_BYTE:
            return Byte.class;
         case SHORT:
         case UNSIGNED_SHORT:
            return Short.class;
         case INT:
         case UNSIGNED_INT:
            return Integer.class;
         case FLOAT:
            return Float.class;
         case LONG:
         case UNSIGNED_LONG:
            return Long.class;
         case DOUBLE:
            return Double.class;
         case BYTE_ARRAY:
            return byte[].class;
      }
      
      return Number.class; // just in case
   }

   /**
    * <pre>
    *       UNSIGNED_CHAR       : "unsigned char"                1
    *       UNSIGNED_BYTE       : "unsigned byte"                1
    *       CHAR                : "char"                         1
    *       BYTE                : "byte"                         1
    *       UNSIGNED_SHORT_INT  : "unsigned short int"           2
    *       UNSIGNED_SHORT      : "unsigned short"               2
    *       SHORT_INT           : "short int"                    2
    *       SHORT               : "short"                        2
    *       UNSIGNED_INT        : "unsigned int"                 4
    *       INT                 : "int"                          4
    *       UNSIGNED_LONG_INT   : "unsigned long int"            8
    *       UNSIGNED_LONG       : "unsigned long"                8
    *       LONG_INT            : "long int"                     8
    *       LONG                : "long"                         8
    *       FLOAT               : "float"                        4
    *       LONG_DOUBLE         : "long double"                  8
    *       DOUBLE              : "double"                       8
    * </pre>
    */
   public static AttributeType getType(final String type) {
      switch (type) {
         case "unsigned char":
         case "unsigned byte":
            return AttributeType.UNSIGNED_BYTE;
         case "char":
         case "byte":
            return AttributeType.BYTE;
         case "unsigned short int":
         case "unsigned short":
            return AttributeType.UNSIGNED_SHORT;
         case "short int":
         case "short":
            return AttributeType.SHORT;
         case "unsigned int":
            return AttributeType.UNSIGNED_INT;
         case "int":
            return AttributeType.INT;
         case "unsigned long int":
         case "unsigned long":
            return AttributeType.UNSIGNED_LONG;
         case "long int":
         case "long":
            return AttributeType.LONG;
         case "float":
            return AttributeType.FLOAT;
         case "long double":
         case "double":
            return AttributeType.DOUBLE;
      }

      return null;
   }
}
