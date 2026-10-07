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
import inetsoft.util.audit.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77834: deleting a user, group, role or organization must not leave a permission that a
 * new identity with the same name would receive, and must report one that could not be removed.
 * <ul>
 *    <li>The deleted identity's own key (who may administer it) is removed and not put back, and
 *    the other identities' own keys are not rewritten.</li>
 *    <li>A grant left behind because every write to its entry failed is reported as a warning of
 *    {@code deleteIdentities}, which fails the action record but keeps the IdentityInfo delete
 *    record and the favorites sweep, and as a user message of a dropped organization member.</li>
 * </ul>
 * The deletes run on the engine's real file providers with a constructor-built
 * {@link IdentityService}. A write failure is injected by swapping the authorization storage for a
 * delegating mock, as {@code IdentityServiceDeletePermissionSweepTest} does. Copy-on-read storage
 * is used because the plain MockCluster hands out live references, which would hide a failed put.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceDeleteLeftoverPermissionTest {
   private static final String ORG = "o77834";
   private static final String OTHER_ORG = "o77834other";
   private static final String VS = "r77834/vs";
   private static final String VS_KEY = "VIEWSHEET:" + ORG + ":" + VS;
   private static final String PROVIDER = "Primary";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID BOB = new IdentityID("bob", ORG);
   private static final IdentityID CAROL = new IdentityID("carol", ORG);
   private static final IdentityID SALES = new IdentityID("sales", ORG);
   private static final IdentityID VIEWER = new IdentityID("viewer", ORG);
   private static final IdentityID GVIEWER = new IdentityID("gviewer", null);

   private SecurityTestDataBuilder builder;
   private FileAuthorizationProvider authz;
   private FileAuthenticationProvider authc;
   private IdentityService service;
   private FavoritesService favoritesService;
   private Audit audit;
   private MockedStatic<Audit> auditStatic;
   private KeyValueStorage<Permission> realStorage;
   private final List<String> extraKeys = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .withMultiTenant("true")
         .addOrg("O77834", ORG)
         .addOrg("O77834OTHER", OTHER_ORG)
         .addUser("alice", ORG, "pw")
         .addUser("bob", ORG, "pw")
         .addUser("carol", ORG, "pw")
         .addUser("dave", ORG, "pw")
         .addGroup("sales", ORG)
         .addRole("viewer", ORG)
         .addGlobalRole("gviewer")
         // the shared resource: each identity and a co-grantee hold READ
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "alice", Identity.USER, ORG)
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "carol", Identity.USER, ORG)
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "sales", Identity.GROUP, ORG)
         .grantPermission(ResourceType.VIEWSHEET, VS, ResourceAction.READ, "viewer", Identity.ROLE, ORG)
         .markPermissionEdited(ResourceType.VIEWSHEET, VS, ORG)
         // the own keys: bob may administer alice, sales and viewer; carol may administer bob
         .grantPermission(ResourceType.SECURITY_USER, ALICE.convertToKey(), ResourceAction.ADMIN,
                          "bob", Identity.USER, ORG)
         .grantPermission(ResourceType.SECURITY_GROUP, SALES.convertToKey(), ResourceAction.ADMIN,
                          "bob", Identity.USER, ORG)
         .grantPermission(ResourceType.SECURITY_ROLE, VIEWER.convertToKey(), ResourceAction.ADMIN,
                          "bob", Identity.USER, ORG)
         .grantPermission(ResourceType.SECURITY_USER, BOB.convertToKey(), ResourceAction.ADMIN,
                          "carol", Identity.USER, ORG)
         .setup();

      SecurityEngine engine = SecurityEngine.getSecurity();
      // the engine's providers are not the builder's instances (they share the storage)
      authz = (FileAuthorizationProvider) engine.getAuthorizationChain().get().getProviders().get(0);
      authc = (FileAuthenticationProvider) engine.getAuthenticationChain().get().getProviders().get(0);

      // the global role: a grant on the resource, and its own key in both organizations
      Permission perm = authz.getPermission(ResourceType.VIEWSHEET, VS, ORG);
      Set<Permission.PermissionIdentity> roles = perm.getAllRoleGrants(ResourceAction.READ);
      roles.add(new Permission.PermissionIdentity(GVIEWER));
      perm.setGrants(ResourceAction.READ, Identity.ROLE, roles);
      authz.setPermission(ResourceType.VIEWSHEET, VS, perm, ORG);

      for(String org : new String[] { ORG, OTHER_ORG }) {
         Permission own = new Permission();
         own.setGrants(ResourceAction.ADMIN, Identity.USER,
                       new HashSet<>(Set.of(new Permission.PermissionIdentity(BOB))));
         authz.setPermission(ResourceType.SECURITY_ROLE, GVIEWER.convertToKey(), own, org);
         extraKeys.add(org);
      }

      // the group delete updates the groups of the current principal
      ThreadContext.setPrincipal(builder.principalOf("dave", ORG));

      // the requesting administrator may delete every identity here
      SecurityEngine serviceEngine = mock(SecurityEngine.class, AdditionalAnswers.delegatesTo(engine));
      doReturn(true).when(serviceEngine).checkPermission(
         any(java.security.Principal.class), any(ResourceType.class), anyString(),
         any(ResourceAction.class));

      favoritesService = mock(FavoritesService.class);
      service = new IdentityService(
         serviceEngine, engine.getSecurityProvider(), mock(IdentityThemeService.class),
         mock(AuthenticationService.class), mock(BlobStorageManager.class),
         favoritesService, mock(Cluster.class), mock(MVManager.class),
         mock(DataCycleManager.class), mock(DataSourceRegistry.class), mock(LogManager.class),
         mock(LicenseManager.class), mock(ScheduleManager.class), mock(IndexedStorage.class),
         Optional.empty(), mock(ScheduleClient.class), mock(CustomThemesManager.class),
         mock(SessionLicenseServiceProvider.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), mock(DashboardManager.class),
         mock(PortalThemesManager.class), mock(RecycleBin.class), mock(DataSpace.class),
         mock(DependencyStorageService.class), mock(ExternalStorageService.class),
         mock(XRepository.class), mock(RepletRegistryManager.class), Optional.empty());

      audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() throws Exception {
      Tool.clearUserMessage();
      auditStatic.close();
      ThreadContext.setPrincipal(null);
      ThreadContext.setContextPrincipal(null);
      restoreStorage();

      for(String org : extraKeys) {
         authz.removePermission(ResourceType.SECURITY_ROLE, GVIEWER.convertToKey(), org);
      }

      builder.teardown();
   }

   // ---- the deleted identity's own key -----------------------------------------------------

   @Test
   void deleteUser_ownKeyRemoved_recreatedUserNotAdministeredByOldGrantee() throws Exception {
      assertTrue(admin(BOB, ResourceType.SECURITY_USER, ALICE), "precondition");
      assertFalse(admin(CAROL, ResourceType.SECURITY_USER, ALICE), "control without a grant");

      assertEquals(List.of(), delete(ALICE, Identity.USER));

      assertNull(authz.getPermission(ResourceType.SECURITY_USER, ALICE.convertToKey(), ORG),
                 "the deleted user's own key must be removed");
      authc.addUser(new FSUser(ALICE));
      assertFalse(admin(BOB, ResourceType.SECURITY_USER, ALICE),
                  "the re-created user must not be administered by the old grantee");
   }

   @Test
   void deleteGroup_ownKeyRemoved_recreatedGroupNotAdministeredByOldGrantee() throws Exception {
      assertTrue(admin(BOB, ResourceType.SECURITY_GROUP, SALES), "precondition");

      assertEquals(List.of(), delete(SALES, Identity.GROUP));

      assertNull(authz.getPermission(ResourceType.SECURITY_GROUP, SALES.convertToKey(), ORG));
      authc.addGroup(new FSGroup(SALES));
      assertFalse(admin(BOB, ResourceType.SECURITY_GROUP, SALES));
   }

   @Test
   void deleteRole_ownKeyRemoved_recreatedRoleNotAdministeredByOldGrantee() throws Exception {
      assertTrue(admin(BOB, ResourceType.SECURITY_ROLE, VIEWER), "precondition");

      assertEquals(List.of(), delete(VIEWER, Identity.ROLE));

      assertNull(authz.getPermission(ResourceType.SECURITY_ROLE, VIEWER.convertToKey(), ORG));
      authc.addRole(new FSRole(VIEWER));
      assertFalse(admin(BOB, ResourceType.SECURITY_ROLE, VIEWER));
   }

   // a global role's own key is stored in each organization, outside the role's (null)
   // organization, and must be removed from all of them
   @Test
   void deleteGlobalRole_ownKeyRemovedFromEveryOrganization() throws Exception {
      assertNotNull(authz.getPermission(ResourceType.SECURITY_ROLE, GVIEWER.convertToKey(), ORG));
      assertNotNull(authz.getPermission(ResourceType.SECURITY_ROLE, GVIEWER.convertToKey(), OTHER_ORG));

      assertEquals(List.of(), delete(GVIEWER, Identity.ROLE));

      assertNull(authz.getPermission(ResourceType.SECURITY_ROLE, GVIEWER.convertToKey(), ORG));
      assertNull(authz.getPermission(ResourceType.SECURITY_ROLE, GVIEWER.convertToKey(), OTHER_ORG));
      assertFalse(granted(VS_KEY, GVIEWER, Identity.ROLE));
   }

   // deleting alice must not remove bob's own key, so a failed put of it cannot lose carol's
   // grant (the unchanged entry is still put back, as every entry of the organization is)
   @Test
   void deleteUser_otherUsersOwnKeyKeptWhenItsPutFails() throws Exception {
      assertTrue(admin(CAROL, ResourceType.SECURITY_USER, BOB), "precondition");
      String bobKey = "SECURITY_USER:" + ORG + ":" + BOB.convertToKey();
      failWrites(bobKey, true, false);

      delete(ALICE, Identity.USER);

      assertNotNull(authz.getPermission(ResourceType.SECURITY_USER, BOB.convertToKey(), ORG));
      assertTrue(admin(CAROL, ResourceType.SECURITY_USER, BOB), "carol must keep ADMIN on bob");
   }

   // an own key is matched by its type too: deleting the user "sales" keeps the group's own key
   @Test
   void deleteUser_groupWithSameNameKeepsItsOwnKey() throws Exception {
      authc.addUser(new FSUser(new IdentityID("sales", ORG)));

      assertEquals(List.of(), delete(new IdentityID("sales", ORG), Identity.USER));

      assertNotNull(authz.getPermission(ResourceType.SECURITY_GROUP, SALES.convertToKey(), ORG));
      assertTrue(admin(BOB, ResourceType.SECURITY_GROUP, SALES), "bob must keep ADMIN on the group");
   }

   // a site administrator may have stored the own key under another organization
   @Test
   void deleteUser_ownKeyStoredUnderOtherOrganizationRemoved() throws Exception {
      Permission own = new Permission();
      own.setGrants(ResourceAction.ADMIN, Identity.USER,
                    new HashSet<>(Set.of(new Permission.PermissionIdentity(BOB))));
      authz.setPermission(ResourceType.SECURITY_USER, ALICE.convertToKey(), own, OTHER_ORG);

      try {
         assertEquals(List.of(), delete(ALICE, Identity.USER));
         assertNull(authz.getPermission(ResourceType.SECURITY_USER, ALICE.convertToKey(), OTHER_ORG));
      }
      finally {
         authz.removePermission(ResourceType.SECURITY_USER, ALICE.convertToKey(), OTHER_ORG);
      }
   }

   // a global role's grant left in another organization is reported too
   @Test
   void deleteGlobalRole_leftoverInOtherOrganization_warns() throws Exception {
      String otherKey = "VIEWSHEET:" + OTHER_ORG + ":" + VS;
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.ROLE,
                     new HashSet<>(Set.of(new Permission.PermissionIdentity(GVIEWER))));
      authz.setPermission(ResourceType.VIEWSHEET, VS, perm, OTHER_ORG);

      try {
         failWrites(otherKey, false, false);
         List<String> warnings = delete(GVIEWER, Identity.ROLE);

         assertEquals(1, warnings.size(), String.valueOf(warnings));
         assertTrue(warnings.get(0).startsWith("gviewer was deleted"), warnings.get(0));
      }
      finally {
         authz.removePermission(ResourceType.VIEWSHEET, VS, OTHER_ORG);
      }
   }

   // a user with the same name in another organization is another identity: its grant is kept
   // and is not reported as a leftover
   @Test
   void deleteUser_sameNameInOtherOrganization_keptAndNotReported() throws Exception {
      IdentityID otherAlice = new IdentityID("alice", OTHER_ORG);
      String otherKey = "VIEWSHEET:" + OTHER_ORG + ":" + VS;
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     new HashSet<>(Set.of(new Permission.PermissionIdentity(otherAlice))));
      authz.setPermission(ResourceType.VIEWSHEET, VS, perm, OTHER_ORG);

      try {
         assertEquals(List.of(), delete(ALICE, Identity.USER));
         assertTrue(granted(otherKey, otherAlice, Identity.USER));
      }
      finally {
         authz.removePermission(ResourceType.VIEWSHEET, VS, OTHER_ORG);
      }
   }

   // ---- no false report --------------------------------------------------------------------

   @Test
   void deleteUser_noFailure_noWarningAndCoGranteeKept() throws Exception {
      List<String> warnings = delete(ALICE, Identity.USER);

      assertEquals(List.of(), warnings);
      assertFalse(granted(VS_KEY, ALICE, Identity.USER));
      assertTrue(granted(VS_KEY, CAROL, Identity.USER), "the co-grantee must keep its grant");
      assertActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
   }

   // one failed write is repaired by the second pass, so nothing is left and nothing reported
   @Test
   void deleteUser_singleFailureRepaired_noWarningAndCoGranteeKept() throws Exception {
      failWrites(VS_KEY, false, true);
      List<String> warnings = delete(ALICE, Identity.USER);

      assertEquals(List.of(), warnings);
      assertFalse(granted(VS_KEY, ALICE, Identity.USER));
      assertTrue(granted(VS_KEY, CAROL, Identity.USER), "the co-grantee must keep its grant");
      assertActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
   }

   @Test
   void deleteGroup_singleFailureRepaired_noWarning() throws Exception {
      failWrites(VS_KEY, false, true);

      assertEquals(List.of(), delete(SALES, Identity.GROUP));
      assertFalse(granted(VS_KEY, SALES, Identity.GROUP));
      assertTrue(granted(VS_KEY, VIEWER, Identity.ROLE));
   }

   @Test
   void deleteRole_singleFailureRepaired_noWarning() throws Exception {
      failWrites(VS_KEY, false, true);

      assertEquals(List.of(), delete(VIEWER, Identity.ROLE));
      assertFalse(granted(VS_KEY, VIEWER, Identity.ROLE));
      assertTrue(granted(VS_KEY, GVIEWER, Identity.ROLE), "the other role must keep its grant");
   }

   // ---- a grant left behind is reported ----------------------------------------------------

   @Test
   void deleteUser_everyWriteFails_warnsAndKeepsDeleteRecordAndFavoritesSweep() throws Exception {
      failWrites(VS_KEY, false, false);
      List<String> warnings = delete(ALICE, Identity.USER);

      assertNull(authc.getUser(ALICE), "the user is deleted");
      assertEquals(1, warnings.size(), String.valueOf(warnings));
      assertTrue(warnings.get(0).startsWith("alice was deleted, but some of its permissions may"),
                 warnings.get(0));
      assertActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
      verify(audit).auditIdentityInfo(any(IdentityInfoRecord.class), any());
      verify(favoritesService).removeFavorites(
         ArgumentMatchers.<Collection<IdentityID>>argThat(ids -> ids.contains(ALICE)));
   }

   @Test
   void deleteGroup_everyWriteFails_warns() throws Exception {
      failWrites(VS_KEY, false, false);
      List<String> warnings = delete(SALES, Identity.GROUP);

      assertNull(authc.getGroup(SALES));
      assertEquals(1, warnings.size(), String.valueOf(warnings));
      assertTrue(warnings.get(0).startsWith("sales was deleted"), warnings.get(0));
      verify(audit).auditIdentityInfo(any(IdentityInfoRecord.class), any());
   }

   @Test
   void deleteRole_everyWriteFails_warns() throws Exception {
      failWrites(VS_KEY, false, false);
      List<String> warnings = delete(VIEWER, Identity.ROLE);

      assertNull(authc.getRole(VIEWER));
      assertEquals(1, warnings.size(), String.valueOf(warnings));
      assertTrue(warnings.get(0).startsWith("viewer was deleted"), warnings.get(0));
   }

   // the delete of a global role cleans the organizations' entries only through the listener
   // (follow-up), so one failed write leaves the grant, which must be reported
   @Test
   void deleteGlobalRole_writeFails_warns() throws Exception {
      failWrites(VS_KEY, false, false);
      List<String> warnings = delete(GVIEWER, Identity.ROLE);

      assertNull(authc.getRole(GVIEWER));
      assertEquals(1, warnings.size(), String.valueOf(warnings));
      assertTrue(warnings.get(0).startsWith("gviewer was deleted"), warnings.get(0));
   }

   @Test
   void dropMember_everyWriteFails_addsUserMessage() throws Exception {
      failWrites(VS_KEY, false, false);

      assertTrue(removeDroppedMember(ALICE, Identity.USER));

      assertNull(authc.getUser(ALICE));
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "the leftover must be reported");
      assertTrue(message.getMessage().startsWith("alice was deleted"), message.getMessage());
   }

   @Test
   void dropMember_noFailure_noUserMessage() throws Exception {
      assertTrue(removeDroppedMember(ALICE, Identity.USER));

      assertNull(authc.getUser(ALICE));
      assertNull(Tool.getUserMessage());
   }

   // ---- organization -----------------------------------------------------------------------

   // the permission steps of the organization delete (syncIdentity's organization branch): the
   // members' removal fires the listener, then the organization's entries are removed
   @Test
   void deleteOrg_noFailure_nothingLeft() throws Exception {
      deleteOrgPermissions();

      assertNull(leftoverWarning(Identity.ORGANIZATION, new IdentityID("O77834", ORG)));
   }

   @Test
   void deleteOrg_singleFailureRepaired_nothingLeft() throws Exception {
      failWrites(VS_KEY, false, true);
      deleteOrgPermissions();

      assertNull(authz.getPermission(ResourceType.VIEWSHEET, VS, ORG));
      assertNull(leftoverWarning(Identity.ORGANIZATION, new IdentityID("O77834", ORG)));
   }

   @Test
   void deleteOrg_everyWriteFails_reported() throws Exception {
      failWrites(VS_KEY, false, false);
      deleteOrgPermissions();

      String warning = leftoverWarning(Identity.ORGANIZATION, new IdentityID("O77834", ORG));
      assertNotNull(warning);
      assertTrue(warning.startsWith("O77834 was deleted"), warning);
   }

   // a legacy key without an organization (Bug #77911) is not a leftover of any delete
   @Test
   void legacyKeyWithoutOrganization_notReported() throws Exception {
      Permission perm = new Permission();
      perm.setGrants(ResourceAction.READ, Identity.USER,
                     new HashSet<>(Set.of(new Permission.PermissionIdentity(ALICE))));
      KeyValueStorage<Permission> storage = storage();
      String legacyKey = "VIEWSHEET:r77834/legacy";
      storage.put(legacyKey, perm).get();

      try {
         assertEquals(List.of(), delete(ALICE, Identity.USER));
         deleteOrgPermissions();
         assertNull(leftoverWarning(Identity.ORGANIZATION, new IdentityID("O77834", ORG)));
      }
      finally {
         storage.remove(legacyKey).get();
      }
   }

   // ---- helpers ----------------------------------------------------------------------------

   private List<String> delete(IdentityID id, int type) {
      IdentityModel model = IdentityModel.builder().identityID(id).type(type).build();

      try {
         return service.deleteIdentities(new IdentityModel[] { model }, PROVIDER,
                                         builder.principalOf("dave", ORG));
      }
      finally {
         restoreStorage();
      }
   }

   private boolean removeDroppedMember(IdentityID id, int type) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "removeDroppedMember", EditableAuthenticationProvider.class, IdentityID.class, int.class,
         String.class);
      method.setAccessible(true);

      try {
         return (Boolean) method.invoke(service, authc, id, type, ORG);
      }
      finally {
         restoreStorage();
      }
   }

   private void deleteOrgPermissions() throws Exception {
      try {
         authc.removeOrganization(ORG);
         SecurityEngine.getSecurity().getAuthorizationChain().get()
            .cleanOrganizationFromPermissions(ORG);
      }
      finally {
         restoreStorage();
      }
   }

   private String leftoverWarning(int type, IdentityID id) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "getLeftoverPermissionsWarning", int.class, IdentityID.class, java.security.Principal.class);
      method.setAccessible(true);
      return (String) method.invoke(service, type, id, null);
   }

   private boolean admin(IdentityID user, ResourceType type, IdentityID target)
      throws Exception
   {
      SRPrincipal principal = builder.principalOf(user.name, user.orgID);
      ThreadContext.setContextPrincipal(principal);
      return SecurityEngine.getSecurity().checkPermission(
         principal, type, target.convertToKey(), ResourceAction.ADMIN);
   }

   private boolean granted(String key, IdentityID id, int type) throws Exception {
      Permission perm = storage().get(key);
      return perm != null && perm.getGrants(ResourceAction.READ, type, null)
         .contains(new Permission.PermissionIdentity(id));
   }

   private void assertActionStatus(String status) {
      ArgumentCaptor<ActionRecord> record = ArgumentCaptor.forClass(ActionRecord.class);
      verify(audit).auditAction(record.capture(), any());
      assertEquals(status, record.getValue().getActionStatus());
   }

   /**
    * Fails the writes of one key.
    *
    * @param putsOnly  {@code true} to fail only its puts, {@code false} for puts and removes.
    * @param firstOnly {@code true} to fail only its first write.
    *
    * @return the count of the attempted writes of the key.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private AtomicInteger failWrites(String key, boolean putsOnly, boolean firstOnly)
      throws Exception
   {
      KeyValueStorage<Permission> real = storage();
      realStorage = real;
      AtomicInteger writes = new AtomicInteger();
      KeyValueStorage failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> {
         if(key.equals(inv.getArgument(0)) && (writes.incrementAndGet() == 1 || !firstOnly)) {
            return CompletableFuture.failedFuture(new IOException("simulated write failure"));
         }

         return real.put(inv.getArgument(0), inv.getArgument(1));
      }).when(failing).put(anyString(), any());
      doAnswer(inv -> {
         if(!putsOnly && key.equals(inv.getArgument(0)) &&
            (writes.incrementAndGet() == 1 || !firstOnly))
         {
            return CompletableFuture.failedFuture(new IOException("simulated write failure"));
         }

         return real.remove(inv.getArgument(0));
      }).when(failing).remove(anyString());
      setStorage(failing);
      return writes;
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      if(realStorage != null) {
         return realStorage;
      }

      authz.getPermission(ResourceType.VIEWSHEET, VS, ORG); // init()
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (KeyValueStorage<Permission>) f.get(authz);
   }

   private void restoreStorage() {
      if(realStorage != null) {
         try {
            setStorage(realStorage);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      }
   }

   private void setStorage(KeyValueStorage<Permission> storage) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(authz, storage);
   }
}
