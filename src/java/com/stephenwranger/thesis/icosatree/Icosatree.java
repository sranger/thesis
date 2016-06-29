package com.stephenwranger.thesis.icosatree;

import com.stephenwranger.graphics.bounds.BoundingBox;
import com.stephenwranger.graphics.bounds.BoundingVolume;
import com.stephenwranger.graphics.bounds.TrianglePrismVolume;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;
import com.stephenwranger.graphics.math.intersection.Triangle3d;
import com.stephenwranger.graphics.utils.MathUtils;
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
   // TODO: better radii
   private static final double RADIUS_MAX = TreeStructure.MAX_RADIUS * 1.258408572364819; // causes internal spherical radius to equal the MAX_RADIUS value
   private static final double RADIUS_MIN = RADIUS_MAX * 0.5;
   private static final double RADIUS_MODIFIER = 1.0 / (2.0 * Math.sin(MathUtils.TWO_PI / 5.0));

// stellarium
//   private static final double G = 0.5 * (1.0 + Math.sqrt(5.0));
//   private static final double B = 1.0 / Math.sqrt(1.0 + G * G);
//   private static final double A = B * G;
//
//   private static final Vector3d[] ICOSAHEDRON_CORNERS = new Vector3d[] {
//      new Vector3d(  A,   -B,  0.0),
//      new Vector3d(  A,    B,  0.0),
//      new Vector3d( -A,    B,  0.0),
//      new Vector3d( -A,   -B,  0.0),
//      new Vector3d(0.0,    A,   -B),
//      new Vector3d(0.0,    A,    B),
//      new Vector3d(0.0,   -A,    B),
//      new Vector3d(0.0,   -A,   -B),
//      new Vector3d( -B,  0.0,    A),
//      new Vector3d(  B,  0.0,    A),
//      new Vector3d(  B,  0.0,   -A),
//      new Vector3d( -B,  0.0,   -A)
//   };
//
//   private static final Vector3d[][] ICOSAHEDRON_FACES = new Vector3d[][] {
//      { ICOSAHEDRON_CORNERS[1],    ICOSAHEDRON_CORNERS[0],    ICOSAHEDRON_CORNERS[10] }, // 0
//      { ICOSAHEDRON_CORNERS[0],    ICOSAHEDRON_CORNERS[1],    ICOSAHEDRON_CORNERS[9] },  // 1
//      { ICOSAHEDRON_CORNERS[0],    ICOSAHEDRON_CORNERS[9],    ICOSAHEDRON_CORNERS[6] },  // 2
//      { ICOSAHEDRON_CORNERS[9],    ICOSAHEDRON_CORNERS[8],    ICOSAHEDRON_CORNERS[6] },  // 3
//      { ICOSAHEDRON_CORNERS[0],    ICOSAHEDRON_CORNERS[7],    ICOSAHEDRON_CORNERS[10] }, // 4
//      { ICOSAHEDRON_CORNERS[6],    ICOSAHEDRON_CORNERS[7],    ICOSAHEDRON_CORNERS[0] },  // 5
//      { ICOSAHEDRON_CORNERS[7],    ICOSAHEDRON_CORNERS[6],    ICOSAHEDRON_CORNERS[3] },  // 6
//      { ICOSAHEDRON_CORNERS[6],    ICOSAHEDRON_CORNERS[8],    ICOSAHEDRON_CORNERS[3] },  // 7
//      { ICOSAHEDRON_CORNERS[11],   ICOSAHEDRON_CORNERS[10],   ICOSAHEDRON_CORNERS[7] },  // 8
//      { ICOSAHEDRON_CORNERS[7],    ICOSAHEDRON_CORNERS[3],    ICOSAHEDRON_CORNERS[11] }, // 9
//      { ICOSAHEDRON_CORNERS[3],    ICOSAHEDRON_CORNERS[2],    ICOSAHEDRON_CORNERS[11] }, // 10
//      { ICOSAHEDRON_CORNERS[2],    ICOSAHEDRON_CORNERS[3],    ICOSAHEDRON_CORNERS[8] },  // 11
//      { ICOSAHEDRON_CORNERS[10],   ICOSAHEDRON_CORNERS[11],   ICOSAHEDRON_CORNERS[4] },  // 12
//      { ICOSAHEDRON_CORNERS[11],    ICOSAHEDRON_CORNERS[2],   ICOSAHEDRON_CORNERS[4] },  // 13
//      { ICOSAHEDRON_CORNERS[5],    ICOSAHEDRON_CORNERS[4],    ICOSAHEDRON_CORNERS[2] },  // 14
//      { ICOSAHEDRON_CORNERS[2],    ICOSAHEDRON_CORNERS[8],    ICOSAHEDRON_CORNERS[5] },  // 15
//      { ICOSAHEDRON_CORNERS[4],    ICOSAHEDRON_CORNERS[1],    ICOSAHEDRON_CORNERS[10] }, // 16
//      { ICOSAHEDRON_CORNERS[4],    ICOSAHEDRON_CORNERS[5],    ICOSAHEDRON_CORNERS[1] },  // 17
//      { ICOSAHEDRON_CORNERS[5],    ICOSAHEDRON_CORNERS[9],    ICOSAHEDRON_CORNERS[1] },  // 18
//      { ICOSAHEDRON_CORNERS[8],    ICOSAHEDRON_CORNERS[9],    ICOSAHEDRON_CORNERS[5] }   // 19
//   };
   
   
   // andreaskahler.com
   private static final double T = (1.0 + Math.sqrt(5.0)) / 2.0;
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

   public Icosatree(DataAttributes attributes, int[] cellSplit) {
      super(attributes, cellSplit);
   }

   @Override
   public TreeCell createTreeCell(final TreeStructure tree, final String parentPath, final int childIndex) {
      final String path = this.getPath(parentPath, childIndex);
      
      return new Icosatet(tree, path);
   }

   @Override
   public BoundingVolume getBoundingVolume(final String parentPath, final int childIndex) {
      final String path = this.getPath(parentPath, childIndex);
      
      return this.getBoundingVolume(path);
   }

   @Override
   public BoundingVolume getBoundingVolume(final String path) {
      if(path.isEmpty()) {
         return new BoundingBox(new Tuple3d(), RADIUS_MAX * 2.0, RADIUS_MAX * 2.0, RADIUS_MAX * 2.0);
      } else {
         Tuple3d[] corners = null;
         double[] radii = null;
         
         if(path.length() == 1) {
            final int index = path.charAt(0) - ASCII_A;
            corners = ICOSAHEDRON_FACES[index];
            radii = new double[] { RADIUS_MAX, RADIUS_MIN };
         } else {
            corners = getFaceVertices(path);
            radii = getRadii(path);
         }
         
         final Tuple3d[] top = new Tuple3d[3];
         final Tuple3d[] bottom = new Tuple3d[3];
         
         for(int i = 0; i < 3; i++) {
            top[i] = new Vector3d(corners[i]).scale(radii[0] * RADIUS_MODIFIER);
            
            bottom[i] = new Vector3d(corners[i]).scale(radii[1] * RADIUS_MODIFIER);
         }
         
         return new TrianglePrismVolume(top, bottom);
      }
   }
   
   /**
    * Returns radius for top and bottom of icosatet defined by the given path; returns { topRadius, bottomRadius }.
    * 
    * @param path
    * @return
    */
   private static double[] getRadii(final String path) {
      double topRadius = RADIUS_MAX;
      double bottomRadius = RADIUS_MIN;
      
      for(int i = 0; i < path.length(); i++) {
         final char ch = path.charAt(i);
         final int index = (i == 0) ? ch - ASCII_A : Integer.parseInt(Character.toString(ch));
         final double midRadius = (topRadius - bottomRadius) / 2.0 + bottomRadius;
         
         if(index < 4) {
            // top half
            bottomRadius = midRadius;
         } else {
            // bottom half
            topRadius = midRadius;
         }
      }
      
      return new double[] { topRadius, bottomRadius };
   }
   
   /**
    * Splits the base icosahedron faces defined by the given path. This does not modify radius; use 
    * Icosatree.getRadii(String) to compute final radii for the triangular prism.
    * 
    * @param path
    * @return
    */
   public static Vector3d[] getFaceVertices(final String path) {
      Triangle3d face = null;
      
      for(int i = 0; i < path.length(); i++) {
         final char ch = path.charAt(i);
         final int index = (i == 0) ? ch - ASCII_A : Integer.parseInt(Character.toString(ch));
         
         if(i == 0) {
            face = new Triangle3d(ICOSAHEDRON_FACES[index][0], ICOSAHEDRON_FACES[index][1], ICOSAHEDRON_FACES[index][2]);
         } else {
            final Triangle3d[] splitFaces = face.split();
            face = splitFaces[index % 4]; // 0-3 are top half, 4-7 are bottom half
         }
      }
      
      final Tuple3d[] corners = face.getCorners();
      
      return new Vector3d[] { new Vector3d(corners[0]), new Vector3d(corners[1]), new Vector3d(corners[2]) };
   }
   
   @Override
   public String getPath(final String parentPath, final int childIndex) {
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
      // verify icosahedron triangles are facing in
      final Tuple3d origin = new Tuple3d();
      
      for(int i = 0; i < ICOSAHEDRON_FACES.length; i++) {
         System.out.println("\n\nface #" + i);
         
         final String path = "" + (char) (i + ASCII_A);
         Triangle3d triangle = new Triangle3d(ICOSAHEDRON_FACES[i][0], ICOSAHEDRON_FACES[i][1], ICOSAHEDRON_FACES[i][2]);
         
//         System.out.println("face # " + i + " origin inside? " + triangle.isBehind(origin));
//         final double[] radii = getRadii(path);
//         final Vector3d[] faceVertices = getFaceVertices(path);
//         
//         final Tuple3d[] top = new Tuple3d[3];
//         final Tuple3d[] bottom = new Tuple3d[3];
//
//         for(int j = 0; j < 3; j++) {
//            top[j] = new Vector3d(ICOSAHEDRON_FACES[i][j]).scale(radii[0] * RADIUS_MODIFIER);
//            
//            bottom[j] = new Vector3d(ICOSAHEDRON_FACES[i][j]).scale(radii[1] * RADIUS_MODIFIER);
//         }
//         
//         final TrianglePrismVolume bounds = new TrianglePrismVolume(top, bottom);
//         final Tuple3d center = bounds.getCenter();
//         System.out.println("prism average in bounds? " + bounds.contains(center));
      }
   }
}
