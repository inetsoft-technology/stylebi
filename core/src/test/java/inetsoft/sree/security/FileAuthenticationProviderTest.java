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

package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.util.PasswordEncryption;

import org.junit.jupiter.api.*;
import org.mindrot.BCrypt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FileAuthenticationProviderTest {
   @BeforeEach
   void createProvider() throws Exception {
      provider = new FileAuthenticationProvider();

      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(provider));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authcChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();

      SecurityEngine.getSecurity().init();
   }

   @AfterEach
   void destroyProvider() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }

      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void testAddGetUser() {
      IdentityID userIdentity = new IdentityID("testUser", "testOrg");
      FSUser expectedUser = new FSUser(userIdentity);
      expectedUser.setPassword("hashedPassword");
      expectedUser.setPasswordAlgorithm("bcrypt");
      provider.addUser(expectedUser);

      User actualUser = provider.getUser(userIdentity);

      assertNotNull(actualUser, "User should not be null");
      assertEquals(expectedUser, actualUser, "Returned user should match the expected user");
      assertTrue(Arrays.asList(provider.getUsers()).contains(userIdentity));
   }

   @Test
   void testUpdateUser() {
      IdentityID userIdentity = new IdentityID("testUser", "testOrg");
      FSUser expectedUser = new FSUser(userIdentity);
      expectedUser.setPassword("hashedPassword");
      expectedUser.setPasswordAlgorithm("bcrypt");
      provider.addUser(expectedUser);

      // Update user password
      FSUser updatedUser = new FSUser(userIdentity);
      updatedUser.setPassword("newHashedPassword");
      updatedUser.setPasswordAlgorithm("bcrypt");
      assertDoesNotThrow(() -> provider.setUser(userIdentity, updatedUser));

      User actualUser = provider.getUser(userIdentity);
      assertNotNull(actualUser, "User should not be null");
      assertEquals(updatedUser, actualUser, "Returned user should match the updated user");

      // Update user identity (username and org)
      IdentityID newIdentity = new IdentityID("newTestUser", "newTestOrg");
      FSUser updatedUser2 = new FSUser(newIdentity);
      updatedUser2.setPassword("newHashedPassword2");
      updatedUser2.setPasswordAlgorithm("bcrypt");
      assertDoesNotThrow(() -> provider.setUser(userIdentity, updatedUser2));

      User actualUser2 = provider.getUser(newIdentity);
      assertNotNull(actualUser2, "User should not be null");
      assertEquals(updatedUser2, actualUser2, "Returned user should match the updated user");

      // Old user should no longer exist
      User oldUser = provider.getUser(userIdentity);
      assertNull(oldUser, "Old user should be null");

      // Remove user
      assertDoesNotThrow(() -> provider.removeUser(newIdentity));
      User removedUser = provider.getUser(newIdentity);
      assertNull(removedUser, "Removed user should be null");
   }

   @Test
   void testAddGetOrganization() {
      Organization organization = new FSOrganization("org1");
      organization.setName("Test Organization");

      assertDoesNotThrow(() -> provider.addOrganization(organization));

      Organization retrievedOrg = provider.getOrganization("org1");
      assertNotNull(retrievedOrg, "Organization should not be null");
      assertEquals("Test Organization", retrievedOrg.getName());
      assertTrue(Arrays.asList(provider.getOrganizationNames()).contains("Test Organization"));
      assertTrue(Arrays.asList(provider.getOrganizationIDs()).contains("org1"));

      //test getOrgIdFromName and getOrgNameFromID
      assertEquals("org1", provider.getOrgIdFromName("Test Organization"));
      assertEquals("Test Organization", provider.getOrgNameFromID("org1"));
      assertNull(provider.getOrgIdFromName("NonExisting"));
      assertNull(provider.getOrgNameFromID("NonExisting"));

      //add invalid organization
      Exception exception = assertThrows(NullPointerException.class, () -> provider.addOrganization(null));
      assertEquals("Cannot invoke \"inetsoft.sree.security.Organization.getName()\" because \"organization\" is null",
                   exception.getMessage());
   }

   @Test
   void testUpdateOrganization() {
      String orgId = "org1";
      Organization organization = new FSOrganization(orgId);
      organization.setName("Test Organization");
      assertDoesNotThrow(() -> provider.addOrganization(organization));

      //update organization name
      FSOrganization updatedOrg = new FSOrganization(orgId);
      updatedOrg.setName("Updated Organization");
      assertDoesNotThrow(() -> provider.setOrganization(orgId, updatedOrg));
      Organization retrievedOrg = provider.getOrganization(orgId);
      assertNotNull(retrievedOrg, "Organization should not be null");
      assertEquals("Updated Organization", retrievedOrg.getName());
      assertFalse(Arrays.asList(provider.getOrganizationNames()).contains("Test Organization"));

      //update organization id and name, old organization should be removed
      FSOrganization updatedOrg2 = new FSOrganization("org2");
      updatedOrg2.setName("Updated Organization 2");
      assertDoesNotThrow(() -> provider.setOrganization(orgId, updatedOrg2));
      Organization retrievedOrg2 = provider.getOrganization("org2");
      assertNotNull(retrievedOrg2, "Organization should not be null");
      assertEquals("Updated Organization 2", retrievedOrg2.getName());
      assertFalse(Arrays.asList(provider.getOrganizationNames()).contains("Updated Organization"));
      assertFalse(Arrays.asList(provider.getOrganizationIDs()).contains(orgId));

      //remove organization
      assertDoesNotThrow(() -> provider.removeOrganization("org2"));
      assertNull(provider.getOrganization("org2"));
   }

   @Test
   void testUpdateOrganizationMembers() {
      String orgId = "org1";
      Organization organization = new FSOrganization(orgId);
      organization.setName("Test Organization");
      assertDoesNotThrow(() -> provider.addOrganization(organization));

      //add user/group/role to the organization
      IdentityID user1Id = new IdentityID("testUser", orgId);
      FSUser user1 = new FSUser(user1Id);
      IdentityID user2Id = new IdentityID("testUser2", orgId);
      FSUser user2 = new FSUser(user2Id);
      IdentityID groupId = new IdentityID("testGroup", orgId);
      FSGroup group = new FSGroup(groupId);
      IdentityID roleId = new IdentityID("testRole", orgId);
      FSRole role = new FSRole(roleId);
      user2.setGroups(new String[]{ "testGroup" }); // add user2 to group
      user2.setRoles(new IdentityID[] {roleId}); // add user2 to role

      provider.addUser(user1);
      provider.addUser(user2);
      provider.addGroup(group);
      provider.addRole(role);

      assertNotNull(provider.getUser(user1Id));
      assertNotNull(provider.getUser(user2Id));
      assertNotNull(provider.getGroup(groupId));
      assertNotNull(provider.getRole(roleId));

      //update organization id and name
      String newOrgId = "org2";
      FSOrganization updatedOrg = new FSOrganization(newOrgId);
      updatedOrg.setName("Updated Organization 2");
      assertDoesNotThrow(() -> provider.setOrganization(orgId, updatedOrg));

      //check organization of user/group/role is updated
      IdentityID newUserId = new IdentityID("testUser", newOrgId);
      IdentityID newUser2Id = new IdentityID("testUser2", newOrgId);
      IdentityID newGroupId = new IdentityID("testGroup", newOrgId);
      IdentityID newRoleId = new IdentityID("testRole", newOrgId);
      assertNotNull(provider.getUser(newUserId));
      assertNotNull(provider.getUser(newUser2Id));
      assertNotNull(provider.getGroup(newGroupId));
      assertNotNull(provider.getRole(newRoleId));
   }

   @Test
   void testAddGetGroup() {
      IdentityID groupId = new IdentityID("testGroup", "testOrg");
      FSGroup expectedGroup = new FSGroup(groupId);
      provider.addGroup(expectedGroup);

      Group actualGroup = provider.getGroup(groupId);

      assertNotNull(actualGroup, "Group should not be null");
      assertEquals(expectedGroup, actualGroup, "Returned group should match the expected group");
      assertTrue(Arrays.asList(provider.getGroups()).contains(groupId),
                 "Groups should contain the expected groupId");
   }

   @Test
   void testUpdateGroup() {
      IdentityID groupId = new IdentityID("testGroup", "testOrg");
      FSGroup expectedGroup = new FSGroup(groupId);
      provider.addGroup(expectedGroup);

      // Update group identity (name and org)
      IdentityID newIdentity = new IdentityID("newTestGroup", "newTestOrg");
      FSGroup updatedGroup = new FSGroup(newIdentity);
      assertDoesNotThrow(() -> provider.setGroup(groupId, updatedGroup));

      Group actualGroup2 = provider.getGroup(newIdentity);
      assertNotNull(actualGroup2, "Group should not be null");
      assertEquals(updatedGroup, actualGroup2, "Returned group should match the updated group");

      // Old group should no longer exist
      Group oldGroup = provider.getGroup(groupId);
      assertNull(oldGroup, "Old group should be null");

      // Remove group
      assertDoesNotThrow(() -> provider.removeGroup(newIdentity));
      Group removedGroup = provider.getGroup(newIdentity);
      assertNull(removedGroup, "Removed group should be null");
   }

   @Test
   void testGetGroupUsers() {
      IdentityID groupId = new IdentityID("group1", "org1");
      FSGroup group = new FSGroup(groupId);

      IdentityID groupId2 = new IdentityID("group1", "org2");// same name but different org
      FSGroup group2 = new FSGroup(groupId2);

      IdentityID user1Identity = new IdentityID("user1", "org1");
      FSUser user1 = new FSUser(user1Identity);
      IdentityID user2Identity = new IdentityID("user2", "org1");
      FSUser user2 = new FSUser(user2Identity);
      IdentityID user3Identity = new IdentityID("user3", "org1");
      FSUser user3 = new FSUser(user3Identity);
      IdentityID user4Identity = new IdentityID("user4", "org2");//different org
      FSUser user4 = new FSUser(user4Identity);

      user1.setGroups(new String[]{ "group1" });
      user2.setGroups(new String[]{ "group1" });
      user4.setGroups(new String[]{ "group1" });

      provider.addGroup(group);
      provider.addGroup(group2);
      provider.addUser(user1);
      provider.addUser(user2);
      provider.addUser(user3);
      provider.addUser(user4);

      assertEquals(2, provider.getUsers(groupId).length);
      assertTrue(Arrays.asList(provider.getUsers(groupId)).contains(user1Identity));
      assertTrue(Arrays.asList(provider.getUsers(groupId)).contains(user2Identity));
      assertFalse(Arrays.asList(provider.getUsers(groupId)).contains(user3Identity));
      assertFalse(Arrays.asList(provider.getUsers(groupId)).contains(user4Identity));
      assertEquals(1, provider.getUsers(groupId2).length);
      assertTrue(Arrays.asList(provider.getUsers(groupId2)).contains(user4Identity));
      //non-existing group
      assertEquals(0, provider.getUsers(new IdentityID("nonExistingGroup", "org1")).length);

      //update group name
      IdentityID newGroupId = new IdentityID("newGroup1", "org1");
      FSGroup updatedGroup = new FSGroup(newGroupId);
      assertDoesNotThrow(() -> provider.setGroup(groupId, updatedGroup));
      assertEquals(0, provider.getUsers(groupId).length);
      assertEquals(2, provider.getUsers(newGroupId).length);
      assertTrue(Arrays.asList(provider.getUsers(newGroupId)).contains(user1Identity));
      assertTrue(Arrays.asList(provider.getUsers(newGroupId)).contains(user2Identity));
   }

   @Test
   void testAddGetRole() {
      IdentityID roleId = new IdentityID("testRole", "testOrg");
      FSRole expectedRole = new FSRole(roleId);
      provider.addRole(expectedRole);

      Role actualRole = provider.getRole(roleId);

      assertNotNull(actualRole, "Role should not be null");
      assertEquals(expectedRole, actualRole, "Returned role should match the expected role");
      assertTrue(Arrays.asList(provider.getRoles()).contains(roleId),
                 "Roles should contain the expected roleId");

      assertFalse(provider.isOrgAdministratorRole(roleId));
      assertFalse(provider.isSystemAdministratorRole(roleId));
   }

   @Test
   void testUpdateRole() {
      IdentityID roleId = new IdentityID("testRole", "testOrg");
      FSRole expectedRole = new FSRole(roleId);
      provider.addRole(expectedRole);

      // Update role identity (name and org)
      IdentityID newIdentity = new IdentityID("newTestRole", "newTestOrg");
      FSRole updatedRole = new FSRole(newIdentity);
      assertDoesNotThrow(() -> provider.setRole(roleId, updatedRole));

      Role actualRole2 = provider.getRole(newIdentity);
      assertNotNull(actualRole2, "Role should not be null");
      assertEquals(updatedRole, actualRole2, "Returned role should match the updated role");

      // Old role should no longer exist
      Role oldRole = provider.getRole(roleId);
      assertNull(oldRole, "Old role should be null");

      // Remove role
      assertDoesNotThrow(() -> provider.removeRole(newIdentity));
      Role removedRole = provider.getRole(newIdentity);
      assertNull(removedRole, "Removed role should be null");
   }

   @Test
   void testChangePassword() {
      IdentityID userIdentity = new IdentityID("testUser", "testOrg");
      FSUser user = new FSUser(userIdentity);
      user.setPassword("oldPassword");
      user.setPasswordAlgorithm("bcrypt");
      provider.addUser(user);

      assertDoesNotThrow(() -> provider.changePassword(userIdentity, "newPassword"));

      User updatedUser = provider.getUser(userIdentity);
      assertNotNull(updatedUser, "User should not be null");
      assertEquals("bcrypt", updatedUser.getPasswordAlgorithm());
      assertTrue(BCrypt.checkpw("newPassword", updatedUser.getPassword()),
                 "Password should be updated");

      // Change password for non-existing user
      IdentityID nonExistentUser = new IdentityID("nonExistentUser", "testOrg");
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                        () -> provider.changePassword(nonExistentUser, "newPassword"));

      assertEquals("User \"IdentityID{name='nonExistentUser', orgID='testOrg'}\" does not exist", exception.getMessage());
   }

   @Test
   void testGetIndividualUsersAndEmail() {
      IdentityID user1Identity = new IdentityID("user1", "org1");
      FSUser user1 = new FSUser(user1Identity);
      user1.setEmails(new String[]{ "user1@example.com" });
      user1.setGroups(new String[0]); // No groups

      IdentityID user2Identity = new IdentityID("user2", "org1");
      FSUser user2 = new FSUser(user2Identity);
      user2.setEmails(new String[]{ "user2@example.com", "user2_alt@example.com" });
      user2.setGroups(new String[0]); // No groups

      IdentityID user3Identity = new IdentityID("user3", "org1");
      FSUser user3 = new FSUser(user3Identity);
      user3.setEmails(new String[]{ "user3@example.com" });
      user3.setGroups(new String[]{ "group1" }); // Belongs to a group

      IdentityID user4Identity = new IdentityID("user4", "org1");
      FSUser user4 = new FSUser(user4Identity); //no email and no group

      provider.addUser(user1);
      provider.addUser(user2);
      provider.addUser(user3);
      provider.addUser(user4);

      String[] emailAddresses = provider.getIndividualEmailAddresses();
      IdentityID[] userIds = provider.getIndividualUsers();

      assertNotNull(emailAddresses, "Email addresses should not be null");
      assertTrue(Arrays.asList(emailAddresses).contains("user1@example.com"));
      assertTrue(Arrays.asList(emailAddresses).contains("user2@example.com"));
      assertFalse(Arrays.asList(emailAddresses).contains("user3@example.com"));
      assertFalse(Arrays.asList(emailAddresses).contains("user4@example.com"));

      assertNotNull(userIds, "User IDs should not be null");
      assertTrue(Arrays.asList(userIds).contains(user1Identity));
      assertTrue(Arrays.asList(userIds).contains(user2Identity));
      assertFalse(Arrays.asList(userIds).contains(user3Identity));
      assertTrue(Arrays.asList(userIds).contains(user4Identity));
   }

   @Test
   void testAuthenticate() {
      //active user with password
      IdentityID userIdentity = new IdentityID("testUser", "testOrg");
      FSUser user = new FSUser(userIdentity);
      String plainPassword = "testPassword";
      String hashedPassword = PasswordEncryption.newInstance().hash(
         plainPassword, "bcrypt", null, false).getHash();
      user.setPassword(hashedPassword);
      user.setPasswordAlgorithm("bcrypt");
      user.setActive(true);

      //inactive user with password
      IdentityID inactiveUserIdentity = new IdentityID("inactiveUser", "testOrg");
      FSUser inactiveUser = new FSUser(inactiveUserIdentity);
      inactiveUser.setPassword(hashedPassword);
      inactiveUser.setPasswordAlgorithm("bcrypt");
      inactiveUser.setActive(false);

      //user has no password
      IdentityID noPassUserId = new IdentityID("noPassUser", "testOrg");
      FSUser noPassUser = new FSUser(noPassUserId);
      noPassUser.setPassword(null);

      provider.addUser(user);
      provider.addUser(inactiveUser);
      provider.addUser(noPassUser);

      DefaultTicket ticket = new DefaultTicket(userIdentity, plainPassword);
      DefaultTicket inactiveTicket = new DefaultTicket(inactiveUserIdentity, plainPassword);
      DefaultTicket invalidTicket = new DefaultTicket(userIdentity, "invalidPassword");

      //null ticket
      assertFalse(provider.authenticate(userIdentity, null));
      //invalid user identity
      assertFalse(provider.authenticate(new IdentityID("notExist", "testOrg"), ticket));
      //no password user
      assertFalse(provider.authenticate(noPassUserId, ticket));
      //inactive user
      assertFalse(provider.authenticate(inactiveUserIdentity, inactiveTicket));
      //invalid password
      assertFalse(provider.authenticate(userIdentity, invalidTicket));
      //correct password
      assertTrue(provider.authenticate(userIdentity, ticket));
   }

   private FileAuthenticationProvider provider;
}