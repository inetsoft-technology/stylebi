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
package inetsoft.sree.security.ldap;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.SreeHome;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import javax.naming.directory.*;
import javax.naming.ldap.LdapContext;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome()
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("slow")
class GenericLdapAuthenticationProviderTest {
   @BeforeAll()
   static void startLdapServer() {
      ldapContainer = new ApacheDSContainer();
      ldapContainer.start();
   }

   @BeforeEach
   void createProvider() throws Exception {
      provider = new GenericLdapAuthenticationProvider();
      provider.setProtocol("ldap");
      provider.setHost("localhost");
      provider.setPort(ldapContainer.getMappedPort(10389));
      provider.setRootDn("dc=example,dc=com");
      provider.setLdapAdministrator("uid=admin,ou=system");
      provider.setPassword("secret");
      provider.setSearchSubtree(true);
      provider.setUserSearch("(objectClass=inetOrgPerson)");
      provider.setUserBase("ou=people");
      provider.setUserAttribute("uid");
      provider.setMailAttribute("mail");
      provider.setGroupSearch("(objectClass=organizationalUnit)");
      provider.setGroupBase("ou=people");
      provider.setGroupAttribute("ou");
      provider.setRoleSearch("(objectClass=groupOfUniqueNames)");
      provider.setRoleBase("ou=groups");
      provider.setRoleAttribute("cn");
      provider.setUserRolesSearch("(&(objectClass=groupOfUniqueNames)(uniqueMember={1}))");
      provider.setRoleRolesSearch("(&(objectClass=groupOfUniqueNames)(uniqueMember={1}))");
      provider.setGroupRolesSearch("(&(objectClass=groupOfUniqueNames)(uniqueMember={1}))");
      provider.setProviderName(String.format("LdapTest%d", testCounter++));

      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(provider));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authcChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();

