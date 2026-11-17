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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81587: while the id of an organization changes (or an organization is cloned), the keys of
 * the new id are already stored, the grantees of the old id are still being renamed, and the new
 * organization is not registered yet. A store re-open in that window must not take those keys
 * for legacy keys and split and remove them, which turned a direct grant into "inherit".
 * The axis is the time of the re-open relative to the change: before the new organization is
 * registered (guarded, and kept), or a real legacy key (migrated as before).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class FileAuthorizationProviderOrgIdChangeTest {
   @BeforeEach
   void setUp() throws Exception {
      authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      AuthorizationChain chain = (AuthorizationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      provider.getPermission(ResourceType.DATA_SOURCE, "probe", ORG);
      real = storage();
      real.removeAll(new HashSet<>(real.keys().toList())).get();
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
   }

   // the key of the new id, with a role grantee of the old id not renamed yet, survives a
   // re-open while the id change runs
   @Test
   void keyOfTheNewIdSurvivesReopenDuringIdChange() throws Exception {
      real.put(KEY, mixed()).get();
      String opId = OrgIdChangeGuard.begin("orgB", "orgB2");

      try {
         reopen();
      }
      finally {
         OrgIdChangeGuard.end(opId, "orgB", false);
      }

      assertEquals(Set.of(KEY), keys());
      assertNotNull(provider.getPermission(ResourceType.DATA_SOURCE, "Examples/Orders", "orgB2"));
   }

   // the same key survives repeated re-opens, as the eviction of the store repeats during the change
   @Test
   void keyOfTheNewIdSurvivesRepeatedReopens() throws Exception {
      real.put(KEY, mixed()).get();
      String opId = OrgIdChangeGuard.begin("orgB", "orgB2");

      try {
         for(int i = 0; i < 3; i++) {
            reopen();
            assertEquals(Set.of(KEY), keys(), "re-open " + (i + 1));
         }
      }
      finally {
         OrgIdChangeGuard.end(opId, "orgB", false);
      }
   }

   // control: the same key with the new organization registered is kept as before
   @Test
   void keyOfRegisteredOrganizationIsKept() throws Exception {
      authc.addOrganization(new FSOrganization("orgB2"));

      try {
         real.put(KEY, mixed()).get();
         reopen();
         assertEquals(Set.of(KEY), keys());
      }
      finally {
         authc.removeOrganization("orgB2");
      }
   }

   // control: a real legacy key without an organization part is still migrated when a change
   // of another organization runs
   @Test
   void legacyKeyWithoutOrganizationPartIsStillMigrated() throws Exception {
      real.put("DATA_SOURCE:Examples/Orders", userGrant("alice", ORG)).get();
      String opId = OrgIdChangeGuard.begin("orgB", "orgB2");

      try {
         reopen();
      }
      finally {
         OrgIdChangeGuard.end(opId, "orgB", false);
      }

      assertEquals(Set.of("DATA_SOURCE:" + ORG + ":Examples/Orders"), keys());
   }

   // control: a legacy key with an unknown organization part and no running change is still
   // migrated (schedule task ids are owner:name)
   @Test
   void legacyKeyWithUnknownPartAndNoChangeIsStillMigrated() throws Exception {
      String task = "admin~;~" + ORG + ":Task1";
      real.put("SCHEDULE_TASK:" + task, userGrant("alice", ORG)).get();

      reopen();

      assertEquals(Set.of("SCHEDULE_TASK:" + ORG + ":" + task), keys());
   }

   private Permission mixed() {
      Permission p = userGrant("bob", "orgB2");
      p.setRoleGrantsForOrg(ResourceAction.READ, Set.of("role1"), "orgB");
      return p;
   }

   private static Permission userGrant(String user, String org) {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), org);
      return p;
   }

   // the next call into the provider opens the store again and runs the migration
   private void reopen() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(provider, null);
      provider.getPermission(ResourceType.DATA_SOURCE, "probe", ORG);
   }

   private Set<String> keys() {
      return real.keys().collect(Collectors.toSet());
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (KeyValueStorage<Permission>) f.get(provider);
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String KEY = "DATA_SOURCE:orgB2:Examples/Orders";
   private FileAuthenticationProvider authc;
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> real;
}
