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
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77770: the subtree lists of a data source folder ({@code getSubDataSourceNames} and
 * {@code getSubfolderNames} with allChild=true) matched by {@code contains}, so they listed
 * entries where the folder's path appears again deeper inside a sibling ({@code F/xF/G/out} and
 * {@code F/A/F/G/rep} for {@code F/G}). A folder move then queued a dependency rename of an
 * unmoved data source, and the portal search of a folder found entries outside it. The root
 * ("/", "" and null) must still list the whole tree.
 *
 * The registry, the repository and the EM / portal services are the real ones. This class
 * stores only JDBC data sources: the portal services get a mocked {@code Config}, with which
 * {@code getDataSourceInfo} can't describe a tabular data source, and the root search visits
 * every data source of the registry.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceSubtreeListTest {
   private static final String URL = "jdbc:derby:memory:bug77770;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private RenameTransformHandler transformHandler;
   private SecurityEngine security;
   private SecurityProvider provider;
   private MockedStatic<SecurityEngine> securityStatic;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      clearInvocations(transformHandler);
      provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
   }

   // the four allChild=true overloads list only the folder's subtree
   @Test
   void subtreeListsExcludeEntriesOutsideTheFolder() throws Exception {
      seed("aF");
      String org = Organization.getDefaultOrganizationID();
      Set<String> inside = Set.of("aF/aG/in", "aF/aG/sub/deep");
      Set<String> insideFolders = Set.of("aF/aG/sub");

      assertEquals(inside, Set.copyOf(registry.getSubDataSourceNames("aF/aG", true)));
      assertEquals(inside, Set.copyOf(registry.getSubDataSourceNames("aF/aG", true, org)));
      assertEquals(inside, Set.copyOf(registry.getSubDataSourceNames("aF/aG/", true)));
      assertEquals(insideFolders, Set.copyOf(registry.getSubfolderNames("aF/aG", true)));
      assertEquals(insideFolders, Set.copyOf(registry.getSubfolderNames("aF/aG", true, org)));

      // a top-level folder still lists its whole subtree
      List<String> top = registry.getSubDataSourceNames("aF", true);
      assertTrue(top.containsAll(List.of("aF/aG/in", "aF/aG/sub/deep", "aF/xaF/aG/out",
                                         "aF/A/aF/aG/rep", "aF/aGx/sib")), "" + top);
      List<String> topFolders = registry.getSubfolderNames("aF", true, org);
      assertTrue(topFolders.containsAll(List.of("aF/aG", "aF/aG/sub", "aF/xaF/aG/osub",
                                                "aF/A/aF/aG/rsub", "aF/aGx")), "" + topFolders);
      assertFalse(topFolders.contains("aF"), "" + topFolders);
   }

   // the dependency infos of a folder move cover only the moved data sources
   @Test
   void folderMoveDependencyInfosCoverOnlyTheFolder() throws Exception {
      seed("bF");
      Map<String, RenameDependencyInfo> infos =
         DependencyTransformer.createDatasourceFolderDependencyInfoMap(registry, "bF/bG", "bH");

      assertEquals(Set.of("bF/bG/in", "bF/bG/sub/deep"), infos.keySet());
   }

   // an EM folder move queues no rename for a data source outside the folder
   @Test
   void emFolderMoveQueuesOnlyTheMovedDataSources() throws Exception {
      seed("cF");
      addFolder("cDest");
      AssetEntry worksheet = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.WORKSHEET, "ws77770", null);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(new ArrayList<>(List.of(worksheet)));
      DependencyStorageService.getInstance().put(
         DependencyTransformer.getTabularAssetId("cF/xcF/cG/out"), info);

      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(node("cF/cG"))).destination(node("cDest")).build();
      objectService().moveFiles(request, true, principal);

      List<RenameDependencyInfo> tasks = queuedTasks();
      assertEquals(Set.of("cF/cG/in->cDest/cG/in", "cF/cG/sub/deep->cDest/cG/sub/deep"),
                   Set.copyOf(renames(tasks)));
      assertTrue(tasks.stream().noneMatch(t -> t.getDependencyMap().containsKey(worksheet)),
                 "the dependent of the unmoved data source is rewritten");

      registry.clearCache();
      assertTrue(containsDataSource("cF/xcF/cG/out"));
      assertTrue(containsDataSource("cDest/cG/in"));
      assertTrue(containsDataSource("cDest/cG/sub/deep"));
   }

   // a portal folder move queues no rename for a data source outside the folder
   @Test
   void portalFolderMoveQueuesOnlyTheMovedDataSources() throws Exception {
      seed("dF");
      addFolder("dDest");
      MoveCommand move = new MoveCommand();
      move.setOldPath("dF/dG");
      move.setPath("dDest/dG");
      move.setName("dG");
      move.setType(PortalDataType.DATA_SOURCE_FOLDER.name());

      browser().moveDataSource(new MoveCommand[] { move }, principal);

      assertEquals(Set.of("dF/dG/in->dDest/dG/in", "dF/dG/sub/deep->dDest/dG/sub/deep"),
                   Set.copyOf(renames(queuedTasks())));
      registry.clearCache();
      assertTrue(containsDataSource("dF/xdF/dG/out"));
      assertTrue(containsDataSource("dDest/dG/in"));
   }

   // the portal search of a folder finds only entries inside it
   @Test
   void portalSearchInFolderFindsOnlyItsSubtree() throws Exception {
      seed("eF");
      List<String> all = paths(browser().getAllSubDataSources("eF/eG", principal));

      assertEquals(Set.of("eF/eG/in", "eF/eG/sub/deep", "eF/eG/sub"), Set.copyOf(all));
      assertTrue(browser().getSearchDataSources("eF/eG", "out", principal).isEmpty());
      assertTrue(browser().getSearchDataSources("eF/eG", "rep", principal).isEmpty());
      assertEquals(List.of("eF/eG/sub/deep"),
                   paths(browser().getSearchDataSources("eF/eG", "deep", principal)));
   }

   // the root still lists and searches the whole tree, nested entries included
   @Test
   void rootListsAndSearchesTheWholeTree() throws Exception {
      seed("rF");
      registry.setDataSource(source("rTop"), false);
      String org = Organization.getDefaultOrganizationID();
      List<String> nested = List.of("rTop", "rF/rG/in", "rF/rG/sub/deep", "rF/xrF/rG/out",
                                    "rF/A/rF/rG/rep");
      List<String> nestedFolders = List.of("rF", "rF/rG", "rF/rG/sub", "rF/xrF/rG/osub");

      for(String root : Arrays.asList("/", "", null)) {
         List<String> names = registry.getSubDataSourceNames(root, true);
         assertTrue(names.containsAll(nested), root + ": " + names);
         List<String> orgNames = registry.getSubDataSourceNames(root, true, org);
         assertTrue(orgNames.containsAll(nested), root + ": " + orgNames);
         List<String> folders = registry.getSubfolderNames(root, true);
         assertTrue(folders.containsAll(nestedFolders), root + ": " + folders);
         List<String> orgFolders = registry.getSubfolderNames(root, true, org);
         assertTrue(orgFolders.containsAll(nestedFolders), root + ": " + orgFolders);
      }

      List<String> all = paths(browser().getAllSubDataSources("/", principal));
      assertTrue(all.containsAll(List.of("rTop", "rF/rG/sub/deep", "rF/rG/sub")), "" + all);
      List<String> hits = paths(browser().getSearchDataSources("/", "deep", principal));
      assertTrue(hits.contains("rF/rG/sub/deep"), "" + hits);
      hits = paths(browser().getSearchDataSources("/", "out", principal));
      assertTrue(hits.contains("rF/xrF/rG/out"), "" + hits);
   }

   // XEngine folder move (its list is filtered by prefix already) is unchanged
   @Test
   void xengineFolderMoveIsUnchanged() throws Exception {
      seed("fF");
      addFolder("fDest");
      DataSourceFolder folder = (DataSourceFolder) registry.getDataSourceFolder("fF/fG").clone();
      folder.setName("fDest/fG");

      repository.updateDataSourceFolder(folder, "fF/fG");

      assertEquals(Set.of("fF/fG/in->fDest/fG/in", "fF/fG/sub/deep->fDest/fG/sub/deep"),
                   Set.copyOf(renames(queuedTasks())));
      registry.clearCache();
      assertTrue(containsDataSource("fF/xfF/fG/out"));
      assertTrue(containsDataSource("fF/A/fF/fG/rep"));
      assertTrue(containsDataSource("fDest/fG/in"));
      assertTrue(containsDataSource("fDest/fG/sub/deep"));
   }

   // p: prefix, g: p's first letter + "G". The folder under test is p/g, holding p/g/in and
   // p/g/sub/deep. Outside it: p/xp/g/out (folder p/xp/g/osub), p/A/p/g/rep (folder
   // p/A/p/g/rsub) and the sibling sharing a name prefix p/gx/sib.
   private void seed(String p) throws Exception {
      String g = p.charAt(0) + "G";
      String folder = p + "/" + g;
      String other = p + "/x" + p + "/" + g;
      String repeat = p + "/A/" + p + "/" + g;

      for(String name : List.of(p, folder, folder + "/sub", p + "/x" + p, other, other + "/osub",
                                p + "/A", p + "/A/" + p, repeat, repeat + "/rsub", folder + "x"))
      {
         addFolder(name);
      }

      for(String name : List.of(folder + "/in", folder + "/sub/deep", other + "/out",
                                repeat + "/rep", folder + "x/sib"))
      {
         registry.setDataSource(source(name), false);
      }
   }

   private List<RenameDependencyInfo> queuedTasks() {
      ArgumentCaptor<RenameDependencyInfo> captor =
         ArgumentCaptor.forClass(RenameDependencyInfo.class);
      verify(transformHandler, atLeast(0)).addTransformTask(captor.capture());
      return captor.getAllValues().stream().filter(Objects::nonNull).collect(Collectors.toList());
   }

   private static List<String> renames(List<RenameDependencyInfo> tasks) {
      return tasks.stream()
         .flatMap(i -> i.getRenameInfos().stream())
         .map(r -> r.getOldName() + "->" + r.getNewName())
         .collect(Collectors.toList());
   }

   private static List<String> paths(List<DataSourceInfo> infos) {
      return infos.stream().map(DataSourceInfo::path).collect(Collectors.toList());
   }

   private DataSourceBrowserService browser() throws Exception {
      return new DataSourceBrowserService(
         security, objectService(), repository, mock(DataSourceService.class), registry,
         mock(Config.class), transformHandler);
   }

   private RepositoryObjectService objectService() throws Exception {
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      return new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), transformHandler,
         repletRegistries, mock(DashboardRegistryManager.class));
   }

   private static ContentRepositoryTreeNode node(String path) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path).type(RepositoryEntry.DATA_SOURCE_FOLDER).build();
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null));
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }
}
