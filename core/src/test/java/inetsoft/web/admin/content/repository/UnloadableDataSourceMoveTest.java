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
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77727: a single move of a data source that can't be loaded (its connector isn't installed
 * or its stored definition is damaged), in EM Content > Repository or in the portal, threw an NPE.
 * It must be refused with a message before anything is moved or any dependency rename is queued,
 * and the data source must stay where it is. A move of several items that includes one is refused
 * whole.
 *
 * The registry is the real one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UnloadableDataSourceMoveTest {
   private static final String URL = "jdbc:derby:memory:bug77727;create=true";
   // the type of TestTabularDataSource
   private static final String TABULAR_TYPE = "folderMoveTabular";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private RenameTransformHandler transformHandler;
   @Autowired
   private Config config;
   private final Map<String, Permission> store = new HashMap<>();
   private final List<String> audits = new ArrayList<>();
   private SecurityEngine security;
   private SecurityProvider provider;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<Audit> auditStatic;
   private Principal principal;
   private IndexedStorage storage;
   private static int seq;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      audits.clear();
      clearInvocations(transformHandler);
      provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> store.get(key(inv.getArgument(0), inv.getArgument(1))));
      doAnswer(inv -> store.put(key(inv.getArgument(0), inv.getArgument(1)), inv.getArgument(2)))
         .when(security).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(security).removePermission(any(ResourceType.class), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);
      Audit audit = mock(Audit.class);
      doAnswer(inv -> audits.add(inv.<ActionRecord>getArgument(0).getActionStatus()))
         .when(audit).auditAction(any(), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());

      Field storageField = DataSourceRegistry.class.getDeclaredField("indexedStorage");
      storageField.setAccessible(true);
      storage = (IndexedStorage) storageField.get(registry);
   }

   @AfterEach
   void tearDown() {
      install();
      auditStatic.close();
      securityStatic.close();
   }

   // the reported case: EM Move of a data source whose connector isn't installed, top-level and
   // in a folder
   @Test
   void emMoveOfAnUninstalledDataSourceIsRefused() throws Exception {
      for(boolean inFolder : new boolean[] { false, true }) {
         String p = prefix();
         String path = inFolder ? p + "F/X" : p + "X";
         tabular(path);
         grant(path);
         uninstall();

         MessageException error = assertThrows(
            MessageException.class,
            () -> emMove(List.of(path), RepositoryEntry.DATA_SOURCE, p + "Dest"), path);

         install();
         assertEquals(unloadable(path), error.getMessage(), path);
         checkNotMoved(path, p + "Dest/X");
         assertNotNull(registry.getDataSource(path), path + " is lost");
         checkNothingQueued(path);
         // refused with the other checks of the move, before any node is moved and audited
         assertEquals(List.of(), audits, path);
      }
   }

   // EM Move of a data source whose stored definition is damaged
   @Test
   void emMoveOfACorruptDataSourceIsRefused() throws Exception {
      String p = prefix();
      String path = p + "F/X";
      corrupt(path);

      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(path), RepositoryEntry.DATA_SOURCE, p + "Dest"));

      assertEquals(unloadable(path), error.getMessage());
      checkNotMoved(path, p + "Dest/X");
      checkNothingQueued(path);
   }

   // EM Move of a data source and of one that can't be loaded: nothing is moved
   @Test
   void emMoveOfSeveralWithAnUninstalledOneIsRefusedWhole() throws Exception {
      String p = prefix();
      jdbc(p + "F/J");
      tabular(p + "F/X");
      grant(p + "F/J");
      grant(p + "F/X");
      uninstall();

      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(p + "F/J", p + "F/X"), RepositoryEntry.DATA_SOURCE, p + "Dest"));

      install();
      assertEquals(unloadable(p + "F/X"), error.getMessage());
      checkNotMoved(p + "F/J", p + "Dest/J");
      checkNotMoved(p + "F/X", p + "Dest/X");
      checkNothingQueued("several");
   }

   // EM Move of a data source that is no longer stored, e.g. deleted after the tree was loaded
   @Test
   void emMoveOfAMissingDataSourceIsRefused() {
      String p = prefix();
      addFolder(p + "F");

      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(p + "F/X"), RepositoryEntry.DATA_SOURCE, p + "Dest"));

      assertEquals(Catalog.getCatalog(principal).getString("data.datasources.findDataSourceError"),
                   error.getMessage());
      assertNull(registry.getDataSource(p + "Dest/X"));
      checkNothingQueued("missing");
   }

   // the connector is removed after the nodes were checked: refused, and audited as a failure
   @Test
   void emMoveOfADataSourceThatCantBeLoadedAnyMoreIsRefusedAndAudited() throws Exception {
      String p = prefix();
      String path = p + "F/X";
      tabular(path);
      grant(path);
      XRepository changed = mock(XRepository.class, delegatesTo(repository));
      doReturn(null).when(changed).getDataSource(path);

      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(objectService(changed), List.of(path), RepositoryEntry.DATA_SOURCE,
                      p + "Dest"));

      assertEquals(unloadable(path), error.getMessage());
      checkNotMoved(path, p + "Dest/X");
      checkNothingQueued(path);
      assertEquals(List.of(ActionRecord.ACTION_STATUS_FAILURE), audits);
   }

   // control: EM Move of a data source that can be loaded still moves it and renames its
   // dependencies
   @Test
   void emMoveOfALoadableDataSource() throws Exception {
      String p = prefix();
      jdbc(p + "F/J");
      grant(p + "F/J");

      emMove(List.of(p + "F/J"), RepositoryEntry.DATA_SOURCE, p + "Dest");

      checkMoved(p + "F/J", p + "Dest/J");
      verify(transformHandler, times(1)).addTransformTask(any(RenameDependencyInfo.class));
   }

   // the reported case in the portal: a move of a data source whose connector isn't installed,
   // top-level and in a folder
   @Test
   void portalMoveOfAnUninstalledDataSourceIsRefused() throws Exception {
      for(boolean inFolder : new boolean[] { false, true }) {
         String p = prefix();
         String path = inFolder ? p + "F/X" : p + "X";
         tabular(path);
         grant(path);
         uninstall();

         MessageException error = assertThrows(
            MessageException.class,
            () -> portalMove(browserService(repository), move(path, p + "Dest/X")), path);

         install();
         assertEquals(unloadable(path), error.getMessage(), path);
         checkNotMoved(path, p + "Dest/X");
         assertNotNull(registry.getDataSource(path), path + " is lost");
         checkNothingQueued(path);
         // refused with the other checks of the move, before any item is moved and audited
         assertEquals(List.of(), audits, path);
      }
   }

   // a portal move of a data source whose stored definition is damaged
   @Test
   void portalMoveOfACorruptDataSourceIsRefused() throws Exception {
      String p = prefix();
      String path = p + "F/X";
      corrupt(path);

      MessageException error = assertThrows(
         MessageException.class,
         () -> portalMove(browserService(repository), move(path, p + "Dest/X")));

      assertEquals(unloadable(path), error.getMessage());
      checkNotMoved(path, p + "Dest/X");
      checkNothingQueued(path);
   }

   // a portal move of a data source and of one that can't be loaded: nothing is moved
   @Test
   void portalMoveOfSeveralWithAnUninstalledOneIsRefusedWhole() throws Exception {
      String p = prefix();
      jdbc(p + "F/J");
      tabular(p + "F/X");
      grant(p + "F/J");
      grant(p + "F/X");
      uninstall();

      MessageException error = assertThrows(
         MessageException.class,
         () -> portalMove(browserService(repository), move(p + "F/J", p + "Dest/J"),
                          move(p + "F/X", p + "Dest/X")));

      install();
      assertEquals(unloadable(p + "F/X"), error.getMessage());
      checkNotMoved(p + "F/J", p + "Dest/J");
      checkNotMoved(p + "F/X", p + "Dest/X");
      checkNothingQueued("several");
   }

   // the connector is removed after the items were checked: refused, and audited as a failure
   @Test
   void portalMoveOfADataSourceThatCantBeLoadedAnyMoreIsRefusedAndAudited() throws Exception {
      String p = prefix();
      String path = p + "F/X";
      tabular(path);
      grant(path);
      XRepository changed = mock(XRepository.class, delegatesTo(repository));
      doReturn(null).when(changed).getDataSource(path);

      MessageException error = assertThrows(
         MessageException.class,
         () -> portalMove(browserService(changed), move(path, p + "Dest/X")));

      assertEquals(unloadable(path), error.getMessage());
      checkNotMoved(path, p + "Dest/X");
      checkNothingQueued(path);
      assertEquals(List.of(ActionRecord.ACTION_STATUS_FAILURE), audits);
   }

   // control: a portal move of a data source that can be loaded still moves it and renames its
   // dependencies
   @Test
   void portalMoveOfALoadableDataSource() throws Exception {
      String p = prefix();
      jdbc(p + "F/J");
      grant(p + "F/J");

      portalMove(browserService(repository), move(p + "F/J", p + "Dest/J"));

      checkMoved(p + "F/J", p + "Dest/J");
      verify(transformHandler, times(1)).addTransformTask(any(RenameDependencyInfo.class));
      assertEquals(List.of(ActionRecord.ACTION_STATUS_SUCCESS), audits);
   }

   // the data source is stored and listed at its old path with its permission, not at the new one
   private void checkNotMoved(String oname, String nname) {
      registry.clearCache();
      String okey = entry(oname).toIdentifier();
      assertTrue(storage.contains(okey), oname + " isn't stored");
      assertTrue(Arrays.stream(registry.getEntries(oname))
                    .anyMatch(e -> e.isDataSource() && e.getPath().equals(oname)),
                 oname + " isn't listed");
      assertFalse(storage.contains(entry(nname).toIdentifier()), nname + " is stored");
      assertFalse(Arrays.stream(registry.getEntries(nname))
                     .anyMatch(e -> e.getPath().equals(nname)), nname + " is listed");
      assertNotNull(perm(oname), oname + " lost its permission");
      assertNull(perm(nname), nname + " has a permission");
   }

   private void checkMoved(String oname, String nname) {
      registry.clearCache();
      assertNull(registry.getDataSource(oname), oname + " is still there");
      XDataSource moved = registry.getDataSource(nname);
      assertNotNull(moved, nname + " isn't there");
      assertEquals(nname, moved.getFullName());
      assertNotNull(perm(nname), nname + " has no permission");
      assertNull(perm(oname), oname + " still has a permission");
   }

   private void checkNothingQueued(String label) {
      verify(transformHandler, never().description(label + ": a dependency rename was queued"))
         .addTransformTask(any(RenameDependencyInfo.class));
      verify(transformHandler, never().description(label + ": a rename was queued"))
         .addTransformTask(any(RenameInfo.class));
   }

   private String unloadable(String path) {
      return Catalog.getCatalog(principal).getString("common.datasource.moveUnloadable", path);
   }

   private String prefix() {
      String p = "ul" + (++seq) + "x";
      addFolder(p + "F");
      addFolder(p + "Dest");
      return p;
   }

   private void uninstall() {
      doReturn(null).when(config).getDataSourceClass(TABULAR_TYPE);
      registry.clearCache();
   }

   private void install() {
      doReturn(TestTabularDataSource.class.getName()).when(config)
         .getDataSourceClass(TABULAR_TYPE);
      registry.clearCache();
   }

   private void tabular(String path) {
      TestTabularDataSource dataSource = new TestTabularDataSource();
      dataSource.setName(path);
      registry.setDataSource(dataSource, false);
      assertNotNull(registry.getDataSource(path));
   }

   private void jdbc(String path) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(path);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      registry.setDataSource(dataSource, false);
      assertNotNull(registry.getDataSource(path));
   }

   // a stored data source whose definition can't be read
   private void corrupt(String path) throws Exception {
      tabular(path);
      grant(path);
      storage.putXMLSerializable(entry(path).toIdentifier(), new XDataSourceWrapper());
      registry.clearCache();
      assertNull(registry.getDataSource(path));
   }

   private void emMove(List<String> paths, int type, String destination) throws Exception {
      emMove(objectService(repository), paths, type, destination);
   }

   private void emMove(RepositoryObjectService service, List<String> paths, int type,
                       String destination) throws Exception
   {
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      for(String path : paths) {
         int index = path.lastIndexOf('/');
         nodes.add(ContentRepositoryTreeNode.builder()
                      .label(index < 0 ? path : path.substring(index + 1))
                      .path(path)
                      .type(type)
                      .build());
      }

      ContentRepositoryTreeNode target = ContentRepositoryTreeNode.builder()
         .label(destination)
         .path(destination)
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .build();
      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(nodes)
         .destination(target)
         .build();
      service.moveFiles(request, true, principal);
   }

   private RepositoryObjectService objectService(XRepository xrepository) throws Exception {
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      return new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         permissions, xrepository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), transformHandler,
         repletRegistries, mock(DashboardRegistryManager.class));
   }

   private DataSourceBrowserService browserService(XRepository xrepository) throws Exception {
      return new DataSourceBrowserService(
         security, objectService(xrepository), xrepository, mock(DataSourceService.class),
         registry, mock(Config.class), transformHandler);
   }

   private void portalMove(DataSourceBrowserService service, MoveCommand... items)
      throws Exception
   {
      service.moveDataSource(items, principal);
   }

   private static MoveCommand move(String oname, String nname) {
      MoveCommand move = new MoveCommand();
      move.setOldPath(oname);
      move.setPath(nname);
      move.setName(nname.substring(nname.lastIndexOf('/') + 1));
      move.setType(PortalDataType.DATA_SOURCE.name());
      return move;
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private void grant(String resource) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(ResourceType.DATA_SOURCE, resource), permission);
   }

   private Permission perm(String resource) {
      return store.get(key(ResourceType.DATA_SOURCE, resource));
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null);
   }

   private static String key(ResourceType type, String resource) {
      return type + ":" + resource;
   }
}
