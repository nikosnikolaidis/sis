/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.internal.referencing;

import org.apache.sis.util.Static;
import org.apache.sis.measure.Latitude;
import org.apache.sis.internal.util.Numerics;
import org.opengis.referencing.datum.Ellipsoid;
import org.apache.sis.referencing.datum.DefaultEllipsoid;

import static java.lang.Math.*;
import static org.apache.sis.math.MathFunctions.atanh;
import static org.apache.sis.internal.metadata.ReferencingServices.NAUTICAL_MILE;


/**
 * Miscellaneous numerical utilities which should not be put in public API.
 * This class contains methods that depend on hard-coded arbitrary tolerance threshold, and we
 * do not want to expose publicly those arbitrary values (or at least not in a too direct way).
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   0.4
 * @module
 */
public final class Formulas extends Static {
    /**
     * Default tolerance threshold for comparing coordinate values in a projected CRS,
     * assuming that the unit of measurement is metre. This constant determines also
     * (indirectly) the minimum accuracy of iterative methods in map projections.
     *
     * @see #ANGULAR_TOLERANCE
     * @see org.apache.sis.internal.util.Numerics#COMPARISON_THRESHOLD
     */
    public static final double LINEAR_TOLERANCE = 0.01;

    /**
     * Default tolerance threshold for comparing coordinate values in a geographic CRS,
     * assuming that the unit of measurement is decimal degrees and using the standard
     * nautical mile length.
     *
     * <p>For a {@link #LINEAR_TOLERANCE} of 1 centimetre, this is slightly less than 1E-7°.</p>
     *
     * @see #LINEAR_TOLERANCE
     * @see org.apache.sis.internal.util.Numerics#COMPARISON_THRESHOLD
     */
    public static final double ANGULAR_TOLERANCE = LINEAR_TOLERANCE / (NAUTICAL_MILE * 60);

    /**
     * Default tolerance threshold for comparing coordinate values in temporal CRS,
     * assuming that the unit of measurement is second. Current value is arbitrary
     * and may change in any future Apache SIS version.
     */
    public static final double TEMPORAL_TOLERANCE = 60;             // One minute.

    /**
     * The maximal longitude value before normalization if a centimetric precision is desired.
     * This is about 4×10⁸ degrees.
     *
     * @see org.apache.sis.measure.Longitude#normalize(double)
     */
    public static final double LONGITUDE_MAX = (1L << Numerics.SIGNIFICAND_SIZE) * ANGULAR_TOLERANCE;

    /**
     * The length of a <cite>Julian year</cite> in milliseconds.
     * From Wikipedia, <cite>"In astronomy, a Julian year (symbol: <b>a</b>) is a unit of measurement of time
     * defined as exactly 365.25 days of 86,400 SI seconds each."</cite>.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Julian_year_%28astronomy%29">Wikipedia: Julian year (astronomy)</a>
     */
    public static final long JULIAN_YEAR_LENGTH = 31557600000L;

    /**
     * Maximum number of iterations for iterative computations. Defined in this {@code Formulas} class as a default value,
     * but some classes may use a derived value (for example twice this amount). This constant is mostly useful for identifying
     * places where iterations occur.
     *
     * <p>Current value has been determined empirically for allowing {@code GeodesicsOnEllipsoidTest} to pass.</p>
     */
    public static final int MAXIMUM_ITERATIONS = 18;

    /**
     * Do not allow instantiation of this class.
     */
    private Formulas() {
    }

    /**
     * Returns {@code true} if {@code ymin} is the south pole and {@code ymax} is the north pole.
     *
     * @param  ymin  the minimal latitude to test.
     * @param  ymax  the maximal latitude to test.
     * @return {@code true} if the given latitudes are south pole to north pole respectively.
     */
    public static boolean isPoleToPole(final double ymin, final double ymax) {
        return abs(ymin - Latitude.MIN_VALUE) <= ANGULAR_TOLERANCE &&
               abs(ymax - Latitude.MAX_VALUE) <= ANGULAR_TOLERANCE;
    }

