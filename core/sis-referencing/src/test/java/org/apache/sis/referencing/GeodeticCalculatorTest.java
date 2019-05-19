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
package org.apache.sis.referencing;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.PathIterator;
import java.util.Arrays;
import java.util.Random;
import java.io.IOException;
import java.io.LineNumberReader;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.internal.referencing.j2d.ShapeUtilities;
import org.apache.sis.internal.referencing.Formulas;
import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.util.CharSequences;
import org.apache.sis.measure.Units;
import org.apache.sis.test.OptionalTestData;
import org.apache.sis.test.DependsOnMethod;
import org.apache.sis.test.TestUtilities;
import org.apache.sis.test.TestCase;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.junit.Test;

import static java.lang.StrictMath.*;
import static org.opengis.test.Assert.*;


/**
 * Tests {@link GeodeticCalculator}. Test values come from the following sources:
 *
 * <ul>
 *   <li><a href="https://en.wikipedia.org/wiki/Great-circle_navigation#Example">Great-circle navigation on Wikipedia.</a></li>
 *   <li><a href="http://doi.org/10.5281/zenodo.32156">Karney, C. F. F. (2010). Test set for geodesics [Data set]. Zenodo.</a></li>
 *   <li>Charles Karney's <a href="https://geographiclib.sourceforge.io/">GeographicLib</a> implementation.</li>
 * </ul>
 *
 * @version 1.0
 * @since 1.0
 * @module
 */