      SecurityEngine.getSecurity().init();
   }

   @AfterEach
   void destroyProvider() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   @AfterAll
   static void stopLdapServer() {
      ldapContainer.stop();
   }

   @Test
   @Order(1)
   void testAuthenticate() {
      IdentityID uid = new IdentityID("candacegriffin", "host-org");
      DefaultTicket ticket = new DefaultTicket(uid, "secret");
      boolean actual = provider.authenticate(uid, ticket);
      assertTrue(actual);
   }

   @Test
   @Order(2)
   void testAuthenticateFailure() {
      IdentityID uid = new IdentityID("candacegriffin", "host-org");
      DefaultTicket ticket = new DefaultTicket(uid, "incorrect");
      boolean actual = provider.authenticate(uid, ticket);
      assertFalse(actual);
   }

   @Test
   @Order(3)
   void testGetUser() {
      waitForCache();

      String expectedName = "candacegriffin";
      String[] expectedEmails = { "candace.griffin@example.com" };
      String[] expectedGroups = { "IT" };
      IdentityID[] expectedRoles = { new IdentityID("Developer", "host-org"),
                                     new IdentityID("Manager", "host-org"),
                                     new IdentityID("System Administrator", "host-org")};
      String expectedOrg = "host-org";

      // test loading directly from LDAP
      IdentityID uid = new IdentityID("candacegriffin", "host-org");
      User actual = provider.getUser(uid);
      assertNotNull(actual);
      assertEquals(expectedName, actual.getName());
      String[] actualEmails = actual.getEmails();
      assertNotNull(actualEmails);
      Arrays.sort(actualEmails);
      assertArrayEquals(expectedEmails, actualEmails);
      String[] actualGroups = actual.getGroups();
      assertNotNull(actualGroups);
      Arrays.sort(actualGroups);
      assertArrayEquals(expectedGroups, actualGroups);
      IdentityID[] actualRoles = actual.getRoles();
      assertNotNull(actualRoles);
      Arrays.sort(actualRoles);
      assertArrayEquals(expectedRoles, actualRoles);
      assertEquals(expectedOrg,actual.getOrganizationID());

      // test loading from cache
      actual = provider.getUser(uid);
      assertNotNull(actual);
      assertEquals(expectedName, actual.getName());
      actualEmails = actual.getEmails();
      assertNotNull(actualEmails);
      Arrays.sort(actualEmails);
      assertArrayEquals(expectedEmails, actualEmails);
      actualGroups = actual.getGroups();
      assertNotNull(actualGroups);
      Arrays.sort(actualGroups);
      assertArrayEquals(expectedGroups, actualGroups);
      actualRoles = actual.getRoles();
      assertNotNull(actualRoles);
      Arrays.sort(actualRoles);
      assertArrayEquals(expectedRoles, actualRoles);
      assertEquals(expectedOrg,actual.getOrganizationID());
   }

   @Test
   @Timeout(15)
   @Order(4)
   void testGetUsers() {
      String[] expected = {
         "arturostevenson", "bryanbell", "candacegriffin", "charlieberry", "gladysweaver",
         "ismaelhogan", "kirklamb", "krystalbrock", "kurtgill", "lloydwilson", "mariejones",
         "stevecurry", "vernagordon"
      };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getUsers()).map(id -> id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      waitForCache();

      // test loading from cache
      actual = Arrays.stream(provider.getUsers()).map(id -> id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(5)
   void testGetGroupUsers() {
      waitForCache();

      String[] expected = { "ismaelhogan", "kurtgill", "lloydwilson", "stevecurry" };

      // test loading directly from LDAP
      IdentityID groupID = new IdentityID("Sales",Organization.getDefaultOrganizationID());
      String[] actual = Arrays.stream(provider.getUsers(groupID)).map(id -> id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      // test loading from cache
      actual = Arrays.stream(provider.getUsers(groupID)).map(id -> id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @SuppressWarnings("deprecation")
   @Test
   @Timeout(15)
   @Order(6)
   void testGetEmails() {
      waitForCache();

      String[] expected = { "candace.griffin@example.com" };

      // test loading directly from LDAP
      IdentityID uid = new IdentityID("candacegriffin", "host-org");
      String[] actual = provider.getEmails(uid);
      assertArrayEquals(expected, actual);

      // test loading from cache
      actual = provider.getEmails(uid);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(7)
   void testGetIndividualUsers() {
      String[] expected = { "arturostevenson", "vernagordon" };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getIndividualUsers()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      waitForCache();

      // test loading from cache
      actual = Arrays.stream(provider.getIndividualUsers()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(8)
   void testGetIndividualEmailAddresses() {
      String[] expected = { "arturo.stevenson@example.com", "verna.gordon@example.com" };

      // test loading directly from LDAP
      String[] actual = provider.getIndividualEmailAddresses();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      waitForCache();

      // test loading from cache
      actual = provider.getIndividualEmailAddresses();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(9)
   void testGetRoles() {
      String[] expected = {
         "Developer", "Manager", "Salesperson", "Strategist", "Support Engineer",
         "System Administrator"
      };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getRoles()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      waitForCache();

      // test loading from cache
      actual = Arrays.stream(provider.getRoles()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(10)
   void testGetUserRoles() {
      waitForCache();

      String[] expected = { "Developer", "Manager", "System Administrator" };

      // test loading directly from LDAP

      IdentityID uid = new IdentityID("candacegriffin", "host-org");
      String[] actual = Arrays.stream(provider.getRoles(uid)).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      // test loading from cache
      actual = Arrays.stream(provider.getRoles(uid)).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(11)
   void testGetRole() {
      waitForCache();

      String expectedName = "Support Engineer";
      IdentityID[] expectedRoles = { new IdentityID("Developer", "host-org") };
      String expectedOrganization = "host-org";

      // test loading directly from LDAP
      IdentityID roleID = new IdentityID("Support Engineer", "host-org");
      Role actual = provider.getRole(roleID);
      assertNotNull(actual);
      assertEquals(expectedName, actual.getName());
      assertArrayEquals(expectedRoles, actual.getRoles());
      assertEquals(expectedOrganization, actual.getOrganizationID());

      // test loading from cache
      actual = provider.getRole(roleID);
      assertEquals(expectedName, actual.getName());
      assertArrayEquals(expectedRoles, actual.getRoles());
      assertEquals(expectedOrganization, actual.getOrganizationID());
   }

   @Test
   @Timeout(15)
   @Order(12)
   void testGetGroups() {
      String[] expected = { "IT", "Marketing", "Sales", "Support" };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getGroups()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      waitForCache();

      // test loading from cache
      actual = Arrays.stream(provider.getGroups()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(13)
   void testGetUserGroups() {
      waitForCache();

      String[] expected = { "IT", "Support" };

      // test loading directly from LDAP
      IdentityID uid = new IdentityID("mariejones", Organization.getDefaultOrganizationID());
      String[] actual = provider.getUserGroups(uid);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      // test loading from cache
      actual = provider.getUserGroups(uid);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(14)
   void testGetGroup() {
      waitForCache();

      String expectedName = "Support";
      String[] expectedGroups = { "IT" };
      IdentityID[] expectedRoles = { new IdentityID("Support Engineer", "host-org") };
      String expectedOrg = Organization.getDefaultOrganizationID();

      // test loading directly from LDAP
      IdentityID groupID = new IdentityID("Support", Organization.getDefaultOrganizationID());
      Group actual = provider.getGroup(groupID);
      assertEquals(expectedName, actual.getName());
      assertArrayEquals(expectedGroups, actual.getGroups());
      assertArrayEquals(expectedRoles, actual.getRoles());
      assertEquals(expectedOrg, actual.getOrganizationID());

      // test loading from cache
      provider.getGroup(groupID);
      assertEquals(expectedName, actual.getName());
      assertArrayEquals(expectedGroups, actual.getGroups());
      assertArrayEquals(expectedRoles, actual.getRoles());
      assertEquals(expectedOrg, actual.getOrganizationID());
   }

   @Test
   @Timeout(15)
   @Order(15)
   void testGetOrganization() {
      waitForCache();

      Organization expected = new Organization("host-org");
      expected.setName(Organization.getDefaultOrganizationName());

      Organization actual = provider.getOrganization("host-org");
      assertNotNull(actual);
      assertEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(16)
   void testReloadOnUpdate() throws Exception {
      waitForCache();

      String[] expected = { "IT", "Marketing", "Sales", "Support" };

      // verify initial state, overlaps with testGetGroups()
      String[] actual = Arrays.stream(provider.getGroups()).map(id -> id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      LdapContext context = null;

      try {
         // make change in LDAP and wait for reload
         context = provider.createContext();
         Attributes newEntry = new BasicAttributes();
         newEntry.put(new BasicAttribute("objectClass", "top"));
         newEntry.put(new BasicAttribute("objectClass", "organizationalUnit"));
         newEntry.put(new BasicAttribute("ou", "Testing"));

         context.createSubcontext("ou=Testing,ou=people", newEntry);

         Thread.sleep(1000L);

         Awaitility.await()
            .atMost(Duration.ofMinutes(1L))
            .pollInterval(Duration.ofMillis(500L))
            .until(() -> !provider.isLoading());

         expected = new String[] { "IT", "Marketing", "Sales", "Support", "Testing" };
         actual = Arrays.stream(provider.getGroups()).map(id -> id.name).toArray(String[]::new);
         assertNotNull(actual);
         Arrays.sort(actual);
         assertArrayEquals(expected, actual);
      }
      finally {
         if(context != null) {
            context.close();
         }
      }
   }

   private void waitForCache() {
      Awaitility.await()
         .atMost(Duration.ofMinutes(1L))
         .pollInterval(Duration.ofMillis(500L))
         .until(provider::isCacheInitialized);
   }

   private static ApacheDSContainer ldapContainer;
   private GenericLdapAuthenticationProvider provider;
   private static int testCounter = 1;
}