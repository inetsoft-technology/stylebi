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

import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77108: when a case-insensitive database collation makes the name of one user match the
 * rows of other users (for example "bob" and "BOB", or organization IDs "acme" and "ACME"), the
 * user roles and user emails queries return the rows of all those users. The database provider
 * must then load no roles and no emails for the ambiguous name, instead of merging them. Runs
 * against in-memory Derby databases with a case-insensitive collation, using the real
 * {@link AuthenticationDAO}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseAuthenticationRoleCollationTest extends DatabaseAuthenticationCollationTestBase {
   @ParameterizedTest(name = "caseSensitive={0}")
   @ValueSource(booleans = { true, false })
   void caseVariantUsers_rolesAndEmailsNotMerged(boolean caseSensitive) throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(caseSensitive);
      AuthenticationChain chain = new AuthenticationChain();
      chain.setProviders(List.of(p));

      for(String name : new String[] { "bob", "BOB" }) {
         assertEquals(List.of(), roles(p.getRoles(id(name))), name + " roles");
         assertEquals(List.of(), sorted(p.getEmails(id(name))), name + " emails");
         assertEquals(List.of(), roles(chain.getRoles(id(name))), name + " chain roles");
         assertEquals(List.of(), sorted(chain.getEmails(id(name))), name + " chain emails");
      }

      User bob = p.getUser(id("bob"));
      assertNotNull(bob);
      assertEquals(List.of(), roles(bob.getRoles()), "getUser(bob) roles");
      assertEquals(List.of(), sorted(bob.getEmails()), "getUser(bob) emails");

      // an unambiguous user on the same database is unchanged
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));

      // #77106: both variants still log in with their own password
      assertTrue(login(p, "bob", "pw-bob"));
      assertTrue(login(p, "BOB", "pw-BOB"));
   }

   @Test
   void caseSensitiveDatabase_rolesAndEmailsUnchanged() throws Exception {
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);

      assertEquals(List.of("role-bob"), roles(p.getRoles(id("bob"))));
      assertEquals(List.of("Administrator"), roles(p.getRoles(id("BOB"))));
      assertEquals(List.of("bob@x"), sorted(p.getEmails(id("bob"))));
      assertEquals(List.of("BOB@y"), sorted(p.getEmails(id("BOB"))));
      assertEquals(List.of("role-bob"), roles(p.getUser(id("bob")).getRoles()));
   }

   @Test
   void transformedNameColumn_rolesAndEmailsNotMerged() throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      // column 1 is the same for both rows, the credentials tell the users apart
      p.setUserQuery("SELECT UPPER(NAME), PW FROM U WHERE NAME = ?");

      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
      assertEquals(List.of(), sorted(p.getEmails(id("bob"))));
      assertEquals(List.of(), roles(p.getUser(id("bob")).getRoles()));
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
   }

   @Test
   void duplicateRowsOfOneUser_rolesUnchanged() throws Exception {
      createSingleTenantDb(true);
      exec("INSERT INTO UR VALUES ('alice', 'r-alice2')");
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      // the join returns one identical row per role of the user
      p.setUserQuery("SELECT U.NAME, U.PW FROM U, UR WHERE U.NAME = UR.USER_NAME AND U.NAME = ?");

      assertEquals(List.of("r-alice", "r-alice2"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));
      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
   }

   @Test
   void caseInsensitiveUsersQuery_distinctUsersFailClosed() throws Exception {
      // Deliberate: when the users query itself treats "bob" and "BOB" as one name, the roles
      // and emails of both are withheld, even on a case-sensitive database.
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      p.setUserQuery("SELECT NAME, PW FROM U WHERE UPPER(NAME) = UPPER(?)");

      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
      assertEquals(List.of(), roles(p.getRoles(id("BOB"))));
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertTrue(login(p, "bob", "pw-bob"));
   }

   @Test
   void caseVariantOrganizations_rolesAndEmailsNotMerged() throws Exception {
      createMultiTenantDb(true);
      DatabaseAuthenticationProvider p = multiTenantProvider();

      for(String org : new String[] { "acme", "ACME" }) {
         IdentityID bob = new IdentityID("bob", org);
         assertEquals(List.of(), roles(p.getRoles(bob)), "roles of bob@" + org);
         assertEquals(List.of(), sorted(p.getEmails(bob)), "emails of bob@" + org);
      }
   }

   @Test
   void caseSensitiveMultiTenant_rolesAndEmailsUnchanged() throws Exception {
      createMultiTenantDb(false);
      DatabaseAuthenticationProvider p = multiTenantProvider();
      IdentityID bob = new IdentityID("bob", "acme");

      assertEquals(List.of("r-acme@acme"), qualifiedRoles(p.getRoles(bob)));
      assertEquals(List.of("bob@acme"), sorted(p.getEmails(bob)));
   }

   @ParameterizedTest(name = "userRoleListQuery={0}")
   @ValueSource(booleans = { false, true })
   void cacheEnabled_rolesAndEmailsNotMerged(boolean userRoleList) throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p =
         cachedSingleTenantProvider(userRoleList ? "SELECT USER_NAME, ROLE_NAME FROM UR" : null);
      assertTrue(p.isCacheEnabled());
      AuthenticationChain chain = new AuthenticationChain();
      chain.setProviders(List.of(p));

      // The cache loads the roles of each user through the checked DAO lookup. The bulk user
      // role list leaves out bob and BOB, since the database may have merged their rows
      // (Bug #78252), so they are loaded through the checked lookup too.
      List<String> bobRoles = List.of();
      List<String> upperBobRoles = List.of();

      for(int i = 0; i < 2; i++) {
         assertEquals(bobRoles, roles(p.getRoles(id("bob"))), "bob roles, call " + i);
         assertEquals(upperBobRoles, roles(p.getRoles(id("BOB"))), "BOB roles, call " + i);
         assertEquals(bobRoles, roles(chain.getRoles(id("bob"))), "bob chain roles, call " + i);
         assertEquals(List.of(), sorted(p.getEmails(id("bob"))), "bob emails, call " + i);
         assertEquals(List.of(), sorted(p.getEmails(id("BOB"))), "BOB emails, call " + i);
      }

      User bob = p.getUser(id("bob"));
      assertNotNull(bob);
      assertEquals(bobRoles, roles(bob.getRoles()), "getUser(bob) roles");
      assertEquals(List.of(), sorted(bob.getEmails()), "getUser(bob) emails");
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));
   }

   @Test
   void unambiguousUsers_rolesUnchanged() throws Exception {
      createSingleTenantDb(true);
      exec("INSERT INTO U VALUES ('dave', 'pw-dave')",
           "INSERT INTO UR VALUES ('carol', 'r-carol')",
           "INSERT INTO UE VALUES ('carol', 'carol@z')");
      DatabaseAuthenticationProvider p = singleTenantProvider(true);

      // a user with no row in the users query is not ambiguous
      assertEquals(List.of("r-carol"), roles(p.getRoles(id("carol"))));
      assertEquals(List.of("carol@z"), sorted(p.getEmails(id("carol"))));
      // a user with no roles and no emails is unchanged
      assertEquals(List.of(), roles(p.getRoles(id("dave"))));
      assertEquals(List.of(), sorted(p.getEmails(id("dave"))));
      assertTrue(login(p, "dave", "pw-dave"));
   }
}
