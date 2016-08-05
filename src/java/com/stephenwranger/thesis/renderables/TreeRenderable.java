package com.stephenwranger.thesis.renderables;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.bounds.BoundsUtils;
import com.stephenwranger.graphics.bounds.BoundsUtils.FrustumResult;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.Matrix4d;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Plane;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.Timings;
import com.stephenwranger.graphics.utils.TupleMath;
import com.stephenwranger.graphics.utils.buffers.AttributeRegion;
import com.stephenwranger.graphics.utils.buffers.BufferRegion;
import com.stephenwranger.graphics.utils.buffers.ColorRegion;
import com.stephenwranger.graphics.utils.buffers.DataType;
import com.stephenwranger.graphics.utils.buffers.SegmentedVertexBufferPool;
import com.stephenwranger.graphics.utils.buffers.VertexRegion;
import com.stephenwranger.graphics.utils.shader.FloatMatrixUniform;
import com.stephenwranger.graphics.utils.shader.FloatUniform;
import com.stephenwranger.graphics.utils.shader.ShaderKernel;
import com.stephenwranger.graphics.utils.shader.ShaderProgram;
import com.stephenwranger.graphics.utils.shader.ShaderStage;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeServerConnection;
import com.stephenwranger.thesis.data.TreeServerProcessor;
import com.stephenwranger.thesis.data.TreeServerProcessor.ConnectionType;
import com.stephenwranger.thesis.data.TreeStructure;
import com.stephenwranger.thesis.icosatree.Icosatree;
import com.stephenwranger.thesis.octree.Octree;
import com.stephenwranger.thesis.selection.Volume;

public class TreeRenderable extends Renderable {
   private static final long TEN_MILLISECONDS_IN_NANOSECONDS = 10L * 1000L * 1000L;
   private static final double MIN_SCREEN_RENDER_AREA = Math.PI * 50.0 * 50.0; // 50 px radius circle
   private static final double MIN_SCREEN_SPLIT_AREA = Math.PI * 200.0 * 200.0; // 200 px radius circle

   private static final String FRUSTUM_CULLING = "Frustum Culling";
   private static final String UPLOAD_CELLS = "Upload Cells";
   private static final String PENDING_UPLOADS = "Pending Upload";
   private static final String RENDERING = "Rendering";
   
   private static final Comparator<TreeCell> DEPTH_COMPARATOR = new Comparator<TreeCell>() {
      @Override
      public int compare(final TreeCell o1, final TreeCell o2) {
         return Integer.compare(o1.path.length(), o2.path.length());
      }
   };

   private final ShaderKernel vert = new ShaderKernel("treecell.points.vert", TreeRenderable.class.getResourceAsStream("cell.vert"), ShaderStage.VERTEX);
   private final ShaderKernel frag = new ShaderKernel("treecell.points.frag", TreeRenderable.class.getResourceAsStream("cell.frag"), ShaderStage.FRAGMENT);
   private final ShaderProgram shader = new ShaderProgram("treecell.points", null, vert, frag);
   private final TreeStructure tree;
   private final TreeServerConnection connection;
   private final SegmentedVertexBufferPool vboPool;
   private final Set<TreeCell> segments = new HashSet<>();
   private final Set<TreeCell> pending = new TreeSet<>(DEPTH_COMPARATOR);
   private final Timings timings = new Timings(100);
   
   private final Tuple3d currentOrigin = new Tuple3d(0,0,0);
   
   private double levelOfDetail = 1.0;

