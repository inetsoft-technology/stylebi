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
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.composer.RemoveAssetController;
import inetsoft.web.composer.model.RemoveAssetEvent;
import inetsoft.web.portal.controller.RepositoryTreeController;
import inetsoft.web.portal.model.RemoveRepositoryEntryEvent;
import inetsoft.web.portal.model.RepositoryEntryModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.analytic.composition.ViewsheetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78093, moving a dashboard (repository) folder to the recycle bin must run the same
 * dependency check a single dashboard delete runs, on every dashboard in the folder and its
 * subfolders, unless the user already confirmed. The check must reach the portal, Composer and
 * EM controllers as a confirm, and must not add a permission refusal or block a delete that
 * works today. Runs against the real asset engine and registry, with the engine listener
 * attached, so the dependency is recorded by the engine itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryFolderDeleteDependencyTest {
   private AssetRepository repo;
   private String orgId;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      repo = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // ---- RecycleUtils ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void unforcedDeleteOfFolderWithNestedDependencyAsksToConfirm() throws Exception {
      String n = "F78093a";
      addReportFolders(n, n + "/G");
      AssetEntry inner = saveViewsheet(vs(n + "/G/Inner"));
      saveOuter(vs("Outer78093a"), inner);
      RecycleBin bin = mock(RecycleBin.class);

      ConfirmException ex = assertThrows(ConfirmException.class, () -> allowAll(
         () -> RecycleUtils.moveRepositoryFolderToRecycleBin(n, n, null, admin(), bin, false)));

      assertInstanceOf(DependencyException.class, ex);
      assertTrue(ex.getMessage().contains("Outer78093a"), ex.getMessage());
      assertTrue(ex.getMessage().contains(n + "/G/Inner"), ex.getMessage());
      assertTrue(registry().isFolder(n));
      assertTrue(repo.containsEntry(inner));
      verifyNoInteractions(bin);

      allowAll(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(n, n, null, admin(), bin, true));

      assertFalse(registry().isFolder(n));
      verify(bin).addEntry(anyString(), eq(n), any(), any(), anyInt(), anyInt(), any());
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void unforcedDeleteOfNestedSubfolderAsksToConfirm() throws Exception {
      String n = "F78093b";
      addReportFolders(n, n + "/G", n + "/G/H");
      AssetEntry inner = saveViewsheet(vs(n + "/G/H/Inner"));
      saveOuter(vs("Outer78093b"), inner);
      RecycleBin bin = mock(RecycleBin.class);

      ConfirmException ex = assertThrows(ConfirmException.class, () -> allowAll(
         () -> RecycleUtils.moveRepositoryFolderToRecycleBin(
            n + "/G", "G", null, admin(), bin, false)));

      assertTrue(ex.getMessage().contains("Outer78093b"), ex.getMessage());
      assertTrue(registry().isFolder(n + "/G"));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void unforcedDeleteOfFolderWithoutDependencyDeletesWithoutPrompt() throws Exception {
      String n = "F78093c";
      addReportFolders(n, n + "/G");
      saveViewsheet(vs(n + "/G/Alone"));
      RecycleBin bin = mock(RecycleBin.class);

      allowAll(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(n, n, null, admin(), bin, false));

      assertFalse(registry().isFolder(n));
      verify(bin).addEntry(anyString(), eq(n), any(), any(), anyInt(), anyInt(), any());
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void privateFolderDeletedByNonOwnerAdminAsksToConfirmThenDeletes() throws Exception {
      String n = "P78093d";
      IdentityID owner = new IdentityID("u78093", orgId);
      RepletRegistry userRegistry = RepletRegistryManager.getInstance().getRegistry(owner);
      // the engine listener mirrors a private folder with the roles of the context principal
      ThreadContext.setContextPrincipal(new SRPrincipal(
         owner, new IdentityID[0], new String[0], orgId, 2L));
      allowAll(() -> {
         userRegistry.addFolder(MY_DASHBOARDS + "/" + n);
         userRegistry.addFolder(MY_DASHBOARDS + "/" + n + "/G");
         userRegistry.save();
      });
      assertTrue(repo.containsEntry(new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, n + "/G", owner, orgId)));
      AssetEntry inner = saveViewsheet(
         new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, n + "/G/Inner",
                        owner, orgId));
      // a private viewsheet can only be embedded by a viewsheet of the same user
      saveOuter(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                               "Outer78093d", owner, orgId), inner);
      RecycleBin bin = mock(RecycleBin.class);

      // the deleting admin is not the owner and is denied My Dashboards READ and the owner's
      // SECURITY_USER ADMIN: the dependency-only check must not turn that into a refusal
      ConfirmException ex = assertThrows(ConfirmException.class, () -> allowAllButPrivate(
         () -> RecycleUtils.moveRepositoryFolderToRecycleBin(
            MY_DASHBOARDS + "/" + n, n, owner, admin(), bin, false)));

      assertTrue(ex.getMessage().contains("Outer78093d"), ex.getMessage());
      assertTrue(userRegistry.isFolder(MY_DASHBOARDS + "/" + n));

      allowAllButPrivate(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(
         MY_DASHBOARDS + "/" + n, n, owner, admin(), bin, true));

      assertFalse(RepletRegistryManager.getInstance().getRegistry(owner)
                     .isFolder(MY_DASHBOARDS + "/" + n));
      verify(bin).addEntry(anyString(), eq(MY_DASHBOARDS + "/" + n), any(), any(), anyInt(),
                           eq(AssetRepository.USER_SCOPE), eq(owner));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void damagedMirrorDoesNotBlockUnforcedDelete() throws Exception {
      String n = "F78093e";
      addReportFolders(n, n + "/G");
      // drop the asset mirror record of the subfolder, but keep it listed in its parent, so the
      // dependency walk fails on it with a non-confirm MessageException (FOLDER_REQUIRED)
      AssetEntry sub = vsFolder(n + "/G");
      IndexedStorage storage = repo.getStorage(sub);
      storage.remove(sub.toIdentifier());
      assertTrue(repo.containsEntry(vsFolder(n)));
      RecycleBin bin = mock(RecycleBin.class);

      allowAll(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(n, n, null, admin(), bin, false));

      assertFalse(registry().isFolder(n));
      verify(bin).addEntry(anyString(), eq(n), any(), any(), anyInt(), anyInt(), any());
   }

   // ---- controllers forward the confirmed/force flag ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeRemoveAsksToConfirmThenDeletes() throws Exception {
      String n = "F78093f";
      addReportFolders(n, n + "/G");
      AssetEntry inner = saveViewsheet(vs(n + "/G/Inner"));
      saveOuter(vs("Outer78093f"), inner);
      RepositoryTreeController controller = portalController();

      MessageCommand first = allowAllResult(() -> controller.removeRepositoryEntry(
         portalEvent(n, false), admin()));

      assertNotNull(first);
      assertEquals(MessageCommand.Type.CONFIRM, first.getType(), first.getMessage());
      assertTrue(first.getMessage().contains("Outer78093f"), first.getMessage());
      assertTrue(registry().isFolder(n));

      MessageCommand second = allowAllResult(() -> controller.removeRepositoryEntry(
         portalEvent(n, true), admin()));

      assertTrue(second == null || second.getType() != MessageCommand.Type.ERROR,
                 () -> second.getMessage());
      assertFalse(registry().isFolder(n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void composerRemoveAssetAsksToConfirmThenDeletes() throws Exception {
      String n = "F78093g";
      addReportFolders(n, n + "/G");
      AssetEntry inner = saveViewsheet(vs(n + "/G/Inner"));
      saveOuter(vs("Outer78093g"), inner);
      RemoveAssetController controller = new RemoveAssetController(
         repo, mock(ViewsheetService.class), mock(SecurityProvider.class),
         mock(LibManagerProvider.class), mock(RecycleBin.class), mock(DependencyHandler.class));

      MessageCommand first = allowAllResult(() -> controller.removeAsset(
         composerEvent(n, false), admin()));

      assertNotNull(first);
      assertEquals(MessageCommand.Type.CONFIRM, first.getType(), first.getMessage());
      assertTrue(first.getMessage().contains("Outer78093g"), first.getMessage());
      assertTrue(registry().isFolder(n));

      MessageCommand second = allowAllResult(() -> controller.removeAsset(
         composerEvent(n, true), admin()));

      assertTrue(second == null || second.getType() == MessageCommand.Type.INFO,
                 () -> second.getType() + ": " + second.getMessage());
      assertFalse(registry().isFolder(n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void emDeleteNodesAsksToConfirmThenDeletes() throws Exception {
      String n = "F78093h";
      addReportFolders(n, n + "/G");
      AssetEntry inner = saveViewsheet(vs(n + "/G/Inner"));
      saveOuter(vs("Outer78093h"), inner);
      RepositoryObjectService service = emService();
      TreeNodeInfo[] nodes = { TreeNodeInfo.builder().label(n).path(n)
                                  .type(RepositoryEntry.FOLDER).build() };

      ConnectionStatus first = allowAllResult(
         () -> service.deleteNodes(nodes, admin(), false, false));

      assertNotNull(first);
      assertTrue(first.getStatus().contains("Outer78093h"), first.getStatus());
      assertTrue(registry().isFolder(n));

      allowAllResult(() -> service.deleteNodes(nodes, admin(), true, false));

      assertFalse(registry().isFolder(n));
   }

   // ---- helpers ----

   private static final String MY_DASHBOARDS = Tool.MY_DASHBOARD;

   private AssetEntry vs(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            orgId);
   }

   private AssetEntry vsFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, path,
                            null, orgId);
   }

   private AssetEntry saveViewsheet(AssetEntry entry) throws Exception {
      repo.setSheet(entry, new Viewsheet(), null, true);
      assertTrue(repo.containsEntry(entry));
      return entry;
   }

   /**
    * Saves a viewsheet that embeds the inner viewsheet. The engine records the outer viewsheet as
    * a dependent of the inner one, which is what a single dashboard delete checks.
    */
   private void saveOuter(AssetEntry outer, AssetEntry inner) throws Exception {
      Viewsheet vs = new Viewsheet();
      Viewsheet embedded = new Viewsheet().createVSAssembly("Embedded1");
      embedded.setEntry(inner);
      vs.addAssembly(embedded);
      repo.setSheet(outer, vs, null, true);

      // sanity: a single dashboard delete of the inner viewsheet asks to confirm
      assertThrows(DependencyException.class, () -> repo.checkSheetRemoveable(inner, null));
   }

   private void addReportFolders(String... paths) throws Exception {
      RepletRegistry reg = registry();

      for(String path : paths) {
         reg.addFolder(path);
      }

      reg.save();

      for(String path : paths) {
         assertTrue(repo.containsEntry(vsFolder(path)), path);
      }
   }

   private RepletRegistry registry() throws Exception {
      return RepletRegistryManager.getInstance().getRegistry(orgId);
   }

   private SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", orgId);
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], orgId, 1L);
   }

   private static RemoveRepositoryEntryEvent portalEvent(String path, boolean confirmed) {
      RepositoryEntry entry = new RepositoryEntry(path, RepositoryEntry.FOLDER);
      return new RemoveRepositoryEntryEvent.Builder()
         .entry(new RepositoryEntryModel<>(entry))
         .confirmed(confirmed)
         .build();
   }

   private RemoveAssetEvent composerEvent(String path, boolean confirmed) {
      return new RemoveAssetEvent.Builder()
         .entry(vsFolder(path))
         .confirmed(confirmed)
         .build();
   }

   private static RepositoryTreeController portalController() {
      return new RepositoryTreeController(SUtil.getRepletRepository(), null, null,
         mock(ScheduleManager.class), mock(RecycleBin.class), RepletRegistryManager.getInstance());
   }

   private static RepositoryObjectService emService() {
      ResourcePermissionService rps = mock(ResourcePermissionService.class);
      when(rps.getRepositoryResourceType(anyInt(), anyString()))
         .thenAnswer(inv -> new Resource(ResourceType.REPORT, inv.getArgument(1)));
      RepletRegistryService registryService = new RepletRegistryService(
         SecurityEngine.getSecurity(), null, mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), RepletRegistryManager.getInstance());
      return new RepositoryObjectService(registryService, mock(ContentRepositoryTreeService.class),
         mock(SecurityProvider.class), rps, mock(XRepository.class),
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         mock(DataSourceRegistry.class), mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         RepletRegistryManager.getInstance(), mock(DashboardRegistryManager.class));
   }

   private interface Body {
      void run() throws Exception;
   }

   private interface ResultBody<T> {
      T run() throws Exception;
   }

   private static void allowAll(Body body) throws Exception {
      allowAllResult(() -> {
         body.run();
         return null;
      });
   }

   /**
    * Runs the body with a security engine that allows every permission check: with security
    * off, the delete permission a move needs is still denied in the test environment.
    */
   private static <T> T allowAllResult(ResultBody<T> body) throws Exception {
      return withSecurity(false, body);
   }

   /**
    * Like {@link #allowAll}, but denies the My Dashboards READ and SECURITY_USER ADMIN
    * permissions a non-owner needs to pass the asset engine's private-asset permission check.
    */
   private static void allowAllButPrivate(Body body) throws Exception {
      withSecurity(true, () -> {
         body.run();
         return null;
      });
   }

   private static <T> T withSecurity(boolean denyPrivate, ResultBody<T> body) throws Exception {
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> {
            if(!inv.getMethod().getName().equals("checkPermission")) {
               return inv.callRealMethod();
            }

            if(denyPrivate) {
               for(Object arg : inv.getArguments()) {
                  if(arg == ResourceType.MY_DASHBOARDS || arg == ResourceType.SECURITY_USER) {
                     return Boolean.FALSE;
                  }
               }
            }

            return Boolean.TRUE;
         }));

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         return body.run();
      }
   }
}
