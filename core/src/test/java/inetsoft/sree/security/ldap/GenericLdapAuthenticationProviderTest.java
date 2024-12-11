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

import inetsoft.sree.security.*;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;

import javax.naming.directory.*;
import javax.naming.ldap.LdapContext;
import java.util.Arrays;

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
   void createProvider() {
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
      IdentityID uid = new IdentityID("candacegriffin", "selfOrg1");
      DefaultTicket ticket = new DefaultTicket(uid, "secret");
      boolean actual = provider.authenticate(uid, ticket);
      assertTrue(actual);
   }

   @Test
   @Order(2)
   void testAuthenticateFailure() {
      IdentityID uid = new IdentityID("candacegriffin", "selfOrg1");
      DefaultTicket ticket = new DefaultTicket(uid, "incorrect");
      boolean actual = provider.authenticate(uid, ticket);
      assertFalse(actual);
   }

   @Test
   @Order(3)
   void testGetUser() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      String expectedName = "candacegriffin";
      String[] expectedEmails = { "candace.griffin@example.com" };
      String[] expectedGroups = { "IT" };
      IdentityID[] expectedRoles = { new IdentityID("Developer", "selfOrg1"),
                                     new IdentityID("Manager", "selfOrg1"),
                                     new IdentityID("System Administrator", null)};
      String expectedOrg = "selfOrg1";

      // test loading directly from LDAP
      IdentityID uid = new IdentityID("candacegriffin", "selfOrg1");
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
   void testGetUsers() throws InterruptedException {
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

      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      // test loading from cache
      actual = Arrays.stream(provider.getUsers()).map(id -> id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(5)
   void testGetGroupUsers() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

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
   void testGetEmails() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      String[] expected = { "candace.griffin@example.com" };

      // test loading directly from LDAP
      IdentityID uid = new IdentityID("candacegriffin", "selfOrg1");
      String[] actual = provider.getEmails(uid);
      assertArrayEquals(expected, actual);

      // test loading from cache
      actual = provider.getEmails(uid);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(7)
   void testGetIndividualUsers() throws InterruptedException {
      String[] expected = { "arturostevenson", "vernagordon" };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getIndividualUsers()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      // test loading from cache
      actual = Arrays.stream(provider.getIndividualUsers()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(8)
   void testGetIndividualEmailAddresses() throws InterruptedException {
      String[] expected = { "arturo.stevenson@example.com", "verna.gordon@example.com" };

      // test loading directly from LDAP
      String[] actual = provider.getIndividualEmailAddresses();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      // test loading from cache
      actual = provider.getIndividualEmailAddresses();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(9)
   void testGetRoles() throws InterruptedException {
      String[] expected = {
         "Developer", "Manager", "Salesperson", "Strategist", "Support Engineer",
         "System Administrator"
      };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getRoles()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      // test loading from cache
      actual = Arrays.stream(provider.getRoles()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(10)
   void testGetUserRoles() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      String[] expected = { "Developer", "Manager", "System Administrator" };

      // test loading directly from LDAP

      IdentityID uid = new IdentityID("candacegriffin", "selfOrg1");
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
   void testGetRole() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      String expectedName = "Support Engineer";
      String[] expectedRoles = { "Developer" };
      String expectedOrganization = "selfOrg1";

      // test loading directly from LDAP
      IdentityID roleID = new IdentityID("Support Engineer", "selfOrg1");
      Role actual = provider.getRole(roleID);
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
   void testGetGroups() throws InterruptedException {
      String[] expected = { "IT", "Marketing", "Sales", "Support" };

      // test loading directly from LDAP
      String[] actual = Arrays.stream(provider.getGroups()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);

      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      // test loading from cache
      actual = Arrays.stream(provider.getGroups()).map(id->id.name).toArray(String[]::new);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   @Timeout(15)
   @Order(13)
   void testGetUserGroups() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

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
   void testGetGroup() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      String expectedName = "Support";
      String[] expectedGroups = { "IT" };
      String[] expectedRoles = { "Support Engineer" };
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
   void testGetOrganization() throws InterruptedException {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

      String expectedName = "selfOrg1";
      String[] expectedRoles = {"Manager","Developer"};
      String[] expectedMembers = {"candacegriffin"};

      // test loading directly from LDAP
      Organization actual = provider.getOrganization("selfOrg1");
      assertEquals(expectedName, actual.getName());
      assertEquals(expectedRoles.length, actual.getRoles().length);
      assertArrayEquals(expectedMembers,actual.getMembers());

      // test loading from cache
      actual = provider.getOrganization("selfOrg1");
      assertEquals(expectedName, actual.getName());
      assertEquals(expectedRoles.length, actual.getRoles().length);
      assertArrayEquals(expectedMembers,actual.getMembers());
   }

   @Test
   @Timeout(15)
   @Order(16)
   void testReloadOnUpdate() throws Exception {
      while(!provider.isCacheInitialized()) {
         Thread.sleep(500L);
      }

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

         while(provider.isLoading()) {
            Thread.sleep(500L);
         }

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

   private static ApacheDSContainer ldapContainer;
   private GenericLdapAuthenticationProvider provider;
   private static int testCounter = 1;
}