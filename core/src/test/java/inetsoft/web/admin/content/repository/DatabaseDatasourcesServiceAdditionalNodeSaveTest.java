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

import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XDataSourceWrapper;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Drivers;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77610: saving an additional connection in its own editor, which reads and saves it by
 * the path parent/name, must keep it under its parent with its name alone, whether or not the
 * registry cache still holds the instance that the repository tree gave a base data source.
 * The registry and the repository are the real ones, so that the save runs through
 * XEngine.updateDataSource.
 * <p>
 * Bug #78207: an additional connection saved before Bug #77610 is stored at parent/name with its
 * path as its name. Its own editor must save it as an additional connection too, moving its
 * permission and repointing the extended views on a rename, and the repository tree must give it
 * the path parent/name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DatabaseDatasourcesServiceAdditionalNodeSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceAdditionalNodeSaveTest {
   private static final String URL = "jdbc:derby:memory:bug77610;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private DatabaseDatasourcesService service;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @Test
   void renameAfterTheCacheIsCleared() throws Exception {
      addParent("nullRename");
      registry.clearCache();

      saveNode("nullRename/nullRenameOld", "nullRenameNew");

      assertUnderParent("nullRename", "nullRenameNew", "nullRenameKeep");
   }

   @Test
   void plainSaveAfterTheCacheIsCleared() throws Exception {
      addParent("nullSave");
      registry.clearCache();

      saveNode("nullSave/nullSaveOld", "nullSaveOld");

      assertUnderParent("nullSave", "nullSaveOld", "nullSaveKeep");
   }

   @Test
   void renameWithTheBaseDataSourceSet() throws Exception {
      addParent("setRename");
      Permission permission = new Permission();
      when(security.getPermission(ResourceType.DATA_SOURCE, "setRename::setRenameOld"))
         .thenReturn(permission);
      readThroughParent("setRename", "setRenameOld");

      saveNode("setRename/setRenameOld", "setRenameNew");

      assertUnderParent("setRename", "setRenameNew", "setRenameKeep");
      verify(security)
         .setPermission(ResourceType.DATA_SOURCE, "setRename::setRenameNew", permission);
   }

   @Test
   void plainSaveWithTheBaseDataSourceSet() throws Exception {
      addParent("setSave");
      readThroughParent("setSave", "setSaveOld");

      saveNode("setSave/setSaveOld", "setSaveOld");

      assertUnderParent("setSave", "setSaveOld", "setSaveKeep");
   }

   @Test
   void parentSaveKeepsTheAdditionalConnections() throws Exception {
      addParent("parentSave");
      JDBCDataSource parent = (JDBCDataSource) repository.getDataSource("parentSave");
      DatabaseDefinition definition = edit(parent);
      definition.setDescription("edited");
      DatabaseDefinition renamed = edit(parent.getDataSource("parentSaveOld"));
      renamed.setOldName("parentSaveOld");
      renamed.setName("parentSaveNew");
      DatabaseDefinition kept = edit(parent.getDataSource("parentSaveKeep"));
      kept.setOldName("parentSaveKeep");

      assertNull(service.saveDatabase("parentSave", DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).additionalDataSources(renamed, kept)
         .build(), ActionRecord.ACTION_NAME_EDIT, principal));

      assertUnderParent("parentSave", "parentSaveNew", "parentSaveKeep");
      assertEquals("edited", registry.getDataSource("parentSave").getDescription());
   }

   @Test
   void placeholderPasswordKeepsTheStoredPassword() throws Exception {
      for(boolean baseSet : new boolean[] { true, false }) {
         String parentName = baseSet ? "passwordSet" : "passwordNull";
         String name = parentName + "Old";
         addParent(parentName);
         ((JDBCDataSource) registry.getDataSource(parentName)).addDatasource(loginSource(name));

         if(baseSet) {
            readThroughParent(parentName, name);
         }
         else {
            registry.clearCache();
         }

         saveNode(parentName + "/" + name, name);

         assertUnderParent(parentName, name, parentName + "Keep");
         JDBCDataSource saved = stored(parentName, name);
         assertEquals("user", saved.getUser(), parentName);
         assertEquals("secret", saved.getPassword(), parentName + " lost its password");
      }
   }

   // the extended views of the parent's data model follow the renamed connection
   @Test
   void renameRepointsTheExtendedPartitions() throws Exception {
      for(boolean baseSet : new boolean[] { true, false }) {
         String parentName = baseSet ? "viewSet" : "viewNull";
         addParent(parentName);
         XPartition partition = new XPartition("view");
         registry.getDataModel(parentName).addPartition(partition);
         XPartition extended = new XPartition(parentName + "Old");
         extended.setConnection(parentName + "Old");
         registry.getDataModel(parentName).getPartition("view").addPartition(extended, false);

         if(baseSet) {
            readThroughParent(parentName, parentName + "Old");
         }
         else {
            registry.clearCache();
         }

         saveNode(parentName + "/" + parentName + "Old", parentName + "New");

         assertUnderParent(parentName, parentName + "New", parentName + "Keep");
         XPartition view = registry.getDataModel(parentName).getPartition("view");
         assertArrayEquals(new String[] { parentName + "New" }, view.getPartitionNames(),
                           parentName);
         assertEquals(parentName + "New", view.getPartition(parentName + "New").getConnection(),
                      parentName);
      }
   }

   // a top-level data source with the name of the additional connection is neither changed nor
   // used for the stored password
   @Test
   void saveOfAnAdditionalConnectionNamedLikeATopLevelSource() throws Exception {
      for(boolean baseSet : new boolean[] { true, false }) {
         String parentName = baseSet ? "sameSet" : "sameNull";
         String name = parentName + "Twin";
         JDBCDataSource topLevel = loginSource(name);
         topLevel.setPassword("other");
         topLevel.setDescription("top level");
         registry.setDataSource(topLevel, false);
         addParent(parentName);
         ((JDBCDataSource) registry.getDataSource(parentName)).addDatasource(loginSource(name));

         if(baseSet) {
            readThroughParent(parentName, name);
         }
         else {
            registry.clearCache();
         }

         saveNode(parentName + "/" + name, name);

         registry.clearCache();
         String[] children = ((JDBCDataSource) registry.getDataSource(parentName))
            .getDataSourceNames();
         Arrays.sort(children);
         assertArrayEquals(new String[] { parentName + "Keep", parentName + "Old", name },
                           children, parentName);
         JDBCDataSource saved = stored(parentName, name);
         assertEquals(name, saved.getFullName(), parentName);
         assertEquals("edited", saved.getDescription(), parentName);
         assertEquals("secret", saved.getPassword(), parentName);
         JDBCDataSource top = (JDBCDataSource) registry.getDataSource(name);
         assertEquals(name, top.getFullName(), parentName);
         assertEquals("top level", top.getDescription(), parentName);
         assertEquals("other", top.getPassword(), parentName);
      }
   }

   // a data source in a folder is saved with its path as its name, so it isn't read through a
   // parent
   @Test
   void saveOfADataSourceInAFolder() throws Exception {
      registry.setDataSourceFolder(
         new DataSourceFolder("folderSave", LocalDateTime.now(), null));
      registry.setDataSource(customSource("folderSave/folderSaveOld"), false);
      registry.clearCache();

      saveNode("folderSave/folderSaveOld", "folderSaveOld");
      registry.clearCache();
      XDataSource saved = registry.getDataSource("folderSave/folderSaveOld");
      assertEquals("folderSave/folderSaveOld", saved.getFullName());
      assertEquals("edited", saved.getDescription());
      assertNull(registry.getDataSource("folderSaveOld"));

      saveNode("folderSave/folderSaveOld", "folderSaveNew");
      registry.clearCache();
      assertNull(registry.getDataSource("folderSave/folderSaveOld"));
      assertEquals("folderSave/folderSaveNew",
                   registry.getDataSource("folderSave/folderSaveNew").getFullName());
      assertNull(registry.getDataSource("folderSaveNew"));
   }

   // Bug #78207: a rename of an additional connection saved before Bug #77610 moves its
   // permission, repoints the extended views and saves it with its name alone
   @Test
   void renameOfALegacyAdditionalConnection() throws Exception {
      for(boolean baseSet : new boolean[] { true, false }) {
         String parentName = baseSet ? "legacyRenameSet" : "legacyRenameNull";
         String oldName = parentName + "Old";
         String newName = parentName + "New";
         addLegacyParent(parentName);
         XPartition partition = new XPartition("view");
         registry.getDataModel(parentName).addPartition(partition);
         XPartition extended = new XPartition(oldName);
         extended.setConnection(oldName);
         registry.getDataModel(parentName).getPartition("view").addPartition(extended, false);
         Permission permission = new Permission();
         when(security.getPermission(ResourceType.DATA_SOURCE, parentName + "::" + oldName))
            .thenReturn(permission);

         if(baseSet) {
            readThroughParent(parentName, oldName);
         }
         else {
            registry.clearCache();
         }

         saveNode(parentName + "/" + oldName, newName);

         assertUnderParent(parentName, newName, parentName + "Keep");
         assertNull(registry.getDataSource(parentName + "/" + oldName), parentName);
         XPartition view = registry.getDataModel(parentName).getPartition("view");
         assertArrayEquals(new String[] { newName }, view.getPartitionNames(), parentName);
         assertEquals(newName, view.getPartition(newName).getConnection(), parentName);
         verify(security).removePermission(ResourceType.DATA_SOURCE, parentName + "::" + oldName);
         verify(security)
            .setPermission(ResourceType.DATA_SOURCE, parentName + "::" + newName, permission);
         verify(security, never()).setPermission(
            eq(ResourceType.DATA_SOURCE), eq(parentName + "/" + newName), any());
      }
   }

   // Bug #78207: a plain save of an additional connection saved before Bug #77610 repairs its
   // name, and isn't taken for a rename onto a top-level data source with its name, which would
   // need the DELETE permission
   @Test
   void plainSaveOfALegacyAdditionalConnection() throws Exception {
      when(security.checkPermission(any(), eq(ResourceType.DATA_SOURCE), anyString(),
                                    eq(ResourceAction.DELETE))).thenReturn(false);

      for(boolean baseSet : new boolean[] { true, false }) {
         String parentName = baseSet ? "legacySaveSet" : "legacySaveNull";
         String name = parentName + "Old";
         JDBCDataSource topLevel = customSource(name);
         topLevel.setDescription("top level");
         registry.setDataSource(topLevel, false);
         addLegacyParent(parentName);

         if(baseSet) {
            readThroughParent(parentName, name);
         }
         else {
            registry.clearCache();
         }

         saveNode(parentName + "/" + name, name);

         registry.clearCache();
         String[] children = ((JDBCDataSource) registry.getDataSource(parentName))
            .getDataSourceNames();
         Arrays.sort(children);
         assertArrayEquals(new String[] { parentName + "Keep", name }, children, parentName);
         JDBCDataSource saved = stored(parentName, name);
         assertEquals(name, saved.getFullName(), parentName);
         assertEquals("edited", saved.getDescription(), parentName);
         XDataSource top = registry.getDataSource(name);
         assertEquals(name, top.getFullName(), parentName);
         assertEquals("top level", top.getDescription(), parentName);
         verify(security, never()).setPermission(
            eq(ResourceType.DATA_SOURCE), eq(parentName + "/" + name), any());
      }
   }

   // Bug #78207: a data source of a data source folder at the path of a data source (older data)
   // is saved with its path as its name, as a legacy additional connection is, but it isn't an
   // additional connection, so it is saved as a top-level data source
   @Test
   void saveOfADataSourceOfAFolderAtTheParentPath() throws Exception {
      registry.setDataSource(customSource("clashSave"), false);
      registry.setDataSourceFolder(new DataSourceFolder("clashSave", LocalDateTime.now(), null));
      setLegacy("clashSave/clashSaveX");
      registry.clearCache();
      assertFalse(registry.isAdditionalConnectionPath("clashSave/clashSaveX"));

      saveNode("clashSave/clashSaveX", "clashSaveX");

      registry.clearCache();
      XDataSource saved = registry.getDataSource("clashSave/clashSaveX");
      assertEquals("clashSave/clashSaveX", saved.getFullName());
      assertEquals("edited", saved.getDescription());
      assertArrayEquals(new String[0],
                        ((JDBCDataSource) registry.getDataSource("clashSave")).getDataSourceNames());
      assertNull(registry.getDataSource("clashSaveX"));
   }

   // Bug #78207: the repository tree gives an additional connection saved before Bug #77610 the
   // path parent/name, which its editor and its permission resource resolve
   @Test
   void treePathOfALegacyAdditionalConnection() throws Exception {
      addLegacyParent("legacyTree");
      registry.clearCache();
      ContentRepositoryTreeService tree = new ContentRepositoryTreeService(
         mock(SecurityProvider.class), repository, mock(ResourcePermissionService.class),
         mock(RepletRegistryService.class), mock(ScheduleTaskFolderService.class),
         mock(MVManager.class), security, mock(ScheduleManager.class), registry,
         mock(DashboardManager.class), mock(IndexedStorage.class),
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(RepletRegistryManager.class));

      List<ContentRepositoryTreeNode> nodes = ReflectionTestUtils.invokeMethod(
         tree, "getAdditionalDataSources", registry.getDataSource("legacyTree"),
         Collections.emptySet());

      assertNotNull(nodes);
      Map<String, String> paths = new HashMap<>();
      nodes.forEach(node -> paths.put(node.label(), node.path()));
      assertEquals(Map.of("legacyTreeOld", "legacyTree/legacyTreeOld",
                          "legacyTreeKeep", "legacyTree/legacyTreeKeep"), paths);
      assertNotNull(repository.getDataSource(paths.get("legacyTreeOld")));
      assertEquals("legacyTree::legacyTreeOld", ResourcePermissionService
         .getDataSourceResourceName(paths.get("legacyTreeOld"), registry));
   }

   // a parent with a bare named additional connection, name + "Keep", and one saved before
   // Bug #77610, name + "Old", stored with its path as its name
   private void addLegacyParent(String name) throws Exception {
      registry.setDataSource(customSource(name), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(name);
      parent.addDatasource(customSource(name + "Keep"));
      setLegacy(name + "/" + name + "Old");
   }

   // stores a data source at the path with the path as its name
   private void setLegacy(String path) {
      registry.setObject(
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null),
         new XDataSourceWrapper(customSource(path)));
   }

   // the names of the additional connections start with the parent name, since every test of
   // the class saves to the same storage
   private void addParent(String name) throws Exception {
      registry.setDataSource(customSource(name), false);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(name);
      parent.addDatasource(customSource(name + "Old"));
      parent.addDatasource(customSource(name + "Keep"));
   }

   // the repository tree reads an additional connection through its parent, which sets the base
   // data source of the instance in the registry cache
   private void readThroughParent(String parentName, String name) {
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(parentName);
      assertNotNull(parent.getDataSource(name).getBaseDatasource());
   }

   // saves the additional connection in the editor that the repository tree opens for it
   private void saveNode(String path, String newName) throws Exception {
      JDBCDataSource additional = (JDBCDataSource) repository.getDataSource(path);
      DatabaseDefinition definition = edit(additional);
      definition.setName(newName);
      definition.setDescription("edited");

      assertNull(service.saveDatabase(path, DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).build(),
                                      ActionRecord.ACTION_NAME_EDIT, principal));
   }

   private void assertUnderParent(String parentName, String... names) {
      // read from the storage, not from instances cached by the save
      registry.clearCache();
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children = ((JDBCDataSource) registry.getDataSource(parentName))
         .getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentName);

      for(String name : names) {
         assertEquals(name, stored(parentName, name).getFullName(),
                      "the stored name of " + parentName + "/" + name);
      }

      for(String suffix : new String[] { "Old", "New", "Keep" }) {
         String name = parentName + suffix;
         assertNull(registry.getDataSource(name),
                    "a data source " + name + " was saved outside of " + parentName);
      }
   }

   private JDBCDataSource stored(String parentName, String name) {
      return (JDBCDataSource) registry.getDataSource(parentName + "/" + name);
   }

   private static JDBCDataSource customSource(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   private static JDBCDataSource loginSource(String name) {
      JDBCDataSource dataSource = customSource(name);
      dataSource.setRequireLogin(true);
      dataSource.setUser("user");
      dataSource.setPassword("secret");
      return dataSource;
   }

   // the definition the data source editor loads
   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }

   @Configuration
   static class Beans {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      // the constructors of these beans are package private
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      // loads the embedded Derby driver of the test sources
      @Bean
      @Primary
      public Drivers testDrivers() throws Exception {
         Drivers drivers = mock(Drivers.class);
         when(drivers.getDriverClass(anyString()))
            .thenAnswer(inv -> Class.forName(inv.<String>getArgument(0)));
         return drivers;
      }
   }
}
