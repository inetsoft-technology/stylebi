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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tuple4;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77911: the callers of {@link FileAuthorizationProvider#getPermissions()} must keep working
 * when the permission storage holds a key that can't be parsed (no ':' or an unknown resource
 * type, kept by the migration in init()), and must never write or remove a legacy two-part key
 * (no organization) through a null organization, which resolves to the current or default
 * organization and so changes that organization's live key.
 *
 * <p>The tests run the real provider, the real authorization chain and the real
 * {@code IdentityService.updateIdentityPermissions}, and seed the permission storage after it was
 * opened. Where the result depends on the order of the entries, stream() is reordered by a mock
 * that delegates everything else to the real storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class FileAuthorizationProviderLegacyKeyCallersTest {
   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getPrincipal();
      ThreadContext.setPrincipal(null);

      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(new FileAuthenticationProvider()));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      provider = (FileAuthorizationProvider) chain().getProviders().get(0);
      // opens the storage, so the keys seeded below are not migrated
      provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG);
      real = storage();
      clear(real);
   }

   @AfterEach
   void tearDown() throws Exception {
      if(provider != null) {
         if(real != null) {
            setStorage(real);
         }

         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
      ThreadContext.setPrincipal(savedPrincipal);
   }

   // ---- keys that can't be parsed ----

   @Test
   void getPermissionsSkipsKeysThatCannotBeParsed() throws Exception {
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("nocolon", grant("dave")).get();
      real.put("BOGUSTYPE:x", grant("erin")).get();
      real.put(":x", grant("frank")).get();

      List<Tuple4<ResourceType, String, String, Permission>> permissions =
         assertDoesNotThrow(() -> provider.getPermissions());

      assertEquals(1, permissions.size());
      Tuple4<ResourceType, String, String, Permission> entry = permissions.get(0);
      assertEquals(ResourceType.VIEWSHEET, entry.getFirst());
      assertEquals(ORG, entry.getSecond());
      assertEquals("vsA", entry.getThird());
      // listing again (warned only once) gives the same result, and the keys are kept
      assertEquals(1, provider.getPermissions().size());
      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vsA", "nocolon", "BOGUSTYPE:x", ":x"), keys(real));
   }

   @Test
   void organizationCleanupRemovesTheOrganizationKeysNextToAnUnparsableKey() throws Exception {
      real.put("VIEWSHEET:orgX:vs1", grant("bob", "orgX")).get();
      real.put("VIEWSHEET:orgX:vs2", grant("bob", "orgX")).get();
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("BOGUSTYPE:x", grant("erin")).get();
      real.put("nocolon", grant("dave")).get();

      assertDoesNotThrow(() -> chain().cleanOrganizationFromPermissions("orgX"));

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vsA", "BOGUSTYPE:x", "nocolon"), keys(real));
   }

   @Test
   void identityRenameMovesTheGrantsNextToAnUnparsableKey() throws Exception {
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("nocolon", grant("dave")).get();
      real.put("BOGUSTYPE:x", grant("erin")).get();

      assertDoesNotThrow(() -> newService().updateIdentityPermissions(
         Identity.USER, new IdentityID("alice", ORG), new IdentityID("alice2", ORG), ORG, ORG, true));

      Permission live = real.get("VIEWSHEET:" + ORG + ":vsA");
      assertTrue(granted(live, "alice2"));
      assertFalse(granted(live, "alice"));
      assertTrue(keys(real).containsAll(Set.of("nocolon", "BOGUSTYPE:x")));
   }

   @Test
   void identityDeleteNextToAnUnparsableKeyDoesNotThrow() throws Exception {
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("nocolon", grant("dave")).get();

      assertDoesNotThrow(() -> newService().updateIdentityPermissions(
         Identity.USER, new IdentityID("zed", ORG), null, ORG, ORG, true));

      assertTrue(granted(real.get("VIEWSHEET:" + ORG + ":vsA"), "alice"));
   }

   // ---- legacy two-part keys ----

   // the reporter's case on a single tenant: deleting orgX must not remove the default
   // organization's live key of a path that also has a legacy key
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void organizationCleanupKeepsTheDefaultOrganizationKey(boolean legacyLast) throws Exception {
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("VIEWSHEET:vsA", grant("mallory")).get();
      real.put("VIEWSHEET:orgX:vs1", grant("bob", "orgX")).get();
      setStorage(ordered(real, legacyLast ? "VIEWSHEET:" + ORG + ":vsA" : "VIEWSHEET:vsA"));

      try {
         chain().cleanOrganizationFromPermissions("orgX");
      }
      finally {
         setStorage(real);
      }

      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vsA", "VIEWSHEET:vsA"), keys(real));
      assertTrue(granted(real.get("VIEWSHEET:" + ORG + ":vsA"), "alice"));
   }

   // multi-tenant: a site administrator whose current organization is orgy deletes orgX
   @Test
   void organizationCleanupKeepsTheCurrentOrganizationKeyWhenMultiTenant() throws Exception {
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();

      try(MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class, CALLS_REAL_METHODS)) {
         license.when(LicenseManager::isEnterprise).thenReturn(true);
         real.put("VIEWSHEET:orgy:vsA", grant("yuri", "orgy")).get();
         real.put("VIEWSHEET:vsA", grant("mallory")).get();
         real.put("VIEWSHEET:orgX:vs1", grant("bob", "orgX")).get();

         OrganizationManager.runInOrgScope("orgy", () -> {
            assertEquals("orgy", provider.getResourceOrgID(null), "the setup is not multi-tenant");
            chain().cleanOrganizationFromPermissions("orgX");
            return null;
         });

         assertEquals(Set.of("VIEWSHEET:orgy:vsA", "VIEWSHEET:vsA"), keys(real));
      }
      finally {
         SreeEnv.remove("security.users.multiTenant");
         SreeEnv.save();
      }
   }

   // deleting an unrelated user of the organization must not replace its live key with the
   // legacy copy, in either order of the entries
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void identityDeleteKeepsTheLiveKey(boolean legacyLast) throws Exception {
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("VIEWSHEET:vsA", grant("mallory")).get();
      setStorage(ordered(real, legacyLast ? "VIEWSHEET:" + ORG + ":vsA" : "VIEWSHEET:vsA"));

      try {
         newService().updateIdentityPermissions(
            Identity.USER, new IdentityID("zed", ORG), null, ORG, ORG, true);
      }
      finally {
         setStorage(real);
      }

      assertLiveKeyKept();
   }

   // a rename writes back every entry of the organization, also the legacy one
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void identityRenameKeepsTheLiveKey(boolean legacyLast) throws Exception {
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("VIEWSHEET:vsA", grant("mallory")).get();
      setStorage(ordered(real, legacyLast ? "VIEWSHEET:" + ORG + ":vsA" : "VIEWSHEET:vsA"));

      try {
         newService().updateIdentityPermissions(
            Identity.USER, new IdentityID("zed", ORG), new IdentityID("zed2", ORG), ORG, ORG, true);
      }
      finally {
         setStorage(real);
      }

      assertLiveKeyKept();
   }

   // a global role delete has no organization, so a write-back would go to the current or
   // default organization
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void globalRoleDeleteKeepsTheLiveKey(boolean legacyLast) throws Exception {
      Permission legacy = grant("mallory");
      legacy.setRoleGrantsForOrg(ResourceAction.READ, Set.of("R"), null);
      real.put("VIEWSHEET:" + ORG + ":vsA", grant("alice")).get();
      real.put("VIEWSHEET:vsA", legacy).get();
      setStorage(ordered(real, legacyLast ? "VIEWSHEET:" + ORG + ":vsA" : "VIEWSHEET:vsA"));

      try {
         newService().updateIdentityPermissions(
            Identity.ROLE, new IdentityID("R", null), null, null, null, true);
      }
      finally {
         setStorage(real);
      }

      assertLiveKeyKept();
   }

   private void assertLiveKeyKept() {
      assertEquals(Set.of("VIEWSHEET:" + ORG + ":vsA", "VIEWSHEET:vsA"), keys(real));
      Permission live = real.get("VIEWSHEET:" + ORG + ":vsA");
      assertTrue(granted(live, "alice"), "the live grant was lost");
      assertFalse(granted(live, "mallory"), "the stale legacy grant replaced the live one");
   }

   // ---- helpers ----

   private static AuthorizationChain chain() {
      return (AuthorizationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
   }

   // the real updateIdentityPermissions over the real chain, only the constructor is bypassed
   private static IdentityService newService() {
      // read before stubbing, the engine of the test context is itself a Mockito mock
      AuthorizationChain chain = chain();
      Organization org = SecurityEngine.getSecurity().getSecurityProvider().getOrganization(ORG);
      assertNotNull(org);
      SecurityProvider sp = mock(SecurityProvider.class);
      when(sp.getOrganization(ORG)).thenReturn(org);
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(sp);
      when(engine.getAuthorizationChain()).thenReturn(Optional.of(chain));
      IdentityService service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      return service;
   }

   // stream() returns the given key first
   @SuppressWarnings("unchecked")
   private static KeyValueStorage<Permission> ordered(KeyValueStorage<Permission> real, String first) {
      KeyValueStorage<Permission> ordered = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> {
         List<String> keys = real.keys().collect(Collectors.toCollection(ArrayList::new));
         keys.remove(first);
         keys.add(0, first);
         return keys.stream().map(k -> new KeyValuePair<>(k, real.get(k)));
      }).when(ordered).stream();
      return ordered;
   }

   private static void clear(KeyValueStorage<Permission> real) throws Exception {
      real.removeAll(new HashSet<>(real.keys().toList())).get();
   }

   private static Set<String> keys(KeyValueStorage<Permission> real) {
      return real.keys().collect(Collectors.toSet());
   }

   private static Permission grant(String user) {
      return grant(user, ORG);
   }

   private static Permission grant(String user, String org) {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of(user), org);
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

   private static final String ORG = Organization.getDefaultOrganizationID();
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> real;
   private Principal savedPrincipal;
}
