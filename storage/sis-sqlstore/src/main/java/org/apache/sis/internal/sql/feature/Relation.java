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

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import org.apache.sis.internal.util.CollectionsExt;
import org.apache.sis.internal.metadata.sql.Reflection;
import org.apache.sis.storage.DataStoreContentException;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.collection.TreeTable;
import org.apache.sis.util.Debug;


/**
 * Description of a relation between two tables, as defined by foreigner keys.
 * Each {@link Table} may contain an arbitrary amount of relations.
 * Relations are defined in two directions:
 *
 * <ul>
 *   <li>{@link Direction#IMPORT}: primary keys of <em>other</em> tables are referenced by the foreigner keys
 *       of the table containing this {@code Relation}.</li>
 *   <li>{@link Direction#EXPORT}: foreigner keys of <em>other</em> tables are referencing the primary keys
 *       of the table containing this {@code Relation}.</li>
 * </ul>
 *
 * Instances of this class are created from the results of {@link DatabaseMetaData#getImportedKeys​ getImportedKeys​}
 * or {@link DatabaseMetaData#getExportedKeys​ getExportedKeys​} with {@code (catalog, schema, table)} parameters.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.0
 * @since   1.0
 * @module
 */
final class Relation extends TableReference {
    /**
     * Whether another table is <em>using</em> or is <em>used by</em> the table containing the {@link Relation}.
     */
    enum Direction {
        /**
         * Primary keys of other tables are referenced by the foreigner keys of the table containing the {@code Relation}.
         * In other words, the table containing {@code Relation} is <em>using</em> the {@link Relation#table}.
         *
         * @see DatabaseMetaData#getImportedKeys(String, String, String)
         */
        IMPORT(Reflection.PKTABLE_CAT, Reflection.PKTABLE_SCHEM, Reflection.PKTABLE_NAME,
               Reflection.PKCOLUMN_NAME, Reflection.FKCOLUMN_NAME),

        /**
         * Foreigner keys of other tables are referencing the primary keys of the table containing the {@code Relation}.
         * In other words, the table containing {@code Relation} is <em>used by</em> {@link Relation#table}.
         *
         * @see DatabaseMetaData#getExportedKeys(String, String, String)
         */
        EXPORT(Reflection.FKTABLE_CAT, Reflection.FKTABLE_SCHEM, Reflection.FKTABLE_NAME,
               Reflection.FKCOLUMN_NAME, Reflection.PKCOLUMN_NAME);

        /*
         * Note: another possible type of relation is the one provided by getCrossReference(…).
         * Inconvenient is that we need to specify the table names on both sides of the relation.
         * But advantage is that it works with any set of columns having unique values, not only
         * the primary keys. However if we use getCrossReference(…), then we need to keep in mind
         * that Table.instances is only for instances identified by their primary keys and shall
         * not be used for instances identified by the columns returned by getCrossReference(…).
         */

        /**
         * The database {@link Reflection} key to use for fetching the name of other table column.
         * That column is part of a primary key if the direction is {@link #IMPORT}, or part of a
         * foreigner key if the direction is {@link #EXPORT}.
         */
        final String catalog, schema, table, column;

        /**
         * The database {@link Reflection} key to use for fetching the name of the column in the table
         * containing the {@code Relation}. That column is part of a foreigner key if the direction is
         * {@link #IMPORT}, or part of a primary key if the direction is {@link #EXPORT}.
         */
        final String containerColumn;

        /**
         * Creates a new {@code Direction} enumeration value.
         */
        private Direction(final String catalog, final String schema, final String table,
                          final String column, final String containerColumn)
        {
            this.catalog         = catalog;
            this.schema          = schema;
            this.table           = table;
            this.column          = column;
            this.containerColumn = containerColumn;
        }
    }

    /**
     * The columns of the other table that constitute a primary or foreigner key. Keys are the columns of the
     * other table and values are columns of the table containing this {@code Relation}.
     */
    private final Map<String,String> columns;

    /**
     * The other table identified by {@link #catalog}, {@link #schema} and {@link #table} names.
     * This is set during {@link Table} construction and should not be modified after that point.
     */
    private Table searchTable;

