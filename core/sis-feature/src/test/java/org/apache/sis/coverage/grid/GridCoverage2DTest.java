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
package org.apache.sis.coverage.grid;

import java.util.Collections;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import org.opengis.geometry.DirectPosition;
import org.opengis.coverage.PointOutsideCoverageException;
import org.opengis.referencing.operation.MathTransform1D;
import org.opengis.referencing.datum.PixelInCell;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.internal.coverage.j2d.RasterFactory;
import org.apache.sis.measure.NumberRange;
import org.apache.sis.measure.Units;
import org.apache.sis.referencing.crs.HardCodedCRS;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.test.TestCase;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Tests the {@link GridCoverage2D} implementation.
 *
 * @author  Johann Sorel (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public final strictfp class GridCoverage2DTest extends TestCase {
    /**
     * Creates a {@link GridCoverage2D} instance with arbitrary sample values.
     * The image size is 2×2 pixels, the "grid to CRS" transform is identity,
     * the range of sample values is [-97.5 … 105] metres and the packed values are:
     *
     * {@preformat text
     *    2    5
     *   -5  -10
     * }
     */
    private static GridCoverage2D createTestCoverage() {
        final int size = 2;
        final GridGeometry grid = new GridGeometry(new GridExtent(size, size),
                PixelInCell.CELL_CENTER, MathTransforms.identity(2), HardCodedCRS.WGS84);

        final MathTransform1D toUnits = (MathTransform1D) MathTransforms.linear(0.5, 100);
        final SampleDimension sd = new SampleDimension.Builder().setName("Some kind of height")
                .addQuantitative("data", NumberRange.create(-10, true, 10, true), toUnits, Units.METRE)
                .build();
        /*
         * Create an image and set values directly as integers. We do not use one of the
         * BufferedImage.TYPE_* constant because this test uses some negative values.
         */
        final BufferedImage  image  = RasterFactory.createGrayScaleImage(DataBuffer.TYPE_INT, size, size, 1, 0, -10, 10);
        final WritableRaster raster = image.getRaster();
        raster.setSample(0, 0, 0,   2);
        raster.setSample(1, 0, 0,   5);
        raster.setSample(0, 1, 0,  -5);
        raster.setSample(1, 1, 0, -10);
        return new GridCoverage2D(grid, Collections.singleton(sd), image);
    }

    /**
     * Tests {@link GridCoverage2D#forConvertedValues(boolean)}.
     */
    @Test
    public void testForConvertedValues() {
        GridCoverage coverage = createTestCoverage();
        /*
         * Verify packed values.
         */
        assertSamplesEqual(coverage, new double[][] {
            { 2,   5},
            {-5, -10}
        });
        /*
         * Verify converted values.
         */
        coverage = coverage.forConvertedValues(true);
        assertSamplesEqual(coverage, new double[][] {
            {101.0, 102.5},
            { 97.5,  95.0}
        });
        /*
         * Test writing converted values and verify the result in the packed coverage.
         * For example for the sample value at (0,0), we have (p is the packed value):
         *
         *   70 = p * 0.5 + 100   →   (70-100)/0.5 = p   →   p = -60
         */
        final WritableRaster raster = ((BufferedImage) coverage.render(null)).getRaster();
        raster.setSample(0, 0, 0,  70);
        raster.setSample(1, 0, 0,   2.5);
        raster.setSample(0, 1, 0,  -8);
        raster.setSample(1, 1, 0, -90);
        assertSamplesEqual(coverage.forConvertedValues(false), new double[][] {
            { -60, -195},
            {-216, -380}
        });
    }

    /**
     * Tests {@link GridCoverage2D#evaluate(DirectPosition, double[])}.
     */
    @Test
    public void testEvaluate() {
        final GridCoverage coverage = createTestCoverage();
        /*
         * Test evaluation at indeger indices. No interpolation should be applied.
         */
        assertArrayEquals(new double[] {  2}, coverage.evaluate(new DirectPosition2D(0, 0), null), STRICT);
        assertArrayEquals(new double[] {  5}, coverage.evaluate(new DirectPosition2D(1, 0), null), STRICT);
        assertArrayEquals(new double[] { -5}, coverage.evaluate(new DirectPosition2D(0, 1), null), STRICT);
        assertArrayEquals(new double[] {-10}, coverage.evaluate(new DirectPosition2D(1, 1), null), STRICT);
        /*
         * Test evaluation at fractional indices. Current interpolation is nearest neighor rounding,
         * but future version may do a bilinear interpolation.
         */
        assertArrayEquals(new double[] {2}, coverage.evaluate(new DirectPosition2D(-0.499, -0.499), null), STRICT);
        assertArrayEquals(new double[] {2}, coverage.evaluate(new DirectPosition2D( 0.499,  0.499), null), STRICT);
        /*
         * Test some points that are outside the coverage extent.
         */
        try {
            coverage.evaluate(new DirectPosition2D(-0.51, 0), null);
            fail("Expected PointOutsideCoverageException.");
        } catch (PointOutsideCoverageException ex) {
            assertNotNull(ex.getMessage());
        }
        try {
            coverage.evaluate(new DirectPosition2D(1.51, 0), null);
            fail("Expected PointOutsideCoverageException.");
        } catch (PointOutsideCoverageException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    /**
     * Asserts that the sample values in the given coverage are equal to the expected values.
     *
     * @param  coverage  the coverage containing the sample values to check.
     * @param  expected  the expected sample values.
     */
    private static void assertSamplesEqual(final GridCoverage coverage, final double[][] expected) {
        final Raster raster = coverage.render(null).getData();
        for (int y=0; y<expected.length; y++) {
            for (int x=0; x<expected[y].length; x++) {
                double value = raster.getSampleDouble(x, y, 0);
                assertEquals(expected[y][x], value, STRICT);
            }
        }
    }
}
