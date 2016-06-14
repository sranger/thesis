package com.stephenwranger.thesis.icosatree;

import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

public class Icosatree extends TreeStructure {

   public Icosatree(DataAttributes attributes, int[] cellSplit) {
      super(attributes, cellSplit);
   }

   @Override
   public TreeCell createTreeCell(final TreeStructure tree, final String path) {
      return new Icosatet(tree, path);
   }

   @Override
   public BoundingVolume getBoundingVolume(String path) {
      // TODO Auto-generated method stub
      return null;
   }
}
