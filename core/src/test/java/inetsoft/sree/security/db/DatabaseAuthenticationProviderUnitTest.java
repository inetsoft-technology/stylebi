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
package inetsoft.sree.security.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Cases deferred - require integration context (Derby DB + SecurityEngine.init()):
 *
 * [DatabaseAuthenticationProvider] authenticate(wrong password)
 *     -> needs real dao.getUserCredential(); covered by DatabaseAuthenticationProviderTests
 * [DatabaseAuthenticationProvider] getUser(unknown user) / getRoles(unknown org)
 *     -> needs cache + DB round-trip; covered by DatabaseAuthenticationProviderTests
 * [DatabaseAuthenticationProvider] isOrgAdministratorRole - org existence check
 *     -> needs getOrganization() hitting DB; covered by DatabaseAuthenticationProviderTests
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseAuthenticationProviderUnitTest {

   private DatabaseAuthenticationProvider provider;

   @BeforeEach
   void setUp() {
      provider = new DatabaseAuthenticationProvider();
      provider.setRequiresLogin(false);
      provider.setSystemAdministratorRoles(new String[] { "Site Admin" });
      provider.setOrgAdministratorRoles(new String[] { "Org Admin" });
      provider.setHashAlgorithm("bcrypt");
      provider.setUserQuery("SELECT PW_HASH FROM U WHERE NAME=? AND ORG=?");
      provider.setUserListQuery("SELECT NAME, ORG FROM U");
      provider.setGroupListQuery("SELECT NAME, ORG FROM G");
      provider.setGroupUsersQuery("SELECT NAME FROM GU WHERE ORG=? AND GRP=?");
      provider.setOrganizationListQuery("SELECT ID FROM O");
      provider.setOrganizationNameQuery("SELECT NAME FROM O WHERE ID=?");
      provider.setOrganizationMembersQuery("SELECT NAME FROM U WHERE ORG=?");
      provider.setOrganizationRolesQuery("SELECT NAME FROM R WHERE ORG=?");
      provider.setRoleListQuery("SELECT NAME, ORG FROM R");
      provider.setUserRolesQuery("SELECT NAME FROM UR WHERE ORG=? AND USER=?");
      provider.setUserRoleListQuery("SELECT USER, NAME, ORG FROM UR");
      provider.setUserEmailsQuery("SELECT EMAIL FROM U WHERE ORG=? AND NAME=?");
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   // ---- authenticate: null / empty-username guards ----

   @Test
   void authenticate_nullCredential_returnsFalse() {
      assertFalse(provider.authenticate(new IdentityID("user", "org"), null));
   }

   @Test
   void authenticate_emptyUsername_returnsFalse() {
      IdentityID id = new IdentityID("", "org");
      assertFalse(provider.authenticate(id, new DefaultTicket(id, "pass")));
   }

   // ---- getUser / getOrganization: null-identity guards ----

   @Test
   void getUser_nullIdentity_returnsNull() {
      assertNull(provider.getUser(null));
   }

   @Test
   void getOrganization_nullId_returnsNull() {
      assertNull(provider.getOrganization(null));
   }

   // ---- isSystemAdministratorRole ----

   @Test
   void isSystemAdministratorRole_configuredRole_returnsTrue() {
      assertTrue(provider.isSystemAdministratorRole(new IdentityID("Site Admin", null)));
   }

   @Test
   void isSystemAdministratorRole_unknownRole_returnsFalse() {
      assertFalse(provider.isSystemAdministratorRole(new IdentityID("Unknown", null)));
   }

   // ---- isAdminRole (package-private): covers both admin arrays and the false branch ----

   @Test
   void isAdminRole_coversAllThreeBranches() {
      assertTrue(provider.isAdminRole("Site Admin"));    // sysAdmin array
      assertTrue(provider.isAdminRole("Org Admin"));     // orgAdmin array
      assertFalse(provider.isAdminRole("RegularRole"));  // neither
   }

   // ---- hashAlgorithm normalization ----

   @Test
   void setHashAlgorithm_nullOrEmpty_normalizesToNone() {
      provider.setHashAlgorithm(null);
      assertEquals("None", provider.getHashAlgorithm());

      provider.setHashAlgorithm("");
      assertEquals("None", provider.getHashAlgorithm());
   }

   // ---- readConfiguration: missing optional userRoleListQuery field ----

   @Test
   void readConfiguration_missingUserRoleListQuery_defaultsToEmpty() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode config = mapper.createObjectNode();
      config.put("driver", "org.apache.derby.jdbc.EmbeddedDriver");
      config.put("url", "jdbc:derby:memory:unitTest");
      config.put("useCredential", false);
      config.putNull("credential");
      config.put("hashAlgorithm", "bcrypt");
      config.put("appendSalt", true);
      config.put("requiresLogin", false);
      config.put("userQuery", "");
      config.put("groupListQuery", "");
      config.put("userListQuery", "");
      config.put("groupUsersQuery", "");
      config.put("organizationListQuery", "");
      config.put("roleListQuery", "");
      config.put("userRolesQuery", "");
      // userRoleListQuery intentionally absent — exercises the config.has() guard unique to this field
      config.put("userEmailsQuery", "");
      config.put("organizationNameQuery", "");
      config.put("organizationMembersQuery", "");
      config.put("organizationRolesQuery", "");
      config.set("sysAdminRoles", mapper.createArrayNode());
      config.set("orgAdminRoles", mapper.createArrayNode());

      DatabaseAuthenticationProvider fresh = new DatabaseAuthenticationProvider();
      fresh.readConfiguration(config);
      assertEquals("", fresh.getUserRoleListQuery());
      fresh.tearDown();
   }

   // ---- writeConfiguration / readConfiguration round-trip ----

   @Test
   void writeAndReadConfiguration_roundTripsAllQueryFieldsAndRoles() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode config = provider.writeConfiguration(mapper);

      DatabaseAuthenticationProvider fresh = new DatabaseAuthenticationProvider();
      fresh.readConfiguration(config);

      assertEquals(provider.getUserQuery(), fresh.getUserQuery());
      assertEquals(provider.getUserListQuery(), fresh.getUserListQuery());
      assertEquals(provider.getGroupListQuery(), fresh.getGroupListQuery());
      assertEquals(provider.getGroupUsersQuery(), fresh.getGroupUsersQuery());
      assertEquals(provider.getOrganizationListQuery(), fresh.getOrganizationListQuery());
      assertEquals(provider.getRoleListQuery(), fresh.getRoleListQuery());
      assertEquals(provider.getUserRolesQuery(), fresh.getUserRolesQuery());
      assertEquals(provider.getUserRoleListQuery(), fresh.getUserRoleListQuery());
      assertEquals(provider.getUserEmailsQuery(), fresh.getUserEmailsQuery());
      assertEquals(provider.getHashAlgorithm(), fresh.getHashAlgorithm());
      assertEquals(provider.isAppendSalt(), fresh.isAppendSalt());
      assertArrayEquals(provider.getSystemAdministratorRoles(), fresh.getSystemAdministratorRoles());
      assertArrayEquals(provider.getOrgAdministratorRoles(), fresh.getOrgAdministratorRoles());
      fresh.tearDown();
   }
   // ---- Bug #77081: getUser / getOrganization return the stored-case id ----

   @Test
   void getUser_caseInsensitive_returnsStoredCaseIdentity() {
      DatabaseAuthenticationProvider p = stubbedProvider(false);
      User user = p.getUser(new IdentityID("alice", "orga"));

      assertNotNull(user);
      assertEquals("Alice", user.getIdentityID().name);
      assertEquals("OrgA", user.getIdentityID().orgID);
      // the attached lookups are keyed by the stored id, not by the caller's case
      verify(p).getEmails(new IdentityID("Alice", "OrgA"));
   }

   @Test
   void getOrganization_caseInsensitive_returnsStoredCaseId() {
      DatabaseAuthenticationProvider p = stubbedProvider(false);
      Organization org = p.getOrganization("orga");

      assertNotNull(org);
      assertEquals("OrgA", org.getOrganizationID());
      assertEquals("Org A", org.getName());
   }

   @Test
   void getUserAndOrganization_caseSensitive_requireExactMatch() {
      DatabaseAuthenticationProvider p = stubbedProvider(true);

      assertNull(p.getUser(new IdentityID("alice", "orga")));
      assertNull(p.getOrganization("orga"));
      assertEquals(new IdentityID("Alice", "OrgA"),
                   p.getUser(new IdentityID("Alice", "OrgA")).getIdentityID());
      assertEquals("OrgA", p.getOrganization("OrgA").getOrganizationID());
   }

   @Test
   void getUserAndOrganization_unknown_returnNull() {
      DatabaseAuthenticationProvider p = stubbedProvider(false);

      assertNull(p.getUser(new IdentityID("bob", "orga")));
      assertNull(p.getUser(new IdentityID("alice", "orgb")));
      assertNull(p.getOrganization("orgb"));
   }

   private static DatabaseAuthenticationProvider stubbedProvider(boolean caseSensitive) {
      String old = SreeEnv.getProperty("security.user.caseSensitive");
      DatabaseAuthenticationProvider p;

      try {
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         p = spy(new DatabaseAuthenticationProvider());
      }
      finally {
         if(old == null) {
            SreeEnv.remove("security.user.caseSensitive");
         }
         else {
            SreeEnv.setProperty("security.user.caseSensitive", old);
         }
      }

      doReturn(new IdentityID[] { new IdentityID("Alice", "OrgA") }).when(p).getUsers();
      doReturn(new String[] { "OrgA" }).when(p).getOrganizationIDs();
      doReturn(new String[0]).when(p).getEmails(any(IdentityID.class));
      doReturn(new String[0]).when(p).getUserGroups(any(IdentityID.class), anyBoolean());
      doReturn(new IdentityID[0]).when(p).getRoles(any(IdentityID.class));
      doReturn("Org A").when(p).getOrganizationName("OrgA");
      doReturn(new String[0]).when(p).getOrganizationMembers(anyString());
      return p;
   }
}