   public TreeRenderable(final String basePath, final ConnectionType connectionType) {
      super(new Tuple3d(), new Quat4d());
      
      this.tree = this.initializeTree(basePath, connectionType);
      this.connection = new TreeServerConnection(tree, basePath, connectionType);
      
      final BufferRegion[] bufferRegions = new BufferRegion[] {
            new VertexRegion(3, DataType.FLOAT),         // XYZ
            new ColorRegion(3, DataType.FLOAT),          // RGB
            new AttributeRegion(10, 1, DataType.FLOAT),  // Altitude
            new AttributeRegion(11, 1, DataType.FLOAT)   // Intensity
      };
      this.vboPool = new SegmentedVertexBufferPool(this.tree.maxPoints == -1 ? 50000 : this.tree.maxPoints, 100, GL2.GL_POINTS, GL2.GL_DYNAMIC_DRAW, bufferRegions);
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      final Tuple3d origin = scene.getOrigin();
      
      if(origin.distance(this.currentOrigin) > 1) {
         for(final TreeCell cell : this.tree) {
            // TODO: add shader + temp origin offset
            cell.clearData();
         }
         
         this.currentOrigin.set(origin);
      }
      
      final TreeCell root = this.tree.getCell(null, 0);
      
      this.segments.clear();
      this.pending.clear();
      
      this.timings.start(FRUSTUM_CULLING);
      this.frustumCulling(gl, scene, true, root);
      this.timings.end(FRUSTUM_CULLING);
      this.timings.start(UPLOAD_CELLS);
      this.uploadPending(gl, scene);
      this.timings.end(UPLOAD_CELLS);

      gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
      
      gl.glDisable(GL2.GL_LIGHTING);
      gl.glPointSize(3f);

      this.timings.start(RENDERING);
      this.shader.enable(gl);
      
      final FloatUniform originOffset = this.shader.getFloatUniform("originOffset");
      originOffset.set(gl, 0, 0, 0); // TODO per cell
      
      final FloatMatrixUniform mvpUniform = this.shader.getFloatMatrixUniform("mvp");
      final Matrix4d p = new Matrix4d(scene.getProjectionMatrix());
      final Matrix4d mv = new Matrix4d(scene.getModelViewMatrix());
      final Matrix4d mvp = new Matrix4d();
      mvp.multiply(mv, p);
      mvpUniform.set(gl, false, mvp.getFloats());
      
      final FloatUniform altitudeRange = this.shader.getFloatUniform("altitudeRange");
      altitudeRange.set(gl, 0, 0); // TODO
      
      final FloatUniform intensityRange = this.shader.getFloatUniform("intensityRange");
      intensityRange.set(gl, 0, 0); // TODO
      
      this.vboPool.render(gl, segments);
      this.shader.disable(gl);
      this.timings.end(RENDERING);
      
      gl.glPopAttrib();
   }
   
   public void setLevelOfDetail(final double levelOfDetail) {
      this.levelOfDetail = levelOfDetail;
      
      if(this.scene != null) {
         this.scene.repaint();
      }
   }
   
   public double getLevelOfDetail() {
      return this.levelOfDetail;
   }
   
   public Timings getTimings() {
      return this.timings;
   }
   
   public Collection<Tuple3d> getVolumeIntersection(final Volume volume) {
      final List<Tuple3d> points = new ArrayList<>();
      final Tuple3d output = new Tuple3d();
      
      // TODO: go through tree structure to cull portions early
      for(final TreeCell cell : this.segments) {
         final BoundingVolume bounds = cell.getBoundingVolume();
         
         if(volume.contains(bounds)) {
            cell.forEach((point) -> {
               point.getXYZ(this.tree, output);
               
               if(volume.contains(output)) {
                  points.add(new Tuple3d(output));
               }
            });
         }
      }
      
      return points;
   }
   
   /**
    * Checks frustum and level of detail.
    * 
    * @param gl
    * @param scene
    * @param ignoreFrustum
    */
   private void frustumCulling(final GL2 gl, final Scene scene, final boolean ignoreFrustum, final TreeCell cell) {
      boolean shouldIgnoreFrustum = ignoreFrustum;
      
      if(!ignoreFrustum) {
         final Plane[] frustum = scene.getFrustumPlanes();
         
         final BoundingVolume bounds = cell.getBoundingVolume().offset(scene.getOrigin());
         final FrustumResult result = BoundsUtils.testFrustum(frustum, bounds);
         
         if(result == FrustumResult.OUT) {
            this.deleteCachedData(gl, scene, cell);
            return;
         }
         
         shouldIgnoreFrustum = result == FrustumResult.IN;
      }
      
      if(cell.isComplete()) {
         if(cell.getSegmentPoolIndex() == -1) {
            this.pending.add(cell);
         } else {
            final boolean[] renderAndSplit = this.checkLevelOfDetail(gl, scene, cell);
            if(renderAndSplit[0]) {
               this.segments.add(cell);
            }
            
            if(renderAndSplit[1]) {
               for(final String childPath : cell.getChildList()) {
                  final TreeCell childCell = this.tree.getCell(childPath);
                  
                  this.frustumCulling(gl, scene, shouldIgnoreFrustum, childCell);
               }
            }
         }
      } else if(cell.isEmpty()) {
         this.connection.request(cell);
      }
   }
   
