package com.stephenwranger.thesis.icosatree;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.TreeCell;
import com.stephenwranger.thesis.data.TreeStructure;

/**
 * Icosahedron structure based on icosahedron stellar grid from Stellarium (http://www.stellarium.org/).<br/>
 * Radius computation from http://math.stackexchange.com/a/441378<br/><br/>
 * 
 * icosphere construction: http://blog.andreaskahler.com/2009/06/creating-icosphere-mesh-in-code.html<br/><br/>
 * 
 * The root cell of the Icosatree will initially be an axis-aligned bounding box with TreeStructure.MAX_RADIUS * 4.0 as 
 * the x, y, and z dimension sizes. The first split will occur and twenty triangular prisms will be created with the top
 * face at a radius of TreeStructure.MAX_RADIUS * 2.0 and the bottom face at a radius of TreeStructure.MAX_RADIUS / 2.0. The, 
 * each successive split will separate each triangular prism into eight smaller triangular prisms with a split along
 * the top and bottom faces into four identical faces (see {@link Triangle3d#split()}) and a secondary split through the
 * depth along the midpoint radius. 
 * 
 * @author rangers
 *
 */
public class Icosatree extends TreeStructure {
   private static final int ASCII_A = 65;
   // andreaskahler.com
   private static final double T = (1.0 + Math.sqrt(5.0)) / 2.0;
   private static final double RADIUS_MAX = TreeStructure.MAX_RADIUS / T;// * 1.258408572364819; // causes internal spherical radius to equal the MAX_RADIUS value
   private static final double RADIUS_MIN = RADIUS_MAX * 0.5;
   private static final Vector3d[] ICOSAHEDRON_CORNERS = new Vector3d[] {
      new Vector3d(-1,  T,  0),
      new Vector3d( 1,  T,  0),
      new Vector3d(-1, -T,  0),
      new Vector3d( 1, -T,  0),
   
      new Vector3d( 0, -1,  T),
      new Vector3d( 0,  1,  T),
      new Vector3d( 0, -1, -T),
      new Vector3d( 0,  1, -T),
   
      new Vector3d( T,  0, -1),
      new Vector3d( T,  0,  1),
      new Vector3d(-T,  0, -1),
      new Vector3d(-T,  0,  1)
   };
   
   private static final Vector3d[][] ICOSAHEDRON_FACES = new Vector3d[][] {
      // 5 faces around point 0
      { ICOSAHEDRON_CORNERS[0], ICOSAHEDRON_CORNERS[11], ICOSAHEDRON_CORNERS[5] },
      { ICOSAHEDRON_CORNERS[0], ICOSAHEDRON_CORNERS[5], ICOSAHEDRON_CORNERS[1] },
      { ICOSAHEDRON_CORNERS[0], ICOSAHEDRON_CORNERS[1], ICOSAHEDRON_CORNERS[7] },
      { ICOSAHEDRON_CORNERS[0], ICOSAHEDRON_CORNERS[7], ICOSAHEDRON_CORNERS[10] },
      { ICOSAHEDRON_CORNERS[0], ICOSAHEDRON_CORNERS[10], ICOSAHEDRON_CORNERS[11] },

      // 5 adjacent faces
      { ICOSAHEDRON_CORNERS[1], ICOSAHEDRON_CORNERS[5], ICOSAHEDRON_CORNERS[9] },
      { ICOSAHEDRON_CORNERS[5], ICOSAHEDRON_CORNERS[11], ICOSAHEDRON_CORNERS[4] },
      { ICOSAHEDRON_CORNERS[11], ICOSAHEDRON_CORNERS[10], ICOSAHEDRON_CORNERS[2] },
      { ICOSAHEDRON_CORNERS[10], ICOSAHEDRON_CORNERS[7], ICOSAHEDRON_CORNERS[6] },
      { ICOSAHEDRON_CORNERS[7], ICOSAHEDRON_CORNERS[1], ICOSAHEDRON_CORNERS[8] },

      // 5 faces around point 3
      { ICOSAHEDRON_CORNERS[3], ICOSAHEDRON_CORNERS[9], ICOSAHEDRON_CORNERS[4] },
      { ICOSAHEDRON_CORNERS[3], ICOSAHEDRON_CORNERS[4], ICOSAHEDRON_CORNERS[2] },
      { ICOSAHEDRON_CORNERS[3], ICOSAHEDRON_CORNERS[2], ICOSAHEDRON_CORNERS[6] },
      { ICOSAHEDRON_CORNERS[3], ICOSAHEDRON_CORNERS[6], ICOSAHEDRON_CORNERS[8] },
      { ICOSAHEDRON_CORNERS[3], ICOSAHEDRON_CORNERS[8], ICOSAHEDRON_CORNERS[9] },

      // 5 adjacent faces
      { ICOSAHEDRON_CORNERS[4], ICOSAHEDRON_CORNERS[9], ICOSAHEDRON_CORNERS[5] },
      { ICOSAHEDRON_CORNERS[2], ICOSAHEDRON_CORNERS[4], ICOSAHEDRON_CORNERS[11] },
      { ICOSAHEDRON_CORNERS[6], ICOSAHEDRON_CORNERS[2], ICOSAHEDRON_CORNERS[10] },
      { ICOSAHEDRON_CORNERS[8], ICOSAHEDRON_CORNERS[6], ICOSAHEDRON_CORNERS[7] },
      { ICOSAHEDRON_CORNERS[9], ICOSAHEDRON_CORNERS[8], ICOSAHEDRON_CORNERS[1] }
   };
   
