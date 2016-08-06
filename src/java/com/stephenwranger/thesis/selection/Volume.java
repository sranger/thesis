package com.stephenwranger.thesis.selection;

import java.util.ArrayList;
import java.util.List;

import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.intersection.IntersectionUtils;
import com.stephenwranger.graphics.math.intersection.LineSegment;
import com.stephenwranger.graphics.utils.TupleMath;

public class Volume {
   private final Scene             scene;
   private final Tuple3d           origin;
   private final List<LineSegment> polygon = new ArrayList<>();
   //   private final Tuple3d[]    worldPoints;
   //   private final Triangle3d[] triangles;

   public Volume(final Scene scene, final Tuple3d origin, final Tuple2d[] screenSpaceBounds) {
      this.scene = scene;
      this.origin = origin;

      final int length = screenSpaceBounds.length;

      for (int i = 0; i < length; i++) {
         final Tuple2d p1 = screenSpaceBounds[i];
         final Tuple2d p2 = screenSpaceBounds[(i + 1) % length];
         this.polygon.add(new LineSegment(p1, p2));
      }

      //      final Tuple3d center = new Tuple3d();
      //      final Tuple3d sceneOrigin = scene.getOrigin();
      //      final int length = screenSpaceBounds.length;
      //      final int height = this.scene.getHeight();
      //
      //
      //      this.worldPoints = new Tuple3d[length];
      //      this.triangles = new Triangle3d[length];
      //
      //      for (int i = 0; i < length; i++) {
      //         this.worldPoints[i] = CameraUtils.gluUnProject(this.scene, new Tuple3d(screenSpaceBounds[i].x, height - screenSpaceBounds[i].y, 1.0));
      //         if (this.worldPoints[i] == null) {
      //            throw new RuntimeException("Cannot unproject given vertex: " + screenSpaceBounds[i]);
      //         } else {
      //            this.worldPoints[i].add(sceneOrigin);
      //         }
      //
      //         center.add(this.worldPoints[i]);
      //      }
      //
      //      center.x /= length;
      //      center.y /= length;
      //      center.z /= length;
      //
      //      for (int i = 0; i < length; i++) {
      //         final Tuple3d p1 = this.worldPoints[i];
      //         final Tuple3d p2 = this.worldPoints[(i + 1) % length];
      //         this.triangles[i] = new Triangle3d(p1, p2, this.origin);
      //
      //         // if average point isn't inside the plane, swap winding order
      //         if (!this.triangles[i].isInside(center)) {
      //            this.triangles[i] = new Triangle3d(p2, p1, this.origin);
      //         }
      //      }

   }

   public boolean contains(final BoundingVolume bounds) {
      // TODO project bounds to screen
      return true;
   }

   public boolean contains(final Tuple3d point) {
      final Tuple3d point3dScreen = CameraUtils.gluProject(this.scene, TupleMath.sub(point, this.scene.getOrigin()));
      final Tuple2d pointScreen = new Tuple2d(point3dScreen.x, this.scene.getHeight() - point3dScreen.y);
      final boolean contains = IntersectionUtils.pointInPolygon(pointScreen, this.polygon);

      return contains;
   }

   //   public boolean contains(final BoundingVolume bounds) {
   //      // TODO: this test uses the Plane.isInside which might not work for non-circular-ish volumes;
   //      // check screen space with 2d polygon test?
   //      return BoundsUtils.testFrustum(this.triangles, bounds) != FrustumResult.OUT;
   //   }
   //
   //   public boolean contains(final Tuple3d point) {
   //      // TODO: check screen space?
   //      return BoundsUtils.testPointInFrustum(this.triangles, point);
   //   }
   //
   //   public Triangle3d[] getTriangles() {
   //      return this.triangles;
   //   }
}
