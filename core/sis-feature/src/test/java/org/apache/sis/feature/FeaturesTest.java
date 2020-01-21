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

import java.util.Collections;
import java.util.Optional;

import org.opengis.feature.AttributeType;
import org.opengis.feature.Feature;
import org.opengis.feature.InvalidPropertyValueException;
import org.opengis.feature.Operation;
import org.opengis.feature.PropertyNotFoundException;
import org.opengis.feature.PropertyType;
import org.opengis.metadata.acquisition.GeometryType;

import org.apache.sis.feature.builder.AttributeRole;
import org.apache.sis.feature.builder.AttributeTypeBuilder;
import org.apache.sis.feature.builder.FeatureTypeBuilder;
import org.apache.sis.test.DependsOn;
import org.apache.sis.test.TestCase;

import org.junit.Test;

import static org.apache.sis.feature.CharacteristicTypeMapTest.temperature;
import static org.apache.sis.feature.DefaultAttributeTypeTest.city;
import static org.apache.sis.feature.DefaultAttributeTypeTest.parliament;
import static org.apache.sis.feature.DefaultFeatureTypeTest.capital;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// Branch-dependent imports

/**
 * Tests {@link Features}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @author  Johann Sorel (Geomatys)
 * @version 0.7
 * @since   0.5
 * @module
 */
@DependsOn(SingletonAttributeTest.class)
public final strictfp class FeaturesTest extends TestCase {
    /**
     * Tests {@link Features#cast(AttributeType, Class)}.
     */
    @Test
    public void testCastAttributeType() {
        final DefaultAttributeType<String> parliament = parliament();
        assertSame(parliament, Features.cast(parliament, String.class));
        try {
            Features.cast(parliament, CharSequence.class);
            fail("Shall not be allowed to cast to a different type.");
        } catch (ClassCastException e) {
            final String message = e.getMessage();
            assertTrue(message, message.contains("parliament"));
            assertTrue(message, message.contains("String"));
            assertTrue(message, message.contains("CharSequence"));
        }
    }

    /**
     * Tests {@link Features#cast(Attribute, Class)}.
     */
    @Test
    public void testCastAttributeInstance() {
        final AbstractAttribute<String> parliament = SingletonAttributeTest.parliament();
        assertSame(parliament, Features.cast(parliament, String.class));
        try {
            Features.cast(parliament, CharSequence.class);
            fail("Shall not be allowed to cast to a different type.");
        } catch (ClassCastException e) {
            final String message = e.getMessage();
            assertTrue(message, message.contains("parliament"));
            assertTrue(message, message.contains("String"));
            assertTrue(message, message.contains("CharSequence"));
        }
    }

    /**
     * Tests {@link Features#validate(Feature)}.
     */
    @Test
    public void testValidate() {
        final Feature feature = DefaultFeatureTypeTest.city().newInstance();

        // Should not pass validation.
        try {
            Features.validate(feature);
            fail("Feature is invalid because of missing property “population”. Validation should have raised an exception.");
        } catch (InvalidPropertyValueException ex) {
            String message = ex.getMessage();
            assertTrue(message, message.contains("city") || message.contains("population"));
        }

        // Should pass validation.
        feature.setPropertyValue("city", "Utopia");
        feature.setPropertyValue("population", 10);
        Features.validate(feature);
    }

    @Test
    public void unwrapAttribute() {
        final Operation link = new LinkOperation(Collections.singletonMap("name", "link"), city());
        AttributeType<?> attr = Features.castOrUnwrap(link)
                .orElseThrow(() -> new AssertionError("Attribute result of link operation has not been found"));
        assertEquals("Found attribute should the link target", city(), attr);

        final LinkOperation overLink = new LinkOperation(Collections.singletonMap("name", "linkOfLink"), link);
        attr = Features.castOrUnwrap(overLink)
                .orElseThrow(() -> new AssertionError("Attribute result of link operation has not been found"));

        assertEquals("Found attribute should the link target", city(), attr);

        attr = Features.castOrUnwrap(city())
                .orElseThrow(() -> new AssertionError("Given attribute should be returned directly"));
        assertEquals("Attribute should be returned directly", attr, city());
    }

    @Test
    public void getCharacteristicAndItsValue() {
        final String characteristicName = "units";
        Optional<AttributeType> units = Features.getCharacteristic(temperature(), characteristicName);
        assertTrue("We should have found a characteristic for "+characteristicName, units.isPresent());
        assertEquals("Characteristic name", characteristicName, units.get().getName().tip().toString());

        // Even through a link, we should be able to find the characteristic
        final LinkOperation link = new LinkOperation(Collections.singletonMap("name", "link for temperature"), temperature());
        units = Features.getCharacteristic(link, characteristicName);
        assertTrue("We should have found a characteristic for "+characteristicName, units.isPresent());
        assertEquals("Characteristic name", characteristicName, units.get().getName().tip().toString());

        // Now we'll check the commodity method to get directly the value
        Optional<String> unitValue = Features.getCharacteristicValue(link, characteristicName);
        assertTrue("We should have found a value for characteristic: "+characteristicName, unitValue.isPresent());
        assertEquals("Unit value", "°C", unitValue.get());
    }

    @Test
    public void getDefaultGeometry() {
        try {
            final PropertyType found = Features.getDefaultGeometry(capital());
            fail("Utility method should fail on given feature type because no geometry is available in it, but returned: "+found);
        } catch (PropertyNotFoundException e) {
            // That's expected behavior
        }

        final FeatureTypeBuilder builder = new FeatureTypeBuilder()
                .setName("CapitalPosition")
                .setSuperTypes(capital());

        final AttributeTypeBuilder<?> geomBuilder = builder.addAttribute(GeometryType.POINT).setName("location");

        final PropertyType geometry = Features.getDefaultGeometry(builder.build());
        assertNotNull("Should have found the location", geometry);
        assertEquals("Found attribute: ", "location", geometry.getName().tip().toString());

        builder.addAttribute(GeometryType.POINT)
                .setName("parliament.location");

        try {
            final PropertyType found = Features.getDefaultGeometry(builder.build());
            fail("Ambiguity (two geometries available without convention) should prevent a result, but got: "+found);
        } catch (IllegalStateException e) {
            // Expected behavior
        }

        // We should be able to choose the SIS convention by default.
        geomBuilder.addRole(AttributeRole.DEFAULT_GEOMETRY);
        final PropertyType found = Features.getDefaultGeometry(builder.build());
        assertNotNull("Should have found the location", geometry);
        assertEquals("Found attribute: ", "location", geometry.getName().tip().toString());
    }
}
