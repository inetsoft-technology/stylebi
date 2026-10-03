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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Drivers;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
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
