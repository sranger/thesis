package com.stephenwranger.thesis.data;

public class PointIndex {
   public final String path;
   public final int index;
   
   public PointIndex(final String path, final int index) {
      this.path = path;
      this.index = index;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + index;
      result = prime * result + ((path == null) ? 0 : path.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      PointIndex other = (PointIndex) obj;
      if (index != other.index)
         return false;
      if (path == null) {
         if (other.path != null)
            return false;
      } else if (!path.equals(other.path))
         return false;
      return true;
   }
}