public final strictfp class GeodeticCalculatorTest extends TestCase {
    /**
     * Verifies that the given point is equals to the given latitude and longitude.
     *
     * @param φ  the expected latitude value, in degrees.
     * @param λ  the expected longitude value, in degrees.
     * @param p  the actual position to verify.
     * @param ε  the tolerance threshold.
     */
    private static void assertPositionEquals(final double φ, final double λ, final DirectPosition p, final double ε) {
        assertEquals("φ", φ, p.getOrdinate(0), ε);
        assertEquals("λ", λ, p.getOrdinate(1), ε);
    }

    /**
     * Asserts that a Java2D point is equal to the expected value. Used for verifying geodesic paths.
     *
     * @param x  the expected <var>x</var> coordinates.
     * @param y  the expected <var>y</var> coordinates.
     * @param p  the actual position to verify.
     * @param ε  the tolerance threshold.
     */
    private static void assertPointEquals(final double x, final double y, final Point2D p, final double ε) {
        assertEquals("x", x, p.getX(), ε);
        assertEquals("y", y, p.getY(), ε);
    }

    /**
     * Returns the calculator to use for testing purpose. This classes uses a calculator for a sphere.
     *
     * @param  normalized  whether to force (longitude, latitude) axis order.
     */
    private static GeodeticCalculator create(final boolean normalized) {
        return new GeodeticCalculator(normalized
                ? CommonCRS.SPHERE.normalizedGeographic()
                : CommonCRS.SPHERE.geographic());
    }

    /**
     * Tests some simple azimuth directions. The expected directions are approximately North, East,
     * South and West, but not exactly because of Earth curvature. The test verify merely that the
     * azimuths are approximately correct.
     */
    @Test
    public void testCardinalAzimuths() {
        final GeodeticCalculator c = create(false);
        final double tolerance = 0.2;
        c.setStartPoint(20, 12);
        c.setEndPoint(20, 13);  assertEquals("East",   90, c.getStartingAzimuth(), tolerance);
        c.setEndPoint(21, 12);  assertEquals("North",   0, c.getStartingAzimuth(), tolerance);
        c.setEndPoint(20, 11);  assertEquals("West",  -90, c.getStartingAzimuth(), tolerance);
        c.setEndPoint(19, 12);  assertEquals("South", 180, c.getStartingAzimuth(), tolerance);
    }

    /**
     * Tests azimuths at poles.
     */
    @Test
    public void testAzimuthAtPoles() {
        final GeodeticCalculator c = create(false);
        final double tolerance = 0.2;
        c.setStartPoint( 90,  30);
        c.setEndPoint  ( 20,  20);  assertEquals(-170, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  ( 20,  40);  assertEquals( 170, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  ( 20,  30);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  (-20,  30);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  (-90,  30);  assertEquals( 180, c.getStartingAzimuth(), tolerance);

        c.setStartPoint( 90,   0);
        c.setEndPoint  ( 20,  20);  assertEquals( 160, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  ( 20, -20);  assertEquals(-160, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  ( 20,   0);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
        c.setEndPoint  (-90,   0);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
    }

    /**
     * Tests geodesic distances and rhumb line length on the equator.
     */
    @Test
    public void testDistanceAtEquator() {
        final Random random = TestUtilities.createRandomNumberGenerator();
        final GeodeticCalculator c = create(false);
        final double r = c.ellipsoid.getSemiMajorAxis() * (PI / 180);
        c.setStartPoint(0, 0);
        for (int i=0; i<100; i++) {
            final double x = 360 * random.nextDouble() - 180;
            c.setEndPoint(0, x);
            final double expected = abs(x) * r;
            assertEquals("Geodesic",   expected, c.getGeodesicDistance(), Formulas.LINEAR_TOLERANCE);
            assertEquals("Rhumb line", expected, c.getRhumblineLength(),  Formulas.LINEAR_TOLERANCE);
        }
    }

    /**
     * Tests {@link GeodeticCalculator#getGeodesicDistance()} and azimuths with the example given in Wikipedia.
     * This computes the great circle route from Valparaíso (33°N 71.6W) to Shanghai (31.4°N 121.8°E).
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Great-circle_navigation#Example">Great-circle navigation on Wikipedia</a>
     */
    @Test
    public void testGeodesicDistanceAndAzimuths() throws TransformException {
        final GeodeticCalculator c = create(false);
        c.setStartPoint(-33.0, -71.6);          // Valparaíso
        c.setEndPoint  ( 31.4, 121.8);          // Shanghai
        /*
         * Wikipedia example gives:
         *
         *     Δλ = −166.6°
         *     α₁ = −94.41°
         *     α₂ = −78.42°
         *     Δσ = 168.56°   →    taking R = 6371 km, the distance is 18743 km.
         */
        assertEquals(Units.METRE,         c.getDistanceUnit());
        assertEquals("α₁",        -94.41, c.getStartingAzimuth(), 0.005);
        assertEquals("α₂",        -78.42, c.getEndingAzimuth(),   0.005);
        assertEquals("distance",   18743, c.getGeodesicDistance() / 1000, 0.5);
        assertPositionEquals(31.4, 121.8, c.getEndPoint(), 1E-12);                  // Should be the specified value.
        /*
         * Keep start point unchanged, but set above azimuth and distance.
         * Verify that we get the Shanghai coordinates.
         */
        c.setStartingAzimuth(-94.41);
        c.setGeodesicDistance(18743000);
        assertEquals("α₁",        -94.41, c.getStartingAzimuth(), 1E-12);           // Should be the specified value.
        assertEquals("α₂",        -78.42, c.getEndingAzimuth(),   0.01);
        assertEquals("distance",   18743, c.getGeodesicDistance() / 1000, STRICT);  // Should be the specified value.
        assertPositionEquals(31.4, 121.8, c.getEndPoint(), 0.01);
    }

    /**
     * Tests geodetic calculator involving a coordinate operation.
     * This test uses a simple CRS with only the axis order interchanged.
     * The coordinates are the same than {@link #testGeodesicDistanceAndAzimuths()}.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    @DependsOnMethod("testGeodesicDistanceAndAzimuths")
    public void testUsingTransform() throws TransformException {
        final GeodeticCalculator c = create(true);
        assertAxisDirectionsEqual("GeographicCRS", c.getGeographicCRS().getCoordinateSystem(), AxisDirection.NORTH, AxisDirection.EAST);
        assertAxisDirectionsEqual("PositionCRS",     c.getPositionCRS().getCoordinateSystem(), AxisDirection.EAST, AxisDirection.NORTH);
        final double φ = -33.0;
        final double λ = -71.6;
        c.setStartPoint(new DirectPosition2D(λ, φ));
        assertPositionEquals(λ, φ, c.getStartPoint(), Formulas.ANGULAR_TOLERANCE);

        c.setStartingAzimuth(-94.41);
        c.setGeodesicDistance(18743000);
        assertPositionEquals(121.8, 31.4, c.getEndPoint(), 0.01);
    }

    /**
     * Tests {@link GeodeticCalculator#toGeodesicPath2D(double)}. This method uses a CRS that swap axis order
     * as a way to verify that user-specified CRS is taken in account. The start point and end point are the
     * same than in {@link #testGeodesicDistanceAndAzimuths()}. Note that this path crosses the anti-meridian,
     * so the end point needs to be shifted by 360°.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    @DependsOnMethod("testUsingTransform")
    public void testGeodesicPath2D() throws TransformException {
        final GeodeticCalculator c = create(true);
        final double tolerance = 0.05;
        c.setStartPoint(-33.0, -71.6);                  // Valparaíso
        c.setEndPoint  ( 31.4, 121.8);                  // Shanghai
        final Shape path = c.toGeodesicPath2D(1000);
        assertPointEquals( -71.6, -33.0, ShapeUtilities.pointOnBezier(path, 0),   tolerance);
        assertPointEquals(-238.2,  31.4, ShapeUtilities.pointOnBezier(path, 1),   tolerance);       // λ₂ = 121.8° - 360°
        assertPointEquals(-159.2,  -6.8, ShapeUtilities.pointOnBezier(path, 0.5), tolerance);
    }

    /**
     * Verifies that all <var>y</var> coordinates are zero for a geodesic path on equator.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void testGeodesicPathOnEquator() throws TransformException {
        final GeodeticCalculator c = create(false);
        final double tolerance = 1E-12;
        c.setStartPoint(0, 20);
        c.setEndPoint  (0, 12);
        assertEquals(-90, c.getStartingAzimuth(), tolerance);
        assertEquals(-90, c.getEndingAzimuth(),   tolerance);
        final Shape geodeticCurve = c.toGeodesicPath2D(1);
        final double[] coords = new double[2];
        for (final PathIterator it = geodeticCurve.getPathIterator(null, 1); !it.isDone(); it.next()) {
            it.currentSegment(coords);
            assertEquals ("φ",  0, coords[0], tolerance);
            assertBetween("λ", 12, 20, coords[1]);
        }
    }

    /**
     * Tests geodesic path between random points. The coordinates are compared with values computed by
     * <a href="https://geographiclib.sourceforge.io/">GeographicLib</a>, taken as reference implementation.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void testBetweenRandomPoints() throws TransformException {
        final Random random = TestUtilities.createRandomNumberGenerator();
        final GeodeticCalculator c = create(false);
        final Geodesic reference = new Geodesic(c.ellipsoid.getSemiMajorAxis(), 1/c.ellipsoid.getInverseFlattening());
        for (int i=0; i<100; i++) {
            final double φ1 = random.nextDouble() * 180 -  90;
            final double λ1 = random.nextDouble() * 360 - 180;
            final double φ2 = random.nextDouble() * 180 -  90;
            final double Δλ = random.nextDouble() * 360 - 180;
            final double λ2 = IEEEremainder(λ1 + Δλ, 360);
            c.setStartPoint(φ1, λ1);
            c.setEndPoint  (φ2, λ2);
            final double geodesic  = c.getGeodesicDistance();
            final double rhumbLine = c.getRhumblineLength();
            final GeodesicData expected = reference.Inverse(φ1, λ1, φ2, λ2);
            assertEquals("Geodesic distance", expected.s12,  geodesic,               Formulas.LINEAR_TOLERANCE);
            assertEquals("Starting azimuth",  expected.azi1, c.getStartingAzimuth(), Formulas.ANGULAR_TOLERANCE);
            assertEquals("Ending azimuth",    expected.azi2, c.getEndingAzimuth(),   Formulas.ANGULAR_TOLERANCE);
            assertTrue  ("Rhumb ≧ geodesic",  rhumbLine >= geodesic);
            if (false) {
                // Disabled because currently too inaccurate - see https://issues.apache.org/jira/browse/SIS-453
                assertEquals("Distance measured along geodesic path", geodesic, length(c), Formulas.ANGULAR_TOLERANCE);
            }
        }
    }

    /**
     * Measures an estimation of the length of the path returned by {@link GeodeticCalculator#toGeodesicPath2D(double)}.
     * This method iterates over line segments and use the given calculator for computing the geodesic distance of each
     * segment. The state of the given calculator is modified by this method.
     */
    private static double length(final GeodeticCalculator c) throws TransformException {
        final PathIterator iterator = c.toGeodesicPath2D(10).getPathIterator(null, 100);
        final double[] buffer = new double[2];
        double length=0;
        while (!iterator.isDone()) {
            switch (iterator.currentSegment(buffer)) {
                default: fail("Unexpected path"); break;
                case PathIterator.SEG_MOVETO: {
                    c.setStartPoint(buffer[0], buffer[1]);
                    break;
                }
                case PathIterator.SEG_LINETO: {
                    c.setEndPoint(buffer[0], buffer[1]);
                    length += c.getGeodesicDistance();
                    c.moveToEndPoint();
                }
            }
            iterator.next();
        }
        return length;
    }

    /**
     * Compares computations against values provided in <cite>Karney (2010) Test set for geodesics</cite>.
     * This is an optional test executed only if the {@code $SIS_DATA/Tests/GeodTest.dat} file is found.
     *
     * @throws IOException if an error occurred while reading the test file.
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void compareAgainstDataset() throws IOException, TransformException {
        try (LineNumberReader reader = OptionalTestData.GEODESIC.reader()) {
            final GeodeticCalculator c = new GeodeticCalculator(CommonCRS.WGS84.geographic());
            final Geodesic reference = new Geodesic(Formulas.getAuthalicRadius(c.ellipsoid), 0);
            final Random random = TestUtilities.createRandomNumberGenerator();
            final double[] data = new double[7];
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    Arrays.fill(data, Double.NaN);
                    final CharSequence[] split = CharSequences.split(line, ' ');
                    for (int i=min(split.length, data.length); --i >= 0;) {
                        data[i] = Double.parseDouble(split[i].toString());
                    }
                    /*
                     * We aim for an 1 cm accuracy. However when spherical formulas are used instead
                     * than ellipsoidal formulas, an error up to 1% is expected (Wikipedia).
                     */
                    final double tolerance = data[6] * 0.01;                // 1% of distance.
                    final double cosφ = abs(cos(toRadians(data[3])));       // For adjusting longitude tolerance.
                    c.setStartPoint(data[0], data[1]);                      // (φ₁, λ₁)
                    if (random.nextBoolean()) {
                        /*
                         * Computes the end point from a distance and azimuth. The angular tolerance
                         * is derived from the linear tolerance, except at pole where we disable the
                         * check of longitude and azimuth values.
                         */
                        c.setStartingAzimuth (data[2]);
                        c.setGeodesicDistance(data[6]);
                        final double latitudeTolerance = tolerance * (1d/6371007 * (180/PI));
                        final double longitudeTolerance;
                        if (data[3] > 89.5) {
                            longitudeTolerance = 180;               // TODO: remove after we use spherical formulas.
                        } else {
                            longitudeTolerance = latitudeTolerance / cosφ;
                        }
                        final double azimuthTolerance = 0.5 / cosφ;
                        compareGeodeticData(data, c, latitudeTolerance, longitudeTolerance,
                                            azimuthTolerance, Formulas.LINEAR_TOLERANCE);
                        /*
                         * Replace the distance and azimuth values by values computed using spherical formulas,
                         * then compare again with values computed by GeodeticCalculator but with low tolerance.
                         */
                        final GeodesicData gd = reference.Direct(data[0], data[1], data[2], data[6]);
                        data[3] = gd.lat2;
                        data[4] = gd.lon2;
                        data[5] = gd.azi2;
                    } else {
                        /*
                         * Compute the distance and azimuth values between two points. We perform
                         * this test or the above test randomly instead of always executing both
                         * of them for making sure that GeodeticCalculator never see the expected
                         * values.
                         */
                        c.setEndPoint(data[3], data[4]);            // (φ₂, λ₂)
                        compareGeodeticData(data, c,
                                Formulas.ANGULAR_TOLERANCE,         // Latitude tolerance
                                Formulas.ANGULAR_TOLERANCE,         // Longitude tolerance
                                100 / cosφ, tolerance);             // Azimuth is inaccurate for reason not yet identified.
                        /*
                         * Replace the distance and azimuth values by values computed using spherical formulas,
                         * then compare again with values computed by GeodeticCalculator but with low tolerance.
                         */
                        final GeodesicData gd = reference.Inverse(data[0], data[1], data[3], data[4]);
                        data[2] = gd.azi1;
                        data[5] = gd.azi2;
                        data[6] = gd.s12;
                    }
                    compareGeodeticData(data, c, Formulas.ANGULAR_TOLERANCE,
                            Formulas.ANGULAR_TOLERANCE, 1E-4, Formulas.LINEAR_TOLERANCE);
                }
            } catch (AssertionError e) {
                out.printf("Test failure at line %d%nGeodetic calculator is:%n", reader.getLineNumber());
                out.println(c);
                throw e;
            }
        }
    }

    /**
     * Verifies that geodetic calculator results are equal to the given values.
     * Order in the {@code data} array is as documented in {@link OptionalTestData#GEODESIC}.
     */
    private static void compareGeodeticData(final double[] expected, final GeodeticCalculator c,
            final double latitudeTolerance, final double longitudeTolerance, final double azimuthTolerance,
            final double linearTolerance) throws TransformException
    {
        final DirectPosition start = c.getStartPoint();
        final DirectPosition end   = c.getEndPoint();
        assertEquals("φ₁",  expected[0], start.getOrdinate(0),    Formulas.ANGULAR_TOLERANCE);
        assertEquals("λ₁",  expected[1], start.getOrdinate(1),    Formulas.ANGULAR_TOLERANCE);
        assertEquals("α₁",  expected[2], c.getStartingAzimuth(),  azimuthTolerance);
        assertEquals("φ₂",  expected[3], end.getOrdinate(0),      latitudeTolerance);
        assertEquals("λ₂",  expected[4], end.getOrdinate(1),      longitudeTolerance);
        assertEquals("α₂",  expected[5], c.getEndingAzimuth(),    azimuthTolerance);
        assertEquals("s₁₂", expected[6], c.getGeodesicDistance(), linearTolerance);
    }
}