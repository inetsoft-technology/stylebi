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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77615, a script library function named <tt>hasTable</tt>, written before the built-in
 * existed, takes precedence over the built-in, so an existing vpm script calling it behaves
 * as before. A scope member is found before a library function, so the built-in would
 * otherwise get the library function's arguments and drop the vpm or its row filter.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class,
                                  CredentialService.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmHasTableLibraryTest {
   private static final String LIBRARY =
      "function hasTable(arr, name) { return arr.indexOf(name) >= 0; }";
   // the query tables of select a.ID from sa.t a parsed on PostgreSQL
   private static final String[] TABLES = { "\"sa\".\"t\"" };
   private static final String[] TALIASES = { "a" };

   @AfterEach
   void removeLibrary() {
      LibManagerProvider.getInstance().getManager().removeScript("hasTable");
   }

   @Test
   void libraryFunctionTakesPrecedence() throws Exception {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("hasTable", LIBRARY);

      // the library compares exactly, as it did before the built-in
      assertTrue(trigger("hasTable(tables, '\"sa\".\"t\"');"));
      assertFalse(trigger("hasTable(tables, 'sa.t');"));
      assertEquals("1 = 1", condition("hasTable(tables, '\"sa\".\"t\"') ? '1 = 1' : null;"));
      assertNull(condition("hasTable(tables, 'sa.t') ? '1 = 1' : null;"));
      assertEquals(1, hidden("hasTable(tables, '\"sa\".\"t\"') ? ['t.SECRET'] : [];").length);
      assertEquals(0, hidden("hasTable(tables, 'sa.t') ? ['t.SECRET'] : [];").length);
   }

   @Test
   void builtInWithoutLibraryFunction() throws Exception {
      assertTrue(trigger("hasTable('sa.t');"));
      assertFalse(trigger("hasTable('sb.t');"));
      assertEquals("1 = 1", condition("hasTable('sa.t') ? '1 = 1' : null;"));
      assertNull(condition("hasTable('sb.t') ? '1 = 1' : null;"));
      assertEquals(1, hidden("hasTable('sa.t') ? ['t.SECRET'] : [];").length);
      assertEquals(0, hidden("hasTable('sb.t') ? ['t.SECRET'] : [];").length);
   }

   @Test
   void builtInAfterLibraryFunctionIsRemoved() throws Exception {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("hasTable", LIBRARY);
      assertTrue(trigger("hasTable(tables, '\"sa\".\"t\"');"));

      mgr.removeScript("hasTable");
      assertTrue(trigger("hasTable('sa.t');"));
   }

   private static boolean trigger(String script) throws Exception {
      VirtualPrivateModel vpm = new VirtualPrivateModel("vpm77615");
      vpm.setScript(script);
      return vpm.evaluate(TABLES, new String[0], new VariableTable(), user(), null, null, true);
   }

   private static String condition(String script) throws Exception {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable("sa.t");
      cond.setScript(script);
      return cond.evaluate(null, TABLES, TALIASES, new String[0], dataSource(),
                           new VariableTable(), user(), false);
   }

   private static String[] hidden(String script) throws Exception {
      HiddenColumns hidden = new HiddenColumns();
      hidden.setScript(script);
      return hidden.evaluate(TABLES, new String[0], new VariableTable(), user(), true, null);
   }

   // the roles are taken from the principal for this user, see XUtil.getUserRoles
   private static XPrincipal user() {
      return new XPrincipal(new IdentityID("unknown_user", null),
                            new IdentityID[] { new IdentityID("viewer", null) },
                            new String[0], null);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77615-postgresql");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost:5432/test");
      ds.setRuntimeProductName("postgresql");
      return ds;
   }
}
