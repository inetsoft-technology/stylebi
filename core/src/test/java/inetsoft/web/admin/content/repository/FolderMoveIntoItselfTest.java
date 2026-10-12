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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.log.LogManager;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.RepositoryTreeController;
import inetsoft.web.portal.model.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug #77721, every server path that moves or renames a worksheet or report folder must refuse a
 * move into the folder itself or one of its subfolders before it changes anything: the asset
 * engine and repository registry primitives, and the copy-based EM tree move. Runs against the
 * real asset engine and registry, with the engine listener attached.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FolderMoveIntoItselfTest {
   private AssetRepository repo;
   private String orgId;

   @BeforeEach
   void setUp() {
      repo = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
   }

   // ---- worksheet (asset) folders: AbstractAssetEngine.changeFolder ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void assetFolderIntoItselfIsRefused() throws Exception {
      String n = "A77721a";
      addWsFolders(n, n + "/G");
      Set<String> before = assetKeys(wsFolder(n), n);

      MessageException ex = assertThrows(MessageException.class,
         () -> repo.changeFolder(wsFolder(n), wsFolder(n + "/" + n), null, true));

      assertRefusal(ex);
      assertEquals(before, assetKeys(wsFolder(n), n));
      assertTrue(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(wsFolder(n + "/G")));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void assetFolderIntoGrandchildIsRefused() throws Exception {
      String n = "A77721b";
      addWsFolders(n, n + "/G");
      Set<String> before = assetKeys(wsFolder(n), n);

      MessageException ex = assertThrows(MessageException.class,
         () -> repo.changeFolder(wsFolder(n), wsFolder(n + "/G/" + n), null, true));

      assertRefusal(ex);
      assertEquals(before, assetKeys(wsFolder(n), n));
      assertTrue(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(wsFolder(n + "/G")));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void userScopeAssetFolderIntoGrandchildIsRefused() throws Exception {
      String n = "A77721u";
      IdentityID admin = new IdentityID("admin", orgId);
      repo.addFolder(userFolder(n, admin), null);
      repo.addFolder(userFolder(n + "/G", admin), null);
      Set<String> before = assetKeys(userFolder(n, admin), n);

      MessageException ex = assertThrows(MessageException.class, () -> repo.changeFolder(
         userFolder(n, admin), userFolder(n + "/G/" + n, admin), null, true));

      assertRefusal(ex);
      assertEquals(before, assetKeys(userFolder(n, admin), n));
      assertTrue(repo.containsEntry(userFolder(n + "/G", admin)));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void assetFolderAliasOnlyChangeStillWorks() throws Exception {
      String n = "A77721c";
      addWsFolders(n, n + "/G");
      AssetEntry nentry = wsFolder(n);
      nentry.setAlias("Alias77721");

      repo.changeFolder(wsFolder(n), nentry, null, true);

      assertTrue(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(wsFolder(n + "/G")));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void assetFolderMoveIntoSiblingWithSamePrefixStillWorks() throws Exception {
      String n = "A77721d";
      addWsFolders(n, n + "/G", n + "x");

      repo.changeFolder(wsFolder(n), wsFolder(n + "x/" + n), null, true);

      assertFalse(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(wsFolder(n + "x/" + n)));
      assertTrue(repo.containsEntry(wsFolder(n + "x/" + n + "/G")));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void assetFolderCrossScopeMoveToSameNamedPathStillWorks() throws Exception {
      String n = "A77721e";
      IdentityID admin = new IdentityID("admin", orgId);
      addWsFolders(n, n + "/G");
      repo.addFolder(userFolder(n, admin), null);
      repo.addFolder(userFolder(n + "/G", admin), null);

      repo.changeFolder(wsFolder(n), userFolder(n + "/G/" + n, admin), null, true);

      assertFalse(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(userFolder(n, admin)));
      assertTrue(repo.containsEntry(userFolder(n + "/G", admin)));
      assertTrue(repo.containsEntry(userFolder(n + "/G/" + n, admin)));
      assertTrue(repo.containsEntry(userFolder(n + "/G/" + n + "/G", admin)));
   }

   // ---- report (repository) folders: RepletRegistry.changeFolder ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void registryRefusesDescendantWithoutChange() throws Exception {
      RepletRegistry reg = new RepletRegistry("raw77721");

      try {
         reg.addFolder("F");
         reg.addFolder("F/G");
         reg.addFolder("Fx");
         Set<String> before = folders(reg, "F");

         assertNotEquals("true", reg.changeFolder("F", "F/F"));
         assertNotEquals("true", reg.changeFolder("F", "F/G/F"));
         assertEquals(before, folders(reg, "F"));
         assertTrue(Arrays.asList(reg.getFolders("/")).contains("F"));

         // a sibling whose name starts with the same text is not a subfolder
         assertEquals("true", reg.changeFolder("F", "Fx/F"));
         assertTrue(folders(reg, "Fx/F").containsAll(Set.of("Fx/F", "Fx/F/G")));
      }
      finally {
         reg.shutdown();
      }
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeChangeIntoItselfIsRefused() throws Exception {
      String n = "R77721a";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      Set<String> beforeKeys = assetKeys(vsFolder(n), n);

      // /api/portal/tree/change with the folder itself as the parent: R -> R/R
      assertRefusal(assertThrows(Exception.class, () -> allowAll(() -> SUtil.getRepletRepository()
         .changeFolder(new RepositoryEntry(n, RepositoryEntry.FOLDER), n, null))));

      assertEquals(before, folders(registry(), n));
      assertEquals(beforeKeys, assetKeys(vsFolder(n), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeChangeIntoGrandchildIsRefused() throws Exception {
      String n = "R77721b";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      Set<String> beforeKeys = assetKeys(vsFolder(n), n);

      // /api/portal/tree/change with a subfolder as the parent: R -> R/G/R
      assertRefusal(assertThrows(Exception.class, () -> allowAll(() -> SUtil.getRepletRepository()
         .changeFolder(new RepositoryEntry(n, RepositoryEntry.FOLDER), n + "/G", null))));

      assertEquals(before, folders(registry(), n));
      assertEquals(beforeKeys, assetKeys(vsFolder(n), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeRenameWithSlashIntoItselfIsRefused() throws Exception {
      String n = "R77721c";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      Set<String> beforeKeys = assetKeys(vsFolder(n), n);

      // /api/portal/tree/rename with a "/" in the new name: R -> R/x and R -> R/G/R
      assertRefusal(assertThrows(Exception.class, () -> allowAll(() -> SUtil.getRepletRepository()
         .renameRepositoryEntry(new RepositoryEntry(n, RepositoryEntry.FOLDER), n + "/x", null))));
      assertRefusal(assertThrows(Exception.class, () -> allowAll(() -> SUtil.getRepletRepository()
         .renameRepositoryEntry(new RepositoryEntry(n, RepositoryEntry.FOLDER), n + "/G/" + n,
                                null))));

      assertEquals(before, folders(registry(), n));
      assertEquals(beforeKeys, assetKeys(vsFolder(n), n));
   }

   // ---- EM tree/move: RepositoryObjectService.moveFiles ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void emMoveWorksheetFolderOntoItselfIsRefused() throws Exception {
      String n = "M77721a";
      addWsFolders(n, n + "/G");
      Set<String> before = assetKeys(wsFolder(n), n);

      MessageException ex = assertThrows(MessageException.class, () -> allowAll(
         () -> emService().moveFiles(moveRequest(n, n, RepositoryEntry.WORKSHEET_FOLDER), true,
                                     admin())));

      assertRefusal(ex);
      assertEquals(before, assetKeys(wsFolder(n), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void emMoveWorksheetFolderIntoChildIsRefused() throws Exception {
      String n = "M77721b";
      addWsFolders(n, n + "/G");
      Set<String> before = assetKeys(wsFolder(n), n);

      MessageException ex = assertThrows(MessageException.class, () -> allowAll(
         () -> emService().moveFiles(moveRequest(n, n + "/G", RepositoryEntry.WORKSHEET_FOLDER),
                                     true, admin())));

      assertRefusal(ex);
      assertEquals(before, assetKeys(wsFolder(n), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void emMoveReportFolderOntoItselfIsRefused() throws Exception {
      String n = "M77721c";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      Set<String> beforeKeys = assetKeys(vsFolder(n), n);

      MessageException ex = assertThrows(MessageException.class, () -> allowAll(
         () -> emService().moveFiles(moveRequest(n, n, RepositoryEntry.REPOSITORY |
                                     RepositoryEntry.FOLDER), true, admin())));

      assertRefusal(ex);
      assertEquals(before, folders(registry(), n));
      assertEquals(beforeKeys, assetKeys(vsFolder(n), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void emMoveReportFolderIntoChildIsRefused() throws Exception {
      String n = "M77721d";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      Set<String> beforeKeys = assetKeys(vsFolder(n), n);

      MessageException ex = assertThrows(MessageException.class, () -> allowAll(
         () -> emService().moveFiles(moveRequest(n, n + "/G", RepositoryEntry.REPOSITORY |
                                     RepositoryEntry.FOLDER), true, admin())));

      assertRefusal(ex);
      assertEquals(before, folders(registry(), n));
      assertEquals(beforeKeys, assetKeys(vsFolder(n), n));
   }

   // ---- copy backstop: RepletRegistryService.copyFolder ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void copyFolderBackstopRefusesReportFolderIntoItself() throws Exception {
      String n = "C77721a";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      int type = RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER;

      // what EM edit/folder with replace=false and new path R/R reaches
      MessageException ex = assertThrows(MessageException.class, () -> allowAll(
         () -> registryService().copyFile(null, n, null, type, n, null, type, false, null,
                                          admin())));

      assertRefusal(ex);
      assertEquals(before, folders(registry(), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void copyFolderBackstopRefusesWorksheetFolderIntoChild() throws Exception {
      String n = "C77721b";
      addWsFolders(n, n + "/G");
      Set<String> before = assetKeys(wsFolder(n), n);
      int type = RepositoryEntry.WORKSHEET_FOLDER;

      MessageException ex = assertThrows(MessageException.class, () -> allowAll(
         () -> registryService().copyFile(null, n, null, type, n + "/G", null, type, true, null,
                                          admin())));

      assertRefusal(ex);
      assertEquals(before, assetKeys(wsFolder(n), n));
   }

   // ---- the refusal reaches the user as a message ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeChangeEndpointReturnsRefusalMessage() throws Exception {
      String n = "H77721a";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      ChangeRepositoryEntryEvent event = new ChangeRepositoryEntryEvent.Builder()
         .entry(new RepositoryEntryModel<>(new RepositoryEntry(n, RepositoryEntry.FOLDER)))
         .parent(new RepositoryEntryModel<>(new RepositoryEntry(n + "/G", RepositoryEntry.FOLDER)))
         .confirmed(false)
         .build();
      MessageCommand[] result = new MessageCommand[1];

      // POST /api/portal/tree/change: R -> R/G/R
      allowAll(() -> result[0] = portalController().changeRepositoryEntry(event, admin()));

      assertNotNull(result[0], "no message was returned for the refused move");
      assertEquals(MessageCommand.Type.ERROR, result[0].getType());
      assertRefusal(new Exception(result[0].getMessage()));
      assertEquals(before, folders(registry(), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeRenameEndpointReturnsRefusalMessage() throws Exception {
      String n = "H77721b";
      addReportFolders(n, n + "/G");
      Set<String> before = folders(registry(), n);
      RenameRepositoryEntryEvent event = new RenameRepositoryEntryEvent.Builder()
         .entry(new RepositoryEntryModel<>(new RepositoryEntry(n, RepositoryEntry.FOLDER)))
         .newName(n + "/G/" + n)
         .confirmed(false)
         .build();
      MessageCommand[] result = new MessageCommand[1];

      // POST /api/portal/tree/rename with a "/" in the new name: R -> R/G/R. Since Bug #77733
      // the endpoint refuses any name with a "/" before it reaches the registry primitive
      allowAll(() -> result[0] = portalController().renameRepositoryEntry(event, admin()));

      assertNotNull(result[0], "no message was returned for the refused rename");
      assertEquals(MessageCommand.Type.ERROR, result[0].getType());
      assertEquals(Tool.getInvalidFolderNameMessage(), result[0].getMessage());
      assertEquals(before, folders(registry(), n));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void emTreeMoveEndpointReturnsRefusalMessage() throws Exception {
      String n = "H77721c";
      addWsFolders(n, n + "/G");
      Set<String> before = assetKeys(wsFolder(n), n);
      MockMvc mvc = MockMvcBuilders
         .standaloneSetup(new RepositoryObjectController(
            emService(), mock(ResourcePermissionService.class), mock(ScheduleManager.class)))
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
      String body = new ObjectMapper().writeValueAsString(
         moveRequest(n, n, RepositoryEntry.WORKSHEET_FOLDER));

      // POST /api/em/content/repository/tree/move with the folder dropped onto itself
      allowAll(() -> mvc.perform(post("/api/em/content/repository/tree/move")
                                    .principal(admin())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .content(body))
         .andExpect(status().isInternalServerError())
         .andExpect(jsonPath("$.message", containsString(REFUSAL))));

      assertEquals(before, assetKeys(wsFolder(n), n));
   }

   // ---- legitimate folder moves that must keep working ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void recycleBinTrashAndRestoreWorksheetFolderStillWorks() throws Exception {
      String n = "B77721a";
      addWsFolders(n, n + "/G");
      RecycleBin bin = mock(RecycleBin.class);

      allowAll(() -> RecycleUtils.moveAssetFolderToRecycleBin(wsFolder(n), admin(), bin, true));

      String binPath = binPath(bin, n);
      assertFalse(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(wsFolder(binPath + "/G")));

      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath(binPath);
      entry.setOriginalPath(n);
      entry.setOriginalScope(AssetRepository.GLOBAL_SCOPE);
      allowAll(() -> RecycleUtils.restoreWSFolder(entry, false, admin(), bin));

      assertTrue(repo.containsEntry(wsFolder(n)));
      assertTrue(repo.containsEntry(wsFolder(n + "/G")));
      assertFalse(repo.containsEntry(wsFolder(binPath)));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void recycleBinTrashAndRestoreReportFolderStillWorks() throws Exception {
      String n = "B77721b";
      addReportFolders(n, n + "/G");
      RecycleBin bin = mock(RecycleBin.class);

      allowAll(() -> RecycleUtils.moveRepositoryFolderToRecycleBin(n, n, null, admin(), bin, true));

      String binPath = binPath(bin, n);
      assertFalse(registry().isFolder(n));
      assertTrue(registry().isFolder(binPath + "/G"));

      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath(binPath);
      entry.setOriginalPath(n);
      entry.setOriginalScope(AssetRepository.GLOBAL_SCOPE);
      allowAll(() -> RecycleUtils.restoreRepositoryFolder(entry, false, admin(), bin));

      assertTrue(registry().isFolder(n));
      assertTrue(registry().isFolder(n + "/G"));
      assertFalse(registry().isFolder(binPath));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void renameUserStillMovesUserFolders() throws Exception {
      String n = "U77721a";
      IdentityID oldUser = new IdentityID("u77721old", orgId);
      IdentityID newUser = new IdentityID("u77721new", orgId);
      repo.addFolder(userFolder(n, oldUser), null);
      repo.addFolder(userFolder(n + "/G", oldUser), null);

      repo.renameUser(oldUser, newUser);

      assertTrue(repo.containsEntry(userFolder(n, newUser)));
      assertTrue(repo.containsEntry(userFolder(n + "/G", newUser)));
      assertFalse(repo.containsEntry(userFolder(n, oldUser)));
   }

   // ---- helpers ----

   private static void assertRefusal(Throwable ex) {
      for(Throwable t = ex; t != null; t = t.getCause()) {
         if(t.getMessage() != null && t.getMessage().contains(REFUSAL)) {
            return;
         }
      }

      fail("unexpected refusal: " + ex);
   }

   // the start of the common.folder.moveIntoItself text
   private static final String REFUSAL = "into itself or one of its subfolders";

   private AssetEntry wsFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null, orgId);
   }

   private AssetEntry userFolder(String path, IdentityID user) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, path, user, orgId);
   }

   private AssetEntry vsFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, path,
                            null, orgId);
   }

   private void addWsFolders(String... paths) throws Exception {
      for(String path : paths) {
         repo.addFolder(wsFolder(path), null);
      }
   }

   private void addReportFolders(String... paths) throws Exception {
      RepletRegistry reg = registry();

      for(String path : paths) {
         reg.addFolder(path);
      }

      reg.save();
   }

   private RepletRegistry registry() throws Exception {
      return RepletRegistryManager.getInstance().getRegistry(orgId);
   }

   private Set<String> assetKeys(AssetEntry sample, String name) throws Exception {
      IndexedStorage storage = repo.getStorage(sample);
      return new TreeSet<>(storage.getKeys(k -> k.contains(name)));
   }

   private static Set<String> folders(RepletRegistry reg, String name) {
      return Arrays.stream(reg.getAllFolders())
         .filter(f -> f.equals(name) || f.startsWith(name + "/"))
         .collect(Collectors.toCollection(TreeSet::new));
   }

   private SRPrincipal admin() {
      IdentityID id = new IdentityID("admin", orgId);
      return new SRPrincipal(id, new IdentityID[] { new IdentityID("Administrator", null) },
                             new String[0], orgId, 1L);
   }

   private static MoveCopyTreeNodesRequest moveRequest(String source, String destination,
                                                       int type)
   {
      return MoveCopyTreeNodesRequest.builder()
         .addSource(node(source, type))
         .destination(node(destination, type))
         .build();
   }

   private static ContentRepositoryTreeNode node(String path, int type) {
      return ContentRepositoryTreeNode.builder().label(path).path(path).type(type).build();
   }

   private static String binPath(RecycleBin bin, String originalPath) {
      ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
      verify(bin).addEntry(path.capture(), eq(originalPath), any(), any(), anyInt(), anyInt(),
                           any());
      return path.getValue();
   }

   private static RepositoryTreeController portalController() {
      return new RepositoryTreeController(SUtil.getRepletRepository(), null, null,
         mock(ScheduleManager.class), mock(RecycleBin.class), RepletRegistryManager.getInstance());
   }

   private static RepletRegistryService registryService() {
      return new RepletRegistryService(
         SecurityEngine.getSecurity(), null, mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), RepletRegistryManager.getInstance());
   }

   private static RepositoryObjectService emService() {
      ResourcePermissionService rps = mock(ResourcePermissionService.class);
      when(rps.getRepositoryResourceType(anyInt(), anyString()))
         .thenAnswer(inv -> new Resource(ResourceType.REPORT, inv.getArgument(1)));
      return new RepositoryObjectService(registryService(), mock(ContentRepositoryTreeService.class),
         mock(SecurityProvider.class), rps, mock(XRepository.class),
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         mock(DataSourceRegistry.class), mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         RepletRegistryManager.getInstance(), mock(DashboardRegistryManager.class));
   }

   private interface Body {
      void run() throws Exception;
   }

   /**
    * Runs the body with a security engine that allows every permission check: with security
    * off, the delete permission a move needs is still denied in the test environment.
    */
   private static void allowAll(Body body) throws Exception {
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine spy = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> inv.getMethod().getName().equals("checkPermission") ?
            Boolean.TRUE : inv.callRealMethod()));

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(spy);
         body.run();
      }
   }
}
