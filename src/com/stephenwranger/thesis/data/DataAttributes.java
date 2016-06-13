package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class DataAttributes implements Iterable<Attribute> {
   private final List<Attribute> attributes = new ArrayList<>();
   private final Comparator<Attribute> indexComparator = new Comparator<Attribute>() {
      @Override
      public int compare(final Attribute o1, final Attribute o2) {
         return Integer.compare(o1.offset, o2.offset);
      }
   };
   
   public final int stride;
   
   public DataAttributes(final List<Attribute> attributes) {
      this.attributes.addAll(attributes);
      this.attributes.sort(indexComparator);
      
      final Attribute last = this.attributes.get(this.attributes.size() - 1);
      this.stride = last.offset + last.size;
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
}