    /**
     * The name of the feature property where the association to {@link #searchTable} table will be stored.
     */
    private String propertyName;

    /**
     * Whether the {@link #columns} map include all primary key columns. This field is set to {@code false}
     * if the foreigner key uses only a subset of the primary key columns, in which case the referenced rows
     * may not be unique.
     *
     * @see #isFullKey()
     */
    private boolean isFullKey;

    /**
     * Creates a new relation for an imported key. The given {@code ResultSet} must be positioned
     * on the first row of {@code DatabaseMetaData.getImportedKeys​(catalog, schema, table)} result,
     * and the result must be sorted in the order of the given keys:
     *
     * <ol>
     *   <li>{@link Direction#catalog}</li>
     *   <li>{@link Direction#schema}</li>
     *   <li>{@link Direction#table}</li>
     * </ol>
     *
     * Note that JDBC specification ensures this order if {@link Direction#IMPORT} is used with the result of
     * {@code getImportedKeys​} and {@link Direction#EXPORT} is used with the result of {@code getExportedKeys​}.
     *
     * <p>After construction, the {@code ResultSet} will be positioned on the first row of the next relation,
     * or be closed if the last row has been reached. This constructor always moves the given result set by at
     * least one row, unless an exception occurs.</p>
     */
    Relation(final Analyzer analyzer, final Direction dir, final ResultSet reflect) throws SQLException, DataStoreContentException {
        super(analyzer.getUniqueString(reflect, dir.catalog),
              analyzer.getUniqueString(reflect, dir.schema),
              analyzer.getUniqueString(reflect, dir.table),
              analyzer.getUniqueString(reflect, Reflection.FK_NAME));

        final Map<String,String> m = new LinkedHashMap<>();
        do {
            final String column = analyzer.getUniqueString(reflect, dir.column);
            if (m.put(column, analyzer.getUniqueString(reflect, dir.containerColumn)) != null) {
                throw new DataStoreContentException(Resources.forLocale(analyzer.locale)
                        .getString(Resources.Keys.DuplicatedColumn_1, column));
            }
            if (!reflect.next()) {
                reflect.close();
                break;
            }
        } while (table.equals(reflect.getString(dir.table)) &&                  // Table name is mandatory.
                 Objects.equals(schema,  reflect.getString(dir.schema)) &&      // Schema and catalog may be null.
                 Objects.equals(catalog, reflect.getString(dir.catalog)));

        columns = CollectionsExt.compact(m);
    }

    /**
     * Invoked after construction for setting the table identified by {@link #catalog}, {@link #schema}
     * and {@link #table} names. Shall be invoked exactly once.
     *
     * @param  search       the other table containing the primary key ({@link Direction#IMPORT})
     *                      or the foreigner key ({@link Direction#EXPORT}) of this relation.
     * @param  property     name of the property of the enclosing {@code Table.featureType}
     *                      where to store the feature instances read from the other table.
     * @param  primaryKeys  the primary key columns of the relation. May be the primary key columns of this table
     *                      or the primary key columns of the other table, depending on {@link Direction}.
     * @param  direction    the direction of this {@code Relation}.
     */
    final void setSearchTable(final Analyzer analyzer, final Table search, final String property,
                              final String[] primaryKeys, final Direction direction) throws DataStoreException
    {
        /*
         * Sanity check (could be assertion): name of the given table
         * must match the name expected by this relation.
         */
        String expected, actual;    // Identity comparison okay because of Analyzer.getUniqueString(…)
        if ((expected = table)  != (actual = search.table)  ||
            (expected = schema) != (actual = search.schema) || searchTable != null)
        {
            String message = analyzer.internalError();                                  // Should never happen.
            if (expected != actual) message += ' ' + actual + " ≠ " + expected;
            throw new DataStoreException(message);
        }
        /*
         * Store the specified table and property name, and verify if this relation
         * contains all columns required by the primary key.
         */
        searchTable  = search;
        propertyName = property;
        final Collection<String> referenced;        // Primary key components referenced by this relation.
        switch (direction) {
            case IMPORT: referenced = columns.keySet(); break;
            case EXPORT: referenced = columns.values(); break;
            default: throw new AssertionError(direction);
        }
        isFullKey = referenced.containsAll(Arrays.asList(primaryKeys));
        if (isFullKey && getKeySize() >= 2) {
            /*
             * Sort the columns in the order expected by the primary key.  This is required because we need
             * a consistent order in the cache provided by Table.instanceForPrimaryKeys() even if different
             * relations declare different column order in their foreigner key. Note that the 'columns' map
             * is unmodifiable if its size is less than 2, and modifiable otherwise.
             */
            final Map<String,String> copy = new HashMap<>(columns);
            columns.clear();
            for (String key : primaryKeys) {
                String value = key;
                switch (direction) {
                    case IMPORT: {                                      // Primary keys are 'columns' keys.
                        value = copy.remove(key);
                        break;
                    }
                    case EXPORT: {                                      // Primary keys are 'columns' values.
                        key = null;
                        for (final Iterator<Map.Entry<String,String>> it = copy.entrySet().iterator(); it.hasNext();) {
                            final Map.Entry<String,String> e = it.next();
                            if (value.equals(e.getValue())) {
                                key = e.getKey();
                                it.remove();
                                break;
                            }
                        }
                        break;
                    }
                }
                if (key == null || value == null || columns.put(key, value) != null) {
                    throw new DataStoreException(analyzer.internalError());
                }
            }
            if (!copy.isEmpty()) {
                throw new DataStoreContentException(Resources.forLocale(analyzer.locale)
                            .getString(Resources.Keys.MalformedForeignerKey_2, freeText,
                                       CollectionsExt.first(copy.keySet())));
            }
        }
    }

