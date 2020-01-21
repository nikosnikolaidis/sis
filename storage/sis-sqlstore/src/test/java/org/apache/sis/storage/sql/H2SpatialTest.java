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
package org.apache.sis.storage.sql;

import java.sql.Connection;
import java.util.logging.Level;

import org.opengis.feature.FeatureType;
import org.opengis.feature.PropertyType;
import org.opengis.geometry.Envelope;

import org.apache.sis.feature.Features;
import org.apache.sis.geometry.AbstractEnvelope;
import org.apache.sis.geometry.Envelope2D;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.FeatureSet;
import org.apache.sis.storage.StorageConnector;
import org.apache.sis.test.sql.TestDatabase;
import org.apache.sis.util.logging.Logging;

import org.h2gis.functions.factory.H2GISFunctions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class H2SpatialTest {

    private static TestDatabase db;
    private static SQLStore store;

    @BeforeAll
    public static void createDatabase() throws Exception {
        db = TestDatabase.createOnH2("spatialFunctions", false);
        try (Connection c = db.source.getConnection()) {
            H2GISFunctions.load(c);
        }
        db.executeSQL(H2SpatialTest.class, "file:"+"h2_base.sql");

        store = new SQLStore(
                new SQLStoreProvider(), new StorageConnector(db.source),
                SQLStoreProvider.createTableName(null, null, "roads")
        );
    }

    @org.junit.Test public void preventVintageError() {}

    @Test
    public void getEnvelope() throws DataStoreException {
        final FeatureSet roads = (FeatureSet) store.findResource("roads");
        final Envelope envelope = roads.getEnvelope()
                .orElseThrow(() -> new AssertionError("No envelope available for spatial dataset"));
        final Envelope2D expected = new Envelope2D(CommonCRS.WGS84.geographic(), 15, 5, 16, 20);

        assertTrue(
                AbstractEnvelope.castOrCopy(envelope).equals(expected, 1e-4, false),
                () -> String.format("Bad envelope.%nExpected: %s%nBut was: %s", expected.toString(), envelope.toString())
        );
    }

    //@Test
    public void readGeometries() throws DataStoreException {
        final FeatureSet roads = (FeatureSet) store.findResource("ROADS");
        final FeatureType type = roads.getType();
        final PropertyType geom = Features.getDefaultGeometry(type);
        // TODO: check CRS and if it's a linestring, then try to read rows.
    }

    //@Test
    public void bboxFilter() throws DataStoreException {
        // TODO: try to execute a simple query with an envelope.
    }

    @AfterAll
    public static void destroyDb() {
        if (db == null) return;
        try (AutoCloseable closeDb = db::close ; AutoCloseable closeStore = () -> { if (store != null) store.close(); }) {
        } catch (Exception e) {
            Logging.getLogger("org.apache.sis.storage.sql").log(Level.WARNING, "Cannot properly free test database", e);
        }
    }
}
