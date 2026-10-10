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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78252: the check of Bug #77108, which refuses the roles and emails of a user whose name
 * the database collation merges with another user's name, must not depend on the users query
 * alone. The users query can be blank, fail, return a transformed first column, or compare
 * names under a different collation than the roles and emails tables. The roles and emails
 * queries are therefore run for listed users with a similar name, and the bulk user role list
 * leaves those users out. A failed check loads nothing and is not cached.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseAuthenticationMergedUserTest extends DatabaseAuthenticationCollationTestBase {
   @ParameterizedTest(name = "usersQuery=''{0}''")
   @ValueSource(strings = { "", "   " })
   void blankUsersQuery_rolesAndEmailsNotMerged(String usersQuery) throws Exception {
      createSingleTenantDb(true);

      for(boolean caseSensitive : new boolean[] { true, false }) {
         DatabaseAuthenticationProvider p = singleTenantProvider(caseSensitive);
         p.setUserQuery(usersQuery);

         for(String name : new String[] { "bob", "BOB" }) {
            assertEquals(List.of(), roles(p.getRoles(id(name))), name + " roles");
            assertEquals(List.of(), sorted(p.getEmails(id(name))), name + " emails");
         }

         assertEquals(List.of(), roles(p.getUser(id("bob")).getRoles()), "getUser(bob) roles");
         assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
         assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));
         tearDown();
      }
   }

   @Test
   void blankUsersQuery_caseVariantOrganizationsNotMerged() throws Exception {
      createMultiTenantDb(true);
      DatabaseAuthenticationProvider p = multiTenantProvider();
      p.setUserQuery("");

      for(String org : new String[] { "acme", "ACME" }) {
         IdentityID bob = new IdentityID("bob", org);
         assertEquals(List.of(), roles(p.getRoles(bob)), "roles of bob@" + org);
         assertEquals(List.of(), sorted(p.getEmails(bob)), "emails of bob@" + org);
      }
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "default", "blankUsersQuery", "cachedJoinedUserRoleList" })
   void caseSensitiveDatabase_distinctVariantsKeepOwnRoles(String setup) throws Exception {
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = switch(setup) {
         case "blankUsersQuery" -> {
            DatabaseAuthenticationProvider provider = singleTenantProvider(true);
            provider.setUserQuery("");
            yield provider;
         }
         case "cachedJoinedUserRoleList" -> cachedSingleTenantProvider(JOINED_USER_ROLE_LIST);
         default -> singleTenantProvider(true);
      };

      for(int i = 0; i < 2; i++) {
         assertEquals(List.of("role-bob"), roles(p.getRoles(id("bob"))), "bob roles, call " + i);
         assertEquals(List.of("Administrator"), roles(p.getRoles(id("BOB"))),
                      "BOB roles, call " + i);
         assertEquals(List.of("bob@x"), sorted(p.getEmails(id("bob"))), "bob emails, call " + i);
         assertEquals(List.of("BOB@y"), sorted(p.getEmails(id("BOB"))), "BOB emails, call " + i);
      }

      assertEquals(List.of("role-bob"), roles(p.getUser(id("bob")).getRoles()));
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
   }

   @ParameterizedTest(name = "cache={0}")
   @ValueSource(booleans = { false, true })
   void usersQueryError_failsClosedAndIsNotCached(boolean cache) throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p =
         cache ? cachedSingleTenantProvider(null) : singleTenantProvider(true);
      assertEquals(cache, p.isCacheEnabled());

      // a transient error of the users query only: the roles and emails tables still work
      exec("RENAME TABLE U TO U_OFF");

      try {
         assertEquals(List.of(), roles(p.getRoles(id("bob"))), "bob roles during the error");
         assertEquals(List.of(), sorted(p.getEmails(id("bob"))), "bob emails during the error");
         assertEquals(List.of(), roles(p.getRoles(id("alice"))), "alice roles during the error");
         assertEquals(List.of(), sorted(p.getEmails(id("alice"))),
                      "alice emails during the error");
         assertTrue(p.getDao().getRoles(id("alice")).failed());
         assertTrue(p.getDao().getEmails(id("alice")).failed());
      }
      finally {
         exec("RENAME TABLE U_OFF TO U");
      }

      // the failed lookups were not cached: once the error clears, the checked result is served
      assertEquals(List.of(), roles(p.getRoles(id("bob"))), "bob roles after the error");
      assertEquals(List.of(), sorted(p.getEmails(id("bob"))), "bob emails after the error");
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))),
                   "alice roles after the error");
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))),
                   "alice emails after the error");
      assertEquals(List.of(), roles(p.getUser(id("bob")).getRoles()), "getUser(bob) roles");
   }

   @Test
   void cacheEnabled_joinedUserRoleList_rolesNotMerged() throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = cachedSingleTenantProvider(JOINED_USER_ROLE_LIST);
      AuthenticationChain chain = new AuthenticationChain();
      chain.setProviders(List.of(p));

      for(int i = 0; i < 2; i++) {
         assertEquals(List.of(), roles(p.getRoles(id("bob"))), "bob roles, call " + i);
         assertEquals(List.of(), roles(p.getRoles(id("BOB"))), "BOB roles, call " + i);
         assertEquals(List.of(), roles(chain.getRoles(id("bob"))), "bob chain roles, call " + i);
      }

      assertEquals(List.of(), roles(p.getUser(id("bob")).getRoles()), "getUser(bob) roles");
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertNoMergedRoles(p.getAllUserRoles());
   }

   @Test
   void joinedUserRoleList_allUserRolesNotMerged() throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      p.setUserRoleListQuery(JOINED_USER_ROLE_LIST);
      assertFalse(p.isCacheEnabled());

      Map<IdentityID, IdentityID[]> allUserRoles = p.getAllUserRoles();
      assertEquals(List.of("r-alice"), roles(allUserRoles.get(id("alice"))));
      assertNoMergedRoles(allUserRoles);
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "sameCredential", "nullCredential", "nameOnly" })
   void transformedNameColumn_noCredentialDifference_rolesAndEmailsNotMerged(String shape)
      throws Exception
   {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);

      switch(shape) {
         case "sameCredential" -> {
            exec("UPDATE U SET PW = 'same' WHERE NAME = 'bob'");
            p.setUserQuery("SELECT UPPER(NAME), PW FROM U WHERE NAME = ?");
         }
         case "nullCredential" -> {
            exec("UPDATE U SET PW = NULL WHERE NAME = 'bob'");
            p.setUserQuery("SELECT UPPER(NAME), PW FROM U WHERE NAME = ?");
         }
         default -> p.setUserQuery("SELECT UPPER(NAME) FROM U WHERE NAME = ?");
      }

      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
      assertEquals(List.of(), sorted(p.getEmails(id("bob"))));
      assertEquals(List.of(), roles(p.getRoles(id("BOB"))));
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
   }

   @Test
   void caseInsensitiveRolesTables_caseSensitiveUsersTable_rolesAndEmailsNotMerged()
      throws Exception
   {
      // Derby has one collation per database: the users table compares exactly, and the roles
      // and emails queries compare like a case-insensitive column collation would
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE UPPER(USER_NAME) = UPPER(?)");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE UPPER(USER_NAME) = UPPER(?)");

      for(String name : new String[] { "bob", "BOB" }) {
         assertEquals(List.of(), roles(p.getRoles(id(name))), name + " roles");
         assertEquals(List.of(), sorted(p.getEmails(id(name))), name + " emails");
      }

      assertEquals(List.of(), roles(p.getUser(id("bob")).getRoles()), "getUser(bob) roles");
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));
      assertTrue(login(p, "bob", "pw-bob"));
   }

   @ParameterizedTest(name = "caseInsensitive={0}")
   @ValueSource(booleans = { true, false })
   void caseVariantOrganizations_membersNotMerged(boolean caseInsensitive) throws Exception {
      createMultiTenantDb(caseInsensitive);
      exec("INSERT INTO U VALUES ('ACME', 'eve', 'pw-eve')");
      DatabaseAuthenticationProvider p = multiTenantProvider();
      p.setOrganizationMembersQuery("SELECT NAME FROM U WHERE ORG_ID = ?");
      List<String> acme = caseInsensitive ? List.of() : List.of("bob");
      List<String> upperAcme = caseInsensitive ? List.of() : List.of("bob", "eve");

      assertEquals(acme, sorted(p.getOrganizationMembers("acme")));
      assertEquals(upperAcme, sorted(p.getOrganizationMembers("ACME")));
      assertEquals(acme, sorted(p.getOrganization("acme").getMembers()));
   }

   @ParameterizedTest(name = "caseInsensitive={0}")
   @ValueSource(booleans = { true, false })
   void cacheLoader_joinedUserRoleList_caseVariantsLeftOut(boolean caseInsensitive)
      throws Exception
   {
      createSingleTenantDb(caseInsensitive);
      DatabaseAuthenticationProvider p = singleTenantProvider(true, true);
      p.setUserRoleListQuery(JOINED_USER_ROLE_LIST);
      loadCache(p);
      Map<IdentityID, IdentityArray> loaded = Cluster.getInstance().getReplicatedMap(
         "DatabaseSecurity:" + p.getProviderName() + ".userRoles");

      // the loader passes its user list to the bulk read, which leaves out bob and BOB, whose
      // roles are then checked one user at a time, and keeps users without a case variant
      assertFalse(loaded.containsKey(id("bob")), "bob preloaded");
      assertFalse(loaded.containsKey(id("BOB")), "BOB preloaded");
      assertEquals(List.of("r-alice"), roles(loaded.get(id("alice")).getValue()));

      for(int i = 0; i < 2; i++) {
         assertEquals(caseInsensitive ? List.of() : List.of("role-bob"),
                      roles(p.getRoles(id("bob"))), "bob roles, call " + i);
         assertEquals(caseInsensitive ? List.of() : List.of("Administrator"),
                      roles(p.getRoles(id("BOB"))), "BOB roles, call " + i);
         assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      }
   }

   @ParameterizedTest(name = "caseInsensitive={0}")
   @ValueSource(booleans = { true, false })
   void cacheLoader_caseVariantOrganizations_membersNotMerged(boolean caseInsensitive)
      throws Exception
   {
      createMultiTenantDb(caseInsensitive);
      exec("INSERT INTO U VALUES ('ACME', 'eve', 'pw-eve')");
      DatabaseAuthenticationProvider p = multiTenantProvider(true);
      p.setOrganizationMembersQuery("SELECT NAME FROM U WHERE ORG_ID = ?");
      // the cache load fails without a group list query
      p.setGroupListQuery("SELECT ROLE_NAME, ORG_ID FROM UR WHERE 1 = 0");
      loadCache(p);

      assertEquals(caseInsensitive ? List.of() : List.of("bob"),
                   sorted(p.getOrganizationMembers("acme")));
      assertEquals(caseInsensitive ? List.of() : List.of("bob", "eve"),
                   sorted(p.getOrganizationMembers("ACME")));
   }

   @Test
   void sameNameInUnrelatedOrganizations_rolesAndEmailsKept() throws Exception {
      createMultiTenantDb(true);
      exec("DELETE FROM O", "DELETE FROM U", "DELETE FROM UR", "DELETE FROM UE",
           "INSERT INTO O VALUES ('acme', 'Acme'), ('beta', 'Beta')",
           "INSERT INTO U VALUES ('acme', 'bob', 'pw-1'), ('beta', 'bob', 'pw-2')",
           "INSERT INTO UR VALUES ('acme', 'bob', 'Designer'), ('beta', 'bob', 'Designer')",
           "INSERT INTO UE VALUES ('acme', 'bob', 'bob@z'), ('beta', 'bob', 'bob@z')");
      DatabaseAuthenticationProvider p = multiTenantProvider();

      // the same name and the same values in organizations with unrelated IDs are no candidates
      for(String org : new String[] { "acme", "beta" }) {
         IdentityID bob = new IdentityID("bob", org);
         assertEquals(List.of("Designer"), roles(p.getRoles(bob)), "roles of bob@" + org);
         assertEquals(List.of("bob@z"), sorted(p.getEmails(bob)), "emails of bob@" + org);
      }
   }

   @Test
   void unlistedCaseVariant_rolesAndEmailsNotMerged() throws Exception {
      // e.g. an SSO user "Bob" that the users table does not list, while it lists bob and BOB
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(false);
      p.setUserQuery("");

      assertEquals(List.of(), roles(p.getRoles(id("Bob"))));
      assertEquals(List.of(), sorted(p.getEmails(id("Bob"))));
   }

   @Test
   void accentVariants_rolesNotMerged() throws Exception {
      createSingleTenantDb(true);
      exec("INSERT INTO U VALUES ('jose', 'pw-1'), ('josé', 'pw-2')",
           "INSERT INTO UR VALUES ('jose', 'r-jose'), ('josé', 'Administrator')");
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      p.setUserQuery("");

      assertEquals(List.of(), roles(p.getRoles(id("jose"))));
      assertEquals(List.of(), roles(p.getRoles(id("josé"))));
   }

   @ParameterizedTest(name = "cache={0}")
   @ValueSource(booleans = { false, true })
   void caseSensitiveDatabase_differentRoleSets_keepOwnRoles(boolean cache) throws Exception {
      createSingleTenantDb(false);
      exec("DELETE FROM UR WHERE USER_NAME IN ('bob', 'BOB')",
           "INSERT INTO UR VALUES ('bob', 'Everyone'), ('bob', 'role-bob'), ('BOB', 'Everyone')");
      DatabaseAuthenticationProvider p = singleTenantProvider(true, cache);

      if(cache) {
         loadCache(p);
      }

      // overlapping but different role sets tell the names apart
      assertEquals(List.of("Everyone", "role-bob"), roles(p.getRoles(id("bob"))));
      assertEquals(List.of("Everyone"), roles(p.getRoles(id("BOB"))));
   }

   /**
    * Fills the replicated maps of the provider's cache with the real cache loader. Its
    * {@code connect()} looks the provider up in a configured SecurityEngine, so the provider is
    * set directly and the private load is called, and a stub service stands in for the
    * cluster singleton.
    */
   private void loadCache(DatabaseAuthenticationProvider p) throws Exception {
      assertTrue(p.isCacheEnabled());
      String name = "mergeduser_cache_" + (cacheCounter++);
      DatabaseAuthenticationCacheServiceImpl loader =
         new DatabaseAuthenticationCacheServiceImpl(name);
      loader.init();

      try {
         Field provider = DatabaseAuthenticationCacheServiceImpl.class.getDeclaredField("provider");
         provider.setAccessible(true);
         provider.set(loader, p);
         Method load = DatabaseAuthenticationCacheServiceImpl.class
            .getDeclaredMethod("loadInternal", boolean.class);
         load.setAccessible(true);
         load.invoke(loader, false);
         assertTrue(loader.isInitialized(), "cache loaded");
      }
      finally {
         loader.cancel();
      }

      DatabaseAuthenticationCacheService service = mock(DatabaseAuthenticationCacheService.class);
      when(service.isInitialized()).thenReturn(true);
      Cluster.getInstance().getSingletonService(
         "DatabaseSecurity:" + name, DatabaseAuthenticationCacheService.class, () -> service);
      p.setProviderName(name);
   }

   private static int cacheCounter = 0;
}
