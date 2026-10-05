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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.IndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.composer.AddFolderController;
import inetsoft.web.composer.RenameAssetController;
import inetsoft.web.composer.model.AddFolderEvent;
import inetsoft.web.composer.model.RenameAssetEvent;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.portal.controller.RepositoryTreeController;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.model.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77733, a folder rename or create request sends only the new name and the server joins it
 * to the parent path. A name with a path separator ("T/S") built a path under another parent, and
 * the folder was moved or created there without the checks of the move endpoint (WRITE on the
 * target parent, the target parent exists). Every such endpoint must refuse the name before it
 * changes anything, while a plain rename or create still works. Runs against the real asset
 * engine and repository registry, with the engine listener attached, and a security engine that
 * allows everything, so that only the name check can refuse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FolderNameSeparatorTest {
   private AssetRepository repo;
   private String orgId;

   @BeforeEach
   void setUp() {
      repo = AssetUtil.getAssetRepository(false);
      orgId = Organization.getDefaultOrganizationID();
   }

   // ---- the shared check ----

   @Test
   void containsPathSeparator() {
      assertTrue(Tool.containsPathSeparator("T/S"));
      assertTrue(Tool.containsPathSeparator("/S"));
      assertTrue(Tool.containsPathSeparator("T~S", '~'));
      assertFalse(Tool.containsPathSeparator("T~S"));
      assertFalse(Tool.containsPathSeparator("S"));
      assertFalse(Tool.containsPathSeparator("T\\S"));
      assertFalse(Tool.containsPathSeparator(null));
      assertThrows(MessageException.class, () -> Tool.checkFolderNameSeparator("T/S"));
      assertDoesNotThrow(() -> Tool.checkFolderNameSeparator("S"));
   }

   // ---- portal repository tree: /api/portal/tree/rename and /api/portal/tree/add-folder ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeRenameWithSlashIsRefused() throws Exception {
      String s = "S77733a";
      String t = "T77733a";
      addReportFolders(s, s + "/G", t);
      Set<String> before = folders(registry(), s);
      Set<String> beforeKeys = assetKeys(vsFolder(s), s);

      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = portalController().renameRepositoryEntry(
         renameEvent(s, t + "/" + s), admin()));

      assertInvalidName(result[0]);
      assertEquals(before, folders(registry(), s));
      assertEquals(beforeKeys, assetKeys(vsFolder(s), s));
      assertFalse(registry().isFolder(t + "/" + s));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeRenameWithSlashIntoMissingParentIsRefused() throws Exception {
      String s = "S77733b";
      addReportFolders(s);

      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = portalController().renameRepositoryEntry(
         renameEvent(s, "Nope77733b/" + s), admin()));

      assertInvalidName(result[0]);
      assertTrue(registry().isFolder(s));
      assertFalse(registry().isFolder("Nope77733b/" + s));
      assertTrue(repo.containsEntry(vsFolder(s)));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalTreeRenameStillWorks() throws Exception {
      String s = "S77733c";
      addReportFolders(s, s + "/G");

      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = portalController().renameRepositoryEntry(
         renameEvent(s, s + "x"), admin()));

      assertNull(result[0], () -> "rename refused: " + result[0].getMessage());
      assertFalse(registry().isFolder(s));
      assertTrue(registry().isFolder(s + "x"));
      assertTrue(registry().isFolder(s + "x/G"));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalEditFolderWithSlashIsRefused() throws Exception {
      String s = "S77733d";
      String t = "T77733d";
      addReportFolders(s, t);
      registry().setFolderAlias(s, "Alias77733d", true);
      Set<String> before = folders(registry(), s);

      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = portalController().addRepositoryFolder(
         addFolderEvent(s, t + "/" + s, true), admin()));

      assertInvalidName(result[0]);
      assertEquals(before, folders(registry(), s));
      assertFalse(registry().isFolder(t + "/" + s));
      assertEquals("Alias77733d", registry().getFolderAlias(s));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalAddFolderWithSlashIsRefused() throws Exception {
      String t = "T77733e";
      addReportFolders(t);

      MessageCommand[] result = new MessageCommand[2];
      allowAll(() -> {
         result[0] = portalController().addRepositoryFolder(
            addFolderEvent("/", t + "/X", false), admin());
         result[1] = portalController().addRepositoryFolder(
            addFolderEvent(t, "Nope77733e/X", false), admin());
      });

      assertInvalidName(result[0]);
      assertInvalidName(result[1]);
      assertFalse(registry().isFolder(t + "/X"));
      assertFalse(registry().isFolder(t + "/Nope77733e/X"));
      assertFalse(registry().isFolder(t + "/Nope77733e"));
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void portalAddFolderStillWorks() throws Exception {
      String t = "T77733f";
      addReportFolders(t);

      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = portalController().addRepositoryFolder(
         addFolderEvent(t, "X", false), admin()));

      assertNull(result[0], () -> "create refused: " + result[0].getMessage());
      assertTrue(registry().isFolder(t + "/X"));
   }

   // ---- Composer: api/composer/asset-tree/rename-asset and add-folder ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void composerRenameReportFolderWithSlashIsRefused() throws Exception {
      String s = "S77733g";
      String t = "T77733g";
      addReportFolders(s, s + "/G", t);
      Set<String> before = folders(registry(), s);
      Set<String> beforeKeys = assetKeys(vsFolder(s), s);
      SecurityProvider provider = mock(SecurityProvider.class);

      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = renameController(repo, provider, mock(LibManagerProvider.class))
         .renameAsset(renameAssetEvent(vsFolder(s), t + "/" + s), admin()));

      assertInvalidName(result[0]);
      assertEquals(before, folders(registry(), s));
      assertEquals(beforeKeys, assetKeys(vsFolder(s), s));
      assertFalse(registry().isFolder(t + "/" + s));
      verifyNoInteractions(provider);
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void composerRenameWorksheetFolderWithSlashIsRefused() throws Exception {
      String s = "S77733h";
      addWsFolders(s, s + "/G");
      Set<String> beforeKeys = assetKeys(wsFolder(s), s);
      SecurityProvider provider = mock(SecurityProvider.class);

      // the same last segment moved nothing but moved the folder's permission to "X/S"
      MessageCommand[] result = new MessageCommand[1];
      allowAll(() -> result[0] = renameController(repo, provider, mock(LibManagerProvider.class))
         .renameAsset(renameAssetEvent(wsFolder(s), "X77733h/" + s), admin()));

      assertInvalidName(result[0]);
      assertEquals(beforeKeys, assetKeys(wsFolder(s), s));
      verifyNoInteractions(provider);
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void composerRenameTableStyleFolderWithTildeIsRefused() throws Exception {
      AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.TABLE_STYLE_FOLDER, "Table Style/S77733i", null, orgId);
      folder.setProperty("folder", "S77733i");
      LibManagerProvider libs = mock(LibManagerProvider.class);

      MessageCommand result = renameController(mock(AssetRepository.class),
         mock(SecurityProvider.class), libs)
         .renameAsset(renameAssetEvent(folder, "T77733i~S77733i"), admin());

      assertInvalidName(result);
      verifyNoInteractions(libs);
   }

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void composerAddFolderWithSeparatorIsRefused() throws Exception {
      String t = "T77733j";
      addReportFolders(t);
      addWsFolders(t);
      AssetEntry styles = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.TABLE_STYLE_FOLDER, "Table Style/" + t, null, orgId);
      styles.setProperty("folder", t);
      AddFolderController controller = new AddFolderController();
      controller.setAssetRepository(repo);
      controller.setRepletRepository(SUtil.getRepletRepository());
      controller.setViewsheetService(mock(ViewsheetService.class));
      controller.setTableStyleService(mock(TableStyleService.class));
      LibManagerProvider libs = mock(LibManagerProvider.class);
      controller.setLibManagerProvider(libs);
      AssetEntry root = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, "/", null, orgId);

      MessageCommand[] result = new MessageCommand[3];
      allowAll(() -> {
         result[0] = controller.addFolder(addFolder(root, t + "/X"), admin());
         result[1] = controller.addFolder(addFolder(wsFolder(t), "Nope77733j/X"), admin());
         result[2] = controller.addFolder(addFolder(styles, "Sub~X"), admin());
      });

      assertInvalidName(result[0]);
      assertInvalidName(result[1]);
      assertInvalidName(result[2]);
      assertFalse(registry().isFolder(t + "/X"));
      assertFalse(repo.containsEntry(wsFolder(t + "/Nope77733j/X")));
      assertFalse(repo.containsEntry(wsFolder(t + "/Nope77733j")));
      verifyNoInteractions(libs);
   }

   // ---- portal Data page: api/data/folders/rename/** and POST /api/data/folders ----

   @Test
   @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
   void dataSetFolderRenameWithSlashIsRefused() throws Exception {
      String s = "S77733k";
      String t = "T77733k";
      addWsFolders(s, s + "/G", t);
      Set<String> beforeKeys = assetKeys(wsFolder(s), s);
      DataSetService service = dataSetService();

      // the endpoint reports a failed rename only in the log and the audit record
      allowAll(() -> service.renameFolder(s, wsInfo(s), t + "/" + s,
                                          AssetRepository.GLOBAL_SCOPE, admin()));

      assertEquals(beforeKeys, assetKeys(wsFolder(s), s));
      assertTrue(repo.containsEntry(wsFolder(s)));
      assertFalse(repo.containsEntry(wsFolder(t + "/" + s)));

      allowAll(() -> service.renameFolder(s, wsInfo(s), s + "x",
                                          AssetRepository.GLOBAL_SCOPE, admin()));

      assertFalse(repo.containsEntry(wsFolder(s)));
      assertTrue(repo.containsEntry(wsFolder(s + "x/G")));
   }

   @Test
   void dataSetAddFolderWithSlashIsRefused() throws Exception {
      DataSetService service = mock(DataSetService.class);
      DataSetController controller = new DataSetController(service);

      assertThrows(MessageException.class, () -> controller.addFolder(
         ImmutableAddFolderRequest.builder().name("T77733l/X").parentPath("/")
            .scope(AssetRepository.GLOBAL_SCOPE).build(), admin()));

      verifyNoInteractions(service);
   }

   // ---- helpers ----

   private static void assertInvalidName(MessageCommand result) {
      assertNotNull(result, "the name with a separator was not refused");
      assertEquals(MessageCommand.Type.ERROR, result.getType());
      assertEquals(Tool.getInvalidFolderNameMessage(), result.getMessage());
   }

   private static RenameRepositoryEntryEvent renameEvent(String path, String newName) {
      return new RenameRepositoryEntryEvent.Builder()
         .entry(new RepositoryEntryModel<>(new RepositoryEntry(path, RepositoryEntry.FOLDER)))
         .newName(newName)
         .confirmed(false)
         .build();
   }

   private static AddRepositoryFolderEvent addFolderEvent(String path, String name, boolean edit) {
      return new AddRepositoryFolderEvent.Builder()
         .entry(new RepositoryEntryModel<>(new RepositoryEntry(path, RepositoryEntry.FOLDER)))
         .name(name)
         .edit(edit)
         .confirmed(false)
         .build();
   }

   private static RenameAssetEvent renameAssetEvent(AssetEntry entry, String newName) {
      return new RenameAssetEvent.Builder().entry(entry).newName(newName).confirmed(false).build();
   }

   private static AddFolderEvent addFolder(AssetEntry parent, String name) {
      return new AddFolderEvent.Builder().parent(parent).name(name).build();
   }

   private static WorksheetBrowserInfo wsInfo(String path) {
      return WorksheetBrowserInfo.builder()
         .name(path.substring(path.lastIndexOf('/') + 1))
         .path(path)
         .type(AssetEntry.Type.FOLDER)
         .scope(AssetRepository.GLOBAL_SCOPE)
         .id(path)
         .createdDate(0)
         .createdDateLabel("")
         .modifiedDate(0)
         .modifiedDateLabel("")
         .editable(true)
         .deletable(true)
         .materialized(false)
         .canMaterialize(false)
         .hasSubFolder(true)
         .workSheetType(0)
         .build();
   }

   private RenameAssetController renameController(AssetRepository assets, SecurityProvider provider,
                                                  LibManagerProvider libs)
   {
      return new RenameAssetController(assets, SUtil.getRepletRepository(),
         mock(ViewsheetService.class), provider, libs, mock(DataSourceRegistry.class));
   }

   private DataSetService dataSetService() {
      return new DataSetService(mock(SecurityProvider.class), SecurityEngine.getSecurity(), repo,
         mock(DataSetSearchService.class), mock(RecycleBin.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class));
   }

   private AssetEntry wsFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null, orgId);
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

   private static RepositoryTreeController portalController() {
      return new RepositoryTreeController(SUtil.getRepletRepository(), null, null,
         mock(ScheduleManager.class), mock(RecycleBin.class), RepletRegistryManager.getInstance());
   }

   private interface Body {
      void run() throws Exception;
   }

   /**
    * Runs the body with a security engine that allows every permission check, so that a request
    * is refused only by the name check.
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