    /**
     * Returns the other table identified by {@link #catalog}, {@link #schema} and {@link #table} names.
     * This is the table where the search operations will be performed. In other words, this is part of
     * the following pseudo-query:
     *
     * <pre>{@code SELECT * FROM <search table> WHERE <search columns> = ...}</pre>
     */
    final Table getSearchTable() {
        if (searchTable != null) {
            return searchTable;
        }
        throw new IllegalStateException(super.toString());              // Should not happen.
    }

    /**
     * Returns the columns of the {@linkplain #getSearchTable() search table}
     * which will need to appear in the {@code WHERE} clause.
     * For {@link Direction#IMPORT}, they are primary key columns.
     * For {@link Direction#EXPORT}, they are foreigner key columns.
     */
    final Collection<String> getSearchColumns() {
        return columns.keySet();
    }

    /**
     * Returns the foreigner key columns of the table that contains this relation.
     * This method should be invoked only for {@link Direction#IMPORT} (otherwise the method name is wrong).
     * This method returns only the foreigner key columns known to this relation; this is not necessarily
     * all the enclosing table foreigner keys. Some columns may be used in more than one relation.
     */
    final Collection<String> getForeignerKeys() {
        return columns.values();
    }

    /**
     * Returns the number of columns used by the foreigner key.
     */
    final int getKeySize() {
        return columns.size();
    }

    /**
     * Returns {@code true} if this relation includes all required primary key columns. Returns {@code false}
     * if the foreigner key uses only a subset of the primary key columns, in which case the referenced rows
     * may not be unique.
     */
    final boolean isFullKey() {
        return isFullKey;
    }

    /**
     * Returns the name of the feature property where the association to {@link #searchTable} table will be stored.
     */
    final String getPropertyName() {
        return propertyName;
    }

    /**
     * Creates a tree representation of this relation for debugging purpose.
     *
     * @param  parent  the parent node where to add the tree representation.
     * @param  arrow   the symbol to use for relating the columns of two tables in a foreigner key.
     */
    @Debug
    void appendTo(final TreeTable.Node parent, final String arrow) {
        final TreeTable.Node node = newChild(parent, freeText);
        for (final Map.Entry<String,String> e : columns.entrySet()) {
            newChild(node, e.getValue() + arrow + e.getKey());
        }
    }

    /**
     * Formats a graphical representation of this relation for debugging purpose. This representation can
     * be printed to the {@linkplain System#out standard output stream} (for example) if the output device
     * uses a monospaced font and supports Unicode.
     */
    @Override
    public String toString() {
        return toString(this, (n) -> appendTo(n, " — "));
    }
}
