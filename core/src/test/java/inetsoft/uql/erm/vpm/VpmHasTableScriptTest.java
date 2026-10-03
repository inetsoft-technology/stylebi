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
package inetsoft.uql.erm.vpm;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.erm.HiddenColumns;
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
 * Bug #77615, the <code>tables</code> array given to the vpm trigger, condition and hidden
 * columns scripts has the names as the query stores them: quoted by the sql helper for a
 * parsed sql text (<tt>"sa"."t"</tt> on PostgreSQL), unquoted for a query built from a model
 * (<tt>sa.t</tt>). No literal compared with <code>tables.indexOf()</code> works for both, so
 * a script written against one form misses the table in the other one. The new
 * <code>hasTable(name)</code> script function compares the names the way the vpm matches its
 * condition tables, and the <code>tables</code> array itself is unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmHasTableScriptTest {
   private static final String TRIGGER = "trigger";
   private static final String CONDITION = "condition";
   private static final String HIDDEN = "hidden";
   private static final String[] SCOPES = { TRIGGER, CONDITION, HIDDEN };

   // the query tables, by how the query is built
   private static final String QUOTED = "select a.ID from sa.t a";
   private static final String KEYWORD = "select u.ID from \"user\" u";
   private static final String DEEP = "select x.ID from db.sa.t x";
   private static final String STRUCTURED = "structured sa.t";

   @Test
   void queryTablesAreStoredInBothForms() throws Exception {
      assertArrayEquals(new String[] { "\"sa\".\"t\"" }, query(QUOTED).tables);
      assertArrayEquals(new String[] { "\"user\"" }, query(KEYWORD).tables);
      assertArrayEquals(new String[] { "\"db\".\"sa\".\"t\"" }, query(DEEP).tables);
      assertArrayEquals(new String[] { "sa.t" }, query(STRUCTURED).tables);
   }

   static Stream<Arguments> hasTableCases() {
      List<Arguments> cases = new ArrayList<>();

      for(String query : new String[] { QUOTED, STRUCTURED }) {
         // quoted or not, any case, fewer or more segments than the query table
         add(cases, query, "'sa.t'", true);
         add(cases, query, "'\"sa\".\"t\"'", true);
         add(cases, query, "'SA.T'", true);
         add(cases, query, "'\"SA\".T'", true);
         add(cases, query, "'t'", true);
         add(cases, query, "'db.sa.t'", true);
         // a different schema or table
         add(cases, query, "'sb.t'", false);
         add(cases, query, "'db.sb.t'", false);
         add(cases, query, "'t2'", false);
         add(cases, query, "'\"sa.t\"'", false);
         // no name
         add(cases, query, "null", false);
         add(cases, query, "undefined", false);
         add(cases, query, "''", false);
         add(cases, query, "' '", false);
      }

      // a keyword table, quoted on every helper
      add(cases, KEYWORD, "'user'", true);
      add(cases, KEYWORD, "'USER'", true);
      add(cases, KEYWORD, "'\"user\"'", true);
      add(cases, KEYWORD, "'users'", false);
      // the query table is qualified deeper than the name
      add(cases, DEEP, "'sa.t'", true);
      add(cases, DEEP, "'Db.Sa.T'", true);
      add(cases, DEEP, "'db2.sa.t'", false);
      add(cases, DEEP, "'sb.t'", false);

      return cases.stream();
   }

   private static void add(List<Arguments> cases, String query, String name, boolean expected) {
      for(String scope : SCOPES) {
         cases.add(Arguments.of(scope, query, name, expected));
      }
   }

   @ParameterizedTest(name = "{0}: {1} hasTable({2}) = {3}")
   @MethodSource("hasTableCases")
   void hasTableMatchesQueryTable(String scope, String query, String name, boolean expected)
      throws Exception
   {
      assertEquals(expected, run(scope, query(query), "hasTable(" + name + ")"));
   }

   @Test
   void hasTableWithoutArgumentIsFalse() throws Exception {
      for(String scope : SCOPES) {
         assertFalse(run(scope, query(QUOTED), "hasTable()"), scope);
      }
   }

   @Test
   void hasTableIgnoresChangesToTablesArray() throws Exception {
      for(String scope : SCOPES) {
         assertTrue(run(scope, query(QUOTED), "tables[0] = 'other', hasTable('sa.t')"), scope);
         assertFalse(run(scope, query(QUOTED), "tables[0] = 'other', hasTable('other')"),
                     scope);
      }
   }

   @Test
   void hasTableIsFalseWithoutTables() throws Exception {
      Query empty = new Query(new String[0], new String[0], dataSource());

      for(String scope : SCOPES) {
         assertFalse(run(scope, empty, "hasTable('sa.t')"), scope);
      }
   }

   // a query table stored without a schema matches the name in any schema
   @Test
   void unqualifiedQueryTableMatchesAnySchema() throws Exception {
      Query bare = new Query(new String[] { "t" }, new String[] { "t" }, dataSource());

      for(String scope : SCOPES) {
         assertTrue(run(scope, bare, "hasTable('sa.t')"), scope);
         assertTrue(run(scope, bare, "hasTable('sb.t')"), scope);
         assertFalse(run(scope, bare, "hasTable('sa.t2')"), scope);
      }
   }

   // the names are lower cased in the root locale, in Turkish ITEMS lower cases to ıtems
   @Test
   void caseIsIgnoredInTurkishLocale() throws Exception {
      Locale locale = Locale.getDefault();
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      try {
         assertTrue(VirtualPrivateModel.isSameTable("\"items\"", "ITEMS"));
         assertTrue(VirtualPrivateModel.isSameTable("\"sa\".\"items\"", "SA.ITEMS"));
         Query items = new Query(new String[] { "\"items\"" }, new String[] { "i" },
                                 dataSource());

         for(String scope : SCOPES) {
            assertTrue(run(scope, items, "hasTable('ITEMS')"), scope);
         }

         // the vpm condition on ITEMS is applied to the query table and qualified by its alias
         VpmCondition cond = new VpmCondition("cond");
         cond.setType(VpmCondition.TABLE);
         cond.setTable("ITEMS");
         cond.setScript("'ITEMS.STATE = 1'");
         XPrincipal user = new XPrincipal(new IdentityID("viewer", null));
         assertEquals("i.STATE = 1",
                      cond.evaluate(null, items.tables, items.taliases, new String[0],
                                    items.ds, new VariableTable(), user, false));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // control: the tables array still has the names as the query stores them, so an existing
   // script comparing them exactly behaves as before
   static Stream<Arguments> controlCases() {
      List<Arguments> cases = new ArrayList<>();
      add(cases, QUOTED, "tables.indexOf('sa.t') >= 0", false);
      add(cases, QUOTED, "tables.indexOf('\"sa\".\"t\"') >= 0", true);
      add(cases, QUOTED, "tables.length == 1 && tables[0] == '\"sa\".\"t\"'", true);
      add(cases, STRUCTURED, "tables.indexOf('sa.t') >= 0", true);
      add(cases, STRUCTURED, "tables.indexOf('\"sa\".\"t\"') >= 0", false);
      add(cases, KEYWORD, "tables.indexOf('user') >= 0", false);
      add(cases, KEYWORD, "tables.indexOf('\"user\"') >= 0", true);
      return cases.stream();
   }

   @ParameterizedTest(name = "{0}: {1} {2} = {3}")
   @MethodSource("controlCases")
   void controlTablesArrayIsUnchanged(String scope, String query, String script,
                                      boolean expected)
      throws Exception
   {
      assertEquals(expected, run(scope, query(query), script));
   }

   /**
    * Run a script testing the query tables in a scope and return the outcome: the trigger
    * applies the vpm, the condition script returns a condition, or the hidden columns script
    * hides a column.
    */
   private static boolean run(String scope, Query query, String test) throws Exception {
      VariableTable vars = new VariableTable();
      // the roles are taken from the principal for this user, see XUtil.getUserRoles
      XPrincipal user = new XPrincipal(new IdentityID("unknown_user", null),
                                       new IdentityID[] { new IdentityID("viewer", null) },
                                       new String[0], null);
      String[] columns = new String[0];

      switch(scope) {
      case TRIGGER -> {
         VirtualPrivateModel vpm = new VirtualPrivateModel("vpm77615");
         vpm.setScript(test + ";");
         return vpm.evaluate(query.tables, columns, vars, user, null, null, true);
      }
      case CONDITION -> {
         VpmCondition cond = new VpmCondition("cond");
         cond.setType(VpmCondition.TABLE);
         cond.setTable("sa.t");
         cond.setScript("(" + test + ") ? '1 = 1' : null;");
         String where = cond.evaluate(null, query.tables, query.taliases, columns, query.ds,
                                      vars, user, false);
         assertTrue(where == null || where.equals("1 = 1"), where);
         return where != null;
      }
      case HIDDEN -> {
         HiddenColumns hidden = new HiddenColumns();
         hidden.setScript("(" + test + ") ? ['t.SECRET'] : [];");
         String[] result = hidden.evaluate(query.tables, columns, vars, user, true, null);
         assertTrue(result.length <= 1, Arrays.toString(result));
         return result.length == 1;
      }
      default -> throw new IllegalArgumentException(scope);
      }
   }

   /**
    * Get the tables of a query as VpmUtil.applyConditions does, parsed from the sql text on a
    * PostgreSQL data source, or added to a structured query as a model query does.
    */
   private static Query query(String text) throws Exception {
      JDBCDataSource ds = dataSource();
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      if(STRUCTURED.equals(text)) {
         sql.addTable("a", "sa.t");
      }
      else {
         sql.setParseSQL(true);

         // the parse is asynchronous
         synchronized(sql) {
            sql.setSQLString(text);
            sql.wait();
         }

         assertTrue(XUtil.isParsedSQL(sql), text);
         assertEquals(PostgreSQLHelper.class, SQLHelper.getSQLHelper(sql).getClass());
      }

      String[] tables = XUtil.getTables(sql);
      String[] taliases = new String[tables.length];

      for(int i = 0; i < tables.length; i++) {
         taliases[i] = XUtil.getTableAlias(sql, tables[i], 0);
         taliases[i] = taliases[i] == null ? tables[i] : taliases[i];
      }

      return new Query(tables, taliases, ds);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77615-postgresql");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost:5432/test");
      ds.setRuntimeProductName("postgresql");
      return ds;
   }

   private record Query(String[] tables, String[] taliases, JDBCDataSource ds) {
   }
}
