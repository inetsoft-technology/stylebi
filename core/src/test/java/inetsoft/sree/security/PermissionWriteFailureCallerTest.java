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

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: callers of the real FileAuthorizationProvider over a storage whose writes fail.
 * The EM permission pane must report a failed grant or revoke, and the identity migration
 * (updateIdentityPermissions) must stay best-effort and keep the old key when the write of the
 * new key fails.
 *
 * <p>The failing storage hands out copies of the stored permissions: the test cluster's map
 * returns the stored instance itself, so a caller that edits the permission it read would make
 * a failed write look applied.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PermissionWriteFailureCallerTest {
   @BeforeEach
   void setUp() throws Exception {
      FileAuthenticationProvider authc = new FileAuthenticationProvider();
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

      chain = (AuthorizationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      // opens the storage
      provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG);
      real = storage();
   }

   @AfterEach
   void tearDown() throws Exception {
      if(provider != null) {
         setStorage(real);
         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
   }

   // the EM permission pane "remove all" (no permissions in the model) is a revoke
   @Test
   void emPaneRevokeAll_putFails_reportsErrorAndGrantStays() throws Exception {
      real.put(key("VIEWSHEET", ORG, "vsRevoke"), grant("mallory")).get();
      failWrites(k -> true);

      ResourcePermissionModel model = model(null);

      assertThrows(MessageException.class, () -> newPermissionService()
         .setResourcePermissions("vsRevoke", ResourceType.VIEWSHEET, model, principal()));

      setStorage(real);
      assertTrue(granted(fresh(key("VIEWSHEET", ORG, "vsRevoke")), "mallory"),
                 "the grant was revoked although the write failed");
   }

   @Test
   void emPaneGrant_putFails_reportsErrorAndNothingStored() throws Exception {
      failWrites(k -> true);

      ResourcePermissionModel model = model(List.of(ResourcePermissionTableModel.builder()
         .identityID(new IdentityID("alice", ORG))
         .type(Identity.Type.USER)
         .actions(EnumSet.of(ResourceAction.READ))
         .build()));

      assertThrows(MessageException.class, () -> newPermissionService()
         .setResourcePermissions("vsGrant", ResourceType.VIEWSHEET, model, principal()));

      setStorage(real);
      assertNull(fresh(key("VIEWSHEET", ORG, "vsGrant")));
   }

   // a org-ID migration re-keys every permission of the org: when the write of the new key
   // fails, the old key must be kept (the remove is skipped) and nothing is thrown
   @Test
   void identityMigration_newKeyWriteFails_oldKeyKeptAndNoThrow() throws Exception {
      String oldOrg = "migOrgOld";
      String newOrg = "migOrgNew";
      real.put(key("VIEWSHEET", oldOrg, "vsMig"), grant("alice")).get();
      real.put(key("VIEWSHEET", oldOrg, "vsMig2"), grant("alice")).get();
      // only the first re-put fails
      failWrites(k -> k.equals(key("VIEWSHEET", newOrg, "vsMig")));

      IdentityService service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", SecurityEngine.getSecurity());
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      assertDoesNotThrow(() -> service.updateIdentityPermissions(
         Identity.USER, new IdentityID("alice", oldOrg), new IdentityID("alice", newOrg),
         oldOrg, newOrg, true));

      setStorage(real);
      assertNotNull(fresh(key("VIEWSHEET", oldOrg, "vsMig")),
                    "the old key was removed although the new key was not written");
      assertNull(fresh(key("VIEWSHEET", newOrg, "vsMig")));
      // the other item is still migrated
      assertNull(fresh(key("VIEWSHEET", oldOrg, "vsMig2")));
      assertNotNull(fresh(key("VIEWSHEET", newOrg, "vsMig2")));
   }

   private ResourcePermissionService newPermissionService() {
      SecurityEngine engine = SecurityEngine.getSecurity();
      return new ResourcePermissionService(engine.getSecurityProvider(), engine,
                                           mock(LibManagerProvider.class),
                                           mock(DataSourceRegistry.class));
   }

   private static ResourcePermissionModel model(List<ResourcePermissionTableModel> permissions) {
      return ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(false)
         .grantReadToAllVisible(false)
         .grantReadToAll(false)
         .permissions(permissions)
         .build();
   }

   private static Principal principal() {
      return new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0], new String[0],
                             ORG, 0L);
   }

   @SuppressWarnings("unchecked")
   private void failWrites(Predicate<String> failKey) throws Exception {
      KeyValueStorage<Permission> failing =
         mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      CompletableFuture<?> failed =
         CompletableFuture.failedFuture(new IOException("simulated write failure"));
      doAnswer(inv -> failKey.test(inv.getArgument(0)) ? failed :
         real.put(inv.getArgument(0), inv.getArgument(1)))
         .when(failing).put(anyString(), any());
      doAnswer(inv -> failKey.test(inv.getArgument(0)) ? failed : real.remove(inv.getArgument(0)))
         .when(failing).remove(anyString());
      doAnswer(inv -> copy(real.get(inv.getArgument(0)))).when(failing).get(anyString());
      doAnswer(inv -> real.stream().map(p -> new KeyValuePair<>(p.getKey(), copy(p.getValue()))))
         .when(failing).stream();
      setStorage(failing);
   }

   private Permission fresh(String key) {
      return copy(real.get(key));
   }

   private static Permission copy(Permission p) {
      return p == null ? null : (Permission) p.clone();
   }

   private static String key(String type, String org, String path) {
      return type + ":" + org + ":" + path;
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

   private static final String ORG = Organization.getDefaultOrganizationID();
   private AuthorizationChain chain;
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> real;
}
