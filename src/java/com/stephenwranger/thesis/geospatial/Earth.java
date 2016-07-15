package com.stephenwranger.thesis.geospatial;

import java.util.HashMap;
import java.util.Map;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingSphere;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.PickingHit;
import com.stephenwranger.graphics.math.PickingRay;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Ellipsoid;
import com.stephenwranger.graphics.renderables.EllipticalGeometry;
import com.stephenwranger.graphics.renderables.EllipticalSegment;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.MathUtils;
import com.stephenwranger.graphics.utils.shader.ShaderKernel;
import com.stephenwranger.graphics.utils.shader.ShaderProgram;
import com.stephenwranger.graphics.utils.shader.ShaderStage;
import com.stephenwranger.thesis.geospatial.EarthImagery.ImageryType;

/**
 * {@link Earth} is a {@link Renderable} that creates an {@link EllipticalGeometry} based on the {@link WGS84} surface
 * projection. All images taken from The Celestial Motherlode (http://www.celestiamotherlode.net/catalog/earth.php) or
 * Visible Earth (http://visibleearth.nasa.gov/view.php?id=73580)
 *
 * @author rangers
 *
 */
public class Earth extends Renderable {
   private static final BoundingSphere BOUNDS            = new BoundingSphere(new Tuple3d(), WGS84.EQUATORIAL_RADIUS);
   private static final double         MAX_DEPTH_RATIO   = 3000.0;

   private final ShaderProgram         shader;
   private final Ellipsoid             ellipsoid         = WGS84.ELLIPSOID;
   private final ImageryType           imageryType;
   
   private EllipticalGeometry          geometry          = null;
   private boolean                     isWireframe       = false;
   private boolean                     isLightingEnabled = false;
   private double                      loadFactor        = 0.75;

   public Earth(final ImageryType imageryType) {
      super(new Tuple3d(), new Quat4d());
      
      this.imageryType = imageryType;
      
      final Map<String, Integer> requestedAttributeLocations = new HashMap<>();
      final ShaderKernel vert = new ShaderKernel("earth.vert", Earth.class.getResourceAsStream("earth.vert"), ShaderStage.VERTEX);
      final ShaderKernel frag = new ShaderKernel("earth.frag", Earth.class.getResourceAsStream("earth.frag"), ShaderStage.FRAGMENT);
      this.shader = new ShaderProgram("Earth Texture Shader", requestedAttributeLocations, vert, frag);
   }

   @Override
   public BoundingVolume getBoundingVolume() {
      return Earth.BOUNDS;
   }

   @Override
   public PickingHit getIntersection(final PickingRay ray) {
      return (this.geometry == null) ? PickingRay.NO_HIT : this.geometry.getIntersection(ray);
   }

   public double getLoadFactor() {
      return this.loadFactor;
   }

   @Override
   public double[] getNearFar(final Scene scene) {
      final double fovy = scene.getFOV();
      final double aspect = scene.getSurfaceWidth() / scene.getSurfaceHeight();
      final double fovx = fovy * aspect;

      final double sin = Math.sin(Math.min(fovx, fovy));
      final double closeDistance = (WGS84.EQUATORIAL_RADIUS / sin);

      final Tuple3d cameraPosition = scene.getCameraPosition();
      // distance from camera to center of earth
      //      final double distance = TupleMath.length(TupleMath.add(cameraPosition, scene.getOrigin()));
      final double distance = cameraPosition.distance(scene.getLookAt());
      double far = distance * 1.618;
      double near = far / MAX_DEPTH_RATIO;

      if (distance <= closeDistance) {
         final double[] nearFar = this.getNearFarDistances(scene);
         
         if(nearFar[0] != Double.MAX_VALUE && nearFar[1] != -Double.MAX_VALUE) {
            return nearFar;
         }
      }

      return new double[] { near, far };
   }
   
   private double[] getNearFarDistances(final Scene scene) {
      final Tuple3d cameraOrigin = scene.getCameraPosition();
      final double maxFar = WGS84.EQUATORIAL_RADIUS;
      final double w = scene.getWidth();
      final double h = scene.getHeight();
      final Tuple3d[] screenCorners = new Tuple3d[] {
            new Tuple3d(0, 0, 1.0),
            new Tuple3d(w, 0, 1.0),
            new Tuple3d(w, h, 1.0),
            new Tuple3d(0, h, 1.0)
      };
      
      double far = cameraOrigin.distance(scene.getLookAt()) * 1.618;
      double near = far / MAX_DEPTH_RATIO;
      
      for(final Tuple3d screenCorner : screenCorners) {
         final Tuple3d worldXyz = CameraUtils.gluUnProject(scene, screenCorner);
         
         if(worldXyz != null) {
            worldXyz.add(scene.getOrigin());
            final Vector3d worldVector = new Vector3d(worldXyz);
            worldVector.subtract(cameraOrigin);
            worldVector.normalize();
            
            final PickingRay ray = new PickingRay(cameraOrigin, worldVector);
            double distance = Double.NaN;
            
            if(this.geometry == null) {
               final double[] hits = this.ellipsoid.getIntersection(ray);
               
               if(hits != null && hits.length > 0) {
                  distance = MathUtils.getMin(hits);
               } else {
                  distance = maxFar;
               }
            } else {
               final PickingHit hit =  this.geometry.getIntersection(ray);
               
               if(hit != PickingRay.NO_HIT) {
                  distance = hit.getDistance();
               } else {
                  distance = maxFar;
               }
            }
   
            if(Double.isFinite(distance)) {
               near = Math.max(1.0, Math.min(near, distance - MAX_DEPTH_RATIO));
               far = Math.max(far, distance + MAX_DEPTH_RATIO);
            }
         }
      }
      
      if(near > far / MAX_DEPTH_RATIO) {
         near = far / MAX_DEPTH_RATIO;
      }
      
      return new double[] { near, far };
   }
   
   public boolean isLightingEnabled() {
      return this.isLightingEnabled;
   }

   public boolean isWireframe() {
      return this.isWireframe;
   }
   
   private void setImagery(final EllipticalSegment segment) {
      EarthImagery.setImagery(segment, this.imageryType);
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      if (this.geometry == null) {
         this.geometry = new EllipticalGeometry(gl, this.ellipsoid, WGS84.EQUATORIAL_RADIUS, 2, this::getAltitude, this::setImagery);
         this.geometry.setLightingEnabled(this.isLightingEnabled);
         this.geometry.setLoadFactor(this.loadFactor);
      }

      gl.glPushAttrib(GL2.GL_POLYGON_BIT);
      gl.glPolygonMode(GL.GL_FRONT_AND_BACK, (this.isWireframe) ? GL2GL3.GL_LINE : GL2GL3.GL_FILL);
      
      this.shader.enable(gl);
      this.geometry.render(gl, glu, glDrawable, scene);
      this.shader.disable(gl);
      gl.glPopAttrib();
   }

   public void setLightingEnabled(final boolean isLightingEnabled) {
      this.isLightingEnabled = isLightingEnabled;

      if (this.geometry != null) {
         this.geometry.setLightingEnabled(this.isLightingEnabled);
      }
   }

   public void setLoadFactor(final double loadFactor) {
      this.loadFactor = loadFactor;

      if (this.geometry != null) {
         this.geometry.setLoadFactor(this.loadFactor);
      }
   }

   public void setWireframe(final boolean isWireframe) {
      this.isWireframe = isWireframe;
   }

   private double getAltitude(final double longitudeDegrees, final double latitudeDegrees) {
      return WGS84.getEllipsoidHeight(longitudeDegrees, latitudeDegrees);
   }
}