    /**
     * Returns the size of a planet described by the given ellipsoid compared to earth.
     * This method returns a ratio of given planet authalic radius compared to WGS84.
     * This can be used for adjusting {@link #LINEAR_TOLERANCE} and {@link #ANGULAR_TOLERANCE} to another planet.
     *
     * @param  planet  ellipsoid of the other planet to compare to Earth, or {@code null}.
     * @return ratio of planet authalic radius on WGS84 authalic radius, or {@code NaN} if the given ellipsoid is null.
     */
    public static double scaleComparedToEarth(final Ellipsoid planet) {
        return getAuthalicRadius(planet) / 6371007.180918474;
    }

    /**
     * Returns the radius of a hypothetical sphere having the same surface than the given ellipsoid.
     *
     * @param  ellipsoid  the ellipsoid for which to get the radius, or {@code null}.
     * @return the authalic radius, or {@link Double#NaN} if the given ellipsoid is null.
     */
    public static double getAuthalicRadius(final Ellipsoid ellipsoid) {
        if (ellipsoid == null) {
            return Double.NaN;
        } else if (ellipsoid instanceof DefaultEllipsoid) {
            return ((DefaultEllipsoid) ellipsoid).getAuthalicRadius();      // Give a chance to subclasses to override.
        } else {
            return getAuthalicRadius(ellipsoid.getSemiMajorAxis(),
                                     ellipsoid.getSemiMinorAxis());
        }
    }

    /**
     * Returns the radius of a hypothetical sphere having the same surface than the ellipsoid
     * specified by the given axis length.
     *
     * @param  a  the semi-major axis length.
     * @param  b  the semi-minor axis length.
     * @return the radius of a sphere having the same surface than the specified ellipsoid.
     *
     * @see org.apache.sis.referencing.datum.DefaultEllipsoid#getAuthalicRadius()
     */
    public static double getAuthalicRadius(final double a, final double b) {
        if (a != b) {
            final double f = 1 - b/a;
            final double e = sqrt(2*f - f*f);
            return sqrt(0.5 * (a*a + b*b*atanh(e)/e));
        } else {
            return a;
        }
    }

    /**
     * Returns the radius of the conformal sphere at a given latitude.
     * The radius of conformal sphere is computed as below:
     *
     * <blockquote>Rc = √(1 – ℯ²) / (1 – ℯ²sin²φ)  where  ℯ² = 1 - (b/a)²</blockquote>
     *
     * This is a function of latitude and therefore not constant.
     *
     * @param  ellipsoid  the ellipsoid for which to compute the radius of conformal sphere.
     * @param  φ          the latitude in radians where to compute the radius of conformal sphere.
     * @return radius of the conformal sphere at latitude φ.
     */
    public static double radiusOfConformalSphere(final Ellipsoid ellipsoid, final double φ) {
        final double sinφ = Math.sin(φ);
        final double a = ellipsoid.getSemiMajorAxis();
        final double r = ellipsoid.getSemiMinorAxis() / a;
        return a * (r / (1 - (1 - r*r) * (sinφ*sinφ)));
    }

    /**
     * Computes the semi-minor axis length from the given semi-major axis and inverse flattening factor.
     *
     * @param  semiMajorAxis      the semi-major axis length.
     * @param  inverseFlattening  the inverse flattening factor.
     * @return the semi-minor axis length.
     */
    public static double getSemiMinor(final double semiMajorAxis, final double inverseFlattening) {
        /*
         * Note: double-double arithmetic does not increase the accuracy here, unless the inverse flattening
         * factor given to this method is very high (i.e. the planet is very close to a perfect sphere).
         */
        return semiMajorAxis * (1 - 1/inverseFlattening);
    }

    /**
     * Computes the inverse flattening factor from the given axis lengths.
     *
     * @param  semiMajorAxis  the semi-major axis length.
     * @param  semiMinorAxis  the semi-minor axis length.
     * @return the inverse flattening factor.
     */
    public static double getInverseFlattening(final double semiMajorAxis, final double semiMinorAxis) {
        /*
         * Note: double-double arithmetic here sometime change the last digit. We ignore for now.
         * We may consider using double-double arithmetic in a future SIS version, not for more
         * accurate map projection but rather for being able to find back the original value after
         * we convert back and forward betwen inverse flattening and semi-minor axis length.
         */
        return semiMajorAxis / (semiMajorAxis - semiMinorAxis);
    }
}
