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
import inetsoft.uql.tabular.TabularView;
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
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77772: an additional connection "P/add" (or "F/P/add") whose parent's stored definition is
 * damaged was not recognised as one, because DataSourceRegistry.isAdditionalConnectionPath decided
 * by loading the parent. An EM or portal move, or a portal PUT, of it alone then turned it into a
 * standalone data source. When the parent can't be loaded it is now recognised from the stored
 * entries: the parent's data source entry and the entry at the path are stored, and the entry
 * isn't the data source of a folder at the parent's path (#77725).
 *
 * The registry, IndexedStorage, XEngine and the EM/portal services are the real ones; the damage
 * is an unparseable stored definition.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AdditionalConnectionUnderDamagedParentTest {
   private static final String URL = "jdbc:derby:memory:bug77772;create=true";
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

   // ---- the reported case: an additional connection under a parent that can't be loaded ----

   // EM Move of F/P/add alone, the parent's definition damaged, foldered and top-level
   @Test
   void emMoveOfAnAdditionalConnectionUnderADamagedParentIsRefused() throws Exception {
      for(boolean inFolder : new boolean[] { false, true }) {
         String p = prefix();
         String parent = parentWithAdditional(inFolder ? p + "F/P" : p + "TP", "add");
         damage(parent);

         MessageException error = assertThrows(
            MessageException.class,
            () -> emMove(List.of(parent + "/add"), RepositoryEntry.DATA_SOURCE, p + "Dest"),
            parent);

         assertEquals(additionalMove(), error.getMessage(), parent);
         checkAdditionalNotMoved(parent, "add", p + "Dest/add");
         checkNothingQueued(parent);
         assertEquals(List.of(), audits, parent);
      }
   }

   // the portal move of F/P/add alone, the parent's definition damaged, foldered and top-level
   @Test
   void portalMoveOfAnAdditionalConnectionUnderADamagedParentIsRefused() throws Exception {
      for(boolean inFolder : new boolean[] { false, true }) {
         String p = prefix();
         String parent = parentWithAdditional(inFolder ? p + "F/P" : p + "TP", "add");
         damage(parent);

         MessageException error = assertThrows(
            MessageException.class,
            () -> portalMove(browserService(repository), move(parent + "/add", p + "Dest/add")),
            parent);

         assertEquals(additionalMove(), error.getMessage(), parent);
         checkAdditionalNotMoved(parent, "add", p + "Dest/add");
         checkNothingQueued(parent);
         assertEquals(List.of(), audits, parent);
      }
   }

   // a hand-made portal PUT of F/P/add, the parent's definition damaged, with the path in the
   // name or split into parentPath and name
   @Test
   void portalUpdateOfAnAdditionalConnectionUnderADamagedParentIsRefused() throws Exception {
      for(boolean inFolder : new boolean[] { false, true }) {
         String p = prefix();
         String parent = parentWithAdditional(inFolder ? p + "F/P" : p + "TP", "add");
         damage(parent);
         DatasourcesService service = new DatasourcesService(
            repository, security, mock(DataSourceStatusService.class), registry,
            mock(Config.class));

         MessageException error = assertThrows(
            MessageException.class,
            () -> service.updateDataSource(parent + "/add", definition("", "add"), principal),
            parent);
         assertEquals(additionalMove(), error.getMessage(), parent);
         error = assertThrows(
            MessageException.class,
            () -> service.updateDataSource("add", definition(parent, "add"), principal), parent);
         assertEquals(additionalMove(), error.getMessage(), parent);

         checkAdditionalNotMoved(parent, "add", "add");
         assertFalse(storage.contains(entry(parent + "/" + parent + "/add").toIdentifier()));
      }
   }

   // a legacy additional connection stored with its own full path (before Bug #77610), no folder
   // at the parent's path: recognised whether the parent loads or not
   @Test
   void legacyFullPathAdditionalConnectionUnderADamagedParentIsRefused() throws Exception {
      String p = prefix();
      String parent = parentWithAdditional(p + "F/P", "add");
      registry.setObject(entry(parent + "/lg"), new XDataSourceWrapper(source(parent + "/lg")));
      registry.clearCache();
      assertTrue(registry.isAdditionalConnectionPath(parent + "/lg"), "control: parent loads");
      damage(parent);

      assertTrue(registry.isAdditionalConnectionPath(parent + "/lg"));
      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(parent + "/lg"), RepositoryEntry.DATA_SOURCE, p + "Dest"));
      assertEquals(additionalMove(), error.getMessage());
      assertTrue(storage.contains(entry(parent + "/lg").toIdentifier()));
      assertFalse(storage.contains(entry(p + "Dest/lg").toIdentifier()));
      checkNothingQueued("legacy");
   }

   // ---- a data source and a folder at the same path (#77725) with the data source damaged ----

   // the folder's data source F/C/m (stored with a full path name) stays movable; the data
   // source's bare-named additional connection F/C/add is refused
   @Test
   void damagedClashParentKeepsTheFolderSideMovable() throws Exception {
      String p = prefix();
      String clash = p + "F/C";
      addFolder(clash);
      jdbc(clash + "/m");
      grant(clash + "/m");
      parentWithAdditional(clash, "add");
      damage(clash);

      assertFalse(registry.isAdditionalConnectionPath(clash + "/m"));
      assertTrue(registry.isAdditionalConnectionPath(clash + "/add"));

      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(clash + "/add"), RepositoryEntry.DATA_SOURCE, p + "Dest"));
      assertEquals(additionalMove(), error.getMessage());
      error = assertThrows(
         MessageException.class,
         () -> portalMove(browserService(repository), move(clash + "/add", p + "Dest/add")));
      assertEquals(additionalMove(), error.getMessage());
      checkAdditionalNotMoved(clash, "add", p + "Dest/add");
      checkNothingQueued("clash add");

      emMove(List.of(clash + "/m"), RepositoryEntry.DATA_SOURCE, p + "Dest");

      checkMoved(clash + "/m", p + "Dest/m");
      verify(transformHandler, times(1)).addTransformTask(any(RenameDependencyInfo.class));
   }

   // an unreadable entry under a damaged clash data source counts as an additional connection,
   // as it does when the data source loads (#77725): refused with additionalConnectionMove, not
   // with #77727's moveUnloadable
   @Test
   void unreadableEntryUnderADamagedClashParentIsRefusedAsAnAdditionalConnection()
      throws Exception
   {
      String p = prefix();
      String clash = p + "F/C";
      addFolder(clash);
      parentWithAdditional(clash, "add");
      jdbc(clash + "/u");
      storage.putXMLSerializable(entry(clash + "/u").toIdentifier(), new XDataSourceWrapper());
      registry.clearCache();
      assertTrue(registry.isAdditionalConnectionPath(clash + "/u"), "control: parent loads");
      damage(clash);

      assertTrue(registry.isAdditionalConnectionPath(clash + "/u"));
      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(clash + "/u"), RepositoryEntry.DATA_SOURCE, p + "Dest"));
      assertEquals(additionalMove(), error.getMessage());
      assertTrue(storage.contains(entry(clash + "/u").toIdentifier()));
      assertFalse(storage.contains(entry(p + "Dest/u").toIdentifier()));
      checkNothingQueued("clash unreadable");
   }

   // ---- stated behaviours ----

   // an orphan: an entry under a path where no data source is stored is not an additional
   // connection, and is moved as a standalone data source
   @Test
   void orphanIsMovedAsAStandaloneDataSource() throws Exception {
      String p = prefix();
      String orphan = p + "F/P/add";
      registry.setObject(entry(orphan), new XDataSourceWrapper(source("add")));
      grant(orphan);
      registry.clearCache();
      assertNotNull(registry.getDataSource(orphan), "setup: the orphan loads");

      assertFalse(registry.isAdditionalConnectionPath(orphan));
      emMove(List.of(orphan), RepositoryEntry.DATA_SOURCE, p + "Dest");

      checkMoved(orphan, p + "Dest/add");
   }

   // a parent whose connector isn't installed: moving its additional connection alone is still
   // refused, now as an additional connection instead of as unloadable
   @Test
   void additionalConnectionUnderAnUninstalledParentIsRefusedAsAnAdditionalConnection()
      throws Exception
   {
      String p = prefix();
      String parent = p + "F/X";
      tabular(parent);
      TestTabularDataSource ds = (TestTabularDataSource) registry.getDataSource(parent);
      TestTabularDataSource add = new TestTabularDataSource();
      add.setName("A");
      ds.addDatasource(add);
      grant(parent);
      uninstall();

      assertTrue(registry.isAdditionalConnectionPath(parent + "/A"));
      MessageException emError = assertThrows(
         MessageException.class,
         () -> emMove(List.of(parent + "/A"), RepositoryEntry.DATA_SOURCE, p + "Dest"));
      MessageException portalError = assertThrows(
         MessageException.class,
         () -> portalMove(browserService(repository), move(parent + "/A", p + "Dest/A")));

      install();
      assertEquals(additionalMove(), emError.getMessage());
      assertEquals(additionalMove(), portalError.getMessage());
      assertTrue(storage.contains(entry(parent + "/A").toIdentifier()));
      assertFalse(storage.contains(entry(p + "Dest/A").toIdentifier()));
      checkNothingQueued("uninstalled");
   }

   // control: with a parent that loads, the additional connection is refused as before
   @Test
   void additionalConnectionUnderALoadableParentIsRefused() throws Exception {
      String p = prefix();
      String parent = parentWithAdditional(p + "F/P", "add");

      MessageException error = assertThrows(
         MessageException.class,
         () -> emMove(List.of(parent + "/add"), RepositoryEntry.DATA_SOURCE, p + "Dest"));

      assertEquals(additionalMove(), error.getMessage());
      checkAdditionalNotMoved(parent, "add", p + "Dest/add");
      assertNotNull(registry.getDataSource(parent));
   }

   // regression: a folder move that carries a damaged parent and its additional connection
   // doesn't run the per-item check on them
   @Test
   void folderMoveCarryingADamagedParentIsNotRefused() throws Exception {
      String p = prefix();
      String parent = parentWithAdditional(p + "F/P", "add");
      damage(parent);

      emMove(List.of(p + "F"), RepositoryEntry.DATA_SOURCE_FOLDER, p + "Dest");

      registry.clearCache();
      assertFalse(storage.contains(entry(parent + "/add").toIdentifier()));
      assertTrue(storage.contains(entry(p + "Dest/" + p + "F/P/add").toIdentifier()));
      assertTrue(storage.contains(entry(p + "Dest/" + p + "F/P").toIdentifier()));
   }

   // ---- the fourth caller: delete removes the additional connection's grant ----

   // deleting F/P/add under a damaged parent removes its "F/P::add" grant, as with a parent that
   // loads (#77700), and leaves the sibling's and the parent's grants
   @Test
   void deleteOfAnAdditionalConnectionUnderADamagedParentRemovesItsGrant() throws Exception {
      String p = prefix();
      String parent = parentWithAdditional(p + "F/P", "add", "add2");
      grant(parent + "::add");
      grant(parent + "::add2");
      damage(parent);

      registry.removeDataSource(parent + "/add");

      registry.clearCache();
      assertFalse(storage.contains(entry(parent + "/add").toIdentifier()));
      assertNull(perm(parent + "::add"), "the removed additional connection keeps its grant");
      assertNotNull(perm(parent + "::add2"), "the sibling lost its grant");
      assertNotNull(perm(parent), "the parent lost its grant");
      assertTrue(storage.contains(entry(parent + "/add2").toIdentifier()));
      assertTrue(storage.contains(entry(parent).toIdentifier()));
   }

   // ---- Bug #78102: delete of a parent that can't be loaded removes its connections' grants ----

   // how the parent of the additional connection is made
   private enum ParentKind { JDBC_LOADABLE, JDBC_DAMAGED, TABULAR_LOADABLE, TABULAR_UNINSTALLED }

   // how the parent is deleted: the registry (EM Content > Repository), XEngine (the portal, which
   // removes the data model first) and a delete of the folder that contains it
   private enum DeletePath { REGISTRY, XENGINE, FOLDER }

   // Bug #78102: deleting a data source whose definition is damaged, or whose connector isn't
   // installed, removes the "F/P::add" grant of its additional connection as with one that loads,
   // so that an additional connection created later with that name doesn't get it. The grant of
   // an additional connection of another data source is kept.
   @Test
   void deleteOfAParentThatCannotBeLoadedRemovesTheGrantsOfItsAdditionalConnections()
      throws Exception
   {
      List<String> failures = new ArrayList<>();

      for(ParentKind kind : ParentKind.values()) {
         for(DeletePath path : DeletePath.values()) {
            String label = kind + " " + path;
            String p = prefix();
            String other = parentWithAdditional(p + "Dest/K", "add");
            grant(other + "::add");
            String parent = parentForDelete(kind, p + "F/P");
            boolean loads = registry.getDataSource(parent) != null;

            try {
               delete(path, parent, p + "F", kind);
            }
            finally {
               install();
            }

            registry.clearCache();
            assertEquals(kind.name().endsWith("LOADABLE"), loads, label + ": setup, loads");
            assertFalse(storage.contains(entry(parent).toIdentifier()), label + ": parent stored");
            assertFalse(storage.contains(entry(parent + "/add").toIdentifier()),
                        label + ": additional connection stored");
            assertNull(perm(parent), label + ": the parent keeps its grant");
            assertNotNull(perm(other + "::add"),
                          label + ": another data source's additional connection lost its grant");

            if(perm(parent + "::add") != null) {
               failures.add(label);
            }
         }
      }

      assertEquals(List.of(), failures,
                   "the removed additional connection keeps its \"F/P::add\" grant");
   }

   // Bug #78102: an additional connection created again under a data source created again at the
   // path of a deleted damaged one doesn't get the grant of the deleted one's
   @Test
   void additionalConnectionCreatedAgainAfterADamagedParentIsDeletedHasNoGrant()
      throws Exception
   {
      String p = prefix();
      String parent = parentForDelete(ParentKind.JDBC_DAMAGED, p + "F/P");

      registry.removeDataSource(parent);

      registry.clearCache();
      jdbc(parent);
      ((JDBCDataSource) registry.getDataSource(parent)).addDatasource(source("add"));
      registry.clearCache();
      assertTrue(registry.isAdditionalConnectionPath(parent + "/add"), "setup: created again");
      assertNull(perm(parent + "::add"), "the new additional connection has the old grant");
   }

   // a data source at "path" with an additional connection "add" and grants on both, made into
   // the given kind
   private String parentForDelete(ParentKind kind, String path) throws Exception {
      switch(kind) {
      case JDBC_LOADABLE:
      case JDBC_DAMAGED:
         parentWithAdditional(path, "add");
         break;
      default:
         tabular(path);
         grant(path);
         TestTabularDataSource ds = (TestTabularDataSource) registry.getDataSource(path);
         TestTabularDataSource add = new TestTabularDataSource();
         add.setName("add");
         ds.addDatasource(add);
         registry.clearCache();
         assertTrue(registry.isAdditionalConnectionPath(path + "/add"),
                    "setup: recognised while the parent loads");
      }

      grant(path + "::add");

      if(kind == ParentKind.JDBC_DAMAGED) {
         damage(path);
      }
      else if(kind == ParentKind.TABULAR_UNINSTALLED) {
         uninstall();
         assertNull(registry.getDataSource(path), "setup: " + path + " can't be loaded");
         assertTrue(registry.containObject(entry(path)), "setup: " + path + " is stored");
      }

      return path;
   }

   private void delete(DeletePath path, String parent, String folder, ParentKind kind)
      throws Exception
   {
      // the connector stays uninstalled while the delete runs
      if(kind == ParentKind.TABULAR_UNINSTALLED) {
         uninstall();
      }

      switch(path) {
      case REGISTRY:
         registry.removeDataSource(parent);
         break;
      case XENGINE:
         assertTrue(repository.removeDataSource(parent, true), "XEngine delete of " + parent);
         break;
      default:
         registry.removeDataSourceFolder(folder);
      }
   }

   // a data source "path" with additional connections stored at "path/<name>" with bare names
   private String parentWithAdditional(String path, String... names) {
      jdbc(path);
      grant(path);
      JDBCDataSource ds = (JDBCDataSource) registry.getDataSource(path);

      for(String name : names) {
         ds.addDatasource(source(name));
      }

      registry.clearCache();

      for(String name : names) {
         assertTrue(registry.isAdditionalConnectionPath(path + "/" + name),
                    "setup: recognised while the parent loads");
      }

      return path;
   }

   // damages the stored definition of a data source, its additional connections stay intact
   private void damage(String path) throws Exception {
      storage.putXMLSerializable(entry(path).toIdentifier(), new XDataSourceWrapper());
      registry.clearCache();
      assertNull(registry.getDataSource(path), "setup: " + path + " can't be loaded");
      assertTrue(registry.containObject(entry(path)), "setup: " + path + " is stored");
   }

   // the additional connection is stored under its parent with its bare name, not at the new path
   private void checkAdditionalNotMoved(String parent, String name, String nname) {
      registry.clearCache();
      assertTrue(storage.contains(entry(parent + "/" + name).toIdentifier()),
                 parent + "/" + name + " isn't stored");
      XDataSource additional = registry.getDataSource(parent + "/" + name);
      assertNotNull(additional, parent + "/" + name + " can't be loaded");
      assertEquals(name, additional.getFullName(), "the stored name of " + parent + "/" + name);
      assertFalse(storage.contains(entry(nname).toIdentifier()), nname + " is stored");
   }

   private String additionalMove() {
      return Catalog.getCatalog(principal).getString("common.datasource.additionalConnectionMove");
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

   // the definition that a hand-made PUT sends
   private static DataSourceDefinition definition(String parentPath, String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType("jdbc");
      definition.setParentPath(parentPath);
      definition.setName(name);
      definition.setDescription("via PUT");
      definition.setTabularView(new TabularView());
      return definition;
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
      String p = "ac" + (++seq) + "x";
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
