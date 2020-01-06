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
package org.apache.sis.internal.coverage.j2d;

import java.awt.color.ColorSpace;
import org.apache.sis.internal.util.Numerics;
import org.apache.sis.util.Debug;
import org.apache.sis.util.collection.WeakHashSet;


/**
 * Color space for images storing pixels as real numbers. The color space can have an
 * arbitrary number of bands, but in current implementation only one band is used.
 * Current implementation create a gray scale.
 *
 * <p>The use of this color space is very slow.
 * It should be used only when no standard color space can be used.</p>
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @version 1.1
 *
 * @see ColorModelFactory#createColorSpace(int, int, double, double)
 *
 * @since 1.0
 * @module
 */
final class ScaledColorSpace extends ColorSpace {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = 438226855772441165L;

    /**
     * Shared instances of {@link ScaledColorSpace}s.
     */
    private static final WeakHashSet<ScaledColorSpace> POOL = new WeakHashSet<>(ScaledColorSpace.class);

    /**
     * Minimal normalized RGB value.
     */
    private static final float MIN_VALUE = 0f;

    /**
     * Maximal normalized RGB value.
     */
    private static final float MAX_VALUE = 1f;

    /**
     * The scaling factor from sample values to RGB normalized values.
     */
    private final float scale;

    /**
     * The offset to subtract from sample values before to apply the {@linkplain #scale} factor.
     */
    private final float offset;

    /**
     * Index of the band to display.
     */
    private final int visibleBand;

    /**
     * Creates a color model for the given range of values.
     * Callers should invoke {@link #unique()} on the newly created instance.
     *
     * @param  numComponents  the number of components.
     * @param  visibleBand    the band to use for computing colors.
     * @param  minimum        the minimal sample value expected.
     * @param  maximum        the maximal sample value expected.
     */
    ScaledColorSpace(final int numComponents, final int visibleBand, final double minimum, final double maximum) {
        super(TYPE_GRAY, numComponents);
        this.visibleBand = visibleBand;
        final double scale  = (MAX_VALUE - MIN_VALUE) / (maximum - minimum);
        this.scale  = (float) scale;
        this.offset = (float) (minimum - MIN_VALUE / scale);
    }

    /**
     * Returns a RGB color for a sample value.
     *
     * @param  samples  sample values in the raster.
     * @return color as normalized RGB values between 0 and 1.
     */
    @Override
    public float[] toRGB(final float[] samples) {
        float value = (samples[visibleBand] - offset) * scale;
        if (!(value >= MIN_VALUE)) {                            // Use '!' for catching NaN.
            value = MIN_VALUE;
        } else if (value > MAX_VALUE) {
            value = MAX_VALUE;
        }
        return new float[] {value, value, value};
    }

    /**
     * Returns a sample value for the specified RGB color.
     *
     * @param  color  normalized RGB values between 0 and 1.
     * @return sample values in the raster.
     */
    @Override
    public float[] fromRGB(final float[] color) {
        final float[] values = new float[getNumComponents()];
        values[visibleBand] = (color[0] + color[1] + color[2]) / (3 * scale) + offset;
        return values;
    }

    /**
     * Returns a CIEXYZ color for a sample value.
     *
     * @param  values  sample values in the raster.
     * @return color as normalized CIEXYZ values between 0 and 1.
     */
    @Override
    public float[] toCIEXYZ(final float[] values) {
        final float[] codes = toRGB(values);
        codes[0] *= 0.9642f;
        codes[2] *= 0.8249f;
        return codes;
    }

    /**
     * Returns a sample value for the specified CIEXYZ color.
     *
     * @param  color  normalized CIEXYZ values between 0 and 1.
     * @return sample values in the raster.
     */
    @Override
    public float[] fromCIEXYZ(final float[] color) {
        final float[] values = new float[getNumComponents()];
        values[visibleBand] = (color[0] / 0.9642f + color[1] + color[2] / 0.8249f) / (3 * scale) + offset;
        return values;
    }

    /**
     * Returns the minimum value for the specified RGB component.
     *
     * @param  component  the component index.
     * @return minimum normalized component value.
     */
    @Override
    public float getMinValue(final int component) {
        return MIN_VALUE / scale + offset;
    }

    /**
     * Returns the maximum value for the specified RGB component.
     *
     * @param  component  the component index.
     * @return maximum normalized component value.
     */
    @Override
    public float getMaxValue(final int component) {
        return MAX_VALUE / scale + offset;
    }

    /**
     * Returns a string representation of this color model.
     *
     * @return a string representation for debugging purpose.
     */
    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder(20).append(getClass().getSimpleName());
        formatRange(buffer);
        return buffer.toString();
    }

    /**
     * Formats the range of values in the given buffer.
     * This method is used for {@link #toString()} implementation and may change in any future version.
     *
     * @param  buffer  where to append the range of values.
     */
    @Debug
    final void formatRange(final StringBuilder buffer) {
        buffer.append('[').append(getMinValue(visibleBand))
            .append(" … ").append(getMaxValue(visibleBand))
            .append(" in band ").append(visibleBand).append(']');
    }

    /**
     * Returns a unique instance of this color space. May be {@code this}.
     */
    final ScaledColorSpace unique() {
        return POOL.unique(this);
    }

    /**
     * Returns a hash code value for this color model.
     * Defined for implementation of {@link #unique()}.
     */
    @Override
    public int hashCode() {
        return Float.floatToIntBits(scale) + 31 * Float.floatToIntBits(offset) + 7 * getNumComponents() + visibleBand;
    }

    /**
     * Compares this color space with the given object for equality.
     * Defined for implementation of {@link #unique()}.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof ScaledColorSpace) {
            final ScaledColorSpace that = (ScaledColorSpace) obj;
            return Numerics.equals(scale,  that.scale)  &&
                   Numerics.equals(offset, that.offset) &&
                   visibleBand         ==  that.visibleBand &&
                   getNumComponents()  ==  that.getNumComponents() &&
                   getType()           ==  that.getType();
        }
        return false;
    }
}