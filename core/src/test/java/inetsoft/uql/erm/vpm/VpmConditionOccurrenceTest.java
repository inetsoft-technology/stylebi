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
import inetsoft.uql.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77612, a VPM condition was applied to one occurrence of a table the query reads more
 * than once (t a join t b), the first of the closest matches, so the rows of the other
 * occurrences were not filtered when the join is not one to one (a.MGR = b.ID).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class,
                                  VpmConditionOccurrenceTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmConditionOccurrenceTest {
   private static final String DEFAULT = "default";
   private static final String POSTGRESQL = "postgresql";
   private static final String SQLSERVER = "sqlserver";
   private static final String H2 = "h2";

   // the GUI condition <field> = 1 of a condition on the table
   static Stream<Arguments> guiConditions() {
      return Stream.of(
         // a self-join, comma and ANSI join
         Arguments.of(DEFAULT, "select a.id, b.id from t a, t b where a.mgr = b.id", "t",
                      "t.STATE", "(a.STATE = 1) and (b.STATE = 1)"),
         Arguments.of(DEFAULT, "select a.id, b.id from t a join t b on a.mgr = b.id", "t",
                      "t.STATE", "(a.STATE = 1) and (b.STATE = 1)"),
         Arguments.of(DEFAULT, "select a.id, b.id from t a left join t b on a.mgr = b.id", "t",
                      "t.STATE", "(a.STATE = 1) and (b.STATE = 1)"),
         Arguments.of(DEFAULT, "select a.id, b.id from t a right join t b on a.mgr = b.id", "t",
                      "t.STATE", "(a.STATE = 1) and (b.STATE = 1)"),
         // three occurrences
         Arguments.of(DEFAULT, "select a.id from t a, t b, t c where a.mgr = b.id and " +
                      "b.mgr = c.id", "t", "t.STATE",
                      "(a.STATE = 1) and (b.STATE = 1) and (c.STATE = 1)"),
         // an unaliased occurrence keeps the table name
         Arguments.of(DEFAULT, "select t.id, b.id from t, t b where t.mgr = b.id", "t",
                      "t.STATE", "(t.STATE = 1) and (b.STATE = 1)"),
         Arguments.of(DEFAULT, "select t.id, b.id from t b, t where t.mgr = b.id", "t",
                      "t.STATE", "(b.STATE = 1) and (t.STATE = 1)"),
         // the same name in different schemas, each the same as the unqualified vpm table
         Arguments.of(DEFAULT, "select x.id, y.id from sa.t x, sb.t y where x.id = y.id", "t",
                      "t.STATE", "(x.STATE = 1) and (y.STATE = 1)"),
         Arguments.of(POSTGRESQL, "select x.id, y.id from sa.t x, sb.t y where x.id = y.id",
                      "t", "t.STATE", "(x.\"STATE\" = 1) and (y.\"STATE\" = 1)"),
         // a table qualified to a different depth may be the same table, so it is filtered as
         // well as the exact match (Bug #77580 filtered only the exact match), in either order
         Arguments.of(DEFAULT, "select x.id, y.id from dbo.t x, db1.dbo.t y where x.id = y.id",
                      "db1.dbo.t", "db1.dbo.t.STATE",
                      "(x.STATE = 1) and (y.STATE = 1)"),
         Arguments.of(DEFAULT, "select x.id, y.id from db1.dbo.t y, dbo.t x where x.id = y.id",
                      "db1.dbo.t", "db1.dbo.t.STATE",
                      "(y.STATE = 1) and (x.STATE = 1)"),
         Arguments.of(DEFAULT, "select x.id, y.id from db2.sa.t x, sa.t y where x.id = y.id",
                      "sa.t", "sa.t.STATE",
                      "(x.STATE = 1) and (y.STATE = 1)"),
         Arguments.of(DEFAULT, "select x.id, y.id from sa.t y, db2.sa.t x where x.id = y.id",
                      "sa.t", "sa.t.STATE",
                      "(y.STATE = 1) and (x.STATE = 1)"),
         Arguments.of(DEFAULT, "select x.id, y.id from t x, sa.t y where x.id = y.id",
                      "sa.t", "sa.t.STATE",
                      "(x.STATE = 1) and (y.STATE = 1)"),
         Arguments.of(DEFAULT, "select x.id, y.id from sa.t y, t x where x.id = y.id",
                      "sa.t", "sa.t.STATE",
                      "(y.STATE = 1) and (x.STATE = 1)"),
         Arguments.of(POSTGRESQL, "select x.id, y.id from sa.t x, db1.sa.t y where x.id = y.id",
                      "db1.sa.t", "db1.sa.t.STATE",
                      "(x.\"STATE\" = 1) and (y.\"STATE\" = 1)"),
         Arguments.of(POSTGRESQL, "select x.id, y.id from db1.sa.t y, sa.t x where x.id = y.id",
                      "db1.sa.t", "db1.sa.t.STATE",
                      "(y.\"STATE\" = 1) and (x.\"STATE\" = 1)"),
         Arguments.of(H2, "select x.id, y.id from dbo.t x, db1.dbo.t y where x.id = y.id",
                      "db1.dbo.t", "db1.dbo.t.STATE",
                      "(x.STATE = 1) and (y.STATE = 1)"),
         Arguments.of(H2, "select x.id, y.id from db1.dbo.t y, dbo.t x where x.id = y.id",
                      "db1.dbo.t", "db1.dbo.t.STATE",
                      "(y.STATE = 1) and (x.STATE = 1)"),
         Arguments.of(SQLSERVER, "select x.id, y.id from db2.dbo.t x, dbo.t y where x.id = y.id",
                      "dbo.t", "dbo.t.STATE",
                      "(x.\"STATE\" = 1) and (y.\"STATE\" = 1)"),
         Arguments.of(SQLSERVER, "select x.id, y.id from dbo.t y, db2.dbo.t x where x.id = y.id",
                      "dbo.t", "dbo.t.STATE",
                      "(y.\"STATE\" = 1) and (x.\"STATE\" = 1)"),
         // a different schema at the same depth is a different table
         Arguments.of(DEFAULT, "select x.id, y.id from sa.t x, sb.t y where x.id = y.id", "sb.t",
                      "sb.t.STATE", "y.STATE = 1"),
         // two fields of the table follow the occurrence
         Arguments.of(DEFAULT, "select a.id, b.id from t a, t b where a.mgr = b.id", "t",
                      "t.OWNER = t.EDITOR",
                      "(a.OWNER = a.EDITOR) and (b.OWNER = b.EDITOR)"),
         // a field of another table is mapped to its first occurrence
         Arguments.of(DEFAULT, "select a.id from t a, t b, u c where a.mgr = b.id and " +
                      "a.id = c.id", "t", "t.DEPT = u.DEPT",
                      "(a.DEPT = c.DEPT) and (b.DEPT = c.DEPT)"),
         // the fields of an expression
         Arguments.of(DEFAULT, "select a.id, b.id from t a, t b where a.mgr = b.id", "t",
                      "upper(t.STATE)", "(upper(a.STATE) = 1) and (upper(b.STATE) = 1)"),
         // the same predicate for each occurrence is added once
         Arguments.of(DEFAULT, "select a.id from t a, t b, u c where a.mgr = b.id and " +
                      "a.id = c.id", "t", "u.REGION", "c.REGION = 1"),
         // one occurrence, unchanged
         Arguments.of(DEFAULT, "select a.id from t a", "t", "t.STATE", "a.STATE = 1"),
         Arguments.of(DEFAULT, "select a.id from t a, u b where a.id = b.id", "t", "t.STATE",
                      "a.STATE = 1"),
         Arguments.of(DEFAULT, "select t.id from t", "t", "t.STATE", "t.STATE = 1"),
         Arguments.of(POSTGRESQL, "select x.id from sa.t x", "sa.t", "sa.t.STATE",
                      "x.\"STATE\" = 1"));
   }

   @ParameterizedTest(name = "{0}: {1} ({3})")
   @MethodSource("guiConditions")
   void guiConditionIsAppliedToEachOccurrence(String helper, String text, String condTable,
                                              String field, String expected)
      throws Exception
   {
      VpmCondition cond = guiCondition(condTable, field);
      assertEquals(expected, apply(helper, text, cond));
      // the condition is shared by queries, so evaluating it doesn't change it
      assertEquals(expected, apply(helper, text, cond));
   }

   // a condition script that returns the text of the table
   static Stream<Arguments> scriptConditions() {
      return Stream.of(
         Arguments.of("select a.id, b.id from t a, t b where a.mgr = b.id", "t",
                      "t.STATE = 'NJ'", "(a.STATE = 'NJ') and (b.STATE = 'NJ')"),
         Arguments.of("select a.id, b.id from t a join t b on a.mgr = b.id", "t",
                      "t.STATE = 'NJ'", "(a.STATE = 'NJ') and (b.STATE = 'NJ')"),
         Arguments.of("select a.id from t a, t b, t c where a.mgr = b.id and b.mgr = c.id", "t",
                      "t.STATE = 'NJ'",
                      "(a.STATE = 'NJ') and (b.STATE = 'NJ') and (c.STATE = 'NJ')"),
         Arguments.of("select t.id, b.id from t, t b where t.mgr = b.id", "t",
                      "t.STATE = 'NJ'", "(t.STATE = 'NJ') and (b.STATE = 'NJ')"),
         Arguments.of("select x.id, y.id from dbo.t x, db1.dbo.t y where x.id = y.id",
                      "db1.dbo.t", "db1.dbo.t.STATE = 'NJ'",
                      "(x.STATE = 'NJ') and (y.STATE = 'NJ')"),
         // a qualifier of the table at any depth is the occurrence evaluated (Bug #77580 kept
         // the exact match, x.STATE = 1 and y.STATE = 2)
         Arguments.of("select x.id, y.id from sa.t x, t y where x.id = y.id", "t",
                      "sa.t.STATE = 1 and t.STATE = 2",
                      "(x.STATE = 1 and x.STATE = 2) and (y.STATE = 1 and y.STATE = 2)"),
         // a table name in a string literal or another identifier is not a qualifier
         Arguments.of("select a.id, b.id from t a, t b where a.mgr = b.id", "t",
                      "t.STATE = 'NJ' and xt.K = 't.x'",
                      "(a.STATE = 'NJ' and xt.K = 't.x') and (b.STATE = 'NJ' and xt.K = 't.x')"),
         // a script building the text of each alias gives the same text for each occurrence
         Arguments.of("select a.id, b.id from t a, t b where a.mgr = b.id", "t", null,
                      "a.STATE = 'NJ' and b.STATE = 'NJ'"),
         // one occurrence, unchanged
         Arguments.of("select a.id from t a", "t", "t.STATE = 'NJ'", "a.STATE = 'NJ'"),
         Arguments.of("select x.id, y.id from sa.t x, u y where x.id = y.id", "sa.t",
                      "sa.t.STATE = 1 and u.STATE = 2", "x.STATE = 1 and y.STATE = 2"));
   }

   @ParameterizedTest(name = "{0} -> {2}")
   @MethodSource("scriptConditions")
   void scriptConditionIsAppliedToEachOccurrence(String text, String condTable, String script,
                                                 String expected)
      throws Exception
   {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(condTable);
      cond.setScript(script == null ? ALIASES_SCRIPT :
         "\"" + script.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");

      assertEquals(expected, apply(DEFAULT, text, cond));
   }

   // the script uses the GUI condition, which is qualified with the alias of the occurrence
   @Test
   void conditionVariableIsAppliedToEachOccurrence() throws Exception {
      VpmCondition cond = guiCondition("t", "t.STATE");
      cond.setScript("condition");

      assertEquals("(a.STATE = 1) and (b.STATE = 1)",
                   apply(DEFAULT, "select a.id, b.id from t a, t b where a.mgr = b.id", cond));
      assertEquals("a.STATE = 1", apply(DEFAULT, "select a.id from t a", cond));
   }

   // a logical model alias table (MANAGER) of the same physical table (EMPLOYEES)
   @Test
   void logicalModelAliasTableIsFiltered() throws Exception {
      VpmCondition cond = guiCondition("EMPLOYEES", "EMPLOYEES.STATE");
      String[] tables = { "EMPLOYEES", "EMPLOYEES" };
      String[] taliases = { "EMPLOYEES", "MANAGER" };

      assertEquals("(EMPLOYEES.STATE = 1) and (MANAGER.STATE = 1)",
                   evaluate(cond, null, tables, taliases, null));

      cond.setScript("'EMPLOYEES.STATE = 1'");
      assertEquals("(EMPLOYEES.STATE = 1) and (MANAGER.STATE = 1)",
                   evaluate(cond, null, tables, taliases, null));
   }

   @Test
   void physicalModelConditionIsAppliedToEachOccurrence() throws Exception {
      JDBCDataSource ds = dataSource(H2);
      XPartition partition = new XPartition("p1");
      partition.addTable("t", new Rectangle(0, 0, 100, 100));
      partition.addTable("u", new Rectangle(200, 0, 100, 100));
      XDataModel model = mock(XDataModel.class);
      when(model.getPartition(eq("p1"), any())).thenReturn(partition);
      when(repository.getDataModel(anyString())).thenReturn(model);

      VpmCondition cond = guiCondition("p1", "t.STATE");
      cond.setType(VpmCondition.PHYSICMODEL);

      assertEquals("(a.STATE = 1) and (b.STATE = 1)",
                   evaluate(cond, "p1", new String[] { "t", "t" },
                            new String[] { "a", "b" }, ds));
      assertEquals("(a.STATE = 1) and (b.STATE = 1)",
                   evaluate(cond, "p1", new String[] { "t", "t", "u" },
                            new String[] { "a", "b", "c" }, ds));
      // one occurrence, unchanged
      assertEquals("a.STATE = 1",
                   evaluate(cond, "p1", new String[] { "t", "u" },
                            new String[] { "a", "c" }, ds));

      // the partition is not found
      when(repository.getDataModel(anyString())).thenReturn(null);
      assertEquals("(a.STATE = 1) and (b.STATE = 1)",
                   evaluate(cond, "p1", new String[] { "t", "t" },
                            new String[] { "a", "b" }, ds));
   }

   private static VpmCondition guiCondition(String condTable, String field) {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable(condTable);

      // a field compared to another field
      if(field.contains(" = ")) {
         String[] fields = field.split(" = ");
         cond.setCondition(new XBinaryCondition(
            new XExpression(fields[0], XExpression.FIELD),
            new XExpression(fields[1], XExpression.FIELD), "="));
      }
      // an expression of the fields
      else if(field.contains("(")) {
         cond.setCondition(new XBinaryCondition(
            new XExpression(field, XExpression.EXPRESSION),
            new XExpression("1", XExpression.VALUE), "="));
      }
      else {
         cond.setCondition(new XBinaryCondition(
            new XExpression(field, XExpression.FIELD),
            new XExpression("1", XExpression.VALUE), "="));
      }

      return cond;
   }

   /**
    * Evaluate the condition on the tables of a parsed query, as VpmUtil.applyConditions does.
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

      String[] tables = XUtil.getTables(sql);
      String[] taliases = new String[tables.length];
      Map<String, Integer> counts = new HashMap<>();

      for(int i = 0; i < tables.length; i++) {
         int count = counts.getOrDefault(tables[i], 0);
         taliases[i] = XUtil.getTableAlias(sql, tables[i], count);
         taliases[i] = taliases[i] == null ? tables[i] : taliases[i];
         counts.put(tables[i], count + 1);
      }

      return evaluate(cond, null, tables, taliases, ds);
   }

   private static String evaluate(VpmCondition cond, String partition, String[] tables,
                                  String[] taliases, JDBCDataSource ds)
      throws Exception
   {
      VariableTable vars = new VariableTable();
      XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
      return cond.evaluate(partition, tables, taliases, new String[0], ds, vars, user, false);
   }

   private static JDBCDataSource dataSource(String helper) {
      if(DEFAULT.equals(helper)) {
         return null;
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77612-" + helper);

      switch(helper) {
      case POSTGRESQL -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
         ds.setRuntimeProductName("postgresql");
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

   @Configuration
   static class TestConfig {
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   // the per-alias text a script can build today, the same for each occurrence evaluated
   private static final String ALIASES_SCRIPT = "var s = [];\n" +
      "for(var i = 0; i < taliases.length; i++) { s.push(taliases[i] + \".STATE = 'NJ'\"); }\n" +
      "s.join(' and ');";

   @Autowired
   private XRepository repository;
}
