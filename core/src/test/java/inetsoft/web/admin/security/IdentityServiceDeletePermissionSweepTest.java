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
package inetsoft.web.admin.security;

import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.log.LogManager;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77831: deleting a user, group or role must remove only that identity from the resource
 * permissions, and keep every other grantee of the same type.
 *
 * The delete sweep ({@code updateIdentityPermissions(type, id, null, ...)}) only finds the deleted
 * identity when the authorization listener left one of its grants behind, so one storage put of
 * the listener is failed here (the same delegating mock as
 * {@code FileAuthorizationProviderAuthenticationChangedFailureTest}). The delete is driven through
 * the real {@code syncIdentity} of a constructor-built {@link IdentityService}, on the engine's real
 * file providers. Copy-on-read storage is used because the plain MockCluster hands out live
 * references, and the in-place setGrants would hide a failed put.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceDeletePermissionSweepTest {
   private static final String ORG = "o77831";
   private static final String OTHER_ORG = "o77831other";
   private static final String[] RES = { "r77831/a", "r77831/b", "r77831/c" };

   private SecurityTestDataBuilder builder;
   private FileAuthorizationProvider authz;
   private FileAuthenticationProvider authc;
   private IdentityService service;
   private KeyValueStorage<Permission> realStorage;
   private final AtomicInteger puts = new AtomicInteger();

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .withMultiTenant("true")
         .addOrg("O77831", ORG)
         .addUser("alice", ORG, "pw")
         .addUser("carol", ORG, "pw")
         .addUser("dave", ORG, "pw")
         .addGroup("g1", ORG)
         .addGroup("g2", ORG)
         .addRole("r1", ORG)
         .addRole("r2", ORG);

      for(String r : RES) {
         builder.grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "alice", Identity.USER, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "carol", Identity.USER, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "g1", Identity.GROUP, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "g2", Identity.GROUP, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "r1", Identity.ROLE, ORG)
                .grantPermission(ResourceType.VIEWSHEET, r, ResourceAction.READ, "r2", Identity.ROLE, ORG)
                .markPermissionEdited(ResourceType.VIEWSHEET, r, ORG);
      }

      builder.setup();
      SecurityEngine engine = SecurityEngine.getSecurity();
      // the engine's providers are not the builder's instances (they share the storage)
      authz = (FileAuthorizationProvider) engine.getAuthorizationChain().get().getProviders().get(0);
      authc = (FileAuthenticationProvider) engine.getAuthenticationChain().get().getProviders().get(0);
      // the group delete updates the groups of the current principal
      ThreadContext.setPrincipal(builder.principalOf("dave", ORG));

      service = new IdentityService(
         engine, engine.getSecurityProvider(), mock(IdentityThemeService.class),
         mock(AuthenticationService.class), mock(BlobStorageManager.class),
         mock(FavoritesService.class), mock(Cluster.class), mock(MVManager.class),
         mock(DataCycleManager.class), mock(DataSourceRegistry.class), mock(LogManager.class),
         mock(LicenseManager.class), mock(ScheduleManager.class), mock(IndexedStorage.class),
         Optional.empty(), mock(ScheduleClient.class), mock(CustomThemesManager.class),
         mock(SessionLicenseServiceProvider.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), mock(DashboardManager.class),
         mock(PortalThemesManager.class), mock(RecycleBin.class), mock(DataSpace.class),
         mock(DependencyStorageService.class), mock(ExternalStorageService.class),
         mock(XRepository.class), mock(RepletRegistryManager.class), Optional.empty());
   }

   @AfterEach
   void tearDown() throws Exception {
      ThreadContext.setPrincipal(null);

      if(realStorage != null) {
         setStorage(realStorage);
      }

      for(String r : RES) {
         authz.removePermission(ResourceType.VIEWSHEET, r, ORG);
      }

      builder.teardown();
   }

   // Control: without a failure the listener cleans every entry and the sweep finds nothing.
   @Test
   void deleteUser_noFailure_keepsOtherUser() throws Exception {
      delete(new IdentityID("alice", ORG), Identity.USER);

      assertEquals(0, granted("alice", ORG, Identity.USER));
      assertEquals(3, granted("carol", ORG, Identity.USER));
   }

   @Test
   void deleteUser_listenerPutFails_keepsOtherUserAndRemovesLeftover() throws Exception {
      failSecondPut();
      delete(new IdentityID("alice", ORG), Identity.USER);

      assertEquals(0, granted("alice", ORG, Identity.USER), "the leftover grant must be removed");
      assertEquals(3, granted("carol", ORG, Identity.USER), "the other user must keep every grant");
   }

   @Test
   void deleteGroup_listenerPutFails_keepsOtherGroupAndRemovesLeftover() throws Exception {
      failSecondPut();
      delete(new IdentityID("g1", ORG), Identity.GROUP);

      assertEquals(0, granted("g1", ORG, Identity.GROUP), "the leftover grant must be removed");
      assertEquals(3, granted("g2", ORG, Identity.GROUP), "the other group must keep every grant");
   }

   @Test
   void deleteRole_listenerPutFails_keepsOtherRoleAndRemovesLeftover() throws Exception {
      failSecondPut();
      delete(new IdentityID("r1", ORG), Identity.ROLE);

      assertEquals(0, granted("r1", ORG, Identity.ROLE), "the leftover grant must be removed");
      assertEquals(3, granted("r2", ORG, Identity.ROLE), "the other role must keep every grant");
   }

   // a grantee with the deleted user's name in another organization is a different identity
   @Test
   void deleteUser_listenerPutFails_keepsSameNamedUserOfOtherOrg() throws Exception {
      for(String r : RES) {
         Permission perm = authz.getPermission(ResourceType.VIEWSHEET, r, ORG);
         Set<Permission.PermissionIdentity> users = perm.getAllUserGrants(ResourceAction.READ);
         users.add(new Permission.PermissionIdentity("alice", OTHER_ORG));
         perm.setGrants(ResourceAction.READ, Identity.USER, users);
         authz.setPermission(ResourceType.VIEWSHEET, r, perm, ORG);
      }

      failSecondPut();
      delete(new IdentityID("alice", ORG), Identity.USER);

      assertEquals(0, granted("alice", ORG, Identity.USER), "the leftover grant must be removed");
      assertEquals(3, granted("alice", OTHER_ORG, Identity.USER),
                   "the same-named user of the other organization must keep every grant");
      assertEquals(3, granted("carol", ORG, Identity.USER));
   }

   /**
    * Deletes the identity through the real syncIdentity(), the delete core of deleteIdentities(),
    * then restores the real storage so the assertions read the stored permissions.
    */
   private void delete(IdentityID id, int type) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);

      try {
         method.invoke(service, authc, new DefaultIdentity(id, type), null);
      }
      catch(InvocationTargetException e) {
         throw (Exception) e.getCause();
      }
      finally {
         if(realStorage != null) {
            setStorage(realStorage);
         }
      }
   }

   private long granted(String name, String granteeOrg, int identityType) {
      return Arrays.stream(RES)
         .map(r -> authz.getPermission(ResourceType.VIEWSHEET, r, ORG))
         .filter(p -> p != null && p.getGrants(ResourceAction.READ, identityType, null).stream()
            .anyMatch(pi -> name.equals(pi.getName()) && granteeOrg.equals(pi.getOrganizationID())))
         .count();
   }

   /**
    * Swaps the engine's authorization storage for a delegating mock whose 2nd put fails without
    * writing, so the listener leaves the deleted identity's grant in that entry.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void failSecondPut() throws Exception {
      authz.getPermission(ResourceType.VIEWSHEET, RES[0], ORG); // init()
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      realStorage = (KeyValueStorage<Permission>) f.get(authz);
      KeyValueStorage spy = Mockito.mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(realStorage));
      Mockito.doAnswer(inv -> {
         if(puts.incrementAndGet() == 2) {
            return CompletableFuture.failedFuture(new IOException("simulated write failure"));
         }

         return realStorage.put(inv.getArgument(0), inv.getArgument(1));
      }).when(spy).put(ArgumentMatchers.anyString(), ArgumentMatchers.any());
      f.set(authz, spy);
   }

   private void setStorage(KeyValueStorage<Permission> storage) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(authz, storage);
   }
}
