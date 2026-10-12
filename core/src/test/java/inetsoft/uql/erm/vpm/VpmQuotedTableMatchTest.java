/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.uql.erm.vpm;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77580, the parser stores query table names with the helper's identifier quotes (every
 * segment on PostgreSQL and Snowflake, keyword or special segments on every helper), while VPM
 * condition tables are unquoted. {@link VirtualPrivateModel#isSameTable} compared the raw names,
 * so the VPM was not selected and the query ran with no row filter. It also never matched names
 * qualified to different depths (a VPM table {@code db.dbo.t} against a query on {@code dbo.t}).
 * A condition script's table-qualified text was not rewritten to the alias for a quoted table,
 * and the rewrite fired inside other identifiers ({@code xt.} became {@code xa.}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmQuotedTableMatchTest {
   private static final String DEFAULT = "default";
   private static final String POSTGRESQL = "postgresql";
   private static final String SNOWFLAKE = "snowflake";
   private static final String H2 = "h2";
   private static final String SQLSERVER = "sqlserver";

   static Stream<Arguments> sameTables() {
      return Stream.of(
         // quote characters
         Arguments.of("\"t\"", "t"),
         Arguments.of("`t`", "t"),
         Arguments.of("[t]", "t"),
         Arguments.of("\"T\"", "t"),
         Arguments.of("\"user\"", "user"),
         // schema and catalog qualified, quoted or not
         Arguments.of("\"sa\".\"t\"", "sa.t"),
         Arguments.of("\"public\".t", "public.t"),
         Arguments.of("`public`.t", "public.t"),
         Arguments.of("[db].[dbo].[t]", "db.dbo.t"),
         Arguments.of("\"db\".\"sa\".\"t\"", "db.sa.t"),
         // one side unqualified (today's rule)
         Arguments.of("\"sa\".\"t\"", "t"),
         Arguments.of("sa.t", "t"),
         // depth mismatch, both qualified
         Arguments.of("db.sa.t", "sa.t"),
         Arguments.of("dbo.t", "db.dbo.t"),
         Arguments.of("\"db\".\"dbo\".\"t\"", "dbo.t"),
         // a dotted quoted segment
         Arguments.of("sa.\"my.table\"", "sa.\"my.table\""),
         Arguments.of("\"sa\".\"my.table\"", "sa.\"my.table\""),
         Arguments.of("\"my.t\"", "\"my.t\""),
         Arguments.of("\"my.t\"", "[my.t]"),
         // unchanged
         Arguments.of("t", "t"),
         Arguments.of("T", "t"));
   }

   @ParameterizedTest(name = "{0} = {1}")
   @MethodSource("sameTables")
   void isSameTableMatchesInBothOrders(String tbl1, String tbl2) {
      assertTrue(VirtualPrivateModel.isSameTable(tbl1, tbl2), tbl1 + " vs " + tbl2);
      assertTrue(VirtualPrivateModel.isSameTable(tbl2, tbl1), tbl2 + " vs " + tbl1);
   }

   static Stream<Arguments> differentTables() {
      return Stream.of(
         // a dotted quoted segment is one name, not a qualified one
         Arguments.of("\"my.t\"", "t"),
         Arguments.of("\"my.t\"", "my.t"),
         Arguments.of("sa.\"my.table\"", "table"),
         Arguments.of("sa.\"my.table\"", "my.table"),
         Arguments.of("sa.\"my.table\"", "sa.table"),
         // different schema at the same or a different depth
         Arguments.of("sa.t", "sb.t"),
         Arguments.of("\"sa\".\"t\"", "sb.t"),
         Arguments.of("db.sa.t", "sb.t"),
         Arguments.of("db1.sa.t", "db2.sa.t"),
         // different table
         Arguments.of("\"t\"", "xt"),
         Arguments.of("\"xt\"", "t"),
         Arguments.of("sa.t", "t2"),
         Arguments.of("t", null));
   }

   @ParameterizedTest(name = "{0} != {1}")
   @MethodSource("differentTables")
   void isSameTableRejectsInBothOrders(String tbl1, String tbl2) {
      assertFalse(VirtualPrivateModel.isSameTable(tbl1, tbl2), tbl1 + " vs " + tbl2);
      assertFalse(VirtualPrivateModel.isSameTable(tbl2, tbl1), tbl2 + " vs " + tbl1);
   }

   @Test
   void splitTableNameUnquotesEachSegment() {
      assertArrayEquals(new String[] { "sa", "my.table" },
                        VirtualPrivateModel.splitTableName("SA.\"My.Table\""));
      assertArrayEquals(new String[] { "db", "dbo", "t" },
                        VirtualPrivateModel.splitTableName("[db].`dbo`.\"T\""));
      assertArrayEquals(new String[] { "a\"b", "c]d" },
                        VirtualPrivateModel.splitTableName("\"a\"\"b\".[c]]d]"));
      assertArrayEquals(new String[] { "t" }, VirtualPrivateModel.splitTableName("t"));
      assertArrayEquals(new String[0], VirtualPrivateModel.splitTableName(null));
   }

   @Test
   void getTableMatchPrefersEqualSegments() {
      assertEquals(Integer.MAX_VALUE,
                   VirtualPrivateModel.getTableMatch("\"db1\".dbo.T", "db1.dbo.t"));
      assertEquals(2, VirtualPrivateModel.getTableMatch("db1.dbo.t", "dbo.t"));
      assertEquals(1, VirtualPrivateModel.getTableMatch("t", "\"sa\".\"t\""));
      assertEquals(0, VirtualPrivateModel.getTableMatch("db1.dbo.t", "db2.dbo.t"));
      assertEquals(0, VirtualPrivateModel.getTableMatch("t", null));
   }

   // the parsed query run through the selection and condition steps VpmUtil.applyConditions
   // takes. The condition is the GUI condition <cond table>.STATE = 1
   static Stream<Arguments> guiConditions() {
      return Stream.of(
         Arguments.of(POSTGRESQL, "select a.id from t a", "t", "a.\"STATE\" = 1"),
         Arguments.of(POSTGRESQL, "select a.id from t a", "public.t", "a.\"STATE\" = 1"),
         // Bug #77861, the column of a table without an alias is quoted as for an alias
         Arguments.of(POSTGRESQL, "select t.id from t", "public.t", "\"t\".\"STATE\" = 1"),
         Arguments.of(POSTGRESQL, "select x.id from sa.t x", "sa.t", "x.\"STATE\" = 1"),
         Arguments.of(POSTGRESQL, "select x.id from sa.\"my.table\" x", "sa.\"my.table\"",
                      "x.\"STATE\" = 1"),
         Arguments.of(SNOWFLAKE, "select a.id from t a", "t", "a.\"STATE\" = 1"),
         Arguments.of(DEFAULT, "select u.id from \"user\" u", "user", "u.STATE = 1"),
         Arguments.of(DEFAULT, "select x.id from public.t x", "public.t", "x.STATE = 1"),
         Arguments.of(DEFAULT, "select x.id from db.sa.t x", "sa.t", "x.STATE = 1"),
         Arguments.of(DEFAULT, "select x.id from sa.t x", "db.sa.t", "x.STATE = 1"),
         Arguments.of(DEFAULT, "select x.id from sa.\"my.table\" x", "sa.\"my.table\"",
                      "x.STATE = 1"),
         // the predicate goes on the matching table, not the first one with a similar name
         Arguments.of(DEFAULT, "select x.id, y.id from \"my.t\" x, t y where x.id = y.id", "t",
                      "y.STATE = 1"),
         Arguments.of(DEFAULT, "select x.id, y.id from sa.t x, sb.t y where x.id = y.id", "sb.t",
                      "y.STATE = 1"),
         // a table qualified to a different depth in the same query, see
         // VpmConditionOccurrenceTest
         // control
         Arguments.of(DEFAULT, "select a.id from t a", "t", "a.STATE = 1"));
   }

   @ParameterizedTest(name = "{0}: {1} ({2})")
   @MethodSource("guiConditions")
   void guiConditionIsAppliedToQuotedTable(String helper, String text, String condTable,
                                           String expected)
      throws Exception
   {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(condTable);
      cond.setCondition(new XBinaryCondition(
         new XExpression(condTable + ".STATE", XExpression.FIELD),
         new XExpression("1", XExpression.VALUE), "="));

      assertEquals(expected, apply(helper, text, cond));
   }

   // a condition script that returns table-qualified text, rewritten to the alias
   static Stream<Arguments> scriptConditions() {
      return Stream.of(
         Arguments.of(POSTGRESQL, "select a.id from t a", "t", "t.STATE = 'NJ'",
                      "a.STATE = 'NJ'"),
         Arguments.of(POSTGRESQL, "select a.id from t a", "t", "\"t\".STATE = 'NJ'",
                      "a.STATE = 'NJ'"),
         Arguments.of(POSTGRESQL, "select x.id from sa.t x", "sa.t", "sa.t.STATE = 'NJ'",
                      "x.STATE = 'NJ'"),
         Arguments.of(POSTGRESQL, "select x.id from sa.t x", "sa.t",
                      "\"sa\".\"t\".STATE = 'NJ'", "x.STATE = 'NJ'"),
         Arguments.of(DEFAULT, "select u.id from \"user\" u", "user", "user.STATE = 'NJ'",
                      "u.STATE = 'NJ'"),
         Arguments.of(DEFAULT, "select x.id from public.t x", "public.t",
                      "public.t.STATE = 'NJ'", "x.STATE = 'NJ'"),
         Arguments.of(DEFAULT, "select a.id from t a", "t", "t.STATE = 'NJ'", "a.STATE = 'NJ'"),
         // t. inside another identifier, a qualifier, or a string literal is not rewritten
         Arguments.of(DEFAULT, "select a.id, b.id from t a, xt b where a.id = b.id", "xt",
                      "xt.STATE = 'NJ'", "b.STATE = 'NJ'"),
         Arguments.of(DEFAULT, "select a.id, b.id from t a, xt b where a.id = b.id", "t",
                      "t.STATE = 'NJ' and xt.K = 1 and t.N = 't.x'",
                      "a.STATE = 'NJ' and b.K = 1 and a.N = 't.x'"),
         // an unaliased table keeps its name
         Arguments.of(POSTGRESQL, "select t.id from t", "t", "t.STATE = 'NJ'", "t.STATE = 'NJ'"));
   }

   @ParameterizedTest(name = "{0}: {1} -> {3}")
   @MethodSource("scriptConditions")
   void scriptConditionIsRewrittenToAlias(String helper, String text, String condTable,
                                          String script, String expected)
      throws Exception
   {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(condTable);
      cond.setScript("\"" + script.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");

      assertEquals(expected, apply(helper, text, cond));
   }

   /**
    * Select the VPM and evaluate its condition the way VpmUtil.applyConditions does for a
    * parsed query, and return the condition, or fail if the VPM or condition is not applied.
    */
   private static String apply(String helper, String text, VpmCondition cond) throws Exception {
      JDBCDataSource ds = dataSource(helper);
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.setParseSQL(true);

      // the parse is asynchronous
      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait();
      }

      assertTrue(XUtil.isParsedSQL(sql), text);
      assertEquals(helperClass(helper), SQLHelper.getSQLHelper(sql).getClass());

      String[] tables = XUtil.getTables(sql);
      String[] taliases = new String[tables.length];
      Map<String, Integer> counts = new HashMap<>();

      for(int i = 0; i < tables.length; i++) {
         int count = counts.getOrDefault(tables[i], 0);
         taliases[i] = XUtil.getTableAlias(sql, tables[i], count);
         taliases[i] = taliases[i] == null ? tables[i] : taliases[i];
         counts.put(tables[i], count + 1);
      }

      VirtualPrivateModel vpm = new VirtualPrivateModel("vpm77580");
      vpm.addCondition(cond);
      VariableTable vars = new VariableTable();
      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      String[] columns = new String[0];

      assertTrue(vpm.evaluate(tables, columns, vars, user, null, null, false),
                 "VPM not selected for " + Arrays.toString(tables));
      assertTrue(Arrays.stream(tables)
                    .anyMatch(table -> VirtualPrivateModel.isSameTable(table, cond.getTable())),
                 "condition not applied to " + Arrays.toString(tables));

      String where = cond.evaluate(null, tables, taliases, columns, ds, vars, user, false);
      assertNotNull(where);
      return where.startsWith("(") && where.endsWith(")") ?
         where.substring(1, where.length() - 1) : where;
   }

   private static Class<?> helperClass(String helper) {
      return switch(helper) {
         case POSTGRESQL -> PostgreSQLHelper.class;
         case SNOWFLAKE -> SnowflakeHelper.class;
         case H2 -> H2Helper.class;
         case SQLSERVER -> SQLServerHelper.class;
         default -> SQLHelper.class;
      };
   }

   private static JDBCDataSource dataSource(String helper) {
      if(DEFAULT.equals(helper)) {
         return null;
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77580-" + helper);

      switch(helper) {
      case POSTGRESQL -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
         ds.setRuntimeProductName("postgresql");
      }
      case SNOWFLAKE -> {
         ds.setDriver("net.snowflake.client.jdbc.SnowflakeDriver");
         ds.setURL("jdbc:snowflake://account.snowflakecomputing.com");
         ds.setRuntimeProductName("snowflake");
      }
      case H2 -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
         ds.setRuntimeProductName("h2");
      }
      case SQLSERVER -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=db0");
         ds.setRuntimeProductName("sql server");
      }
      default -> throw new IllegalArgumentException(helper);
      }

      return ds;
   }
}