   public Icosatree(final DataAttributes attributes, final int maxPoints) {
      super(attributes, new int[3], maxPoints);
   }

   public Icosatree(DataAttributes attributes, int[] cellSplit) {
      super(attributes, cellSplit, -1);
   }

   @Override
   public TreeCell createTreeCell(final TreeStructure tree, final String childPath) {
      return new Icosatet(tree, childPath);
   }

   @Override
   public BoundingVolume getBoundingVolume(final String parentPath, final int childIndex) {
      final String path = this.getPath(parentPath, childIndex);
      
      return this.getBoundingVolume(path);
   }
   
   @Override
   public BoundingVolume getBoundingVolume(final String path) {
      return Icosatree.getCellBoundingVolume(path);
   }
   
   public static BoundingVolume getCellBoundingVolume(final String path) {
      if(path.isEmpty()) {
         return new BoundingBox(new Tuple3d(), RADIUS_MAX * 2.0, RADIUS_MAX * 2.0, RADIUS_MAX * 2.0);
      } else {
         Triangle3d top = null;
         Triangle3d bottom = null;
         
         for(int i = 0; i < path.length(); i++) {
            final int index = getIndex(path, i);
            
            if(i == 0) {
               final Tuple3d[] topCorners = new Tuple3d[3];
               final Tuple3d[] bottomCorners = new Tuple3d[3];

               for(int j = 0; j < 3; j++) {
                  topCorners[j] = new Vector3d(ICOSAHEDRON_FACES[index][j]).scale(RADIUS_MAX);
                  bottomCorners[j] = new Vector3d(ICOSAHEDRON_FACES[index][j]).scale(RADIUS_MIN);
               }

               top = new Triangle3d(topCorners[0], topCorners[1], topCorners[2]);
               bottom = new Triangle3d(bottomCorners[0], bottomCorners[1], bottomCorners[2]);
            } else {
               final Triangle3d topFaceSplit = top.split()[index % 4];
               final Triangle3d bottomFaceSplit = bottom.split()[index % 4];
               final Triangle3d midFaceSplit = Triangle3d.getMidFace(topFaceSplit, bottomFaceSplit);
               
               if(index < 4) {
                  top = topFaceSplit;
                  bottom = midFaceSplit;
               } else {
                  top = midFaceSplit;
                  bottom = bottomFaceSplit;
               }
            }
         }

         return new TrianglePrismVolume(top.getCorners(), bottom.getCorners());
      }
   }
   
