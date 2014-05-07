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

import org.apache.sis.test.DependsOn;
import org.apache.sis.test.TestCase;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Tests {@link DefaultFeature}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.5
 * @version 0.5
 * @module
 */
@DependsOn({
    DefaultFeatureTypeTest.class,
    DefaultAttributeTest.class,
    PropertySingletonTest.class
})
public final strictfp class DefaultFeatureTest extends TestCase {
    /**
     * Tests the construction of a simple feature without super-types.
     */
    @Test
    public void testSimple() {
        final DefaultFeature cityPopulation = new DefaultFeature(DefaultFeatureTypeTest.cityPopulation());

        assertEquals("Utopia", cityPopulation.getAttributeValue("city"));
        cityPopulation.setAttributeValue("city", "Atlantide");
        assertEquals("Atlantide", cityPopulation.getAttributeValue("city"));

        assertNull(cityPopulation.getAttributeValue("population"));
        cityPopulation.setAttributeValue("population", 1000);
        assertEquals(1000, cityPopulation.getAttributeValue("population"));
    }
}
