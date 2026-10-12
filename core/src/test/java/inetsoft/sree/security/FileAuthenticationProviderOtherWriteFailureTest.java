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
package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77502 (follow-up to Bug #77354): the FileAuthenticationProvider write methods other than
 * the four setters fixed by #77354 (changePassword, addUser, addGroup, addRole, addOrganization,
 * removeUser, removeGroup, removeRole, removeOrganization) must report storage write failures to
 * the caller instead of only logging them. removeOrganization must keep the organization when one
 * of its members can't be removed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class FileAuthenticationProviderOtherWriteFailureTest {
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
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();

      SecurityEngine.getSecurity().init();

      // the ORGANIZATION removed event strips the org's grants and logs out its sessions, so it
      // must only be fired once the organization record is really gone
      orgRemovedEvents.clear();
      provider.addAuthenticationChangeListener(e -> {
         if(e.getType() == Identity.ORGANIZATION && e.isRemoved()) {
            orgRemovedEvents.add(e.getOldOrgID() + ":orgExists=" +
                                    (provider.getOrganization(e.getOldOrgID()) != null));
         }
      });
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
   void changePassword_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("aPwUser", "testOrg");
      provider.addUser(new FSUser(id));

      withFailing("userStorage", true, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.changePassword(id, "N3w-Passw0rd!"),
         "changePassword returned normally although the storage write failed"));
   }

   @Test
   void addUser_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("aAddUser", "testOrg");

      withFailing("userStorage", true, null, storage -> {
         assertThrows(
            RuntimeException.class, () -> provider.addUser(new FSUser(id)),
            "addUser returned normally although the storage write failed");
         // the failure must come from the storage write, not from an earlier check
         Mockito.verify(storage).put(ArgumentMatchers.eq(id.convertToKey()), ArgumentMatchers.any());
      });
   }

   @Test
   void addGroup_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("aAddGroup", "testOrg");

      withFailing("groupStorage", true, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.addGroup(new FSGroup(id)),
         "addGroup returned normally although the storage write failed"));
   }

   @Test
   void addRole_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("aAddRole", "testOrg");

      withFailing("roleStorage", true, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.addRole(new FSRole(id)),
         "addRole returned normally although the storage write failed"));
   }

   @Test
   void addOrganization_putFails_throws() throws Exception {
      FSOrganization org = new FSOrganization("aAddOrg");
      org.setName("aAddOrg");

      withFailing("organizationStorage", true, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.addOrganization(org),
         "addOrganization returned normally although the storage write failed"));
   }

   @Test
   void removeUser_removeFails_throws() throws Exception {
      IdentityID id = new IdentityID("aRmUser", "testOrg");
      provider.addUser(new FSUser(id));

      withFailing("userStorage", false, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.removeUser(id),
         "removeUser returned normally although the storage remove failed"));

      assertNotNull(provider.getUser(id), "the user was not removed");
   }

   // a failed remove must not run the reference cleanup for a group that may still exist
   @Test
   void removeGroup_removeFails_throwsAndKeepsReferences() throws Exception {
      IdentityID id = new IdentityID("aRmGroup", "testOrg");
      IdentityID userId = new IdentityID("aRmGroupUser", "testOrg");
      provider.addGroup(new FSGroup(id));
      FSUser user = new FSUser(userId);
      user.setGroups(new String[] { "aRmGroup" });
      provider.addUser(user);

      withFailing("groupStorage", false, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.removeGroup(id),
         "removeGroup returned normally although the storage remove failed"));

      assertAll(
         () -> assertNotNull(provider.getGroup(id), "the group was not removed"),
         () -> assertTrue(Arrays.asList(provider.getUser(userId).getGroups()).contains("aRmGroup"),
                          "the group must not be removed from its users"));
   }

   @Test
   void removeRole_removeFails_throws() throws Exception {
      IdentityID id = new IdentityID("aRmRole", "testOrg");
      provider.addRole(new FSRole(id));

      withFailing("roleStorage", false, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.removeRole(id),
         "removeRole returned normally although the storage remove failed"));

      assertNotNull(provider.getRole(id), "the role was not removed");
   }

   @Test
   void removeOrganization_removeFails_throws() throws Exception {
      addOrganization("aRmOrg");

      withFailing("organizationStorage", false, null, storage -> assertThrows(
         RuntimeException.class, () -> provider.removeOrganization("aRmOrg"),
         "removeOrganization returned normally although the storage remove failed"));

      assertNotNull(provider.getOrganization("aRmOrg"), "the organization was not removed");
      assertEquals(List.of(), orgRemovedEvents,
                   "the organization removed event must not fire for a kept organization");
   }

   // the organization must be kept when its members can't be removed, or the users would be
   // orphaned (keeping their passwords) and inherited by a new organization with the same id
   @Test
   void removeOrganization_userRemovesFail_throwsAndKeepsOrganizationAndUsers() throws Exception {
      IdentityID user1 = new IdentityID("oUser1", "oOrg");
      IdentityID user2 = new IdentityID("oUser2", "oOrg");
      IdentityID group = new IdentityID("oGroup", "oOrg");
      IdentityID role = new IdentityID("oRole", "oOrg");
      addOrganizationWithMembers("oOrg", user1, user2, group, role);
      RuntimeException[] thrown = new RuntimeException[1];

      withFailing("userStorage", false, null, storage -> thrown[0] = assertThrows(
         RuntimeException.class, () -> provider.removeOrganization("oOrg"),
         "removeOrganization returned normally although its users could not be removed"));

      assertAll(
         () -> assertNull(provider.getGroup(group), "the group is still removed"),
         () -> assertNull(provider.getRole(role), "the role is still removed"),
         () -> assertNotNull(provider.getUser(user1), "user1 was not removed"),
         () -> assertNotNull(provider.getUser(user2), "user2 was not removed"),
         () -> assertNotNull(provider.getOrganization("oOrg"),
                             "the organization must be kept so the removal can be retried"),
         () -> assertTrue(thrown[0].getMessage().contains("oUser1") &&
                          thrown[0].getMessage().contains("oUser2"),
                          "message must name the users: " + thrown[0].getMessage()),
         () -> assertEquals(List.of(), orgRemovedEvents,
                            "the organization removed event must not fire for a kept organization"));
   }

   // one failing member must not stop the removal of the others
   @Test
   void removeOrganization_oneUserRemoveFails_removesOtherMembersAndKeepsOrganization()
      throws Exception
   {
      IdentityID user1 = new IdentityID("pUser1", "pOrg");
      IdentityID user2 = new IdentityID("pUser2", "pOrg");
      IdentityID group = new IdentityID("pGroup", "pOrg");
      IdentityID role = new IdentityID("pRole", "pOrg");
      addOrganizationWithMembers("pOrg", user1, user2, group, role);

      withFailing("userStorage", false, user1.convertToKey(), storage -> assertThrows(
         RuntimeException.class, () -> provider.removeOrganization("pOrg"),
         "removeOrganization returned normally although a user could not be removed"));

      assertAll(
         () -> assertNotNull(provider.getUser(user1), "user1 was not removed"),
         () -> assertNull(provider.getUser(user2), "user2 is still removed"),
         () -> assertNull(provider.getGroup(group), "the group is still removed"),
         () -> assertNull(provider.getRole(role), "the role is still removed"),
         () -> assertNotNull(provider.getOrganization("pOrg"),
                             "the organization must be kept so the removal can be retried"),
         () -> assertEquals(List.of(), orgRemovedEvents,
                            "the organization removed event must not fire for a kept organization"));
   }

   // a retry of a partially failed removal completes it, and the organization removed event is
   // fired once, after the organization record is gone
   @Test
   void removeOrganization_retryAfterMemberFailure_removesAllAndFiresOrgEventOnce()
      throws Exception
   {
      IdentityID user1 = new IdentityID("qUser1", "qOrg");
      IdentityID user2 = new IdentityID("qUser2", "qOrg");
      IdentityID group = new IdentityID("qGroup", "qOrg");
      IdentityID role = new IdentityID("qRole", "qOrg");
      addOrganizationWithMembers("qOrg", user1, user2, group, role);

      withFailing("userStorage", false, user1.convertToKey(), storage -> assertThrows(
         RuntimeException.class, () -> provider.removeOrganization("qOrg")));

      assertDoesNotThrow(() -> provider.removeOrganization("qOrg"), "the retry must succeed");

      assertAll(
         () -> assertNull(provider.getUser(user1), "user1 is removed by the retry"),
         () -> assertNull(provider.getUser(user2)),
         () -> assertNull(provider.getGroup(group)),
         () -> assertNull(provider.getRole(role)),
         () -> assertNull(provider.getOrganization("qOrg"), "the organization is removed"),
         () -> assertEquals(List.of("qOrg:orgExists=false"), orgRemovedEvents,
                            "the organization removed event must fire once, after the record " +
                            "is removed"));
   }

   private FSOrganization addOrganization(String id, String... members) {
      FSOrganization org = new FSOrganization(id);
      org.setName(id);
      org.setMembers(members);
      provider.addOrganization(org);
      return org;
   }

   private void addOrganizationWithMembers(String orgId, IdentityID user1, IdentityID user2,
                                           IdentityID group, IdentityID role)
   {
      addOrganization(orgId, user1.name, user2.name, group.name, role.name);
      provider.addGroup(new FSGroup(group));
      provider.addRole(new FSRole(role));

      for(IdentityID userId : new IdentityID[] { user1, user2 }) {
         FSUser user = new FSUser(userId);
         user.setOrganization(orgId);
         user.setGroups(new String[] { group.name });
         user.setRoles(new IdentityID[] { role });
         provider.addUser(user);
      }
   }

   /**
    * Runs the action with the named storage replaced by one whose put() (failPut) or remove()
    * (!failPut) fails, for every key or only for failKey, and whose other operations delegate to
    * the real storage.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void withFailing(String fieldName, boolean failPut, String failKey,
                            Consumer<KeyValueStorage> action)
      throws Exception
   {
      // open the storages first, the provider opens them lazily
      provider.getUser(new IdentityID("initProbe", "testOrg"));
      Field f = FileAuthenticationProvider.class.getDeclaredField(fieldName);
      f.setAccessible(true);
      KeyValueStorage real = (KeyValueStorage) f.get(provider);
      assertNotNull(real, fieldName + " is not open");
      KeyValueStorage failing =
         Mockito.mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      CompletableFuture failed =
         CompletableFuture.failedFuture(new IOException("simulated write failure"));
      if(failPut) {
         Mockito.doReturn(failed).when(failing).put(
            failKey == null ? ArgumentMatchers.anyString() : ArgumentMatchers.eq(failKey),
            ArgumentMatchers.any());
      }
      else {
         Mockito.doReturn(failed).when(failing).remove(
            failKey == null ? ArgumentMatchers.anyString() : ArgumentMatchers.eq(failKey));
      }

      f.set(provider, failing);

      try {
         action.accept(failing);
      }
      finally {
         f.set(provider, real);
      }
   }

   private final List<String> orgRemovedEvents = Collections.synchronizedList(new ArrayList<>());
   private FileAuthenticationProvider provider;
}
