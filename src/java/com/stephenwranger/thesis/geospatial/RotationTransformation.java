package com.stephenwranger.thesis.geospatial;

import com.stephenwranger.graphics.math.Quat4d;
import com.stephenwranger.graphics.math.Tuple3d;
import com.stephenwranger.graphics.math.Vector3d;

/**
 * A rotational transformation around a WGS84 projected ellipsoid.
 *
 */
public class RotationTransformation {
    /** The rotation axis. */
    public final Vector3d rotationAxis;
    /** The rotation angle in radians. */
    public final double angleRadians;

    /**
     * Creates a new rotation transformation using the specified axis and angle.
     *
     * @param axis The rotation axis
     * @param angleRadians The rotation angle
     */
    public RotationTransformation(final Vector3d axis, final double angleRadians) {
        this.angleRadians = angleRadians / 2.0;
        this.rotationAxis = axis;
    }

    /**
     * Returns the axis of rotation.
     *
     * @return The rotation axis
     */
    public Vector3d getRotationAxis() {
        return this.rotationAxis;
    }

    /**
     * Returns the rotation angle in radians.
     *
     * @return the rotation angle
     */
    public double getRotationAmount() {
        return angleRadians * 2.0;
    }

   public void apply(final Tuple3d xyz, final double scale) {
        final double sinRotAmount = Math.sin((angleRadians * scale));
        final Tuple3d surfaceCoordinate = WGS84.cartesianToGeodesic(xyz);
        final double alt = surfaceCoordinate.z;
        
        final Quat4d orientation = new Quat4d();
        orientation.set(this.rotationAxis.x * sinRotAmount, 
                        this.rotationAxis.y * sinRotAmount,
                        this.rotationAxis.z * sinRotAmount,
                        Math.cos((this.angleRadians * scale)));
        orientation.normalize();
        orientation.mult(xyz);

        final Tuple3d outputCoordinate = WGS84.cartesianToGeodesic(xyz);
        
        if (outputCoordinate.z != alt) {
           xyz.set(WGS84.geodesicToCartesian(new Tuple3d(outputCoordinate.x, outputCoordinate.y, alt)));
        }
    }

   public void apply(final Tuple3d xyz) {
        apply(xyz, 1);
    }

}
