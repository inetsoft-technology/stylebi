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
import inetsoft.sree.security.ldap.GenericLdapAuthenticationProvider;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77832: the legacy-storage migration in FileAuthorizationProvider.init() must decide per
 * key whether a key still needs to be isolated by organization, must never replace a stored
 * per-organization permission, must remove a legacy key only after its copies were written, and
 * must leave a key it can't handle as it is without throwing.
 *
 * <p>The tests seed the real permission storage and set the provider's storage field to null, so
 * the next call into the provider runs the real migration again. Where the result depends on the
 * key order, keys() is reordered by a spy that delegates everything else to the real storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class FileAuthorizationProviderIsolationTest {
   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
   }

   // a leftover legacy key next to the live per-org key: the live key wins and nothing is
   // moved to a double-prefixed key
   @Test
   void legacyKeyNextToLiveKeyKeepsTheLiveGrant() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      real.put("VIEWSHEET:" + ORG + ":vsA", edited(grant("alice"))).get();
      real.put("VIEWSHEET:vsA", grant("mallory")).get();

      runInit(ordered(real, "VIEWSHEET:vsA"));

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vsA"), keys(real));
      Permission live = provider.getPermission(ResourceType.VIEWSHEET, "vsA", ORG);
      assertTrue(granted(live, "alice"));
      assertFalse(granted(live, "mallory"), "the stale legacy grant replaced the live one");
      assertTrue(live.hasOrgEditedGrantAll(ORG));
   }

   // fresh-install defaults under an authentication chain without the file provider, where the
   // SELF organization is unknown: nothing may change, in the production (HashSet) key order
   @Test
   void freshDefaultsUnderLdapOnlyChainAreUnchanged() throws Exception {
      setUp(new GenericLdapAuthenticationProvider());
      assertNull(SecurityEngine.getSecurity().getSecurityProvider()
                    .getOrganization(Organization.getSelfOrganizationID()));
      KeyValueStorage<Permission> real = storage();
      seedFreshDefaults(real);
      provider.setPermission(ResourceType.VIEWSHEET, "/sales", grant("alice"), ORG);
      Map<String, String> before = snapshot(real);
      KeyValueStorage<Permission> hashOrder = hashSetOrder(real);

      for(int i = 0; i < 3; i++) {
         runInit(hashOrder);
         assertEquals(before, snapshot(real), "restart " + (i + 1) + " changed the permissions");
      }

      assertTrue(granted(provider.getPermission(ResourceType.VIEWSHEET, "/sales", ORG), "alice"));
   }

   // the same defaults with a SELF key first in any order
   @Test
   void freshDefaultsUnderLdapOnlyChainAreUnchangedWithSelfKeyFirst() throws Exception {
      setUp(new GenericLdapAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      seedFreshDefaults(real);
      Map<String, String> before = snapshot(real);

      runInit(ordered(real, "REPORT:" + Organization.getSelfOrganizationID() + ":/"));

      assertEquals(before, snapshot(real));
   }

   // the key of a deleted organization is left as it is, and the other keys are not re-split
   @Test
   void deletedOrganizationKeyFirstLeavesStorageUnchanged() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      Permission gone = new Permission();
      gone.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "orgGone");
      real.put("VIEWSHEET:orgGone:vsX", gone).get();
      real.put("VIEWSHEET:" + ORG + ":vsA", edited(grant("alice"))).get();
      Map<String, String> before = snapshot(real);

      runInit(ordered(real, "VIEWSHEET:orgGone:vsX"));

      assertEquals(before, snapshot(real));
      assertTrue(granted(provider.getPermission(ResourceType.VIEWSHEET, "vsA", ORG), "alice"));
   }

   // legacy keys whose path contains ':' (task ids are owner:name, cubes are ds::cube) are
   // still migrated
   @Test
   void legacyScheduleTaskAndCubeKeysAreMigrated() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      String task = "admin~;~" + ORG + ":Task1";
      real.put("SCHEDULE_TASK:" + task, grant("alice")).get();
      real.put("CUBE:ds::cube1", grant("bob")).get();
      real.put("VIEWSHEET:vs1", grant("carol")).get();

      runInit(real);

      assertEquals(Set.of("SCHEDULE_TASK:" + ORG + ":" + task, "CUBE:" + ORG + ":ds::cube1",
                          "VIEWSHEET:" + ORG + ":vs1"), keys(real));
      assertTrue(granted(provider.getPermission(ResourceType.SCHEDULE_TASK, task, ORG), "alice"));
      assertTrue(granted(provider.getPermission(ResourceType.CUBE, "ds::cube1", ORG), "bob"));
      assertTrue(granted(provider.getPermission(ResourceType.VIEWSHEET, "vs1", ORG), "carol"));
   }

   // a legacy entry with grantees of two organizations is split into one key per organization,
   // also under a chain that knows only the default organization
   @Test
   void legacyEntryOfTwoOrganizationsIsSplitUnderLdapOnlyChain() throws Exception {
      setUp(new GenericLdapAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      String task = "admin~;~" + ORG + ":Task1";
      Permission vs = grant("alice");
      vs.setUserGrantsForOrg(ResourceAction.WRITE, Set.of("bob"), "org2");
      real.put("VIEWSHEET:vs1", vs).get();
      real.put("SCHEDULE_TASK:" + task, grant("carol")).get();
      real.put("CUBE:ds::cube1", grant("dave")).get();

      runInit(real);

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vs1", "VIEWSHEET:org2:vs1",
                          "SCHEDULE_TASK:" + ORG + ":" + task, "CUBE:" + ORG + ":ds::cube1"),
                   keys(real));
      Permission own = provider.getPermission(ResourceType.VIEWSHEET, "vs1", ORG);
      assertTrue(granted(own, "alice"));
      assertTrue(own.getUserGrants(ResourceAction.WRITE, "org2").isEmpty());
      Permission other = provider.getPermission(ResourceType.VIEWSHEET, "vs1", "org2");
      assertTrue(other.getUserGrants(ResourceAction.WRITE, "org2").stream()
                    .anyMatch(i -> "bob".equals(i.getName())));
      assertTrue(granted(provider.getPermission(ResourceType.SCHEDULE_TASK, task, ORG), "carol"));
      assertTrue(granted(provider.getPermission(ResourceType.CUBE, "ds::cube1", ORG), "dave"));
   }

   // the key of a known organization is kept, also when a user of another known organization is
   // granted on it
   @Test
   void knownOrganizationKeyWithGranteeOfAnotherOrganizationIsUnchanged() throws Exception {
      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      setUp(authc);
      authc.addOrganization(new FSOrganization("orgB"));

      try {
         assertNotNull(SecurityEngine.getSecurity().getSecurityProvider().getOrganization("orgB"));
         KeyValueStorage<Permission> real = storage();
         clear(real);
         Permission vs = grant("alice");
         vs.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "orgB");
         real.put("VIEWSHEET:" + ORG + ":vsA", vs).get();
         Map<String, String> before = snapshot(real);

         runInit(real);

         assertEquals(before, snapshot(real));
      }
      finally {
         authc.removeOrganization("orgB");
      }
   }

   // a legacy entry of two organizations, one of which already has its key: that key is kept, the
   // missing one is written and the legacy key is removed
   @Test
   void legacyEntryOfTwoOrganizationsKeepsTheExistingTarget() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      Permission legacy = grant("mallory");
      legacy.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "org2");
      real.put("VIEWSHEET:vsA", legacy).get();
      real.put("VIEWSHEET:" + ORG + ":vsA", edited(grant("alice"))).get();
      String live = snapshot(real).get("VIEWSHEET:" + ORG + ":vsA");

      runInit(real);

      Map<String, String> after = snapshot(real);
      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vsA", "VIEWSHEET:org2:vsA"), after.keySet());
      assertEquals(live, after.get("VIEWSHEET:" + ORG + ":vsA"));
      Permission other = provider.getPermission(ResourceType.VIEWSHEET, "vsA", "org2");
      assertTrue(other.getUserGrants(ResourceAction.READ, "org2").stream()
                    .anyMatch(i -> "bob".equals(i.getName())));
      assertTrue(other.getUserGrants(ResourceAction.READ, ORG).isEmpty());
   }

   // a legacy entry keeps the edited flag of its organization, also when nobody is granted
   @Test
   void legacyMigrationKeepsTheEditedFlag() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      real.put("VIEWSHEET:legacyA", edited(grant("alice"))).get();
      real.put("VIEWSHEET:legacyEmpty", edited(new Permission())).get();

      runInit(real);

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":legacyA", "VIEWSHEET:" + ORG + ":legacyEmpty"),
                   keys(real));
      assertTrue(real.get("VIEWSHEET:" + ORG + ":legacyA").hasOrgEditedGrantAll(ORG));
      assertTrue(real.get("VIEWSHEET:" + ORG + ":legacyEmpty").hasOrgEditedGrantAll(ORG));
   }

   // a legacy key is removed only after its copy was written
   @Test
   void failedPutKeepsTheLegacyKey() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      real.put("VIEWSHEET:legacyA", grant("alice")).get();
      real.put("VIEWSHEET:legacyB", grant("bob")).get();
      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> {
         String key = inv.getArgument(0);

         if(key.endsWith(":legacyB")) {
            return CompletableFuture.failedFuture(new IOException("simulated write failure"));
         }

         return real.put(key, inv.getArgument(1));
      }).when(failing).put(anyString(), any());

      runInit(failing);

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":legacyA", "VIEWSHEET:legacyB"), keys(real));
      assertTrue(granted(real.get("VIEWSHEET:legacyB"), "bob"));

      // the next start migrates it
      runInit(real);
      assertEquals(Set.of("VIEWSHEET:" + ORG + ":legacyA", "VIEWSHEET:" + ORG + ":legacyB"),
                   keys(real));
   }

   // a legacy key whose remove failed stays next to its copy; once the copy is edited, the next
   // start drops the stale legacy key and keeps the edit
   @Test
   void failedRemoveKeepsTheLiveEditOnTheNextStart() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      real.put("VIEWSHEET:legacyA", grant("mallory")).get();
      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doReturn(CompletableFuture.failedFuture(new IOException("simulated write failure")))
         .when(failing).remove(anyString());

      runInit(failing);

      assertEquals(Set.of("VIEWSHEET:legacyA", "VIEWSHEET:" + ORG + ":legacyA"), keys(real));

      provider.setPermission(ResourceType.VIEWSHEET, "legacyA", grant("alice"), ORG);
      runInit(real);

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":legacyA"), keys(real));
      Permission live = provider.getPermission(ResourceType.VIEWSHEET, "legacyA", ORG);
      assertTrue(granted(live, "alice"));
      assertFalse(granted(live, "mallory"), "the stale legacy grant replaced the live one");
   }

   // when every put fails during the migration run by authenticationChanged, nothing is lost
   @Test
   void failedPutsInAuthenticationChangedKeepTheStorage() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      real.put("VIEWSHEET:legacyA", grant("alice")).get();
      real.put("SCHEDULE_TASK:admin~;~" + ORG + ":Task1", grant("bob")).get();
      real.put("VIEWSHEET:" + ORG + ":vsA", edited(grant("carol"))).get();
      Map<String, String> before = snapshot(real);
      KeyValueStorage<Permission> failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doReturn(CompletableFuture.failedFuture(new IOException("simulated write failure")))
         .when(failing).put(anyString(), any());
      AuthenticationChangeEvent event = new AuthenticationChangeEvent(
         this, new IdentityID("zed", ORG), null, ORG, ORG, Identity.USER, true);

      runInit(failing, () -> provider.authenticationChanged(event));

      assertEquals(before, snapshot(real));
   }

   @Test
   void malformedKeysAreKeptAndInitDoesNotThrow() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      seedMalformed(real);

      runInit(ordered(real, "nocolon"), () -> provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG));

      assertMalformedKeptAndLegacyMigrated(real);
   }

   // authenticationChanged must not throw into the authentication listener chain (#77799), also
   // when it is the first call into the provider and so runs the migration
   @Test
   void malformedKeysDoNotEscapeAuthenticationChanged() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      seedMalformed(real);
      AuthenticationChangeEvent event = new AuthenticationChangeEvent(
         this, new IdentityID("zed", ORG), null, ORG, ORG, Identity.USER, true);

      runInit(ordered(real, "nocolon"), () -> provider.authenticationChanged(event));

      assertMalformedKeptAndLegacyMigrated(real);
   }

   // running the migration again changes nothing
   @Test
   void rerunIsNoOp() throws Exception {
      setUp(new FileAuthenticationProvider());
      KeyValueStorage<Permission> real = storage();
      clear(real);
      Permission gone = new Permission();
      gone.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "orgGone");
      real.put("VIEWSHEET:orgGone:vsX", gone).get();
      real.put("VIEWSHEET:" + ORG + ":vsA", edited(grant("alice"))).get();
      real.put("VIEWSHEET:legacyB", grant("carol")).get();
      real.put("SCHEDULE_TASK:admin~;~" + ORG + ":Task1", grant("dave")).get();

      runInit(ordered(real, "VIEWSHEET:orgGone:vsX"));
      Map<String, String> migrated = snapshot(real);
      assertEquals(Set.of("VIEWSHEET:orgGone:vsX", "VIEWSHEET:" + ORG + ":vsA",
                          "VIEWSHEET:" + ORG + ":legacyB",
                          "SCHEDULE_TASK:" + ORG + ":admin~;~" + ORG + ":Task1"), migrated.keySet());

      runInit(ordered(real, "VIEWSHEET:orgGone:vsX"));
      assertEquals(migrated, snapshot(real));
   }

   private void setUp(AuthenticationProvider authc) throws Exception {
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
      // opens the storage
      provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG);
   }

   private static void seedMalformed(KeyValueStorage<Permission> real) throws Exception {
      clear(real);
      real.put("VIEWSHEET:legacyA", grant("alice")).get();
      real.put("VIEWSHEET:legacyB", grant("bob")).get();
      real.put("VIEWSHEET:legacyC", grant("carol")).get();
      real.put("nocolon", grant("dave")).get();
      real.put("BOGUSTYPE:x", grant("erin")).get();
   }

   private static void assertMalformedKeptAndLegacyMigrated(KeyValueStorage<Permission> real) {
      assertEquals(Set.of("nocolon", "BOGUSTYPE:x", "VIEWSHEET:" + ORG + ":legacyA",
                          "VIEWSHEET:" + ORG + ":legacyB", "VIEWSHEET:" + ORG + ":legacyC"),
                   keys(real));
   }

   // the next call into the provider opens the given storage, and so runs the migration
   private void runInit(KeyValueStorage<Permission> s) throws Exception {
      runInit(s, () -> provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG));
   }

   private void runInit(KeyValueStorage<Permission> s, Body call) throws Exception {
      KeyValueStorage<Permission> real = storage();
      // the other storages, like the authentication provider's, are the real ones
      KeyValueStorageManager manager = mock(KeyValueStorageManager.class,
         AdditionalAnswers.delegatesTo(KeyValueStorageManager.getInstance()));
      doReturn(s).when(manager).getStorage(eq("defaultSecurityPermissions"), any());
      setStorage(null);

      try(MockedStatic<KeyValueStorageManager> statics = mockStatic(KeyValueStorageManager.class)) {
         statics.when(KeyValueStorageManager::getInstance).thenReturn(manager);
         assertDoesNotThrow(call::run);
      }
      finally {
         setStorage(real);
      }
   }

   // keys() with the given key first, the rest in the storage's order
   @SuppressWarnings("unchecked")
   private static KeyValueStorage<Permission> ordered(KeyValueStorage<Permission> real, String first) {
      KeyValueStorage<Permission> ordered = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> {
         List<String> keys = real.keys().collect(Collectors.toCollection(ArrayList::new));
         keys.remove(first);
         keys.add(0, first);
         return keys.stream();
      }).when(ordered).keys();
      doAnswer(inv -> {
         List<String> keys = real.keys().collect(Collectors.toCollection(ArrayList::new));
         keys.remove(first);
         keys.add(0, first);
         return keys.stream().map(k -> new KeyValuePair<>(k, real.get(k)));
      }).when(ordered).stream();
      return ordered;
   }

   // keys() in HashSet order, as IgniteDistributedMap.keySet() returns them in production
   @SuppressWarnings("unchecked")
   private static KeyValueStorage<Permission> hashSetOrder(KeyValueStorage<Permission> real) {
      KeyValueStorage<Permission> ordered = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> new HashSet<>(real.keys().toList()).stream()).when(ordered).keys();
      doAnswer(inv -> new HashSet<>(real.keys().toList()).stream()
         .map(k -> new KeyValuePair<>(k, real.get(k)))).when(ordered).stream();
      return ordered;
   }

   @SuppressWarnings("unchecked")
   private static void seedFreshDefaults(KeyValueStorage<Permission> real) throws Exception {
      clear(real);
      Class<?> c = Class.forName("inetsoft.sree.security.FileAuthorizationProvider$LoadPermissionsTask");
      Constructor<?> ctor = c.getDeclaredConstructor();
      ctor.setAccessible(true);
      Object task = ctor.newInstance();
      Method m = c.getDeclaredMethod("initialize", Map.class);
      m.setAccessible(true);
      Map<String, Permission> map = new LinkedHashMap<>();
      m.invoke(task, map);

      for(Map.Entry<String, Permission> e : map.entrySet()) {
         real.put(e.getKey(), e.getValue()).get();
      }
   }

   private static void clear(KeyValueStorage<Permission> real) throws Exception {
      real.removeAll(new HashSet<>(real.keys().toList())).get();
   }

   private static Set<String> keys(KeyValueStorage<Permission> real) {
      return real.keys().collect(Collectors.toSet());
   }

   // the test cluster returns the stored instances, so compare by value
   private static Map<String, String> snapshot(KeyValueStorage<Permission> real) {
      return real.stream().collect(Collectors.toMap(
         KeyValuePair::getKey, p -> p.getValue() + " edited=" + p.getValue().getOrgEditedGrantAll()));
   }

   private static Permission edited(Permission p) {
      Map<String, Boolean> e = new HashMap<>();
      e.put(ORG, true);
      p.setOrgEditedGrantAll(e);
      return p;
   }

   private static Permission grant(String user) {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), ORG);
      return p;
   }

   private static boolean granted(Permission p, String user) {
      return p != null && p.getUserGrants(ResourceAction.READ, ORG).stream()
         .anyMatch(i -> user.equals(i.getName()));
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (KeyValueStorage<Permission>) f.get(provider);
   }

   private void setStorage(KeyValueStorage<Permission> s) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(provider, s);
   }

   interface Body {
      void run() throws Exception;
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private FileAuthorizationProvider provider;
}
