/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.db;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77106: the database provider must check the password against the row of the requested
 * user when a case-insensitive collation makes the users query return the rows of several
 * case-variant users. Runs against an in-memory Derby database with a case-insensitive
 * collation, using the real {@link AuthenticationDAO}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseAuthenticationCollationTest {
   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   @ParameterizedTest(name = "caseSensitive={0}, upperFirst={1}")
   @CsvSource({ "true, false", "true, true", "false, false", "false, true" })
   void caseVariantRows_passwordCheckedAgainstExactUser(boolean caseSensitive, boolean upperFirst)
      throws Exception
   {
      String[][] rows = upperFirst ?
         new String[][] { { "BOB", "pw-BOB" }, { "bob", "pw-bob" } } :
         new String[][] { { "bob", "pw-bob" }, { "BOB", "pw-BOB" } };
      createDb(true, "VARCHAR(50)", rows);
      DatabaseAuthenticationProvider p = provider(caseSensitive);
      AuthenticationChain chain = new AuthenticationChain();
      chain.setProviders(List.of(p));

      assertFalse(login(p, "BOB", "pw-bob"), "BOB must not log in with bob's password");
      assertFalse(login(p, "bob", "pw-BOB"), "bob must not log in with BOB's password");
      assertTrue(login(p, "BOB", "pw-BOB"), "BOB must log in with its own password");
      assertTrue(login(p, "bob", "pw-bob"), "bob must log in with its own password");
      assertFalse(login(chain, "BOB", "pw-bob"));
      assertTrue(login(chain, "BOB", "pw-BOB"));
   }

   @Test
   void caseVariantRows_noExactMatch_refused() throws Exception {
      createDb(true, "VARCHAR(50)", new String[][] { { "bob", "pw-bob" }, { "BOB", "pw-BOB" } });
      DatabaseAuthenticationProvider p = provider(false);

      assertFalse(login(p, "Bob", "pw-bob"));
      assertFalse(login(p, "Bob", "pw-BOB"));
   }

   @Test
   void caseVariantRows_queryUserReturnsExactUserRow() throws Exception {
      createDb(true, "VARCHAR(50)", new String[][] { { "bob", "pw-bob" }, { "BOB", "pw-BOB" } });
      DatabaseAuthenticationProvider p = provider(true);

      assertEquals("BOB", p.queryUser(new IdentityID("BOB", org)).get("name"));
      assertEquals("bob", p.queryUser(new IdentityID("bob", org)).get("name"));
   }

   @Test
   void identicalDuplicateRows_accepted() throws Exception {
      createDb(false, "VARCHAR(50)", new String[][] { { "bob", "pw-bob" }, { "bob", "pw-bob" } });
      DatabaseAuthenticationProvider p = provider(true);

      assertTrue(login(p, "bob", "pw-bob"));
      assertFalse(login(p, "bob", "wrong"));
      assertEquals("bob", p.queryUser(new IdentityID("bob", org)).get("name"));
   }

   @Test
   void identicalDuplicateRowsWithCaseVariant_accepted() throws Exception {
      createDb(true, "VARCHAR(50)",
               new String[][] { { "bob", "pw-bob" }, { "BOB", "pw-BOB" }, { "bob", "pw-bob" } });
      DatabaseAuthenticationProvider p = provider(true);

      assertTrue(login(p, "bob", "pw-bob"));
      assertFalse(login(p, "BOB", "pw-bob"));
      assertTrue(login(p, "BOB", "pw-BOB"));
   }

   @Test
   void conflictingDuplicateRows_refused() throws Exception {
      createDb(false, "VARCHAR(50)", new String[][] { { "bob", "pw1" }, { "bob", "pw2" } });
      DatabaseAuthenticationProvider p = provider(true);

      assertFalse(login(p, "bob", "pw1"));
      assertFalse(login(p, "bob", "pw2"));
      Map<String, Object> row = p.queryUser(new IdentityID("bob", org));
      assertTrue(row == null || row.isEmpty());
   }

   @Test
   void charColumnPadding_exactUserMatched() throws Exception {
      createDb(true, "CHAR(10)", new String[][] { { "bob", "pw-bob" }, { "BOB", "pw-BOB" } });
      DatabaseAuthenticationProvider p = provider(true);

      assertFalse(login(p, "BOB", "pw-bob"));
      assertTrue(login(p, "BOB", "pw-BOB"));
      assertTrue(login(p, "bob", "pw-bob"));
   }

   @Test
   void singleRow_behaviorUnchanged() throws Exception {
      createDb(true, "VARCHAR(50)", new String[][] { { "bob", "pw-bob" } });
      DatabaseAuthenticationProvider p = provider(true);

      // a single row is used as is, even when its first column differs from the bound name
      assertTrue(login(p, "bob", "pw-bob"));
      assertTrue(login(p, "BOB", "pw-bob"));
      assertFalse(login(p, "bob", "wrong"));
      assertEquals("bob", p.queryUser(new IdentityID("BOB", org)).get("name"));
   }

   @Test
   void singleRow_firstColumnNotRawName_behaviorUnchanged() throws Exception {
      createDb(false, "VARCHAR(50)", new String[][] { { "bob", "pw-bob" } });
      DatabaseAuthenticationProvider p = provider(true);
      p.setUserQuery("SELECT UPPER(NAME), PW FROM U WHERE NAME = ?");

      assertTrue(login(p, "bob", "pw-bob"));
      assertFalse(login(p, "bob", "wrong"));
   }

   @Test
   void caseSensitiveCollation_distinctUsers() throws Exception {
      createDb(false, "VARCHAR(50)", new String[][] { { "bob", "pw-bob" }, { "BOB", "pw-BOB" } });
      DatabaseAuthenticationProvider p = provider(true);

      assertFalse(login(p, "BOB", "pw-bob"));
      assertTrue(login(p, "BOB", "pw-BOB"));
      assertTrue(login(p, "bob", "pw-bob"));
   }

   private void createDb(boolean ciCollation, String nameType, String[][] users) throws Exception {
      url = "jdbc:derby:memory:collation77106_" + (dbCounter++) + ";create=true" +
         (ciCollation ? ";territory=en_US;collation=TERRITORY_BASED:PRIMARY" : "");

      try(Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
         s.execute("CREATE TABLE U (NAME " + nameType + " NOT NULL, PW VARCHAR(50))");

         try(PreparedStatement ps = c.prepareStatement("INSERT INTO U VALUES (?, ?)")) {
            for(String[] user : users) {
               ps.setString(1, user[0]);
               ps.setString(2, user[1]);
               ps.executeUpdate();
            }
         }
      }
   }

   private DatabaseAuthenticationProvider provider(boolean caseSensitive) throws Exception {
      String oldCaseSensitive = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      DatabaseAuthenticationProvider p;

      try {
         // the cache-backed user list is empty without Ignite, so read the database directly
         SreeEnv.setProperty("security.cache", "false");
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         p = new DatabaseAuthenticationProvider();
      }
      finally {
         restore("security.cache", oldCache);
         restore("security.user.caseSensitive", oldCaseSensitive);
      }

      p.setMultiTenantSupplier(() -> false);
      p.setRequiresLogin(false);
      p.setHashAlgorithm("None");
      p.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
      p.setUserListQuery("SELECT NAME FROM U");
      p.setUserRolesQuery("");
      p.setRoleListQuery("");
      p.setGroupListQuery("");
      ConnectionProvider cp = mock(ConnectionProvider.class);
      when(cp.getConnection()).thenAnswer(inv -> DriverManager.getConnection(url));
      Field f = DatabaseAuthenticationProvider.class.getDeclaredField("connectionProvider");
      f.setAccessible(true);
      f.set(p, cp);
      provider = p;
      return p;
   }

   private static void restore(String name, String value) {
      if(value == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, value);
      }
   }

   private boolean login(AuthenticationProvider p, String name, String password) {
      IdentityID id = new IdentityID(name, org);
      return p.authenticate(id, new DefaultTicket(id, password));
   }

   private static int dbCounter = 0;
   private String url;
   private DatabaseAuthenticationProvider provider;
   private final String org = Organization.getDefaultOrganizationID();
}