   public static int getIndex(final String path, final int charIndex) {
      final char ch = path.charAt(charIndex);
      return (charIndex == 0) ? ch - ASCII_A : Integer.parseInt(Character.toString(ch));
   }

//   @Override
//   public BoundingVolume getBoundingVolume(final String path) {
//      if(path.isEmpty()) {
//         return new BoundingBox(new Tuple3d(), RADIUS_MAX * 2.0, RADIUS_MAX * 2.0, RADIUS_MAX * 2.0);
//      } else {
//         Tuple3d[] corners = null;
//         double[] radii = null;
//         
//         if(path.length() == 1) {
//            final int index = path.charAt(0) - ASCII_A;
//            corners = ICOSAHEDRON_FACES[index];
//            radii = new double[] { RADIUS_MAX, RADIUS_MIN };
//         } else {
//            corners = getFaceVertices(path);
//            radii = getRadii(path);
//         }
//         
//         final Tuple3d[] top = new Tuple3d[3];
//         final Tuple3d[] bottom = new Tuple3d[3];
//         
//         for(int i = 0; i < 3; i++) {
//            top[i] = new Vector3d(corners[i]).scale(radii[0] * RADIUS_MODIFIER);
//            
//            bottom[i] = new Vector3d(corners[i]).scale(radii[1] * RADIUS_MODIFIER);
//         }
//         
//         return new TrianglePrismVolume(top, bottom);
//      }
//   }
//   
//   /**
//    * Returns radius for top and bottom of icosatet defined by the given path; returns { topRadius, bottomRadius }.
//    * 
//    * @param path
//    * @return
//    */
//   private static double[] getRadii(final String path) {
//      double topRadius = RADIUS_MAX;
//      double bottomRadius = RADIUS_MIN;
//      
//      for(int i = 0; i < path.length(); i++) {
//         final char ch = path.charAt(i);
//         final int index = (i == 0) ? ch - ASCII_A : Integer.parseInt(Character.toString(ch));
//         final double midRadius = (topRadius - bottomRadius) / 2.0 + bottomRadius;
//         
//         if(index < 4) {
//            // top half
//            bottomRadius = midRadius;
//         } else {
//            // bottom half
//            topRadius = midRadius;
//         }
//      }
//      
//      return new double[] { topRadius, bottomRadius };
//   }
//   
//   /**
//    * Splits the base icosahedron faces defined by the given path. This does not modify radius; use 
//    * Icosatree.getRadii(String) to compute final radii for the triangular prism.
//    * 
//    * @param path
//    * @return
//    */
//   public static Vector3d[] getFaceVertices(final String path) {
//      Triangle3d face = null;
//      
//      for(int i = 0; i < path.length(); i++) {
//         final char ch = path.charAt(i);
//         final int index = (i == 0) ? ch - ASCII_A : Integer.parseInt(Character.toString(ch));
//         
//         if(i == 0) {
//            face = new Triangle3d(ICOSAHEDRON_FACES[index][0], ICOSAHEDRON_FACES[index][1], ICOSAHEDRON_FACES[index][2]);
//         } else {
//            final Triangle3d[] splitFaces = face.split();
//            face = splitFaces[index % 4]; // 0-3 are top half, 4-7 are bottom half
//         }
//      }
//      
//      final Tuple3d[] corners = face.getCorners();
//      
//      return new Vector3d[] { new Vector3d(corners[0]), new Vector3d(corners[1]), new Vector3d(corners[2]) };
//   }
   
   @Override
   public String getPath(final String parentPath, final int childIndex) {
      return Icosatree.getCellPath(parentPath, childIndex);
   }
   
   public static String getCellPath(final String parentPath, final int childIndex) {
      String path = parentPath;
      
      if(path == null) {
         return "";
      } else if(path.isEmpty()) {
         // the first level can't use integers as it can have up to twenty children; using letters instead [A-T]
         // 65 in ASCII is A
         path += (char)(childIndex + ASCII_A);
      } else {
         path += childIndex;
      }
      
      return path;
   }
   
   public static void main(final String[] args) {
      String path = "";
      
      for(int i = 0; i < 50; i++) {
         final BoundingVolume bounds = Icosatree.getCellBoundingVolume(path);
         System.out.println();
         System.out.println("path depth:     " + path.length());
         System.out.println("   type:        " + bounds.getClass().getSimpleName());
         System.out.println("   axis bounds: " + bounds.getDimensions());
         
         if(bounds instanceof TrianglePrismVolume) {
            final TrianglePrismVolume tpv = (TrianglePrismVolume) bounds;
            final double topArea = tpv.getTopFace().getArea();
            final double bottomArea = tpv.getBottomFace().getArea();
            System.out.println("   depth:       " + tpv.getDepth() + " m");
            System.out.println("   top area:    " + topArea + " m^2");
            System.out.println("   bottom area: " + bottomArea + " m^2");
            System.out.println("   volume:      " + tpv.getVolume() + " m^3");
         } else if(bounds instanceof BoundingBox) {
            final BoundingBox bb = (BoundingBox) bounds;
            System.out.println("   X-Y area:    " + bb.getXYSideArea() + " m^2");
            System.out.println("   X-Z area:    " + bb.getXZSideArea() + " m^2");
            System.out.println("   Y-Z area:    " + bb.getYZSideArea() + " m^2");
            System.out.println("   volume:      " + bb.getVolume() + " m^3");
         }
         
         path = Icosatree.getCellPath(path, 0);
      }
   }
}
