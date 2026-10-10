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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManagerProvider;
import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.data.CommonKVModel;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77378: the EM content-repository folder, object and tree endpoints must not let a caller
 * pass an owner from another organization and so read, change, create, move or delete that
 * organization's user "My Dashboards" folders. Sibling of #77349.
 * <p>
 * The permission checks are not mocked to "allow": the real {@link RepletRegistryService}
 * permission methods run against a {@link RepletEngine} whose real
 * {@code checkPermission(Principal, ResourceType, String, ResourceAction)} collapses every
 * REPORT/ASSET action on a "My Dashboards/..." path to MY_DASHBOARDS READ. The caller is a plain
 * user of org A who holds only that grant, which is enough to pass every resource check on these
 * paths for any owner. The owner organization check is what refuses a foreign owner.
 */
@Tag("core")
class RepositoryContentCrossOrgOwnerTest {
   private static final IdentityID DAVE = new IdentityID("dave", "orgA");
   private static final IdentityID CAROL = new IdentityID("carol", "orgA");
   private static final IdentityID BOB = new IdentityID("bob", "orgB");
   private static final IdentityID SITE = new IdentityID("site", "host-org");
   private static final String ROOT = Tool.MY_DASHBOARD;
   private static final String FOLDER = Tool.MY_DASHBOARD + "/Q";

   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<Util> util;
   private MockedStatic<AssetUtil> assetUtil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<RecycleUtils> recycleUtils;
   private MockedStatic<XSessionService> sessionService;

   private final Set<String> myDashboardsGrants = new HashSet<>();
   private RepletRegistryManager repletRegistryManager;
   private RepletRegistryService registryService;
   private ContentRepositoryTreeService treeService;
   private RepletRegistry bobRegistry;
   private RepositoryFolderService folderService;
   private RepositoryObjectService objectService;
   private ContentRepositoryTreeController treeController;
   private XPrincipal dave;
   private XPrincipal siteAdmin;

