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
package org.apache.sis.internal.sql.reverse;

import org.apache.sis.internal.sql.Dialect;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.UUID;
import org.opengis.feature.AttributeType;
import org.apache.sis.feature.DefaultAttributeType;
import org.apache.sis.storage.DataStoreException;


/**
 * Description of a table column.
 *
 * @author  Johann Sorel (Geomatys)
 * @version 1.0
 * @since   1.0
 * @module
 */
public final class ColumnMetaModel {
    /**
     * Description of the attribute holding native SRID associated to a certain descriptor.
     */
    static final AttributeType<Integer> JDBC_PROPERTY_SRID = new DefaultAttributeType<>(
            Collections.singletonMap(DefaultAttributeType.NAME_KEY, "nativeSRID"),
            Integer.class, 1, 1, null);

    /**
     * Description of the attribute telling whether a field is unique in the database.
     */
    static final AttributeType<Boolean> JDBC_PROPERTY_UNIQUE = new DefaultAttributeType<>(
            Collections.singletonMap(DefaultAttributeType.NAME_KEY, "unique"),
            Boolean.class, 1, 1, null);

    /**
     * Property information, if the field is a relation.
     */
    static final AttributeType<RelationMetaModel> JDBC_PROPERTY_RELATION = new DefaultAttributeType<>(
            Collections.singletonMap(DefaultAttributeType.NAME_KEY, "relation"),
            RelationMetaModel.class, 1, 1, null);

    /**
     * Whether values in a column are generated by the database, computed from a sequence of supplied.
     */
    enum Type {
        /**
         * Indicate this field value is generated by the database.
         */
        AUTO,

        /**
         * Indicate a sequence is used to generate field values.
         */
        SEQUENCED,

        /**
         * Indicate field value must be provided.
         */
        PROVIDED
    }

    /**
     * Database scheme where this column is found.
     */
    final String schema;

    /**
     * Database table where this column is found
     */
    final String table;

    /**
     * Name of the column.
     */
    final String name;

    /**
     * Column SQL type (integer, characters, …) as one of {@link java.sql.Types} constants.
     */
    final int sqlType;

    /**
     * Name of {@link #sqlType}.
     */
    final String sqlTypeName;

    /**
     * Java class for the {@link #sqlType}.
     */
    final Class<?> clazz;

    /**
     * If the column is a primary key, specifies how the value is generated.
     */
    final Type type;

    /**
     * If the column is a primary key, the optional sequence name.
     */
    final String sequenceName;

    /**
     * Stores information about a table column.
     *
     * @param schema        database scheme where this column is found.
     * @param table         database table where this column is found.
     * @param name          name of this column.
     * @param sqlType       column SQL type as one of {@link java.sql.Types} constants.
     * @param sqlTypeName   name of {@code sqlType}.
     * @param clazz         Java class for {@code sqlType}.
     * @param type          if the column is a primary key, specify how the value is generated.
     * @param sequenceName  if the column is a primary key, optional sequence name.
     */
    ColumnMetaModel(final String schema, final String table, final String name,
            final int sqlType, final String sqlTypeName, final Class<?> clazz,
            final Type type, final String sequenceName)
    {
        this.schema       = schema;
        this.table        = table;
        this.name         = name;
        this.sqlType      = sqlType;
        this.sqlTypeName  = sqlTypeName;
        this.clazz        = clazz;
        this.type         = type;
        this.sequenceName = sequenceName;
    }

    /**
     * Tries to compute next column value.
     *
     * @param  dialect  handler for syntax elements specific to the database.
     * @param  cx       connection to the database.
     * @return next field value.
     * @throws SQLException if a JDBC error occurred while executing a statement.
     * @throws DataStoreException if another error occurred while fetching the next value.
     */
    public Object nextValue(final Dialect dialect, final Connection cx) throws SQLException, DataStoreException {
        Object next = null;
        if (type == Type.AUTO || type == Type.SEQUENCED) {
            // Delegate to the database for next value.
            next = dialect.nextValue(this, cx);
        } else {
            // Generate value if possible.
            if (Number.class.isAssignableFrom(clazz)) {
                // Get the maximum value in the database and increment it
                final StringBuilder sql = new StringBuilder();
                sql.append("SELECT 1 + MAX(");
                dialect.encodeColumnName(sql, name);
                sql.append(") FROM ");
                dialect.encodeSchemaAndTableName(sql, schema, table);
                try (Statement st = cx.createStatement();
                    ResultSet rs = st.executeQuery(sql.toString())) {
                    rs.next();
                    next = rs.getObject(1);
                }
                if (next == null) {
                    // Can be the result of an empty table.
                    next = 1;
                }
            } else if (CharSequence.class.isAssignableFrom(clazz)) {
                // Use an UUID to reduce risk of conflicts.
                next = UUID.randomUUID().toString();
            }
            if (next == null) {
                throw new DataStoreException("Failed to generate a value for column " + toString());
            }
        }
        return next;
    }

    /**
     * Returns a string representation of this column description for debugging purpose.
     * The string returned by this method may change in any future SIS version.
     *
     * @return a string representation for debugging purpose.
     */
    @Override
    public String toString() {
        return new StringBuilder(name)
                .append('[')
                .append(sqlType)
                .append(", ")
                .append(sqlTypeName)
                .append(", ")
                .append(type)
                .append(']')
                .toString();
    }
}
