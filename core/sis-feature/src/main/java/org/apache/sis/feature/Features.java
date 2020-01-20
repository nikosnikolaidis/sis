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
package org.apache.sis.feature;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.opengis.feature.Attribute;
import org.opengis.feature.AttributeType;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureAssociationRole;
import org.opengis.feature.FeatureType;
import org.opengis.feature.IdentifiedType;
import org.opengis.feature.InvalidPropertyValueException;
import org.opengis.feature.Operation;
import org.opengis.feature.PropertyNotFoundException;
import org.opengis.feature.PropertyType;
import org.opengis.metadata.maintenance.ScopeCode;
import org.opengis.metadata.quality.ConformanceResult;
import org.opengis.metadata.quality.DataQuality;
import org.opengis.metadata.quality.Element;
import org.opengis.metadata.quality.Result;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.GenericName;
import org.opengis.util.InternationalString;
import org.opengis.util.NameFactory;

import org.apache.sis.internal.feature.AttributeConvention;
import org.apache.sis.internal.feature.Resources;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.util.Static;
import org.apache.sis.util.iso.DefaultNameFactory;
import org.apache.sis.util.logging.Logging;

// Branch-dependent imports


/**
 * Static methods working on features or attributes.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @author  Johann Sorel (Geomatys)
 * @author  Alexis Manin (Geomatys)
 * @version 1.1
 * @since   0.5
 * @module
 */
public final class Features extends Static {

    /**
     * A test to know if a given property is an SIS convention or not. Return true if
     * the property is NOT marked as an SIS convention, false otherwise.
     */
    private static final Predicate<IdentifiedType> IS_NOT_CONVENTION = p -> !AttributeConvention.contains(p.getName());

    /**
     * Do not allow instantiation of this class.
     */
    private Features() {
    }

    /**
     * Casts the given attribute type to the given parameterized type.
     * An exception is thrown immediately if the given type does not have the expected
     * {@linkplain DefaultAttributeType#getValueClass() value class}.
     *
     * @param  <V>         the expected value class.
     * @param  type        the attribute type to cast, or {@code null}.
     * @param  valueClass  the expected value class.
     * @return the attribute type casted to the given value class, or {@code null} if the given type was null.
     * @throws ClassCastException if the given attribute type does not have the expected value class.
     *
     * @category verification
     */
    @SuppressWarnings("unchecked")
    public static <V> AttributeType<V> cast(final AttributeType<?> type, final Class<V> valueClass)
            throws ClassCastException
    {
        if (type != null) {
            final Class<?> actual = type.getValueClass();
            /*
             * We require a strict equality - not type.isAssignableFrom(actual) - because in
             * the later case we could have (to be strict) to return a <? extends V> type.
             */
            if (!valueClass.equals(actual)) {
                throw new ClassCastException(Resources.format(Resources.Keys.MismatchedValueClass_3,
                        type.getName(), valueClass, actual));
            }
        }
        return (AttributeType<V>) type;
    }

    /**
     * Casts the given attribute instance to the given parameterized type.
     * An exception is thrown immediately if the given instance does not have the expected
     * {@linkplain DefaultAttributeType#getValueClass() value class}.
     *
     * @param  <V>         the expected value class.
     * @param  attribute   the attribute instance to cast, or {@code null}.
     * @param  valueClass  the expected value class.
     * @return the attribute instance casted to the given value class, or {@code null} if the given instance was null.
     * @throws ClassCastException if the given attribute instance does not have the expected value class.
     *
     * @category verification
     */
    @SuppressWarnings("unchecked")
    public static <V> Attribute<V> cast(final Attribute<?> attribute, final Class<V> valueClass)
            throws ClassCastException
    {
        if (attribute != null) {
            final Class<?> actual = attribute.getType().getValueClass();
            /*
             * We require a strict equality - not type.isAssignableFrom(actual) - because in
             * the later case we could have (to be strict) to return a <? extends V> type.
             */
            if (!valueClass.equals(actual)) {
                throw new ClassCastException(Resources.format(Resources.Keys.MismatchedValueClass_3,
                        attribute.getName(), valueClass, actual));
            }
        }
        return (Attribute<V>) attribute;
    }

