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
package org.apache.sis.internal.sql.feature;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import static org.apache.sis.util.ArgumentChecks.ensureNonNull;

/**
 * Utility to handle conversion of a result set cell value. This object is a bi-function whose input is a result set
 * placed on row of interest, and an index specifying which column defines the cell to read on this line.
 *
 * @param <T> Type of object decoded from cell.
 * @author Alexis Manin (Geomatys)
 * @version 2.0
 * @since   2.0
 * @module
 */
public interface ColumnAdapter<T> {

    /**
     * Gives a function ready to extract and interpret values of a result set for the column it has been designed for.
     *
     * @param target A read-only connection that can be used to load metadata and stuff related to target column.
     * @return A function which will interpret values for the column this component has been created for. User will have
     * to give it a well-positioned cursor (result set on the wanted line) as the index of the cell it must read on it.
     */
    SQLBiFunction<ResultSet, Integer, T> prepare(final Connection target);

    /**
     * Note : This method could be used not only for geometric fields, but also on numeric ones representing 1D systems.
     *
     * @return Potentially an empty shell, or the default coordinate reference system for this column values.
     */
    default Optional<CoordinateReferenceSystem> getCrs() {
        return Optional.empty();
    }

    /**
     * If possible, return a fast estimation of the dataset envelope based on this column geometry.
     *
     * @implNote Note that no cache system should be implemented here, as we query a valid connection for direct
     * computation. However, containers are free to cache returned information at their convenience.
     *
     * @param target The database connection to use to compute the envelope.
     * @return If available, the envelope of the current column. Otherwise, an empty shell.
     * @throws SQLException If we fail fetching information from database.
     */
    default Optional<Envelope> getEnvelope(final Connection target) throws SQLException { return Optional.empty(); }

    /**
     *
     * @return The (possibly parent) type of objects read by this mapper. Note that it MUST NOT return null values.
     */
    Class<T> getJavaType();

    final class Simple<T> implements ColumnAdapter<T> {
        private final Class<T> javaType;
        private final SQLBiFunction<ResultSet, Integer, T> fetchValue;

        Simple(final Class<T> targetType, SQLBiFunction<ResultSet, Integer, T> fetchValue) {
            ensureNonNull("Target type", targetType);
            ensureNonNull("Function for value retrieval", fetchValue);
            javaType = targetType;
            this.fetchValue = fetchValue;
        }

        @Override
        public SQLBiFunction<ResultSet, Integer, T> prepare(Connection target) {
            return fetchValue;
        }

        @Override
        public Class<T> getJavaType() {
            return javaType;
        }
    }
}
