package com.stephenwranger.thesis.selection;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.bounds.BoundsUtils;
import com.stephenwranger.graphics.bounds.BoundsUtils.FrustumResult;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Plane;

public class Volume {
   private final Scene scene;
   private final Tuple3d origin;
   private final Tuple3d[] worldPoints;
   private final Plane[] planes; 
   
   public Volume(final Scene scene, final Tuple3d origin, final Tuple2d[] screenSpaceBounds) {
      this.scene = scene;
      this.origin = origin;
      
      final int length = screenSpaceBounds.length;
      
      this.worldPoints = new Tuple3d[length];
      this.planes = new Plane[length];
      
      for(int i = 0; i < length; i++) {
         this.worldPoints[i] = CameraUtils.gluUnProject(this.scene, new Tuple3d(screenSpaceBounds[i].x, screenSpaceBounds[i].y, 1.0));
      }
      
      for(int i = 0; i < length; i++) {
         final Tuple3d p1 = this.worldPoints[i];
         final Tuple3d p2 = this.worldPoints[(i+1) % length];
         this.planes[i] = new Plane(p1, p2, this.origin);
      }
      
//      final Tuple3d farOrigin = CameraUtils.gluUnProject(this.scene, new Tuple3d(this.scene.getWidth() / 2.0, this.scene.getHeight() / 2.0, 1.0));
//      final Vector3d negativeViewVector = this.scene.getViewVector();
//      negativeViewVector.scale(-1);
//      this.planes[length] = new Plane(farOrigin, negativeViewVector); // far plane
   }
   
   public boolean contains(final BoundingVolume bounds) {
      return BoundsUtils.testFrustum(planes, bounds) != FrustumResult.OUT;
   }
   
   public boolean contains(final Tuple3d point) {
      return BoundsUtils.testPointInFrustum(planes, point);
   }
}
