package com.stephenwranger.thesis.renderables;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.Renderable;

public class TreeRenderable extends Renderable {

   public TreeRenderable() {
      super(new Tuple3d(), new Quat4d());
      
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      // TODO Auto-generated method stub

   }

   @Override
   public BoundingVolume getBoundingVolume() {
      // TODO Auto-generated method stub
      return null;
   }

}