   private boolean[] checkLevelOfDetail(final GL2 gl, final Scene scene, final TreeCell cell) {
      if(!cell.isComplete()) {
         return new boolean[] { false, false };
      }
      
      final BoundingVolume bounds = cell.getBoundingVolume();
      final Tuple3d boundsCenter = TupleMath.sub(bounds.getCenter(), scene.getOrigin());
      final Vector3d viewVector = scene.getViewVector();
      final Vector3d rightVector = scene.getRightVector();
      final double boundsHalfSpan = bounds.getSpannedDistance(rightVector) / 2.0;
      rightVector.scale(boundsHalfSpan);
      rightVector.add(boundsCenter);
      
      final double distanceToCamera = scene.getCameraPosition().distance(boundsCenter);
      final double boundsViewSpan = bounds.getSpannedDistance(viewVector);

      final Tuple3d cellCenterScreen = CameraUtils.gluProject(scene, boundsCenter);
      final Tuple3d cellRightScreen = CameraUtils.gluProject(scene, rightVector);
      boolean render = false;
      boolean split = false;
      
      if(cellCenterScreen != null && cellRightScreen != null) {
         final double radius = cellCenterScreen.distance(cellRightScreen) * this.levelOfDetail;
         final double screenArea = Math.PI * radius * radius;
         
         render = distanceToCamera <= boundsViewSpan || screenArea >= MIN_SCREEN_RENDER_AREA;
         split = cell.hasChildren() || screenArea >= MIN_SCREEN_SPLIT_AREA;
      }
      
      return new boolean[] { render, split };
   }
   
   /**
    * Checks any segments added to render list and requests any that haven't been loaded into memory.
    * 
    * @param gl
    * @param scene
    */
   private void uploadPending(final GL2 gl, final Scene scene) {
      final long startTime = System.nanoTime();
      
      for(final TreeCell pendingCell : this.pending) {
         if(System.nanoTime() - startTime >= TEN_MILLISECONDS_IN_NANOSECONDS) {
            break;
         }
         
         if(pendingCell.isComplete() && pendingCell.getSegmentPoolIndex() == -1 && pendingCell.getPointCount() > 0) {
            this.timings.start(PENDING_UPLOADS);
            
            this.vboPool.setSegmentObject(gl, this.currentOrigin, pendingCell);
            this.timings.end(PENDING_UPLOADS);
         }
      }
   }
   
   /**
    * Deletes all cached data and clears buffer pool location for the given tree cell and any children.
    * 
    * @param gl
    * @param scene
    * @param cell
    */
   private void deleteCachedData(final GL2 gl, final Scene scene, final TreeCell cell) {
      this.vboPool.clearSegmentObject(gl, cell);
      
      for(final String childPath : cell.getChildList()) {
         final TreeCell childCell = this.tree.containsCell(childPath);
         
         if(childCell != null) {
            this.deleteCachedData(gl, scene, childCell);
         }
      }
      
      cell.clearData();
   }

   @Override
   public BoundingVolume getBoundingVolume() {
      return this.tree.getBoundingVolume("");
   }

   private TreeStructure initializeTree(final String basePath, final ConnectionType connectionType) {
      DataAttributes attributes = null;
      String[] children = null;
      TreeStructure tree = null;
      int maxPoints = -1;
      
      try {
         switch(connectionType) {
            case FILESYSTEM:
               children = TreeServerProcessor.getChildren(new File(basePath, "root.txt"));
               attributes = TreeServerProcessor.getAttributes(new File(basePath, "attributes.csv"));
               maxPoints = TreeServerProcessor.getMaxPoints(new File(basePath, "root.txt"));
               break;
            case HTTP:
               children = TreeServerProcessor.getChildren(new URL(basePath + "/root.txt"));
               attributes = TreeServerProcessor.getAttributes(new URL(basePath + "/attributes.csv"));
               maxPoints = TreeServerProcessor.getMaxPoints(new URL(basePath + "/root.txt"));
               break;
         }
      } catch(final IOException e) {
         e.printStackTrace();
      }
      
      if(attributes != null && children != null && children.length > 0) {
         if(Character.isAlphabetic(children[0].charAt(0))) {
            tree = new Icosatree(attributes, maxPoints);
         } else {
            tree = new Octree(attributes, maxPoints);
         }
      }
      
      return tree;
   }
}
