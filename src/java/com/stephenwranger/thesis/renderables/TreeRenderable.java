package com.stephenwranger.thesis.renderables;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.bounds.BoundsUtils;
import com.stephenwranger.graphics.bounds.BoundsUtils.FrustumResult;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Plane;
import com.stephenwranger.graphics.renderables.Renderable;
import com.stephenwranger.graphics.utils.Timings;
import com.stephenwranger.graphics.utils.buffers.AttributeRegion;
import com.stephenwranger.graphics.utils.buffers.BufferRegion;
import com.stephenwranger.graphics.utils.buffers.ColorRegion;
import com.stephenwranger.graphics.utils.buffers.DataType;
import com.stephenwranger.graphics.utils.buffers.SegmentObject;
import com.stephenwranger.graphics.utils.buffers.SegmentedVertexBufferPool;
import com.stephenwranger.graphics.utils.buffers.VertexRegion;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeServerConnection;
import com.stephenwranger.thesis.data.TreeServerProcessor;
import com.stephenwranger.thesis.data.TreeServerProcessor.ConnectionType;
import com.stephenwranger.thesis.data.TreeStructure;
import com.stephenwranger.thesis.icosatree.Icosatree;
import com.stephenwranger.thesis.octree.Octree;

public class TreeRenderable extends Renderable {
   private static final long TEN_MILLISECONDS_IN_NANOSECONDS = 10L * 1000L * 1000L;
   private static final double MIN_SCREEN_RENDER_AREA = Math.PI * 100.0 * 100.0; // 100 px radius circle
   private static final double MIN_SCREEN_SPLIT_AREA = Math.PI * 400.0 * 400.0; // 100 px radius circle

   private static final String FRUSTUM_CULLING = "Frustum Culling";
   private static final String UPLOAD_CELLS = "Upload Cells";
   private static final String PENDING_UPLOADS = "Pending Upload";
   private static final String RENDERING = "Rendering";
   
   private final TreeStructure tree;
   private final TreeServerConnection connection;
   private final SegmentedVertexBufferPool vboPool;
   private final Set<SegmentObject> segments = new HashSet<>();
   private final Queue<TreeCell> pending = new LinkedBlockingQueue<>();
   private final Timings timings = new Timings(100);
   
   private final Tuple3d currentOrigin = new Tuple3d(0,0,0);

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
      }
      
      final TreeCell root = this.tree.getCell(null, 0);
      
      this.segments.clear();
      this.pending.clear();
      
      this.timings.start(FRUSTUM_CULLING);
      this.frustumCulling(gl, scene, false, root);
      this.timings.end(FRUSTUM_CULLING);
      this.timings.start(UPLOAD_CELLS);
      this.uploadPending(gl, scene);
      this.timings.end(UPLOAD_CELLS);

      gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
      gl.glDisable(GL2.GL_LIGHTING);
      gl.glPointSize(4f);

      this.timings.start(RENDERING);
      this.vboPool.render(gl, segments);
      this.timings.end(RENDERING);
      
      gl.glPopAttrib();
   }
   
   public Timings getTimings() {
      return this.timings;
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
         
         final BoundingVolume bounds = cell.getBoundingVolume();
         final FrustumResult result = BoundsUtils.testFrustum(frustum, bounds);
         
         if(result == FrustumResult.OUT) {
//            System.out.println(cell.path + " out");
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
//            System.out.println("render '" + cell.path + "': " + renderAndSplit[0] + ", " + renderAndSplit[1]);
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
      final BoundingVolume bounds = cell.getBoundingVolume();
      final Tuple3d boundsCenter = bounds.getCenter();
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
         final double radius = cellCenterScreen.distance(cellRightScreen);
         final double screenArea = Math.PI * radius * radius;
         
         render = distanceToCamera <= boundsViewSpan || screenArea >= MIN_SCREEN_RENDER_AREA;
         split = cell.hasChildren() || screenArea >= MIN_SCREEN_SPLIT_AREA;
         
   //      if(cell.path.length() > 2) {
   //         System.out.println("'" + cell.path + "': " + screenArea + " >=? " + MIN_SCREEN_RENDER_AREA);
   //      }
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
      
      while(!this.pending.isEmpty() && System.nanoTime() - startTime < TEN_MILLISECONDS_IN_NANOSECONDS) {
         final TreeCell pendingCell = this.pending.remove();
         
         if(pendingCell.isComplete() && pendingCell.getSegmentPoolIndex() == -1 && pendingCell.getPointCount() > 0) {
            this.timings.start(PENDING_UPLOADS);
//            System.out.println("uploading: '" + pendingCell.path + "'");
            
            this.vboPool.setSegmentObject(gl, this.currentOrigin, pendingCell);
            this.timings.end(PENDING_UPLOADS);
            this.segments.add(pendingCell);
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
