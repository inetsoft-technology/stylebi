/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which SQL operations each database is given, the table that decides whether a worksheet query
 * is pushed down to the database or run in memory.
 * <p>
 * The expected sets are the current content of {@code sqlhelper.xml} (and the code overrides of
 * the helpers), not taken from the manuals of the databases: the test pins the table so that a
 * change of it is a deliberate one. A database that is not in the set of an operation supports it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SQLHelperOperationSupportTest {
   private static final List<String> TYPES = List.of(
      "oracle", "mysql", "sql server", "postgresql", "db2", "informix", "derby", "sybase",
      "access", "h2", "clickhouse");

   /** operation to the databases of {@link #TYPES} that do not support it */
   private static final Map<String, Set<String>> UNSUPPORTED = new LinkedHashMap<>();

   static {
      UNSUPPORTED.put(SQLHelper.MAXROWS, Set.of("sybase", "informix", "h2", "clickhouse"));
      UNSUPPORTED.put(SQLHelper.CONCATENATION_TABLE,
                      Set.of("access", "derby", "informix", "mysql", "sybase"));
      UNSUPPORTED.put(SQLHelper.AGGREGATE_ORDERBY, Set.of("informix"));
      UNSUPPORTED.put(SQLHelper.CONDITION_SUBQUERY, Set.of("sybase"));
      UNSUPPORTED.put(SQLHelper.FROM_SUBQUERY, Set.of("informix"));
      UNSUPPORTED.put(SQLHelper.FULL_OUTERJOIN, Set.of("access", "mysql", "derby", "sybase", "h2"));
      UNSUPPORTED.put(SQLHelper.HIDDEN_ORDERBY, Set.of("informix"));
      UNSUPPORTED.put(SQLHelper.EXPRESSION_GROUPBY, Set.of("derby"));
      UNSUPPORTED.put(SQLHelper.EXPRESSION_COLUMN_GROUPBY, Set.of("informix"));
      UNSUPPORTED.put("sumsq", Set.of("sybase"));
      UNSUPPORTED.put("variance", Set.of("sybase"));
      UNSUPPORTED.put("populationvariance", Set.of("sybase"));
      UNSUPPORTED.put("standarddeviation", Set.of("sybase"));
      UNSUPPORTED.put("populationstandarddeviation", Set.of("sybase"));
      UNSUPPORTED.put("covariance", Set.of("sybase", "h2"));
      UNSUPPORTED.put("correlation", Set.of("sybase", "h2"));
      UNSUPPORTED.put("distinctcount", Set.of("access"));
      UNSUPPORTED.put("group_ordered", Set.of("h2"));
      // not listed for any of the databases above
      UNSUPPORTED.put(SQLHelper.DISTINCT_ORDERBY, Set.of());
      UNSUPPORTED.put(SQLHelper.AGGREGATE_COLUMN_ORDERBY, Set.of());
   }

   @Test
   void operationsSupportedPerDatabase() {
      List<String> problems = new ArrayList<>();

      UNSUPPORTED.forEach((op, unsupported) -> {
         for(String type : TYPES) {
            // the table concatenation is asked for the union, the way it is used for a union
            boolean actual = SQLHelper.CONCATENATION_TABLE.equals(op) ?
               SQLHelper.getSQLHelper(type).supportsOperation(op, SQLHelper.UNION_TABLE) :
               SQLHelper.getSQLHelper(type).supportsOperation(op);

            if(actual == unsupported.contains(type)) {
               problems.add(type + " " + op + ": expected " + !unsupported.contains(type) +
                            " but was " + actual);
            }
         }
      });

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   // multiple distinct aggregates in one query: the default and the embedded databases do not
   // have it, so they are the ones that need a different query
   @ParameterizedTest(name = "{0}")
   @CsvSource({"default,false", "derby,false", "derby embedded,false", "cloudscape,false",
               "oracle,true", "mysql,true", "sql server,true", "postgresql,true"})
   void multipleDistinctAggregates(String type, boolean expected) {
      assertEquals(expected, SQLHelper.getSQLHelper(type).supportsOperation("multi_distinct"),
                   type);
   }

   // the union of tables is supported by SQL Server with the info 'union_table' only: not for
   // intersect or minus
   @Test
   void sqlServerConcatenatesUnionOnly() {
      SQLHelper helper = SQLHelper.getSQLHelper("sql server");

      assertTrue(helper.supportsOperation(SQLHelper.CONCATENATION_TABLE, SQLHelper.UNION_TABLE));
      assertFalse(helper.supportsOperation(SQLHelper.CONCATENATION_TABLE, "intersect_table"));
      assertFalse(helper.supportsOperation(SQLHelper.CONCATENATION_TABLE, null));
   }

   // mysql supports the union of tables only from version 4.1 on, known from the data source; without a
   // data source it falls back to the table of sqlhelper.xml, where it is unsupported
   @Test
   void mySqlWithoutDataSourceDoesNotConcatenateTables() {
      SQLHelper helper = SQLHelper.getSQLHelper("mysql");

      assertFalse(helper.supportsOperation(SQLHelper.CONCATENATION_TABLE, SQLHelper.UNION_TABLE));
   }

   // the row limit is never pushed into a where subquery on mysql and derby (Bug #75322 for derby)
   @ParameterizedTest(name = "{0}")
   @CsvSource({"mysql,true", "derby,true"})
   void rowLimitNotInWhereSubquery(String type, boolean outside) {
      SQLHelper helper = SQLHelper.getSQLHelper(type);

      assertFalse(helper.supportsOperation(SQLHelper.MAXROWS, SQLHelper.WHERE_SUBQUERY));
      assertEquals(outside, helper.supportsOperation(SQLHelper.MAXROWS, null));
   }
}
