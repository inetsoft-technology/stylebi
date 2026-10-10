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

import inetsoft.mv.SharedMVUtil;
import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.*;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.schedule.*;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.portal.data.DatasourcesBaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78217: the permission writes that follow a structural change that is already committed
 * (a delete, a recycle-bin restore, a folder rename, a rename of additional connections, the
 * dashboard store load, the grant to the creator of a new item) run against the real
 * FileAuthorizationProvider over a storage whose writes fail. A failed write must not fail the
 * change, stop a loop or recursion halfway, or delete the only copy of a permission.
 *
 * <p>The failing storage hands out copies of the stored permissions, see
 * {@link PermissionWriteFailureCallerTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PermissionSideEffectWriteFailureTest {
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

      engine = SecurityEngine.getSecurity();
      AuthorizationChain chain =
         (AuthorizationChain) engine.getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      // opens the storage
      provider.getPermission(ResourceType.VIEWSHEET, "probe", ORG);
      real = storage();
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() throws Exception {
      Tool.clearUserMessage();

      if(provider != null) {
         setStorage(real);
         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
   }

   // a moved permission whose new key can't be written stays at the old key
   @Test
   void movePermission_newKeyWriteFails_oldKeyKeptAndReported() throws Exception {
      real.put(key("ASSET", "from"), grant("alice")).get();
      failWrites(k -> k.equals(key("ASSET", "to")));

      assertFalse(AbstractAssetEngine.movePermissionBestEffort(
         engine, ResourceType.ASSET, "from", "to", grant("alice")));

      setStorage(real);
      assertTrue(granted(fresh(key("ASSET", "from")), "alice"));
      assertNull(fresh(key("ASSET", "to")));
      assertNotNull(Tool.getUserMessage(), "the permission left at the old key is not reported");
   }

   @Test
   void movePermission_samePath_nothingRemoved() throws Exception {
      real.put(key("ASSET", "same"), grant("alice")).get();

      assertTrue(AbstractAssetEngine.movePermissionBestEffort(
         engine, ResourceType.ASSET, "same", "same", grant("alice")));

      assertTrue(granted(fresh(key("ASSET", "same")), "alice"));
   }

   // EM Content > Repository delete of two scripts: the delete of the second one runs although
   // the permission of the first one can't be removed, and the leftover grant is reported
   @Test
   void emDeleteNodes_scriptPermissionRemoveFails_allScriptsDeletedAndWarned() throws Exception {
      real.put(key("SCRIPT", "s1"), grant("alice")).get();
      failWrites(k -> true);

      LibManager lib = mock(LibManager.class);
      LibManagerProvider libProvider = mock(LibManagerProvider.class);
      when(libProvider.getManager(any(Principal.class))).thenReturn(lib);
      RepletRegistryManager registryManager = mock(RepletRegistryManager.class);
      when(registryManager.getRegistry((IdentityID) any())).thenReturn(mock(RepletRegistry.class));
      when(registryManager.getRegistry()).thenReturn(mock(RepletRegistry.class));
      RepositoryObjectService service = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         engine.getSecurityProvider(), mock(ResourcePermissionService.class),
         mock(inetsoft.uql.XRepository.class), mock(RepositoryDashboardService.class),
         mock(inetsoft.web.admin.content.database.model.DataModelFolderManagerService.class),
         mock(DataSourceRegistry.class), libProvider, mock(RecycleBin.class),
         mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), registryManager,
         mock(inetsoft.sree.web.dashboard.DashboardRegistryManager.class));

      TreeNodeInfo[] nodes = {
         TreeNodeInfo.builder().label("s1").path("s1").type(RepositoryEntry.SCRIPT).build(),
         TreeNodeInfo.builder().label("s2").path("s2").type(RepositoryEntry.SCRIPT).build()
      };

      assertDoesNotThrow(() -> service.deleteNodes(nodes, principal(), true, true));

      verify(lib).removeScript("s1");
      verify(lib).removeScript("s2");
      assertNotNull(Tool.getUserMessage(), "the grant left at s1 is not reported");
   }

   // EM recycle bin restore of two worksheets: the permission is written at the original path
   // before it's removed from the bin path, and the second restore runs
   @Test
   void recycleRestore_binKeyRemoveFails_bothRestoredWithTheirPermission() throws Exception {
      real.put(key("ASSET", "Recycle Bin/bin1"), grant("alice")).get();
      real.put(key("ASSET", "Recycle Bin/bin2"), grant("bob")).get();
      failWrites(k -> k.equals(key("ASSET", "Recycle Bin/bin1")));

      RecycleBin recycleBin = mock(RecycleBin.class);
      RecycleBin.Entry e1 = recycleEntry("Recycle Bin/bin1", "ws1", grant("alice"));
      RecycleBin.Entry e2 = recycleEntry("Recycle Bin/bin2", "ws2", grant("bob"));

      withRecycleStatics(() -> {
         RecycleUtils.restoreSheet(e1, false, principal(), recycleBin);
         RecycleUtils.restoreSheet(e2, false, principal(), recycleBin);
      });

      setStorage(real);
      assertTrue(granted(fresh(key("ASSET", "ws1")), "alice"));
      assertTrue(granted(fresh(key("ASSET", "ws2")), "bob"));
      assertNull(fresh(key("ASSET", "Recycle Bin/bin2")));
      verify(recycleBin).removeEntry("Recycle Bin/bin2");
   }

   // the original path can't be written: the only copy of the permission, at the bin path, is kept
   @Test
   void recycleRestore_originalKeyWriteFails_binKeyKept() throws Exception {
      real.put(key("ASSET", "Recycle Bin/bin1"), grant("alice")).get();
      failWrites(k -> k.equals(key("ASSET", "ws1")));

      RecycleBin.Entry e1 = recycleEntry("Recycle Bin/bin1", "ws1", grant("alice"));

      withRecycleStatics(
         () -> RecycleUtils.restoreSheet(e1, false, principal(), mock(RecycleBin.class)));

      setStorage(real);
      assertTrue(granted(fresh(key("ASSET", "Recycle Bin/bin1")), "alice"));
   }

   // EM Schedule > Tasks folder rename of a folder with a subfolder: the subfolder is moved and
   // the old folders are removed although no permission can be written, and the folder's own
   // permission is not lost
   @Test
   void scheduleFolderRename_permissionWritesFail_subfoldersMovedAndPermissionKept()
      throws Exception
   {
      real.put(key("SCHEDULE_TASK_FOLDER", "A"), grant("alice")).get();
      real.put(key("SCHEDULE_TASK_FOLDER", "A/sub"), grant("bob")).get();
      failWrites(k -> true);

      Map<String, Object> folders = new HashMap<>();
      AssetEntry root = scheduleFolder("/");
      AssetEntry a = scheduleFolder("A");
      AssetEntry sub = scheduleFolder("A/sub");
      folders.put(root.toIdentifier(), folder(a));
      folders.put(a.toIdentifier(), folder(sub));
      folders.put(sub.toIdentifier(), new AssetFolder());
      IndexedStorage storage = inMemoryStorage(folders);
      ScheduleTaskFolderService service = new ScheduleTaskFolderService(
         mock(inetsoft.sree.schedule.ScheduleManager.class), engine,
         engine.getSecurityProvider(), storage, mock(RenameTransformHandler.class));

      service.changeFolder(a, scheduleFolder("B"), principal());

      assertTrue(folders.containsKey(scheduleFolder("B").toIdentifier()));
      assertTrue(folders.containsKey(scheduleFolder("B/sub").toIdentifier()),
                 "the subfolder was not moved");
      assertFalse(folders.containsKey(a.toIdentifier()), "the old folder was not removed");
      assertFalse(folders.containsKey(sub.toIdentifier()), "the old subfolder was not removed");
      AssetFolder newRoot = (AssetFolder) folders.get(root.toIdentifier());
      assertFalse(newRoot.containsEntry(a), "the old folder is still listed in the parent");

      setStorage(real);
      assertTrue(granted(fresh(key("SCHEDULE_TASK_FOLDER", "A")), "alice"),
                 "the folder permission was lost");
      assertTrue(granted(fresh(key("SCHEDULE_TASK_FOLDER", "A/sub")), "bob"));
   }

   // portal data source save that swaps the names of two additional connections
   @Test
   void portalAdditionalPermissions_swappedNames_keepBothPermissions() throws Exception {
      String a = "ds" + XUtil.ADDITIONAL_DS_CONNECTOR + "a";
      String b = "ds" + XUtil.ADDITIONAL_DS_CONNECTOR + "b";
      real.put(key("DATA_SOURCE", a), grant("alice")).get();
      real.put(key("DATA_SOURCE", b), grant("bob")).get();
      DatasourcesBaseService service = mock(DatasourcesBaseService.class, CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "dataSourceRegistry", mock(DataSourceRegistry.class));
      Map<String, String> renames = new LinkedHashMap<>();
      renames.put("a", "b");
      renames.put("b", "a");
      Map<String, Permission> permissions = Map.of("a", grant("alice"), "b", grant("bob"));

      ReflectionTestUtils.invokeMethod(service, "updateAdditionalPermissions", "ds", "ds",
                                       Set.of(), Set.of(), renames, permissions);

      assertTrue(granted(fresh(key("DATA_SOURCE", b)), "alice"));
      assertFalse(granted(fresh(key("DATA_SOURCE", b)), "bob"));
      assertTrue(granted(fresh(key("DATA_SOURCE", a)), "bob"));
      assertFalse(granted(fresh(key("DATA_SOURCE", a)), "alice"));
   }

   // the new name of a renamed connection can't be written: its old key is kept, nothing throws
   @Test
   void portalAdditionalPermissions_newKeyWriteFails_oldKeyKept() throws Exception {
      String a = "ds" + XUtil.ADDITIONAL_DS_CONNECTOR + "a";
      String c = "ds" + XUtil.ADDITIONAL_DS_CONNECTOR + "c";
      real.put(key("DATA_SOURCE", a), grant("alice")).get();
      failWrites(k -> k.equals(key("DATA_SOURCE", c)));
      DatasourcesBaseService service = mock(DatasourcesBaseService.class, CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "dataSourceRegistry", mock(DataSourceRegistry.class));

      assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
         service, "updateAdditionalPermissions", "ds", "ds", Set.of(), Set.of(),
         Map.of("a", "c"), Map.of("a", grant("alice"))));

      setStorage(real);
      assertTrue(granted(fresh(key("DATA_SOURCE", a)), "alice"));
   }

   // EM data source save that swaps the names of two additional connections
   @Test
   void emAdditionalPermissions_swappedNames_keepBothPermissions() throws Exception {
      String a = "ds" + XUtil.ADDITIONAL_DS_CONNECTOR + "a";
      String b = "ds" + XUtil.ADDITIONAL_DS_CONNECTOR + "b";
      real.put(key("DATA_SOURCE", a), grant("alice")).get();
      real.put(key("DATA_SOURCE", b), grant("bob")).get();
      DatabaseDatasourcesService service =
         mock(DatabaseDatasourcesService.class, CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "dataSourceRegistry", mock(DataSourceRegistry.class));
      Map<String, String> renames = new LinkedHashMap<>();
      renames.put("a", "b");
      renames.put("b", "a");

      ReflectionTestUtils.invokeMethod(service, "updateAdditionalPermissions", "ds", Set.of(),
                                       renames);

      assertTrue(granted(fresh(key("DATA_SOURCE", b)), "alice"));
      assertTrue(granted(fresh(key("DATA_SOURCE", a)), "bob"));
   }

   // the dashboard store load writes the permission of every dashboard, a failed write must not
   // abort the load
   @Test
   void loadDashboards_permissionWritesFail_validateDoesNotThrow() throws Exception {
      failWrites(k -> true);
      Class<?> taskClass =
         Class.forName("inetsoft.sree.web.dashboard.DashboardManager$LoadDashboardsTask");
      Constructor<?> constructor = taskClass.getDeclaredConstructor(String.class);
      constructor.setAccessible(true);
      Object task = constructor.newInstance("dashboards");
      Method validate = taskClass.getDeclaredMethod("validate", Map.class);
      validate.setAccessible(true);
      inetsoft.sree.web.dashboard.DashboardManager.DashboardData data =
         new inetsoft.sree.web.dashboard.DashboardManager.DashboardData();
      data.setDashboards(new ArrayList<>(List.of("d1", "d2")));
      Map<String, Object> map = new HashMap<>();
      map.put(inetsoft.uql.util.Identity.USER + ":alice", data);

      assertDoesNotThrow(() -> {
         try {
            validate.invoke(task, map);
         }
         catch(InvocationTargetException e) {
            throw e.getCause();
         }
      });
   }

   // EM Security > new user/group/role: the identity is created, a failed grant to its creator
   // is returned as a warning instead of thrown
   @Test
   void createIdentityPermissions_grantFails_returnsWarning() throws Exception {
      failWrites(k -> true);
      IdentityService service = mock(IdentityService.class, CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(service, "securityProvider", engine.getSecurityProvider());

      String warning = assertDoesNotThrow(() -> service.createIdentityPermissions(
         new IdentityID("user1", ORG), ResourceType.SECURITY_USER, principal()));

      assertNotNull(warning);
   }

   @Test
   void createIdentityPermissions_grantSaved_returnsNoWarning() throws Exception {
      IdentityService service = mock(IdentityService.class, CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(service, "securityProvider", engine.getSecurityProvider());

      assertNull(service.createIdentityPermissions(
         new IdentityID("user1", ORG), ResourceType.SECURITY_USER, principal()));
   }

   // EM Schedule > Data Cycles new cycle: the cycle is returned with its name and a warning
   @Test
   void addDataCycle_grantFails_returnsCycleWithWarning() throws Exception {
      failWrites(k -> true);
      DataCycleManager cycles = mock(DataCycleManager.class);
      ScheduleCycleService service = new ScheduleCycleService(
         cycles, mock(ScheduleConditionService.class), mock(SchedulerMonitoringService.class),
         mock(ResourcePermissionService.class), engine);

      DataCycleInfo info = service.addDataCycle(principal(), "UTC");

      assertEquals("Cycle1", info.getName());
      assertNotNull(info.getWarning());
      verify(cycles).save();
   }

   private void withRecycleStatics(ThrowingRunnable r) throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.containsEntry(any())).thenReturn(true);
      when(repository.getAssetEntry(any())).thenAnswer(inv -> inv.getArgument(0));

      try(MockedStatic<AssetUtil> au = mockStatic(AssetUtil.class, CALLS_REAL_METHODS);
          MockedStatic<RepletRegistryManager> rm = mockStatic(RepletRegistryManager.class);
          MockedStatic<SharedMVUtil> mv = mockStatic(SharedMVUtil.class))
      {
         RepletRegistryManager mgr = mock(RepletRegistryManager.class);
         rm.when(RepletRegistryManager::getInstance).thenReturn(mgr);
         when(mgr.getRegistry()).thenReturn(mock(RepletRegistry.class));
         when(mgr.getRegistry(any(IdentityID.class))).thenReturn(mock(RepletRegistry.class));
         au.when(() -> AssetUtil.getAssetRepository(anyBoolean())).thenReturn(repository);
         au.when(() -> AssetUtil.isDuplicatedEntry(any(), any())).thenReturn(false);
         r.run();
      }
   }

   private static RecycleBin.Entry recycleEntry(String path, String originalPath,
                                                Permission permission)
   {
      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath(path);
      entry.setOriginalPath(originalPath);
      entry.setOriginalScope(AssetRepository.GLOBAL_SCOPE);
      entry.setType(RepositoryEntry.WORKSHEET);
      entry.setPermission(permission);
      return entry;
   }

   private static AssetEntry scheduleFolder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }

   private static AssetFolder folder(AssetEntry... entries) {
      AssetFolder folder = new AssetFolder();

      for(AssetEntry entry : entries) {
         folder.addEntry(entry);
      }

      return folder;
   }

   private static IndexedStorage inMemoryStorage(Map<String, Object> folders) throws Exception {
      IndexedStorage storage = mock(IndexedStorage.class);
      when(storage.getXMLSerializable(anyString(), any()))
         .thenAnswer(inv -> folders.get((String) inv.getArgument(0)));
      doAnswer(inv -> folders.put(inv.getArgument(0), inv.getArgument(1)))
         .when(storage).putXMLSerializable(anyString(), any());
      when(storage.contains(anyString()))
         .thenAnswer(inv -> folders.containsKey((String) inv.getArgument(0)));
      doAnswer(inv -> folders.remove((String) inv.getArgument(0)) != null)
         .when(storage).remove(anyString());
      return storage;
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

   private static String key(String type, String path) {
      return type + ":" + ORG + ":" + path;
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

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private SecurityEngine engine;
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> real;
}
