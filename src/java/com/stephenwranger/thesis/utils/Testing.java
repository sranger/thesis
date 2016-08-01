package com.stephenwranger.thesis.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.thesis.data.Attribute;
import com.stephenwranger.thesis.data.DataAttributes;
import com.stephenwranger.thesis.data.Point;
import com.stephenwranger.thesis.icosatree.Icosatree;

public class Testing {

   public static void main(final String[] args) {
      final List<Attribute> attributes = new ArrayList<>();
      attributes.add(new Attribute("0,X,0,8,DOUBLE,8,-9999999999,9999999999,0,0"));
      attributes.add(new Attribute("1,Y,8,8,DOUBLE,8,-9999999999,9999999999,0,0"));
      attributes.add(new Attribute("2,Z,16,8,DOUBLE,8,-9999999999,9999999999,0,0"));
      
      final DataAttributes dataAttributes = new DataAttributes(attributes);
      final Icosatree tree = new Icosatree(dataAttributes, new int[] { 250, 250, 250 });
      final Tuple3d point = new Tuple3d(-2107651.6, 4271176.38, 4230261.720000002);
      final ByteBuffer buffer = ByteBuffer.allocate(24);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      buffer.putDouble(point.x).putDouble(point.y).putDouble(point.z);
      buffer.rewind();
      
      for(int i = 0; i < 100; i++) {
         tree.addPoint(new Point(dataAttributes, buffer.array()));
      }
      
//      final Tuple3d[] top = new Tuple3d[] {
//         new Tuple3d(-2331042.3626545407, 4073389.3159727165, 4623673.771990905),
//         new Tuple3d(-2594314.953318176, 4236100.725309081, 4197689.771990905),
//         new Tuple3d(-2168330.953318176, 4499373.315972717, 4360401.181327269)
//      };
//      final Tuple3d[] bottom = new Tuple3d[] {
//         new Tuple3d(-2494533.6089597843, 4073173.774335655, 4036240.1653758697),
//         new Tuple3d(-2241386.887167827, 3916720.496127612, 4445840.16537587),
//         new Tuple3d(-2084933.6089597843, 4326320.496127612, 4192693.443583913)
//      };
//      final Tuple3d[] child0top = new Tuple3d[] {
//         new Tuple3d(-2462678.6579863583, 4154745.020640899, 4410681.771990905),
//         new Tuple3d(-2594314.953318176, 4236100.725309081, 4197689.771990905),
//         new Tuple3d(-2381322.953318176, 4367737.0206408985, 4279045.476659087)
//      };
//      final Tuple3d[] child0bottom = new Tuple3d[] {
//         new Tuple3d(-2544424.28113898, 4154637.249822368, 4116964.968683387),
//         new Tuple3d(-2415319.453025082, 4074846.0779362665, 4325860.968683387),
//         new Tuple3d(-2335528.28113898, 4283742.077936266, 4196756.140569489)
//      };
//      final Tuple3d[] child1top = new Tuple3d[] {
//         new Tuple3d(-2331042.3626545407, 4073389.3159727165, 4623673.771990905),
//         new Tuple3d(-2462678.6579863583, 4154745.020640899, 4410681.771990905),
//         new Tuple3d(-2249686.6579863583, 4286381.315972717, 4492037.476659087)
//      };
//      final Tuple3d[] child1bottom = new Tuple3d[] {
//         new Tuple3d(-2415319.453025082, 4074846.0779362665, 4325860.968683387),
//         new Tuple3d(-2286214.624911184, 3995054.9060501643, 4534756.968683387),
//         new Tuple3d(-2206423.453025082, 4203950.906050164, 4405652.140569489)
//      };
//      final Tuple3d[] child2top = new Tuple3d[] {
//         new Tuple3d(-2249686.6579863583, 4286381.315972717, 4492037.476659087),
//         new Tuple3d(-2381322.953318176, 4367737.0206408985, 4279045.476659087),
//         new Tuple3d(-2168330.953318176, 4499373.315972717, 4360401.181327269)
//      };
//      final Tuple3d[] child2bottom = new Tuple3d[] {
//         new Tuple3d(-2335528.28113898, 4283742.077936266, 4196756.140569489),
//         new Tuple3d(-2206423.453025082, 4203950.906050164, 4405652.140569489),
//         new Tuple3d(-2126632.28113898, 4412846.906050164, 4276547.312455591)
//      };
//      final Tuple3d[] child3top = new Tuple3d[] {
//         new Tuple3d(-2249686.6579863583, 4286381.315972717, 4492037.476659087),
//         new Tuple3d(-2462678.6579863583, 4154745.020640899, 4410681.771990905),
//         new Tuple3d(-2381322.953318176, 4367737.0206408985, 4279045.476659087)
//      };
//      final Tuple3d[] child3bottom = new Tuple3d[] {
//         new Tuple3d(-2415319.453025082, 4074846.0779362665, 4325860.968683387),
//         new Tuple3d(-2206423.453025082, 4203950.906050164, 4405652.140569489),
//         new Tuple3d(-2335528.28113898, 4283742.077936266, 4196756.140569489)
//      };
//      final Tuple3d[] child4top = new Tuple3d[] {
//         new Tuple3d(-2415319.453025082, 4074846.0779362665, 4325860.968683387),
//         new Tuple3d(-2544424.28113898, 4154637.249822368, 4116964.968683387),
//         new Tuple3d(-2335528.28113898, 4283742.077936266, 4196756.140569489)
//      };
//      final Tuple3d[] child4bottom = new Tuple3d[] {
//         new Tuple3d(-2494533.6089597843, 4073173.774335655, 4036240.1653758697),
//         new Tuple3d(-2367960.2480638055, 3994947.1352316337, 4241040.16537587),
//         new Tuple3d(-2289733.6089597843, 4199747.135231634, 4114466.8044798914)
//      };
//      final Tuple3d[] child5top = new Tuple3d[] {
//         new Tuple3d(-2286214.624911184, 3995054.9060501643, 4534756.968683387),
//         new Tuple3d(-2415319.453025082, 4074846.0779362665, 4325860.968683387),
//         new Tuple3d(-2206423.453025082, 4203950.906050164, 4405652.140569489)
//      };
//      final Tuple3d[] child5bottom = new Tuple3d[] {
//         new Tuple3d(-2367960.2480638055, 3994947.1352316337, 4241040.16537587),
//         new Tuple3d(-2241386.887167827, 3916720.496127612, 4445840.16537587),
//         new Tuple3d(-2163160.2480638055, 4121520.496127612, 4319266.804479891)
//      };
//      final Tuple3d[] child6top = new Tuple3d[] {
//         new Tuple3d(-2206423.453025082, 4203950.906050164, 4405652.140569489),
//         new Tuple3d(-2335528.28113898, 4283742.077936266, 4196756.140569489),
//         new Tuple3d(-2126632.28113898, 4412846.906050164, 4276547.312455591)
//      };
//      final Tuple3d[] child6bottom = new Tuple3d[] {
//         new Tuple3d(-2289733.6089597843, 4199747.135231634, 4114466.8044798914),
//         new Tuple3d(-2163160.2480638055, 4121520.496127612, 4319266.804479891),
//         new Tuple3d(-2084933.6089597843, 4326320.496127612, 4192693.443583913)
//      };
//      final Tuple3d[] child7top = new Tuple3d[] {
//         new Tuple3d(-2206423.453025082, 4203950.906050164, 4405652.140569489),
//         new Tuple3d(-2415319.453025082, 4074846.0779362665, 4325860.968683387),
//         new Tuple3d(-2335528.28113898, 4283742.077936266, 4196756.140569489)
//      };
//      final Tuple3d[] child7bottom = new Tuple3d[] {
//         new Tuple3d(-2367960.2480638055, 3994947.1352316337, 4241040.16537587),
//         new Tuple3d(-2163160.2480638055, 4121520.496127612, 4319266.804479891),
//         new Tuple3d(-2289733.6089597843, 4199747.135231634, 4114466.8044798914)
//      };
//
//      final List<Pair<Tuple3d[], Tuple3d[]>> triangles = new ArrayList<>();
//      triangles.add(Pair.getInstance(top, bottom));
//      triangles.add(Pair.getInstance(child0top, child0bottom));
//      triangles.add(Pair.getInstance(child1top, child1bottom));
//      triangles.add(Pair.getInstance(child2top, child2bottom));
//      triangles.add(Pair.getInstance(child3top, child3bottom));
//      triangles.add(Pair.getInstance(child4top, child4bottom));
//      triangles.add(Pair.getInstance(child5top, child5bottom));
//      triangles.add(Pair.getInstance(child6top, child6bottom));
//      triangles.add(Pair.getInstance(child7top, child7bottom));
//      
//      for(final Pair<Tuple3d[], Tuple3d[]> prism : triangles) {
//         final Tuple3d[] t1 = prism.left;
//         final Tuple3d[] t2 = prism.right;
//         
//         final Tuple3d temp = t1[0];
//         t1[0] = t1[1];
//         t1[1] = temp;
//         
//         final Triangle3d p1 = new Triangle3d(t1[0], t1[1], t1[2]);
//         final Triangle3d p2 = new Triangle3d(t2[0], t2[1], t2[2]);
//         System.out.println("\nto top: " + p1.distanceToPoint(point) + "\nto bottom: " + p2.distanceToPoint(point));
//         final Tuple3d center = getCenter(t1, t2);
//         System.out.println("to center: " + center.distance(point));
//         
//         final TrianglePrismVolume bounds = new TrianglePrismVolume(t1, t2);
//         System.out.println("contains? " + bounds.contains(point));
//         System.out.println("top uv: " + p1.getBarycentricCoordinate(point));
//         System.out.println("bot uv: " + p2.getBarycentricCoordinate(point));
//
//         System.out.println("t2.0 = " + p1.getBarycentricCoordinate(t2[0]));
//         System.out.println("t2.1 = " + p1.getBarycentricCoordinate(t2[1]));
//         System.out.println("t2.2 = " + p1.getBarycentricCoordinate(t2[2]));
//      }
   }
   
   private static Tuple3d getCenter(final Tuple3d[] t1, final Tuple3d[] t2) {
      final Tuple3d center = new Tuple3d();
      
      for(final Tuple3d c : t1) {
         center.add(c);
      }
      
      for(final Tuple3d c : t2) {
         center.add(c);
      }

      final double count = t1.length + t2.length;
      center.x /= count;
      center.y /= count;
      center.z /= count;
      
      return center;
   }

}