    /**
     * Returns the given type as an {@link AttributeType} by casting if possible, or by getting the result type
     * of an operation. More specifically this method returns the first of the following types which apply:
     *
     * <ul>
     *   <li>If the given type is an instance of {@link AttributeType}, then it is returned as-is.</li>
     *   <li>If the given type is an instance of {@link Operation} and the {@linkplain Operation#getResult()
     *       result type} is an {@link AttributeType}, then that result type is returned.</li>
     *   <li>If the given type is an instance of {@link Operation} and the {@linkplain Operation#getResult()
     *       result type} is another operation, then the above check is performed recursively.</li>
     * </ul>
     *
     * @param  type  the data type to express as an attribute type.
     * @return the attribute type, or empty if this method cannot find any.
     *
     * @since 1.1
     */
    public static Optional<AttributeType<?>> toAttribute(IdentifiedType type) {
        if (!(type instanceof AttributeType<?>)) {
            if (!(type instanceof Operation)) {
                return Optional.empty();
            }
            type = ((Operation) type).getResult();
            if (!(type instanceof AttributeType<?>)) {
                if (!(type instanceof Operation)) {
                    return Optional.empty();
                }
                /*
                 * Operation returns another operation. This case should be rare and should never
                 * contain a cycle. However given that the consequence of an infinite cycle here
                 * would be thread freeze, we check as a safety.
                 */
                final Map<IdentifiedType,Boolean> done = new IdentityHashMap<>(4);
                while (!((type = ((Operation) type).getResult()) instanceof AttributeType<?>)) {
                    if (!(type instanceof Operation) || done.put(type, Boolean.TRUE) != null) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.of((AttributeType<?>) type);
    }

    /**
     * Finds a feature type common to all given types, or returns {@code null} if none is found.
     * The return value is either one of the given types, or a parent common to all types.
     * A feature <var>F</var> is considered a common parent if <code>F.{@link DefaultFeatureType#isAssignableFrom
     * isAssignableFrom}(type)</code> returns {@code true} for all elements <var>type</var> in the given array.
     *
     * @param  types  types for which to find a common type, or {@code null}.
     * @return a feature type which is assignable from all given types, or {@code null} if none.
     *
     * @see FeatureType#isAssignableFrom(FeatureType)
     *
     * @since 1.0
     */
    public static FeatureType findCommonParent(final Iterable<? extends FeatureType> types) {
        return (types != null) ? CommonParentFinder.select(types) : null;
    }

    /**
     * Returns the type of values provided by the given property. For {@linkplain AttributeType attributes}
     * (which is the most common case), the value type is given by {@link AttributeType#getValueClass()}.
     * For {@linkplain FeatureAssociationRole feature associations}, the value type is {@link Feature}.
     * For {@linkplain Operation operations}, the value type is determined recursively from the
     * {@linkplain Operation#getResult() operation result}.
     * If the value type can not be determined, then this method returns {@code null}.
     *
     * @param  type  the property for which to get the type of values, or {@code null}.
     * @return the type of values provided by the given property, or {@code null} if unknown.
     *
     * @see AttributeType#getValueClass()
     *
     * @since 1.0
     */
    public static Class<?> getValueClass(PropertyType type) {
        while (type instanceof Operation) {
            final IdentifiedType result = ((Operation) type).getResult();
            if (result != type && result instanceof PropertyType) {
                type = (PropertyType) result;
            } else if (result instanceof FeatureType) {
                return Feature.class;
            } else {
                break;
            }
        }
        if (type instanceof AttributeType<?>) {
            return ((AttributeType<?>) type).getValueClass();
        } else if (type instanceof FeatureAssociationRole) {
            return Feature.class;
        } else {
            return null;
        }
    }

    /**
     * Returns the name of the type of values that the given property can take.
     * The type of value can be a {@link Class}, a {@link org.opengis.feature.FeatureType}
     * or another {@code PropertyType} depending on given argument:
     *
     * <ul>
     *   <li>If {@code property} is an {@link AttributeType}, then this method gets the
     *       {@linkplain DefaultAttributeType#getValueClass() value class} and
     *       {@linkplain DefaultNameFactory#toTypeName(Class) maps that class to a name}.</li>
     *   <li>If {@code property} is a {@link FeatureAssociationRole}, then this method gets
     *       the name of the {@linkplain DefaultAssociationRole#getValueType() value type}.
     *       This methods can work even if the associated {@code FeatureType} is not yet resolved.</li>
     *   <li>If {@code property} is an {@link Operation}, then this method returns the name of the
     *       {@linkplain AbstractOperation#getResult() result type}.</li>
     * </ul>
     *
     * @param  property  the property for which to get the name of value type.
     * @return the name of value type, or {@code null} if none.
     *
     * @since 0.8
     */
    public static GenericName getValueTypeName(final PropertyType property) {
        if (property instanceof FeatureAssociationRole) {
            // Tested first because this is the main interest for this method.
            return DefaultAssociationRole.getValueTypeName((FeatureAssociationRole) property);
        } else if (property instanceof AttributeType<?>) {
            final DefaultNameFactory factory = DefaultFactories.forBuildin(NameFactory.class, DefaultNameFactory.class);
            return factory.toTypeName(((AttributeType<?>) property).getValueClass());
        } else if (property instanceof Operation) {
            final IdentifiedType result = ((Operation) property).getResult();
            if (result != null) {
                return result.getName();
            }
        }
        return null;
    }

    /**
     * Ensures that all characteristics and property values in the given feature are valid.
     * An attribute is valid if it contains a number of values between the
     * {@linkplain DefaultAttributeType#getMinimumOccurs() minimum} and
     * {@linkplain DefaultAttributeType#getMaximumOccurs() maximum number of occurrences} (inclusive),
     * all values are instances of the expected {@linkplain DefaultAttributeType#getValueClass() value class},
     * and the attribute is compliant with any other restriction that the implementation may add.
     *
     * <p>This method gets a quality report as documented in the {@link AbstractFeature#quality()} method
     * and verifies that all {@linkplain org.apache.sis.metadata.iso.quality.DefaultConformanceResult#pass()
     * conformance tests pass}. If at least one {@code ConformanceResult.pass} attribute is false, then an
     * {@code InvalidPropertyValueException} is thrown. Otherwise this method returns doing nothing.
     *
     * @param  feature  the feature to validate, or {@code null}.
     * @throws InvalidPropertyValueException if the given feature is non-null and does not pass validation.
     *
     * @since 0.7
     */
    public static void validate(final Feature feature) throws InvalidPropertyValueException {
        if (feature != null) {
            /*
             * Delegate to AbstractFeature.quality() if possible because the user may have overridden the method.
             * Otherwise fallback on the same code than AbstractFeature.quality() default implementation.
             */
            final DataQuality quality;
            if (feature instanceof AbstractFeature) {
                quality = ((AbstractFeature) feature).quality();
            } else {
                final Validator v = new Validator(ScopeCode.FEATURE);
                v.validate(feature.getType(), feature);
                quality = v.quality;
            }
            /*
             * Loop on quality elements and check conformance results.
             * NOTE: other types of result are ignored for now, since those other
             * types may require threshold and other information to be evaluated.
             */
            for (Element element : quality.getReports()) {
                for (Result result : element.getResults()) {
                    if (result instanceof ConformanceResult) {
                        if (Boolean.FALSE.equals(((ConformanceResult) result).pass())) {
                            final InternationalString message = ((ConformanceResult) result).getExplanation();
                            if (message != null) {
                                throw new InvalidFeatureException(message);
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Search for the main geometric property in the given type. We'll search
     * for an SIS convention first (see
     * {@link AttributeConvention#GEOMETRY_PROPERTY}. If no convention is set on
     * the input type, we'll check if it contains a single geometric property.
     * If it's the case, we return it. Otherwise (no or multiple geometries), we
     * throw an exception.
     *
     * @param type The data type to search into.
     * @return The main geometric property we've found. It will never be null. If no geometric property is found, an
     * exception is thrown.
     * @throws PropertyNotFoundException If no geometric property is available
     * in the given type.
     * @throws IllegalStateException If no convention is set (see
     * {@link AttributeConvention#GEOMETRY_PROPERTY}), and we've found more than
     * one geometry.
     */
    public static PropertyType getDefaultGeometry(final FeatureType type) throws PropertyNotFoundException, IllegalStateException {
        PropertyType geometry;
        try {
            geometry = type.getProperty(AttributeConvention.GEOMETRY_PROPERTY.toString());
        } catch (PropertyNotFoundException e) {
            try {
                geometry = searchForGeometry(type);
            } catch (RuntimeException e2) {
                e2.addSuppressed(e);
                throw e2;
            }
        }

        return geometry;
    }

    /**
     * Search for a geometric attribute outside SIS conventions. More accurately,
     * we expect the given type to have a single geometry attribute. If many are
     * found, an exception is thrown.
     *
     * @param type The data type to search into.
     * @return The only geometric property we've found.
     * @throws PropertyNotFoundException If no geometric property is available in
     * the given type.
     * @throws IllegalStateException If we've found more than one geometry.
     */
    private static PropertyType searchForGeometry(final FeatureType type) throws PropertyNotFoundException, IllegalStateException {
        final List<? extends PropertyType> geometries = type.getProperties(true).stream()
                .filter(IS_NOT_CONVENTION)
                .filter(AttributeConvention::isGeometryAttribute)
                .collect(Collectors.toList());

        if (geometries.size() < 1) {
            throw new PropertyNotFoundException("No geometric property can be found outside of sis convention.");
        } else if (geometries.size() > 1) {
            throw new IllegalStateException("Multiple geometries found. We don't know which one to select.");
        } else {
            return geometries.get(0);
        }
    }

    /**
     * Test if given property type is an attribute as defined by {@link AttributeType}, or if it produces one as an
     * {@link Operation#getResult() operation result}. It it is, we return the found attribute.
     *
     * @param input the data type to unravel the attribute from.
     * @return The found attribute or an empty shell if we cannot find any.
     */
    public static Optional<AttributeType<?>> castOrUnwrap(IdentifiedType input) {
        // In case an operation also implements attribute type, we check it first.
        // TODO : cycle detection ?
        while (!(input instanceof AttributeType) && input instanceof Operation) {
            input = ((Operation) input).getResult();
        }

        if (input instanceof AttributeType) {
            return Optional.of((AttributeType) input);
        }

        return Optional.empty();
    }

    /**
     * Search for a specific characteristic in given property. Note that if the property is not an attribute, we'll try
     * to get one for the search (see {@link #castOrUnwrap(IdentifiedType)} for more details).
     *
     * @param type The property to search into.
     * @param characteristicName The name of the searched characteristic.
     * @return Found characteristic, or nothing if we cannot find any matching given name.
     */
    public static Optional<AttributeType> getCharacteristic(PropertyType type, String characteristicName) {
        return castOrUnwrap(type)
                .map(attr -> attr.characteristics().get(characteristicName));
    }

    /**
     * Search for a characteristic value in a given property. For more complete information, you can get the complete
     * characteristic definition through {@link #getCharacteristic(PropertyType, String)}.
     *
     * @param type The property to search into
     * @param characteristicName Name of the characteristic to get a value for.
     * @param <T> Expected type for characteristics values. Be careful, if using a wrong type, an error could occur on
     *           execution.
     * @return The default value of the characteristic if we've found it, or an empty shell if the characteristic does
     * not exists or has no default value.
     * @throws ClassCastException If a value is found, but does not match specified data type.
     */
    public static <T> Optional<T> getCharacteristicValue(PropertyType type, String characteristicName) {
        return getCharacteristic(type, characteristicName)
                .map(characteristic -> (T) characteristic.getDefaultValue());
    }

    /**
     * Extract the coordinate reference system associated to the primary geometry of input data type.
     *
     * @implNote
     * Primary geometry is determined using {@link #getDefaultGeometry(org.opengis.feature.FeatureType) }.
     *
     * @param type The data type to extract reference system from.
     * @return The CRS associated to the default geometry of this data type, or
     * an empty value if we cannot determine what is the primary geometry of the
     * data type. Note that an empty value is also returned if a geometry property
     * is found, but no CRS characteristics is associated with it.
     */
    public static Optional<CoordinateReferenceSystem> getDefaultCrs(FeatureType type) {
        try {
            return getDefaultCrs(getDefaultGeometry(type));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Logging.getLogger("org.apache.sis.feature").log(Level.FINE, "Cannot extract CRS from type, cause no default geometry is available", ex);
            //no default geometry property
            return Optional.empty();
        }
    }

    /**
     * Extract CRS characteristic if it exists.
     *
     * @param type The property that we want information for.
     * @return If any Coordinate reference system characteristic (as defined by {@link AttributeConvention#CRS_CHARACTERISTIC SIS convention})
     * is available, its default value is returned. Otherwise, nothing.
     */
    public static Optional<CoordinateReferenceSystem> getDefaultCrs(PropertyType type) {
        return getCharacteristicValue(type, AttributeConvention.CRS_CHARACTERISTIC.toString());
    }
}
