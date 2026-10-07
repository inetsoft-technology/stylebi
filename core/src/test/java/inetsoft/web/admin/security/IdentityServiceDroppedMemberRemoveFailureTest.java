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
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77964: when an organization save drops a member and the provider's first removal of it
 * fails, removeDroppedMember() removes it again. That fallback must run the rest of the delete,
 * like syncIdentity() does after a removal: the member's own permission key (who may administer
 * it) is removed, or a new identity with the same name is administered by the old grantee, and a
 * permission left behind is reported.
 * <p>
 * The save runs through the real {@code setOrganizationInfo} on the engine's real file providers.
 * The provider passed in is a spy of the real one whose first removal of the dropped member throws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceDroppedMemberRemoveFailureTest {
   private static final String ORG = "o77964";
   private static final String VS = "r77964/vs";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID BOB = new IdentityID("bob", ORG);
   private static final IdentityID SALES = new IdentityID("sales", ORG);
   private static final IdentityID VIEWER = new IdentityID("viewer", ORG);

   private SecurityTestDataBuilder builder;
   private FileAuthorizationProvider authz;
   private FileAuthenticationProvider authc;
   private FileAuthenticationProvider provider;
   private IdentityService service;
   private IdentityThemeService themeService;
   private IndexedStorage indexedStorage;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .withMultiTenant("true")
         .addOrg("O77964", ORG)
         .addUser("alice", ORG, "pw")
         .addUser("bob", ORG, "pw")
         .addUser("dave", ORG, "pw")
         .addGroup("sales", ORG)
         .addRole("viewer", ORG)
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "alice", Identity.USER, ORG)
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "sales", Identity.GROUP, ORG)
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "viewer", Identity.ROLE, ORG)
         .markPermissionEdited(ResourceType.VIEWSHEET, VS, ORG)
         // the own keys: bob may administer alice, sales and viewer
         .grantPermission(ResourceType.SECURITY_USER, ALICE.convertToKey(), ResourceAction.ADMIN,
                          "bob", Identity.USER, ORG)
         .grantPermission(ResourceType.SECURITY_GROUP, SALES.convertToKey(), ResourceAction.ADMIN,
                          "bob", Identity.USER, ORG)
         .grantPermission(ResourceType.SECURITY_ROLE, VIEWER.convertToKey(), ResourceAction.ADMIN,
                          "bob", Identity.USER, ORG)
         .setup();

      SecurityEngine engine = SecurityEngine.getSecurity();
      // the engine's providers are not the builder's instances (they share the storage)
      authz = (FileAuthorizationProvider) engine.getAuthorizationChain().get().getProviders().get(0);
      authc = (FileAuthenticationProvider) engine.getAuthenticationChain().get().getProviders().get(0);
      provider = spy(authc);

      // the group delete updates the groups of the current principal
      ThreadContext.setPrincipal(builder.principalOf("dave", ORG));

      // the requesting organization administrator may delete every identity here
      SecurityEngine serviceEngine = mock(SecurityEngine.class, AdditionalAnswers.delegatesTo(engine));
      doReturn(true).when(serviceEngine).checkPermission(
         any(Principal.class), any(ResourceType.class), anyString(), any(ResourceAction.class));

      themeService = mock(IdentityThemeService.class);
      indexedStorage = mock(IndexedStorage.class);
      service = spy(new IdentityService(
         serviceEngine, engine.getSecurityProvider(), themeService,
         mock(AuthenticationService.class), mock(BlobStorageManager.class),
         mock(FavoritesService.class), mock(Cluster.class), mock(MVManager.class),
         mock(DataCycleManager.class), mock(DataSourceRegistry.class), mock(LogManager.class),
         mock(LicenseManager.class), mock(ScheduleManager.class), indexedStorage,
         Optional.empty(), mock(ScheduleClient.class), mock(CustomThemesManager.class),
         mock(SessionLicenseServiceProvider.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), mock(DashboardManager.class),
         mock(PortalThemesManager.class), mock(RecycleBin.class), mock(DataSpace.class),
         mock(DependencyStorageService.class), mock(ExternalStorageService.class),
         mock(XRepository.class), mock(RepletRegistryManager.class), Optional.empty()));
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
      ThreadContext.setPrincipal(null);
      ThreadContext.setContextPrincipal(null);
      builder.teardown();
   }

   // ---- the provider's first removal fails, the fallback's removal succeeds ----------------

   @Test
   void dropRole_firstRemoveFails_ownKeyRemoved() throws Exception {
      doThrow(new RuntimeException("storage remove failed")).doCallRealMethod()
         .when(provider).removeRole(VIEWER);

      dropMember(VIEWER);

      verify(provider, times(2)).removeRole(VIEWER);
      assertDeleted(VIEWER, Identity.ROLE, ResourceType.SECURITY_ROLE);
      authc.addRole(new FSRole(VIEWER));
      assertFalse(admin(BOB, ResourceType.SECURITY_ROLE, VIEWER),
                  "the re-created role must not be administered by the old grantee");
      verify(themeService).removeIdentity(eq("viewer"), eq(ORG), any());
   }

   @Test
   void dropUser_firstRemoveFails_ownKeyRemovedAndUserDataCleaned() throws Exception {
      doThrow(new RuntimeException("storage remove failed")).doCallRealMethod()
         .when(provider).removeUser(ALICE);

      dropMember(ALICE);

      verify(provider, times(2)).removeUser(ALICE);
      assertDeleted(ALICE, Identity.USER, ResourceType.SECURITY_USER);
      authc.addUser(new FSUser(ALICE));
      assertFalse(admin(BOB, ResourceType.SECURITY_USER, ALICE),
                  "the re-created user must not be administered by the old grantee");
      // the user-scoped assets and the themes are cleaned up as by a delete (the second scan of
      // the indexed storage is the favorites sweep)
      verify(indexedStorage, times(2)).getKeys(any(), eq(ORG));
      verify(themeService).removeIdentity(eq("alice"), eq(ORG), any());
   }

   @Test
   void dropGroup_firstRemoveFails_ownKeyRemoved() throws Exception {
      doThrow(new RuntimeException("storage remove failed")).doCallRealMethod()
         .when(provider).removeGroup(SALES);

      dropMember(SALES);

      verify(provider, times(2)).removeGroup(SALES);
      assertDeleted(SALES, Identity.GROUP, ResourceType.SECURITY_GROUP);
      authc.addGroup(new FSGroup(SALES));
      assertFalse(admin(BOB, ResourceType.SECURITY_GROUP, SALES),
                  "the re-created group must not be administered by the old grantee");
      verify(themeService).removeIdentity(eq("sales"), eq(ORG), any());
   }

   // a failure in one step of the fallback's cleanup does not skip the others
   @Test
   void dropUser_firstRemoveFails_failedCleanupStepDoesNotSkipTheRest() throws Exception {
      doThrow(new RuntimeException("storage remove failed")).doCallRealMethod()
         .when(provider).removeUser(ALICE);
      // the first scan is the user-scoped assets, the next one the favorites sweep
      when(indexedStorage.getKeys(any(), anyString()))
         .thenThrow(new RuntimeException("storage down")).thenReturn(Set.of());

      dropMember(ALICE);

      assertDeleted(ALICE, Identity.USER, ResourceType.SECURITY_USER);
      verify(themeService).removeIdentity(eq("alice"), eq(ORG), any());
   }

   // ---- the member is already gone when the fallback looks for it --------------------------

   // the storage remove times out and completes after the provider found the role still stored:
   // the provider's listener never ran, the fallback finds nothing to remove, and the grants and
   // the own key are removed by the fallback
   @Test
   void dropRole_removeTimesOutThenCompletes_ownKeyAndGrantRemoved() throws Exception {
      AtomicBoolean pending = new AtomicBoolean();
      doAnswer(inv -> {
         pending.set(true);
         throw new RuntimeException("storage remove timed out");
      }).when(provider).removeRole(VIEWER);
      // the delete checks whether the role still exists, then the remove completes
      doAnswer(inv -> {
         Object role = inv.callRealMethod();

         if(role != null && pending.getAndSet(false)) {
            roleStorage().remove(VIEWER.convertToKey()).get();
         }

         return role;
      }).when(provider).getRole(VIEWER);

      dropMember(VIEWER);

      assertNull(authc.getRole(VIEWER), "the role is removed");
      verify(provider, times(1)).removeRole(VIEWER);
      assertNull(authz.getPermission(ResourceType.SECURITY_ROLE, VIEWER.convertToKey(), ORG),
                 "the dropped role's own key must be removed");
      assertFalse(granted(VIEWER, Identity.ROLE), "the dropped role's grant must be removed");
      assertTrue(granted(ALICE, Identity.USER), "the co-grantee must keep its grant");
      assertNull(leftoverWarning(Identity.ROLE, VIEWER));
   }

   // the delete fails after the permissions were cleaned: the fallback finds nothing left and
   // does not write the organization's permissions again
   @Test
   void dropUser_failsAfterPermissionCleanup_permissionsNotRewritten() throws Exception {
      // the first scan is the user-scoped assets, the next one the favorites sweep
      when(indexedStorage.getKeys(any(), anyString()))
         .thenThrow(new RuntimeException("storage down")).thenReturn(Set.of());

      dropMember(ALICE);

      assertDeleted(ALICE, Identity.USER, ResourceType.SECURITY_USER);
      verify(provider, times(1)).removeUser(ALICE);
      verify(service, times(1)).updateIdentityPermissions(
         eq(Identity.USER), eq(ALICE), isNull(), any(), any(), anyBoolean());
   }

   // the permission cleanup of the delete fails after the removal: the fallback finds the own key
   // left and removes it
   @Test
   void dropRole_permissionCleanupFailsAfterRemove_ownKeyRemoved() throws Exception {
      doThrow(new RuntimeException("permissions unavailable")).doCallRealMethod()
         .when(service).updateIdentityPermissions(
            eq(Identity.ROLE), eq(VIEWER), isNull(), any(), any(), anyBoolean());

      dropMember(VIEWER);

      verify(provider, times(1)).removeRole(VIEWER);
      assertDeleted(VIEWER, Identity.ROLE, ResourceType.SECURITY_ROLE);
      verify(service, times(2)).updateIdentityPermissions(
         eq(Identity.ROLE), eq(VIEWER), isNull(), any(), any(), anyBoolean());
   }

   // ---- control ----------------------------------------------------------------------------

   @Test
   void dropRole_noFailure_noMessageAndNoSecondRewrite() throws Exception {
      dropMember(VIEWER);

      assertNull(authc.getRole(VIEWER));
      assertNull(authz.getPermission(ResourceType.SECURITY_ROLE, VIEWER.convertToKey(), ORG));
      assertNull(Tool.getUserMessage());
      verify(service, times(1)).updateIdentityPermissions(
         eq(Identity.ROLE), eq(VIEWER), isNull(), any(), any(), anyBoolean());
   }

   // ---- helpers ----------------------------------------------------------------------------

   private void assertDeleted(IdentityID id, int type, ResourceType ownType) throws Exception {
      assertFalse(exists(id, type), "the dropped member is removed");
      assertNull(authz.getPermission(ownType, id.convertToKey(), ORG),
                 "the dropped member's own key must be removed");
      assertFalse(granted(id, type), "the dropped member's grant must be removed");
      assertNull(leftoverWarning(type, id), "no permission of the dropped member is left");
   }

   private boolean exists(IdentityID id, int type) {
      return switch(type) {
         case Identity.USER -> authc.getUser(id) != null;
         case Identity.GROUP -> authc.getGroup(id) != null;
         default -> authc.getRole(id) != null;
      };
   }

   /**
    * Saves the organization with every member except the dropped one, as the organization pane
    * does, by the organization's administrator dave.
    */
   private void dropMember(IdentityID dropped) throws Exception {
      FSOrganization oldOrg = (FSOrganization) authc.getOrganization(ORG);
      List<IdentityModel> members = new ArrayList<>();
      addMembers(members, authc.getUsers(), Identity.USER, dropped);
      addMembers(members, authc.getGroups(), Identity.GROUP, dropped);
      addMembers(members, authc.getRoles(), Identity.ROLE, dropped);
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(ORG)
         .name(oldOrg.getName())
         .oldName(oldOrg.getName())
         .members(members)
         .status(true)
         .theme("")
         .build();

      Method method = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      method.setAccessible(true);
      SRPrincipal dave = builder.principalOf("dave", ORG);
      ThreadContext.setContextPrincipal(dave);

      try {
         method.invoke(service, oldOrg, model, provider, dave);
      }
      catch(InvocationTargetException e) {
         throw e.getCause() instanceof Exception ex ? ex : e;
      }
   }

   private static void addMembers(List<IdentityModel> members, IdentityID[] ids, int type,
                                  IdentityID dropped)
   {
      Arrays.stream(ids)
         .filter(id -> ORG.equals(id.orgID) && !id.equals(dropped))
         .forEach(id -> members.add(IdentityModel.builder().identityID(id).type(type).build()));
   }

   private boolean admin(IdentityID user, ResourceType type, IdentityID target)
      throws Exception
   {
      SRPrincipal principal = builder.principalOf(user.name, user.orgID);
      ThreadContext.setContextPrincipal(principal);
      return SecurityEngine.getSecurity().checkPermission(
         principal, type, target.convertToKey(), ResourceAction.ADMIN);
   }

   private boolean granted(IdentityID id, int type) {
      Permission perm = authz.getPermission(ResourceType.VIEWSHEET, VS, ORG);
      return perm != null && perm.getGrants(ResourceAction.READ, type, null)
         .contains(new Permission.PermissionIdentity(id));
   }

   private String leftoverWarning(int type, IdentityID id) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "getLeftoverPermissionsWarning", int.class, IdentityID.class, Principal.class);
      method.setAccessible(true);
      return (String) method.invoke(service, type, id, null);
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<FSRole> roleStorage() throws Exception {
      Field f = FileAuthenticationProvider.class.getDeclaredField("roleStorage");
      f.setAccessible(true);
      return (KeyValueStorage<FSRole>) f.get(authc);
   }
}
