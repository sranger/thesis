package com.stephenwranger.thesis.data;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class DataAttributes implements Iterable<Attribute> {
   private static final String X_ATTRIBUTE_NAME = "X";
   private static final String Y_ATTRIBUTE_NAME = "Y";
   private static final String Z_ATTRIBUTE_NAME = "Z";
   private static final String R_ATTRIBUTE_NAME = "Red";
   private static final String G_ATTRIBUTE_NAME = "Green";
   private static final String B_ATTRIBUTE_NAME = "Blue";
   private static final String A_ATTRIBUTE_NAME = "Altitude";
   private static final String I_ATTRIBUTE_NAME = "Intensity";
   
   private static final String[] USED_ATTRIBUTE_NAMES = new String[] { X_ATTRIBUTE_NAME, Y_ATTRIBUTE_NAME, 
         Z_ATTRIBUTE_NAME, R_ATTRIBUTE_NAME, G_ATTRIBUTE_NAME, B_ATTRIBUTE_NAME, A_ATTRIBUTE_NAME, I_ATTRIBUTE_NAME };
   
   private final List<Attribute> attributes = new ArrayList<>();
   private final Comparator<Attribute> indexComparator = new Comparator<Attribute>() {
      @Override
      public int compare(final Attribute o1, final Attribute o2) {
         return Integer.compare(o1.offset, o2.offset);
      }
   };
   
   private final Attribute[] usedAttributes = new Attribute[USED_ATTRIBUTE_NAMES.length];
   public final int stride;
   
   public DataAttributes(final List<Attribute> attributes) {
      this.attributes.addAll(attributes);
      this.attributes.sort(indexComparator);
      
      final Attribute last = this.attributes.get(this.attributes.size() - 1);
      this.stride = last.offset + last.size;
      
      for(int i = 0; i < USED_ATTRIBUTE_NAMES.length; i++) {
         // sets to null if unavailable
         usedAttributes[i] = this.getAttribute(USED_ATTRIBUTE_NAMES[i]);
      }
   }
   
   public Attribute getAttribute(final String name) {
      Attribute foundAttribute = null;
      for(final Attribute attribute : this.attributes) {
         if(attribute.name.equals(name)) {
            foundAttribute = attribute;
            break;
         }
      }
         
      return foundAttribute;
   }
   
   public String getAttributeNames() {
      final StringBuilder sb = new StringBuilder();
      
      for(int i = 0; i < attributes.size(); i++) {
         if(i != 0) {
            sb.append(",");
         }
         sb.append(attributes.get(i).name);
      }
      
      return sb.toString();
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
   public void loadBuffer(final FloatBuffer buffer, final ByteBuffer pointData, final int pointIndex) {
      for(final Attribute attribute : this.usedAttributes) {
         if(attribute == null) {
            buffer.put(0);
         } else {
            buffer.put(attribute.getValue(pointData, pointIndex, this.stride).floatValue());
         }
      }
   }
}
