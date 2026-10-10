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
 * Bug #78205: the new paths of the data sources of a moved data source folder were built with
 * {@code name.replaceFirst(oldPath, newPath)}, so the folder paths were read as a regex and a
 * regex replacement. A target folder with {@code $} threw "Illegal group reference" before
 * anything was moved, and a moved folder like {@code C++} or {@code Sales (EU)} was moved but
 * queued a wrong ({@code Dev/C++++/ds}) or no-op rename for its dependents.
 *
 * The registry, the repository and the EM / portal services are the real ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderMoveRegexCharsTest {
   private static final String URL = "jdbc:derby:memory:bug78205;create=true";

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

   // every regex character in the moved folder or the target gives the literal new path
   @Test
   void dependencyInfosUseLiteralPaths() throws Exception {
      String[][] moves = {
         { "a1 $", "Z1 $/a1 $" }, { "Sales (EU)", "Z2/Sales (EU)" }, { "C++", "Dev/C++" },
         { "What?", "Z3/What?" }, { "x*", "Z4/x*" }, { "a|b", "Z5/a|b" }, { "[x]", "Z6/[x]" },
         { "q{2}", "Z7/q{2}" }, { "a.b", "Z8/a.b" }, { "back", "Z9\\b/back" },
         { "g1", "$1/g1" }, { "h1", "Cost $/h1" }
      };

      for(String[] move : moves) {
         String oldPath = move[0];
         String newPath = move[1];
         addFolder(oldPath);
         addFolder(oldPath + "/sub");
         registry.setDataSource(source(oldPath + "/ds"), false);
         registry.setDataSource(source(oldPath + "/sub/ds2"), false);

         Map<String, RenameDependencyInfo> infos =
            DependencyTransformer.createDatasourceFolderDependencyInfoMap(
               registry, oldPath, newPath);

         assertEquals(Set.of(oldPath + "/ds->" + newPath + "/ds",
                             oldPath + "/sub/ds2->" + newPath + "/sub/ds2"),
                      Set.copyOf(renames(new ArrayList<>(infos.values()))), oldPath);
      }
   }

   // only the leading folder path is replaced, and a trailing slash is tolerated
   @Test
   void onlyTheLeadingPathIsReplaced() throws Exception {
      addFolder("pF");
      addFolder("pF/pF");
      registry.setDataSource(source("pF/pF/ds"), false);

      assertEquals(List.of("pF/pF/ds->pZ/pF/ds"), renames(new ArrayList<>(
         DependencyTransformer.createDatasourceFolderDependencyInfoMap(
            registry, "pF", "pZ").values())));
      assertEquals(List.of("pF/pF/ds->pZ/pF/ds"), renames(new ArrayList<>(
         DependencyTransformer.createDatasourceFolderDependencyInfoMap(
            registry, "pF/", "pZ/").values())));
   }

   // an EM move onto a folder with "$" moves the data source and queues its rename
   @Test
   void emMoveOntoDollarFolder() throws Exception {
      addFolder("eA");
      registry.setDataSource(source("eA/ds"), false);
      addFolder("Cost $");

      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(node("eA"))).destination(node("Cost $")).build();
      objectService().moveFiles(request, true, principal);

      assertEquals(List.of("eA/ds->Cost $/eA/ds"), renames(queuedTasks()));
      registry.clearCache();
      assertFalse(containsDataSource("eA/ds"));
      assertTrue(containsDataSource("Cost $/eA/ds"));
   }

   // an EM move of a folder with parentheses queues the real new path
   @Test
   void emMoveOfParenthesesFolder() throws Exception {
      addFolder("Sales (EU)");
      registry.setDataSource(source("Sales (EU)/ds"), false);
      addFolder("eDest");

      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(node("Sales (EU)"))).destination(node("eDest")).build();
      objectService().moveFiles(request, true, principal);

      assertEquals(List.of("Sales (EU)/ds->eDest/Sales (EU)/ds"), renames(queuedTasks()));
      registry.clearCache();
      assertTrue(containsDataSource("eDest/Sales (EU)/ds"));
   }

   // a portal move of "C++" queues the real new path, and one onto "$" no longer fails
   @Test
   void portalMoves() throws Exception {
      addFolder("C++");
      registry.setDataSource(source("C++/ds"), false);
      addFolder("pDev");
      addFolder("pB");
      registry.setDataSource(source("pB/ds"), false);
      addFolder("pCost $");

      browser().moveDataSource(new MoveCommand[] { move("C++", "pDev/C++"),
                                                   move("pB", "pCost $/pB") }, principal);

      assertEquals(Set.of("C++/ds->pDev/C++/ds", "pB/ds->pCost $/pB/ds"),
                   Set.copyOf(renames(queuedTasks())));
      registry.clearCache();
      assertTrue(containsDataSource("pDev/C++/ds"));
      assertTrue(containsDataSource("pCost $/pB/ds"));
   }

   private static MoveCommand move(String oldPath, String path) {
      MoveCommand move = new MoveCommand();
      move.setOldPath(oldPath);
      move.setPath(path);
      move.setName(oldPath);
      move.setType(PortalDataType.DATA_SOURCE_FOLDER.name());
      return move;
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
