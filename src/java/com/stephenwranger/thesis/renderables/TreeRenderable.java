package com.stephenwranger.thesis.renderables;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.renderables.Renderable;
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
   private final TreeStructure tree;
   private final TreeServerConnection connection;
   private final SegmentedVertexBufferPool vboPool;
   private final Set<SegmentObject> segments = new HashSet<>();
   private final Set<TreeCell> pending = new HashSet<>();

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
      this.vboPool = new SegmentedVertexBufferPool(50000, 100, GL2.GL_POINTS, bufferRegions);
   }

   @Override
   public void render(final GL2 gl, final GLU glu, final GLAutoDrawable glDrawable, final Scene scene) {
      final TreeCell root = this.tree.getCell(null, 0);
      
      if(root.isEmpty()) {
         this.connection.request(root);
         this.pending.add(root);
      }
      
      this.checkPending(gl);

      gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
      gl.glDisable(GL2.GL_LIGHTING);
      gl.glPointSize(40f);
      
      this.vboPool.render(gl, segments);
      
      gl.glPopAttrib();
   }
   
   private void checkPending(final GL2 gl) {
      final Set<TreeCell> stillPending = new HashSet<>();
      int checked = 0;
      
      for(final TreeCell cell : this.pending) {
         if(checked == 10) {
            stillPending.add(cell);
         } else {
            checked++;
            if(cell.isComplete()) {            
               if(cell.getSegmentPoolIndex() == -1) {
                  this.vboPool.setSegmentObject(gl, cell);
                  this.segments.add(cell);
                  
                  if(cell.path.length() < 20) {
                     for(final String child : cell.getChildList()) {
                        final TreeCell childCell = this.tree.getCell(child);
                        this.connection.request(childCell);
                        stillPending.add(childCell);
                     }
                  }
               }
            } else {
               stillPending.add(cell);
            }
         }
      }
      
      this.pending.clear();
      this.pending.addAll(stillPending);
      
      gl.glPopAttrib();
   }

   @Override
   public BoundingVolume getBoundingVolume() {
      return this.tree.getBoundingVolume("");
   }

   private TreeStructure initializeTree(final String basePath, final ConnectionType connectionType) {
      DataAttributes attributes = null;
      String[] children = null;
      TreeStructure tree = null;
      
      try {
         switch(connectionType) {
            case FILESYSTEM:
               children = TreeServerProcessor.getChildren(new File(basePath, "root.txt"));
               attributes = TreeServerProcessor.getAttributes(new File(basePath, "attributes.csv"));
               break;
            case HTTP:
               children = TreeServerProcessor.getChildren(new URL(basePath + "/root.txt"));
               attributes = TreeServerProcessor.getAttributes(new URL(basePath + "/attributes.csv"));
               break;
         }
      } catch(final IOException e) {
         e.printStackTrace();
      }
      
      if(attributes != null && children != null && children.length > 0) {
         if(Character.isAlphabetic(children[0].charAt(0))) {
            tree = new Icosatree(attributes);
         } else {
            tree = new Octree(attributes);
         }
      }
      
      return tree;
   }
}