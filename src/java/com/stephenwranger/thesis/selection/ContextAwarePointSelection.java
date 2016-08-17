package com.stephenwranger.thesis.selection;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.activity.InvalidActivityException;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import com.stephenwranger.compgeo.algorithms.delaunay.DelaunayTriangulation;
import com.stephenwranger.graphics.Scene;
import com.stephenwranger.graphics.color.Color4f;
import com.stephenwranger.graphics.math.CameraUtils;
import com.stephenwranger.graphics.math.Tuple2d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.IntersectionUtils;
import com.stephenwranger.graphics.math.intersection.LineSegment;
import com.stephenwranger.graphics.math.intersection.Triangle2d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.renderables.PointRenderable;
import com.stephenwranger.graphics.renderables.PostProcessor;
import com.stephenwranger.graphics.renderables.TriangleMesh;
import com.stephenwranger.graphics.utils.TupleMath;
import com.stephenwranger.thesis.data.GridCell;
import com.stephenwranger.thesis.geospatial.WGS84;
import com.stephenwranger.thesis.renderables.TreeRenderable;

public class ContextAwarePointSelection implements PostProcessor, MouseListener, MouseMotionListener, MouseWheelListener {
   private final Scene           scene;
   private final TreeRenderable  tree;
   private final PointRenderable pointRenderer;

   private TriangleMesh          selectionTriangles  = null;
   private Volume                selection           = null;
   private final List<Tuple2d>   mouseSelection      = new ArrayList<>();

   private double                minDensity          = 0.2;
   private double                gridSize            = 5.0;
   private int                   k                   = 25;
   private boolean               isDrawTriangles     = false;
   private boolean               isDrawPoints        = true;
   private double                normalOffset        = 0.98;
   private double                groundDistanceRatio = 0.1;

   public ContextAwarePointSelection(final Scene scene, final TreeRenderable tree) {
      this.scene = scene;
      this.tree = tree;

      this.scene.addMouseListener(this);
      this.scene.addMouseMotionListener(this);
      this.scene.addMouseWheelListener(this);

      this.pointRenderer = new PointRenderable();
      this.pointRenderer.setPointSize(10f);
      this.scene.addRenderable(this.pointRenderer);
   }

   public double getGridSizeMeters() {
      return this.gridSize;
   }

   public double getGroundDistanceRatio() {
      return this.groundDistanceRatio;
   }

   public double getMinDensity() {
      return this.minDensity;
   }

   public int getNeighborCount() {
      return this.k;
   }

   public double getNormalOffset() {
      return this.normalOffset;
   }

   public boolean isDrawPoints() {
      return this.isDrawPoints;
   }

   public boolean isDrawTriangles() {
      return this.isDrawTriangles;
   }

