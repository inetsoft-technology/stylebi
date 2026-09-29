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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77354: FileAuthenticationProvider.setUser/setGroup/setRole/setOrganization must report
 * storage write failures to the caller instead of only logging them, while the nested updates in
 * processAuthenticationChange stay best-effort per item.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FileAuthenticationProviderWriteFailureTest {
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
   void setUser_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("f11User", "testOrg");
      provider.addUser(new FSUser(id));
      FSUser updated = new FSUser(id);
      updated.setAlias("changed");

      withFailing("userStorage", true, storage -> assertThrows(
         RuntimeException.class, () -> provider.setUser(id, updated),
         "setUser returned normally although the storage write failed"));
   }

   @Test
   void setGroup_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("f11Group", "testOrg");
      provider.addGroup(new FSGroup(id));

      withFailing("groupStorage", true, storage -> assertThrows(
         RuntimeException.class, () -> provider.setGroup(id, new FSGroup(id)),
         "setGroup returned normally although the storage write failed"));
   }

   @Test
   void setRole_putFails_throws() throws Exception {
      IdentityID id = new IdentityID("f11Role", "testOrg");
      provider.addRole(new FSRole(id));

      withFailing("roleStorage", true, storage -> assertThrows(
         RuntimeException.class, () -> provider.setRole(id, new FSRole(id)),
         "setRole returned normally although the storage write failed"));
   }

   @Test
   void setOrganization_putFails_throws() throws Exception {
      FSOrganization org = new FSOrganization("f11Org");
      org.setName("F11 Org");
      provider.addOrganization(org);

      withFailing("organizationStorage", true, storage -> assertThrows(
         RuntimeException.class, () -> provider.setOrganization("f11Org", org),
         "setOrganization returned normally although the storage write failed"));
   }

   // rename: the new key is written, then removing the old key fails. The new key is not rolled
   // back (a failed remove may still have completed), so the old user must survive and the error
   // must name both users
   @Test
   void setUser_renameRemoveFails_throwsAndOldUserSurvives() throws Exception {
      IdentityID oldId = new IdentityID("f11Old", "testOrg");
      IdentityID newId = new IdentityID("f11New", "testOrg");
      provider.addUser(new FSUser(oldId));
      RuntimeException[] thrown = new RuntimeException[1];

      withFailing("userStorage", false, storage -> thrown[0] = assertThrows(
         RuntimeException.class, () -> provider.setUser(oldId, new FSUser(newId)),
         "setUser rename returned normally although removing the old key failed"));

      assertAll(
         () -> assertNotNull(provider.getUser(oldId), "the old user must not be lost"),
         () -> assertNotNull(provider.getUser(newId), "the new user is kept, not rolled back"),
         () -> assertTrue(thrown[0].getMessage().contains("f11Old") &&
                          thrown[0].getMessage().contains("f11New"),
                          "message must name both users: " + thrown[0].getMessage()));
   }

   // a failed organization member update must not skip the rest of the group reference cleanup
   @Test
   void removeGroup_orgUpdateFails_stillRemovesGroupFromUsers() throws Exception {
      IdentityID groupId = new IdentityID("pGroup", "pOrg");
      IdentityID userId = new IdentityID("pUser", "pOrg");
      addOrganization("pOrg", "pGroup", "pUser");
      provider.addGroup(new FSGroup(groupId));
      FSUser user = new FSUser(userId);
      user.setGroups(new String[] { "pGroup" });
      provider.addUser(user);

      withFailing("organizationStorage", true, storage -> provider.removeGroup(groupId));

      assertNull(provider.getGroup(groupId));
      assertFalse(Arrays.asList(provider.getUser(userId).getGroups()).contains("pGroup"),
                  "the deleted group must be removed from its users");
   }

   // a failed organization member update must not skip the rest of the role reference cleanup
   @Test
   void removeRole_orgUpdateFails_stillRemovesRoleFromUsers() throws Exception {
      IdentityID roleId = new IdentityID("qRole", "pOrg");
      IdentityID userId = new IdentityID("qUser", "pOrg");
      addOrganization("pOrg", "qRole", "qUser");
      provider.addRole(new FSRole(roleId));
      FSUser user = new FSUser(userId);
      user.setRoles(new IdentityID[] { roleId });
      provider.addUser(user);

      withFailing("organizationStorage", true, storage -> provider.removeRole(roleId));

      assertNull(provider.getRole(roleId));
      assertFalse(Arrays.asList(provider.getUser(userId).getRoles()).contains(roleId),
                  "the deleted role must be removed from its users");
   }

   // renaming an organization updates each member separately; one failure must not stop the others
   @Test
   void setOrganization_renameMemberUpdateFails_updatesRemainingMembers() throws Exception {
      IdentityID user1 = new IdentityID("rUser1", "rOrg");
      IdentityID user2 = new IdentityID("rUser2", "rOrg");
      FSOrganization org = addOrganization("rOrg", "rUser1", "rUser2");
      provider.addUser(new FSUser(user1));
      provider.addUser(new FSUser(user2));
      org.setName("Renamed Org");

      withFailing("userStorage", true, storage -> {
         assertDoesNotThrow(() -> provider.setOrganization("rOrg", org),
                            "the organization itself was saved");
         Mockito.verify(storage).put(ArgumentMatchers.eq(user1.convertToKey()), ArgumentMatchers.any());
         Mockito.verify(storage).put(ArgumentMatchers.eq(user2.convertToKey()), ArgumentMatchers.any());
      });

      assertEquals("Renamed Org", provider.getOrganization("rOrg").getName());
   }

   private FSOrganization addOrganization(String id, String... members) {
      FSOrganization org = new FSOrganization(id);
      org.setName(id);
      org.setMembers(members);
      provider.addOrganization(org);
      return org;
   }

   /**
    * Runs the action with the named storage replaced by one whose put() (failPut) or remove()
    * (!failPut) always fails and whose other operations delegate to the real storage.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void withFailing(String fieldName, boolean failPut, Consumer<KeyValueStorage> action)
      throws Exception
   {
      Field f = FileAuthenticationProvider.class.getDeclaredField(fieldName);
      f.setAccessible(true);
      KeyValueStorage real = (KeyValueStorage) f.get(provider);
      KeyValueStorage failing =
         Mockito.mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      CompletableFuture failed =
         CompletableFuture.failedFuture(new IOException("simulated write failure"));

      if(failPut) {
         Mockito.doReturn(failed).when(failing)
            .put(ArgumentMatchers.anyString(), ArgumentMatchers.any());
      }
      else {
         Mockito.doReturn(failed).when(failing).remove(ArgumentMatchers.anyString());
      }

      f.set(provider, failing);

      try {
         action.accept(failing);
      }
      finally {
         f.set(provider, real);
      }
   }

   private FileAuthenticationProvider provider;
}