   @BeforeEach
   void setUp() throws Exception {
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), any()))
         .thenReturn(mock(ActionRecord.class));
      sutil.when(() -> SUtil.localize(anyString(), any(Principal.class)))
         .thenAnswer(inv -> inv.getArgument(0));
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      util = mockStatic(Util.class, CALLS_REAL_METHODS);
      util.when(() -> Util.getObjectFullPath(anyInt(), any(), any(), any())).thenReturn("x");
      util.when(() -> Util.getObjectFullPath(anyInt(), any(), any())).thenReturn("x");
      recycleUtils = mockStatic(RecycleUtils.class, CALLS_REAL_METHODS);
      recycleUtils.when(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(
         anyString(), any(), any(), any(), any(), anyBoolean())).thenAnswer(inv -> null);
      // stubbing a static with CALLS_REAL_METHODS records the stubbing call itself
      recycleUtils.clearInvocations();
      // addFolder creates an XPrincipal for the folder owner, which needs a session service
      XSessionService session = mock(XSessionService.class);
      sessionService = mockStatic(XSessionService.class);
      sessionService.when(XSessionService::getService).thenReturn(session);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      dave = principal(DAVE, "orgA");
      siteAdmin = principal(SITE, "host-org");
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(orgManager.isSiteAdmin(siteAdmin)).thenReturn(true);
      myDashboardsGrants.add(dave.getName());
      myDashboardsGrants.add(siteAdmin.getName());

      RepletEngine engine = myReportsEngine();
      assetUtil = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);

      bobRegistry = registry();
      repletRegistryManager = mock(RepletRegistryManager.class);
      when(repletRegistryManager.getRegistry(any(IdentityID.class)))
         .thenAnswer(inv -> BOB.equals(inv.getArgument(0)) ? bobRegistry : registry());

      // real permission methods; only the storage-changing primitives are stubbed
      registryService = spy(new RepletRegistryService(
         mock(SecurityEngine.class), mock(ScheduleManager.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), repletRegistryManager));
      doReturn(false).when(registryService).isDuplicatedName(anyString(), any(), any());
      doNothing().when(registryService).updateRepositoryFolder(
         anyString(), any(), anyString(), any(), any(), any(), anyBoolean(), any(), any(), any());
      doNothing().when(registryService).addRepositoryFolder(anyString(), any(), any(), any());
      doReturn(true).when(registryService).copyFile(
         any(), anyString(), any(), anyInt(), anyString(), any(), anyInt(), anyBoolean(), any(),
         any());
      doNothing().when(registryService).removeFile(anyString(), any(), anyInt(), any(), any());

      ResourcePermissionService permissionService = mock(ResourcePermissionService.class);
      when(permissionService.getRepositoryResourceType(anyInt(), anyString()))
         .thenAnswer(inv -> new Resource(ResourceType.REPORT, inv.getArgument(1)));
      treeService = mock(ContentRepositoryTreeService.class);
      RecycleBin recycleBin = mock(RecycleBin.class);

      folderService = new RepositoryFolderService(registryService, permissionService, treeService,
                                                  recycleBin, repletRegistryManager);
      objectService = new RepositoryObjectService(
         registryService, treeService, mock(SecurityProvider.class), permissionService,
         mock(XRepository.class), mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), mock(DataSourceRegistry.class),
         mock(LibManagerProvider.class), recycleBin, mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), repletRegistryManager,
         mock(DashboardRegistryManager.class));
      treeController = new ContentRepositoryTreeController(
         treeService, mock(SecurityEngine.class), repletRegistryManager);
   }

   @AfterEach
   void tearDown() {
      sessionService.close();
      recycleUtils.close();
      assetUtil.close();
      orgManagerStatic.close();
      util.close();
      audit.close();
      sutil.close();
   }

   // ---- the gap: resource permission checks do not distinguish the owner ----

   @Test
   void myReportsPermission_collapsesToMyDashboardsRead_forAnyAction() throws Exception {
      for(ResourceAction action : List.of(ResourceAction.READ, ResourceAction.WRITE,
                                          ResourceAction.DELETE, ResourceAction.ADMIN))
      {
         assertTrue(registryService.checkPermission(FOLDER, ResourceType.REPORT, action, dave));
      }

      XPrincipal noGrant = principal(new IdentityID("erin", "orgA"), "orgA");
      assertThrows(MessageException.class, () -> registryService.checkPermission(
         FOLDER, ResourceType.REPORT, ResourceAction.ADMIN, noGrant));
   }

   // ---- RepositoryFolderService.getSettings ----

   @Test
   void folderGetSettings_crossOrgOwner_isRefused() throws Exception {
      assertThrows(MessageException.class,
                   () -> folderService.getSettings(FOLDER, false, BOB, dave));
      assertNoRegistryLoaded();
   }

   @Test
   void folderGetSettings_sameOrgOwner_isAllowed() throws Exception {
      assertEquals("Q", folderService.getSettings(FOLDER, false, CAROL, dave).folderName());
      verify(repletRegistryManager).getRegistry(CAROL);
   }

   @Test
   void folderGetSettings_siteAdmin_crossOrgOwner_isAllowed() throws Exception {
      folderService.getSettings(FOLDER, false, BOB, siteAdmin);
      verify(repletRegistryManager).getRegistry(BOB);
   }

   @Test
   void folderGetSettings_mixedCaseOrgId_sameOrgPassesAndForeignOrgIsRefused() throws Exception {
      XPrincipal mixed = principal(new IdentityID("dave", "OrgA"), "OrgA");
      myDashboardsGrants.add(mixed.getName());
      IdentityID carolLower = new IdentityID("carol", "orga");

      folderService.getSettings(FOLDER, false, carolLower, mixed);
      verify(repletRegistryManager).getRegistry(carolLower);

      IdentityID bobUpper = new IdentityID("bob", "ORGB");
      assertThrows(MessageException.class,
                   () -> folderService.getSettings(FOLDER, false, bobUpper, mixed));
      verify(repletRegistryManager, never()).getRegistry(bobUpper);
   }

   @Test
   void folderGetSettings_ownerWithoutOrg_isTreatedAsCallersOrg() throws Exception {
      // "eve~;~null" parses to an owner without an org
      IdentityID noOrg = IdentityID.getIdentityIDFromKey("eve" + IdentityID.KEY_DELIMITER + "null");
      assertNull(noOrg.orgID);
      folderService.getSettings(FOLDER, false, noOrg, dave);
      verify(repletRegistryManager).getRegistry(noOrg);

      // but it is not a blanket skip: the other owner checks still apply
      IdentityID traversal = new IdentityID("../orgB/bob", null);
      assertThrows(MessageException.class,
                   () -> folderService.getSettings(FOLDER, false, traversal, dave));
      verify(repletRegistryManager, never()).getRegistry(traversal);
   }

   @Test
   void folderGetSettings_ownerWithParentPathSegment_isRefused() throws Exception {
      IdentityID traversal = new IdentityID("../../orgB/bob", "orgA");
      assertThrows(MessageException.class,
                   () -> folderService.getSettings(FOLDER, false, traversal, dave));
      assertThrows(MessageException.class,
                   () -> folderService.getSettings(FOLDER, false, traversal, siteAdmin));
      assertNoRegistryLoaded();
   }

   @Test
   void folderGetSettings_ownPrivateFolder_isAllowed() throws Exception {
      // #75997: a normal user manages their own private folder without the ADMIN grid check
      assertEquals("Q", folderService.getSettings(FOLDER, false, DAVE, dave).folderName());
      folderService.getSettings(ROOT, false, DAVE, dave);
      verify(repletRegistryManager, times(2)).getRegistry(DAVE);
   }

   // ---- RepositoryFolderService.applySettings ----

   @Test
   void folderApplySettings_crossOrgOwner_isRefused() throws Exception {
      assertThrows(MessageException.class, () -> folderService.applySettings(
         BOB, settings(FOLDER, ROOT + "/R"), dave));
      assertNoRegistryLoaded();
      verify(registryService, never()).updateRepositoryFolder(
         anyString(), any(), anyString(), any(), any(), any(), anyBoolean(), any(), any(), any());
   }

   @Test
   void folderApplySettings_crossOrgDestinationInNewPath_isRefused() throws Exception {
      // an own-org folder moved into another org's registry via the "(key)" prefix of newPath
      String newPath = "(" + BOB.convertToKey() + ")" + ROOT + "/R";
      assertThrows(MessageException.class, () -> folderService.applySettings(
         CAROL, settings(FOLDER, newPath), dave));
      assertNoRegistryLoaded();
      verify(registryService, never()).updateRepositoryFolder(
         anyString(), any(), anyString(), any(), any(), any(), anyBoolean(), any(), any(), any());
   }

   @Test
   void folderApplySettings_sameOrgOwner_isAllowed() throws Exception {
      folderService.applySettings(CAROL, settings(FOLDER, ROOT + "/R"), dave);
      verify(registryService).updateRepositoryFolder(
         eq(FOLDER), eq(CAROL), eq(ROOT + "/R"), eq(CAROL), any(), any(), anyBoolean(), any(),
         any(), eq(dave));
   }

   @Test
   void folderApplySettings_ownPrivateFolder_isAllowed() throws Exception {
      folderService.applySettings(DAVE, settings(FOLDER, ROOT + "/R"), dave);
      verify(registryService).updateRepositoryFolder(
         eq(FOLDER), eq(DAVE), eq(ROOT + "/R"), eq(DAVE), any(), any(), anyBoolean(), any(),
         any(), eq(dave));
   }

   @Test
   void folderApplySettings_siteAdmin_crossOrgOwner_isAllowed() throws Exception {
      folderService.applySettings(BOB, settings(FOLDER, ROOT + "/R"), siteAdmin);
      verify(registryService).updateRepositoryFolder(
         eq(FOLDER), eq(BOB), eq(ROOT + "/R"), eq(BOB), any(), any(), anyBoolean(), any(),
         any(), eq(siteAdmin));
   }

   // ---- RepositoryObjectService.deleteNodes ----

   @Test
   void deleteNodes_mixedBatchWithCrossOrgFolderOwner_isRefusedBeforeAnyDelete() throws Exception {
      TreeNodeInfo[] nodes = { node(RepositoryEntry.FOLDER, FOLDER, CAROL),
                               node(RepositoryEntry.FOLDER, FOLDER, BOB) };

      assertThrows(MessageException.class,
                   () -> objectService.deleteNodes(nodes, dave, false, false));
      assertNoRegistryLoaded();
      recycleUtils.verify(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(
         anyString(), any(), any(), any(), any(), anyBoolean()), never());
   }

   @Test
   void deleteNodes_crossOrgViewsheetOrWorksheetOwner_isRefused() throws Exception {
      for(int type : new int[]{ RepositoryEntry.VIEWSHEET, RepositoryEntry.WORKSHEET,
                                RepositoryEntry.WORKSHEET_FOLDER })
      {
         TreeNodeInfo[] nodes = { node(type, FOLDER, BOB) };
         assertThrows(MessageException.class,
                      () -> objectService.deleteNodes(nodes, dave, false, false));
      }

      assertNoRegistryLoaded();
      verifyNoInteractions(treeService);
   }

   @Test
   void deleteNodes_sameOrgOwner_isAllowed() throws Exception {
      objectService.deleteNodes(new TreeNodeInfo[]{ node(RepositoryEntry.FOLDER, FOLDER, CAROL) },
                                dave, false, false);
      recycleUtils.verify(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(
         eq(FOLDER), any(), eq(CAROL), eq(dave), any(), eq(false)));
   }

   @Test
   void deleteNodes_forcedRepositoryFolder_forwardsForce() throws Exception {
      // Bug #78093, the confirmed retry of a dependency prompt skips the dependency check
      objectService.deleteNodes(new TreeNodeInfo[]{ node(RepositoryEntry.FOLDER, FOLDER, CAROL) },
                                dave, true, false);
      recycleUtils.verify(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(
         eq(FOLDER), any(), eq(CAROL), eq(dave), any(), eq(true)));
   }

   @Test
   void deleteNodes_siteAdmin_crossOrgOwner_isAllowed() throws Exception {
      objectService.deleteNodes(new TreeNodeInfo[]{ node(RepositoryEntry.FOLDER, FOLDER, BOB) },
                                siteAdmin, false, false);
      recycleUtils.verify(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(
         eq(FOLDER), any(), eq(BOB), eq(siteAdmin), any(), eq(false)));
   }

   @ParameterizedTest
   @ValueSource(ints = {
      RepositoryEntry.FOLDER, RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER,
      RepositoryEntry.TRASHCAN, RepositoryEntry.DASHBOARD })
   void deleteNodes_crossOrgOwner_binTrashcanOrDashboard_isRefused(int type) throws Exception {
      // an in-bin folder is removed straight from the owner's registry (removeFolder + save);
      // trashcan and dashboard nodes go through the same per-node owner check
      String binPath = ROOT + "/" + RecycleUtils.RECYCLE_BIN_FOLDER + "/Q";
      TreeNodeInfo[] nodes = { node(type, binPath, BOB) };

      assertThrows(MessageException.class,
                   () -> objectService.deleteNodes(nodes, dave, false, true));
      assertNoRegistryLoaded();
   }

   @Test
   void deleteNodes_sameOrgOwnerInRecycleBin_isAllowed() throws Exception {
      String binPath = ROOT + "/" + RecycleUtils.RECYCLE_BIN_FOLDER + "/Q";
      TreeNodeInfo[] nodes =
         { node(RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER, binPath, CAROL) };
      objectService.deleteNodes(nodes, dave, false, true);
      verify(repletRegistryManager).getRegistry(CAROL);
   }

   // ---- RepositoryObjectService.addFolder ----

   @Test
   void addFolder_crossOrgOwner_isRefused() throws Exception {
      assertThrows(MessageException.class,
                   () -> objectService.addFolder(folderRequest(BOB), false, dave));
      assertNoRegistryLoaded();
      verify(registryService, never()).addRepositoryFolder(anyString(), any(), any(), any());
   }

   @Test
   void addFolder_ownAndSameOrgOwner_isAllowed() throws Exception {
      objectService.addFolder(folderRequest(DAVE), false, dave);
      verify(registryService).addRepositoryFolder(ROOT + "/New", null, null, DAVE);
      objectService.addFolder(folderRequest(CAROL), false, dave);
      verify(registryService).addRepositoryFolder(ROOT + "/New", null, null, CAROL);
   }

   @Test
   void addFolder_siteAdmin_crossOrgOwner_isAllowed() throws Exception {
      objectService.addFolder(folderRequest(BOB), false, siteAdmin);
      verify(registryService).addRepositoryFolder(ROOT + "/New", null, null, BOB);
   }

   // ---- RepositoryObjectService.moveFiles ----

   @Test
   void moveFiles_crossOrgDestination_isRefused() throws Exception {
      assertThrows(MessageException.class,
                   () -> objectService.moveFiles(moveRequest(CAROL, BOB), true, dave));
      assertNoRegistryLoaded();
      verifyNoCopy();
   }

   @Test
   void moveFiles_anyCrossOrgSource_isRefusedBeforeAnyMove() throws Exception {
      // a same-org source listed first must not be moved before the foreign source is refused
      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .addSource(treeNode(FOLDER, CAROL))
         .addSource(treeNode(ROOT + "/P", BOB))
         .destination(treeNode(ROOT + "/Target", CAROL))
         .build();
      assertThrows(MessageException.class, () -> objectService.moveFiles(request, true, dave));
      assertNoRegistryLoaded();
      verifyNoCopy();
   }

   @Test
   void moveFiles_sameOrgOwners_isAllowed() throws Exception {
      objectService.moveFiles(moveRequest(CAROL, DAVE), true, dave);
      verify(registryService).copyFile(any(), eq(FOLDER), eq(CAROL), anyInt(), anyString(),
                                       eq(DAVE), anyInt(), eq(true), any(), eq(dave));
   }

   @Test
   void moveFiles_siteAdmin_crossOrgOwners_isAllowed() throws Exception {
      objectService.moveFiles(moveRequest(BOB, BOB), true, siteAdmin);
      verify(registryService).copyFile(any(), eq(FOLDER), eq(BOB), anyInt(), anyString(),
                                       eq(BOB), anyInt(), eq(true), any(), eq(siteAdmin));
   }

   // ---- ContentRepositoryTreeController ----

   @Test
   void privateTree_anyCrossOrgOwner_isRefusedBeforeAnyRegistryLoad() throws Exception {
      CommonKVModel<String, String>[] users = users(CAROL, BOB);

      assertThrows(MessageException.class,
                   () -> treeController.getRepositoryPrivateTree(users, dave));
      assertNoRegistryLoaded();
      verifyNoInteractions(treeService);
   }

   @Test
   void privateTree_sameOrgAndSiteAdmin_areAllowed() throws Exception {
      treeController.getRepositoryPrivateTree(users(CAROL, DAVE), dave);
      treeController.getRepositoryPrivateTree(users(BOB), siteAdmin);
      verify(repletRegistryManager).getRegistry(CAROL);
      verify(repletRegistryManager).getRegistry(DAVE);
      verify(repletRegistryManager).getRegistry(BOB);
   }

   @Test
   void tree_crossOrgOwner_isRefused() throws Exception {
      for(String path : List.of(Tool.MY_DASHBOARD, SUtil.MY_DASHBOARD)) {
         assertThrows(MessageException.class,
                      () -> treeController.getRepositoryTree(path, BOB.convertToKey(), dave));
      }

      assertNoRegistryLoaded();
      verifyNoInteractions(treeService);
   }

   @Test
   void tree_sameOrgAndSiteAdmin_areAllowed() throws Exception {
      treeController.getRepositoryTree("other", CAROL.convertToKey(), dave);
      treeController.getRepositoryTree("other", BOB.convertToKey(), siteAdmin);
      verify(repletRegistryManager).getRegistry(CAROL);
      verify(repletRegistryManager).getRegistry(BOB);
   }

   @Test
   void tree_ownOwner_isAllowedEvenForPlainPrincipal() throws Exception {
      Principal bob = mock(Principal.class);
      when(bob.getName()).thenReturn(BOB.convertToKey());

      treeController.getRepositoryTree("other", BOB.convertToKey(), bob);
      verify(repletRegistryManager).getRegistry(BOB);
   }

   // ---- helpers ----

   /**
    * A RepletEngine that runs the real single-action and multi-action checkPermission, with the
    * MY_DASHBOARDS grant answered from {@link #myDashboardsGrants}. Every other resource is
    * denied, so the only way through is the "My Dashboards" collapse.
    */
   private RepletEngine myReportsEngine() {
      RepletEngine engine = mock(RepletEngine.class);
      when(engine.checkPermission(any(), any(ResourceType.class), anyString(),
                                  any(ResourceAction.class)))
         .thenAnswer(inv -> {
            Principal user = inv.getArgument(0);
            ResourceType type = inv.getArgument(1);
            String resource = inv.getArgument(2);

            if(type == ResourceType.MY_DASHBOARDS) {
               return "*".equals(resource) && inv.getArgument(3) == ResourceAction.READ &&
                  user != null && myDashboardsGrants.contains(user.getName());
            }

            return SUtil.isMyReport(resource) && (boolean) inv.callRealMethod();
         });
      when(engine.checkPermission(any(), any(ResourceType.class), anyString(), any(EnumSet.class)))
         .thenCallRealMethod();
      return engine;
   }

   private void assertNoRegistryLoaded() {
      verifyNoInteractions(repletRegistryManager);
      verifyNoInteractions(bobRegistry);
   }

   private void verifyNoCopy() {
      try {
         verify(registryService, never()).copyFile(
            any(), anyString(), any(), anyInt(), anyString(), any(), anyInt(), anyBoolean(),
            any(), any());
      }
      catch(Exception e) {
         fail(e);
      }
   }

   private static XPrincipal principal(IdentityID id, String orgId) {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(id.convertToKey());
      when(principal.getOrgId()).thenReturn(orgId);
      when(principal.getIdentityID()).thenReturn(id);
      return principal;
   }

   private static RepletRegistry registry() {
      RepletRegistry registry = mock(RepletRegistry.class);
      when(registry.getFolders(anyString(), anyBoolean())).thenReturn(new String[0]);
      when(registry.isFolder(anyString())).thenReturn(true);
      return registry;
   }

   private static SetRepositoryFolderSettingsModel settings(String oldPath, String newPath) {
      return SetRepositoryFolderSettingsModel.builder()
         .oldPath(oldPath)
         .newPath(newPath)
         .replace(false)
         .isWSFolder(false)
         .build();
   }

   private static TreeNodeInfo node(int type, String path, IdentityID owner) {
      return TreeNodeInfo.builder()
         .label("Q")
         .path(path)
         .owner(owner)
         .type(type)
         .build();
   }

   private static NewRepositoryFolderRequest folderRequest(IdentityID owner) {
      NewRepositoryFolderRequest request = new NewRepositoryFolderRequest();
      request.setOwner(owner);
      request.setParentFolder(ROOT);
      request.setFolderName("New");
      request.setType(RepositoryEntry.FOLDER);
      return request;
   }

   private static ContentRepositoryTreeNode treeNode(String path, IdentityID owner) {
      return ContentRepositoryTreeNode.builder()
         .label(path)
         .path(path)
         .owner(owner)
         .type(RepositoryEntry.FOLDER)
         .build();
   }

   private static MoveCopyTreeNodesRequest moveRequest(IdentityID from, IdentityID to) {
      return MoveCopyTreeNodesRequest.builder()
         .source(List.of(treeNode(FOLDER, from)))
         .destination(treeNode(ROOT + "/Target", to))
         .build();
   }

   @SuppressWarnings("unchecked")
   private static CommonKVModel<String, String>[] users(IdentityID... owners) {
      return Arrays.stream(owners)
         .map(owner -> new CommonKVModel<>(owner.convertToKey(), "other"))
         .toArray(CommonKVModel[]::new);
   }
}