   @Override
   public void mouseClicked(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mouseDragged(final MouseEvent event) {
      if (event.isControlDown()) {
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
      } else {
         this.mouseSelection.clear();
         this.selection = null;
      }
   }

   @Override
   public void mouseEntered(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mouseExited(final MouseEvent e) {
      // nothing

   }

   @Override
   public void mouseMoved(final MouseEvent e) {
      // nothing
   }

   @Override
   public void mousePressed(final MouseEvent event) {
      if (event.isControlDown() && SwingUtilities.isLeftMouseButton(event)) {
         this.mouseSelection.clear();
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
      } else {
         this.mouseSelection.clear();

         if (event.isControlDown()) {
            this.pointRenderer.setPoints(null);
         }

         this.selection = null;
      }
   }

   @Override
   public void mouseReleased(final MouseEvent event) {
      if (event.isControlDown() && SwingUtilities.isLeftMouseButton(event)) {
         this.mouseSelection.add(new Tuple2d(event.getX(), event.getY()));
         final int length = this.mouseSelection.size();
         final List<LineSegment> temp = new ArrayList<>();
         final List<Tuple2d> polygon = new ArrayList<>();

         for (int i = 0; i < length; i++) {
            final LineSegment line = new LineSegment(this.mouseSelection.get(i), this.mouseSelection.get((i + 1) % length));
            final Tuple2d intersection = ContextAwarePointSelection.getIntersections(line, temp);
            polygon.add(this.mouseSelection.get(i));

            if (intersection == null) {
               temp.add(line);

               if (i == (length - 1)) {
                  // end of loop so add the first point again to close the polygon
                  polygon.add(this.mouseSelection.get((i + 1) % length));
               }
            } else {
               // we're ending the loop so add the intersection point
               temp.add(new LineSegment(this.mouseSelection.get(i), intersection));
               polygon.add(intersection);
               break;
            }
         }

         final Tuple2d[] polygonVertices = polygon.toArray(new Tuple2d[polygon.size()]);
         this.selection = new Volume(this.scene, this.scene.getCameraPosition(), polygonVertices);
      } else {
         this.mouseSelection.clear();

         if (event.isControlDown()) {
            this.pointRenderer.setPoints(null);
         }

         this.selection = null;
      }
   }

   @Override
   public void mouseWheelMoved(final MouseWheelEvent e) {
      this.mouseSelection.clear();
   }

   @Override
   public void process(final GL2 gl, final GLU glu, final Scene scene) {
      final Volume selection = this.selection;
      this.selection = null;

      if (selection != null) {
         final Collection<Tuple3d> selectedPoints = this.tree.getVolumeIntersection(selection);
         this.prune(selectedPoints);

         final List<GridCell> cells = new ArrayList<>();
//         final double averageDensity = this.getAverageDensity(scene, selection, selectedPoints, cells);
         System.out.println("selected points count: " + selectedPoints.size());
//         System.out.println("average density: " + averageDensity);

         if (!selectedPoints.isEmpty()) {
            if (this.isDrawPoints) {
               this.pointRenderer.setPoints(selectedPoints);
            }

            if (this.selectionTriangles != null) {
               this.selectionTriangles.remove();
               this.selectionTriangles = null;
            }

            if (this.isDrawTriangles) {
//               final Triangle3d[] triangles = ContextAwarePointSelection.getTriangles(cells);
               final Triangle3d[] triangles = ContextAwarePointSelection.getTriangles(scene, selectedPoints);

               if (triangles.length > 0) {
                  this.selectionTriangles = new TriangleMesh(triangles, Color4f.red());
                  this.selectionTriangles.setPolygonMode(GL.GL_FRONT_AND_BACK);
                  this.selectionTriangles.setDrawNormals(false);
                  this.selectionTriangles.setCullFace(false);
                  this.scene.addRenderable(this.selectionTriangles);
               }
            }
         }
      } else if (!this.mouseSelection.isEmpty()) {
         final int size = this.mouseSelection.size();
         gl.glPushMatrix();
         gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
         gl.glLoadIdentity();
         glu.gluOrtho2D(0, this.scene.getWidth(), 0, this.scene.getHeight());
         gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
         gl.glLoadIdentity();

         gl.glPushAttrib(GL2.GL_LIGHTING_BIT | GL2.GL_LINE_BIT);
         gl.glDisable(GLLightingFunc.GL_LIGHTING);

         gl.glLineWidth(3f);
         gl.glBegin(GL.GL_LINE_STRIP);
         gl.glColor4f(0f, 0.6f, 0f, 1f);

         for (int i = 0; i <= size; i++) {
            final Tuple2d point = this.mouseSelection.get(i % size);
            gl.glVertex2f((float) point.x, (float) (this.scene.getHeight() - point.y));
         }

         gl.glEnd();
         gl.glPopAttrib();
         gl.glPopMatrix();
      }
   }

   public void setDrawPoints(final boolean isDrawPoints) {
      this.isDrawPoints = isDrawPoints;

      if (!this.isDrawPoints) {
         this.pointRenderer.setPoints(null);
      }
   }

   public void setDrawTriangles(final boolean isDrawTriangles) {
      this.isDrawTriangles = isDrawTriangles;
   }

   public void setGridSizeMeters(final double gridSize) {
      this.gridSize = gridSize;
   }

   public void setGroundDistanceRatio(final double groundDistanceRatio) {
      this.groundDistanceRatio = groundDistanceRatio;
   }

   public void setMinDensity(final double minDensity) {
      this.minDensity = minDensity;
   }

   public void setNeighborCount(final int neighborCount) {
      this.k = neighborCount;
   }

   public void setNormalOffset(final double normalOffset) {
      this.normalOffset = normalOffset;
   }

   // TODO: fix grid size
   private double getAverageDensity(final Scene scene, final Volume volume, final Collection<Tuple3d> points, final Collection<GridCell> outputCells) {
      if (points.isEmpty()) {
         return 0;
      }

      // average density is computed as:
      // pf = 1/nf * sigma(density(node[n]))

      // first, split collected points into grid
      final Tuple3d offset = new Tuple3d(0, 0, 0);//gridSize, gridSize, gridSize);
      final Tuple3d min = Tuple3d.getMin(points);
      min.subtract(offset);
      final Tuple3d max = Tuple3d.getMax(points);
      min.add(offset);
      final Tuple3d range = TupleMath.sub(max, min);
      final int xSize = (int) Math.max(1, Math.ceil(range.x / this.gridSize));
      final int ySize = (int) Math.max(1, Math.ceil(range.y / this.gridSize));
      final int zSize = (int) Math.max(1, Math.ceil(range.z / this.gridSize));

      // TODO: change to use https://commons.apache.org/sandbox/commons-graph/apidocs/org/apache/commons/graph/spanning/Kruskal.html
      // TODO: for now, just points per cubic meter = point count / (xSize * ySize * zSize)
      // average all the nodes' densities by dividing the sum by the number of nodes
      final GridCell[][][] cells = new GridCell[xSize][ySize][zSize];
      //      final double halfGrid = gridSize / 2.0;

      for (int x = 0; x < xSize; x++) {
         for (int y = 0; y < ySize; y++) {
            for (int z = 0; z < zSize; z++) {
               final double px = min.x + (x * this.gridSize);
               final double py = min.y + (y * this.gridSize);
               final double pz = min.z + (z * this.gridSize);

               //               if (volume.contains(new Tuple3d(px + halfGrid, py + halfGrid, pz + halfGrid), halfGrid)) {
               cells[x][y][z] = new GridCell(new Tuple3d(px, py, pz), new Tuple3d(px + this.gridSize, py + this.gridSize, pz + this.gridSize), x, y, z);
               //               }
            }
         }
      }

      int pointCount = 0;

      for (final Tuple3d point : points) {
         final int x = (int) Math.floor((point.x - min.x) / this.gridSize);
         final int y = (int) Math.floor((point.y - min.y) / this.gridSize);
         final int z = (int) Math.floor((point.z - min.z) / this.gridSize);

         if (cells[x][y][z] != null) {
            cells[x][y][z].points.add(point);
            pointCount++;
         }
      }

      int nodeCount = 0;
      System.out.println("point count: " + pointCount + " of " + points.size());

      for (int x = 0; x < xSize; x++) {
         for (int y = 0; y < ySize; y++) {
            for (int z = 0; z < zSize; z++) {
               final GridCell cell = cells[x][y][z];
               if ((cell != null) && !cell.points.isEmpty() && ((cell.points.size() / (this.gridSize * this.gridSize * this.gridSize)) > this.minDensity)) {
                  cell.computeNormals(scene, cells, this.k);
                  outputCells.add(cell);
                  nodeCount++;
               }
            }
         }
      }

      System.out.println("node count: " + nodeCount);

      for (final GridCell cell : outputCells) {
         cell.finish(scene);
      }

      //      // TODO: replace with nearest neighbor search; any empty cells copy vertex values from neighbors
      //      for (int x = 0; x < xSize; x++) {
      //         for (int y = 0; y < ySize; y++) {
      //            for (int z = 0; z < zSize; z++) {
      //               final GridCell cell = cells[x][y][z];
      //               if ((cell != null) && (cell.points.isEmpty() || (cell.normals == null) || cell.normals.isEmpty())) {
      //                  //                  System.out.println("check neighbors: " + x + ", " + y + ", " + z);
      //                  cell.checkNeighborCells(scene, cells);
      //               }
      //            }
      //         }
      //      }

      // points per unit square
      return pointCount / (nodeCount * Math.pow(this.gridSize, 3));
   }

   private void prune(final Collection<Tuple3d> points) {
      this.pruneOrthonormal(points);
//      this.pruneByAltitude(points);
   }

   private void pruneByAltitude(final Collection<Tuple3d> points) {
      final TreeMap<Double, Tuple3d> altitudeMap = new TreeMap<>();
      double minAltitude = Double.MAX_VALUE;
      double maxAltitude = -Double.MAX_VALUE;

      for (final Tuple3d point : points) {
         final Tuple3d lla = WGS84.cartesianToGeodesic(point);
         altitudeMap.put(lla.z, point);
         minAltitude = Math.min(minAltitude, lla.z);
         maxAltitude = Math.max(maxAltitude, lla.z);
      }

      final double pruneAlt = minAltitude + ((maxAltitude - minAltitude) * 0.1);
      System.out.println("minAltitude: " + minAltitude);
      System.out.println("maxAltitude: " + maxAltitude);
      System.out.println("pruneAlt: " + pruneAlt);
      System.out.println("before: " + points.size());
      for (final Entry<Double, Tuple3d> entry : altitudeMap.entrySet()) {
         if (entry.getKey() <= pruneAlt) {
            points.remove(entry.getValue());
         }
      }
      System.out.println("after: " + points.size());
   }

   private void pruneOrthonormal(final Collection<Tuple3d> points) {
      final int k = Math.max(3, this.k);
      final List<Tuple3d> toRemove = new ArrayList<>();

      for (final Tuple3d point : points) {
         final TreeMap<Double, Tuple3d> neighbors = ContextAwarePointSelection.getNeighbors(point, points, k);

         if (neighbors.lastKey() > (this.gridSize * 2)) {
            toRemove.add(point);
         } else {
            final Tuple3d lla = WGS84.cartesianToGeodesic(point);
            final Vector3d up = Vector3d.getVector(point, WGS84.geodesicToCartesian(new Tuple3d(lla.x, lla.y, lla.z + 1)), true);
            final Vector3d normal = GridCell.getAverageNormal(neighbors.values());
            normal.normalize();

            if (normal.dot(up) > this.normalOffset) {
               toRemove.add(point);
            }
         }
      }

      points.removeAll(toRemove);
   }

   private static Tuple2d getIntersections(final LineSegment toCheck, final List<LineSegment> existing) {
      final Tuple2d intersection = new Tuple2d();

      for (final LineSegment segment : existing) {
         if (IntersectionUtils.lineSegmentsIntersect(toCheck, segment, intersection)) {
            // make sure the intersection isn't on the endpoints
            if (!IntersectionUtils.isZero(segment.min.distance(intersection)) && !IntersectionUtils.isZero(segment.max.distance(intersection))) {
               return intersection;
            }
         }
      }

      return null;
   }

   private static TreeMap<Double, Tuple3d> getNeighbors(final Tuple3d point, final Collection<Tuple3d> points, final int k) {
      final TreeMap<Double, Tuple3d> neighbors = new TreeMap<>();

      for (final Tuple3d p : points) {
         final double distance = p.distanceSquared(point);

         if (neighbors.size() < k) {
            neighbors.put(distance, p);
         } else if (distance < neighbors.lastKey()) {
            neighbors.remove(neighbors.lastKey());
            neighbors.put(distance, p);
         }
      }

      return neighbors;
   }
   
   private static Triangle3d[] getTriangles(final Scene scene, final Collection<Tuple3d> points) {
      final Map<Tuple3d, Tuple3d> projectedPoints = new HashMap<>();
      final List<Tuple3d> visiblePoints = new ArrayList<>();
      
      for(final Tuple3d point : points) {
         final Tuple3d xyDepth = CameraUtils.gluProject(scene, point);
         projectedPoints.put(xyDepth, point);
      }
      
      for(final Tuple3d xyDepth : projectedPoints.keySet()) {
         if(!isOccluded(xyDepth, projectedPoints.keySet())) {
            visiblePoints.add(xyDepth);
         }
      }
      
      System.out.println("visible: " + visiblePoints.size());
      
      if(visiblePoints.isEmpty()) {
         return new Triangle3d[0];
      }

      final Tuple3d min = Tuple3d.getMin(visiblePoints);
      final Tuple3d max = Tuple3d.getMax(visiblePoints);
      final double xRange = (max.x - min.x) * 2;
      final double yRange = (max.y - min.y) * 2;
      final Tuple2d c1 = new Tuple2d(min.x - 1, min.y - 1);
      final Tuple2d c2 = new Tuple2d(min.x + xRange + 1, min.y - 1);
      final Tuple2d c3 = new Tuple2d(min.x - 1, min.y + yRange + 1);
      final Triangle2d boundingTriangle = new Triangle2d(c1, c2, c3, false);
      final DelaunayTriangulation dt = new DelaunayTriangulation(boundingTriangle);
      
      for(final Tuple3d xyDepth : visiblePoints) {
         try {
            dt.addVertex(xyDepth.xy(), false);
         } catch (final InvalidActivityException e) {
            e.printStackTrace();
         }
      }
      
      System.out.println("triangulated count: " + dt.getImmutableTriangles().size());
      
      final List<Triangle3d> output = new ArrayList<>();
      final Vector3d viewVector = scene.getViewVector();
      
      for(final Triangle2d triangle : dt.getTriangles()) {
         final Tuple2d[] corners = triangle.getCorners(false);

         final Tuple3d p1 = projectedPoints.get(corners[0]);
         final Tuple3d p2 = projectedPoints.get(corners[1]);
         final Tuple3d p3 = projectedPoints.get(corners[2]);

         if ((p1 != null) && (p2 != null) && (p3 != null)) {
            Triangle3d tri = new Triangle3d(p1, p2, p3);

            if (tri.getNormal().dot(viewVector) > 0) {
               tri = new Triangle3d(p2, p1, p3);
            }
            output.add(tri);
         } else {
            System.err.println("cannot find 3d match: " + p1 + ", " + p2 + ", " + p3);
         }
      }
      
      System.out.println("triangles: " + output.size());
      
      return output.toArray(new Triangle3d[output.size()]);
   }
   
   private static boolean isOccluded(final Tuple3d xyDepth, final Collection<Tuple3d> points) {
      for(final Tuple3d xyd : points) {
         if(xyDepth != xyd) {
            if(xyDepth.z > xyd.z && xyd.xy().distance(xyDepth.xy()) < 1.1) {
               return true;
            }
         }
      }
      
      return false;
   }

   private static Triangle3d[] getTriangles(final List<GridCell> cells) {
      List<Triangle3d> triangles = new ArrayList<>();

      for (final GridCell cell : cells) {
         triangles.addAll(cell.triangles);
      }

      final TriangleMerge merge = new TriangleMerge(triangles);
      // rebuilding triangles hash lookup not correct
      triangles = merge.process(false, true);

      return triangles.toArray(new Triangle3d[triangles.size()]);
   }
}
