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
package inetsoft.util.dep;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XUtil;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.AssetItem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79843: a physical view in a data model folder, imported into an existing data source with
 * overwrite off, must register its folder in the data model. The data source asset, which carries
 * the folders of the data model, is skipped in that case, and the tree lists a physical view only
 * under a registered folder. The assets are parsed in the order of
 * {@code DeployManagerService.sortFiles}: the data source before the partition.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  XPartitionAssetFolderImportTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XPartitionAssetFolderImportTest {
   private static final String URL = "jdbc:derby:memory:bug79843;create=true";

   @Autowired
   private DataSourceRegistry registry;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
   }

   @Test
   void folderOfPartitionIsRegisteredInExistingDataSourceWithoutOverwrite() throws Exception {
      String ds = "fiA79843";
      createDataSource(ds);

      importDataSource(ds, "F1", false);
      importPartition(ds, "F1", "P1", false);

      XDataModel model = readModel(ds);
      assertEquals(List.of("F1"), Arrays.asList(model.getFolders()));
      assertEquals("F1", model.getPartition("P1").getFolder());
   }

   @Test
   void partitionWithoutFolderAddsNoFolder() throws Exception {
      String ds = "fiB79843";
      createDataSource(ds);

      importDataSource(ds, null, false);
      importPartition(ds, null, "P1", false);

      XDataModel model = readModel(ds);
      assertEquals(0, model.getFolders().length);
      assertNotNull(model.getPartition("P1"));
      assertNull(model.getPartition("P1").getFolder());
   }

   // a partition that already exists and is not overwritten keeps its place; no folder is added
   @Test
   void skippedPartitionAddsNoFolder() throws Exception {
      String ds = "fiC79843";
      createDataSource(ds);
      XDataModel model = registry.getDataModel(ds);
      model.addPartition(new XPartition("P1"), true);

      importDataSource(ds, "F1", false);
      importPartition(ds, "F1", "P1", false);

      model = readModel(ds);
      assertEquals(0, model.getFolders().length);
      assertNull(model.getPartition("P1").getFolder());
   }

   // with overwrite on the folder comes from the data source, with its creation info
   @Test
   void overwriteKeepsFolderFromDataSource() throws Exception {
      String ds = "fiD79843";
      createDataSource(ds);

      importDataSource(ds, "F1", true);
      importPartition(ds, "F1", "P1", true);

      XDataModel model = readModel(ds);
      assertEquals(List.of("F1"), Arrays.asList(model.getFolders()));
      assertEquals("admin", model.getFolderCreatedBy("F1"));
      assertEquals("F1", model.getPartition("P1").getFolder());
   }

   // the Portal data model browser shows folder F1 at the root and the imported view under it
   @Test
   void portalListsImportedPartitionUnderItsFolder() throws Exception {
      String ds = "fiE79843";
      createDataSource(ds);

      importDataSource(ds, "F1", false);
      importPartition(ds, "F1", "P1", false);

      registry.clearCache();
      assertEquals(List.of("data_model_folder:F1"), portalItems(ds, null));
      assertEquals(List.of("physical_model:P1"), portalItems(ds, "F1"));
   }

   @Test
   void portalListsPartitionUnderFolderWithOverwrite() throws Exception {
      String ds = "fiF79843";
      createDataSource(ds);

      importDataSource(ds, "F1", true);
      importPartition(ds, "F1", "P1", true);

      registry.clearCache();
      assertEquals(List.of("data_model_folder:F1"), portalItems(ds, null));
      assertEquals(List.of("physical_model:P1"), portalItems(ds, "F1"));
   }

   @Test
   void portalListsPartitionWithoutFolderAtRoot() throws Exception {
      String ds = "fiG79843";
      createDataSource(ds);

      importDataSource(ds, null, false);
      importPartition(ds, null, "P1", false);

      registry.clearCache();
      assertEquals(List.of("physical_model:P1"), portalItems(ds, null));
   }

   // the folder already exists in the target: it is not added again and keeps its creation info
   @Test
   void existingFolderIsKept() throws Exception {
      String ds = "fiH79843";
      createDataSource(ds);
      XDataModel model = registry.getDataModel(ds);
      model.addFolder("F1", "bob", 1000L);
      registry.setDataModel(model);

      importDataSource(ds, "F1", false);
      importPartition(ds, "F1", "P1", false);

      model = readModel(ds);
      assertEquals(List.of("F1"), Arrays.asList(model.getFolders()));
      assertEquals("bob", model.getFolderCreatedBy("F1"));
      assertEquals("F1", model.getPartition("P1").getFolder());
      assertEquals(List.of("data_model_folder:F1"), portalItems(ds, null));
      assertEquals(List.of("physical_model:P1"), portalItems(ds, "F1"));
   }

   // the view exists outside any folder and is not overwritten: it stays at the root, no F1
   @Test
   void portalKeepsSkippedPartitionAtRoot() throws Exception {
      String ds = "fiI79843";
      createDataSource(ds);
      importPartition(ds, null, "P1", false);

      importDataSource(ds, "F1", false);
      importPartition(ds, "F1", "P1", false);

      registry.clearCache();
      assertEquals(0, readModel(ds).getFolders().length);
      assertEquals(List.of("physical_model:P1"), portalItems(ds, null));
   }

   // the items of the Portal data model browser (DatabaseModelBrowserController) in a folder
   private List<String> portalItems(String ds, String folder) throws Exception {
      SecurityEngine security = mock(SecurityEngine.class, inv ->
         inv.getMethod().getReturnType() == boolean.class ? Boolean.TRUE :
            RETURNS_DEFAULTS.answer(inv));
      doReturn(mock(SecurityProvider.class)).when(security).getSecurityProvider();
      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(anyString()))
         .thenAnswer(inv -> registry.getDataModel(inv.<String>getArgument(0)));
      DataSourceService dataSourceService = new DataSourceService(
         mock(AssetRepository.class), security, repository, registry);
      DatabaseModelBrowserService browser = new DatabaseModelBrowserService(
         dataSourceService, repository, mock(PhysicalModelManagerService.class),
         mock(LogicalModelService.class), security, mock(DataModelFolderManagerService.class),
         mock(RenameTransformHandler.class));
      AssetItem[] items =
         browser.getDataModelBrowseModel(ds, folder, null, false).getListModel().getItems();
      return Arrays.stream(items).map(i -> i.getType() + ":" + i.getName())
         .collect(Collectors.toList());
   }

   private XDataModel readModel(String ds) {
      registry.clearCache();
      XDataModel model = registry.getDataModel(ds);
      assertNotNull(model, ds);
      return model;
   }

   private void createDataSource(String name) throws Exception {
      importDataSource(name, null, false);
      registry.clearCache();
      assertNotNull(registry.getDataModel(name));
      assertEquals(0, registry.getDataModel(name).getFolders().length);
   }

   private void importDataSource(String name, String folder, boolean overwrite) throws Exception {
      new XDataSourceAsset(name).parseContent(
         new ByteArrayInputStream(dataSourceXml(name, folder).getBytes(StandardCharsets.UTF_8)),
         config(overwrite), true, true);
   }

   private void importPartition(String ds, String folder, String name, boolean overwrite)
      throws Exception
   {
      XPartition partition = new XPartition(name);
      partition.setFolder(folder);
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      partition.writeXML(writer);
      writer.flush();
      String path = folder == null ? ds + "^" + name :
         ds + XUtil.DATAMODEL_FOLDER_SPLITER + folder + "^" + name;
      // a new asset per file, as DeployManagerService does
      new XPartitionAsset(path).parseContent(
         new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)),
         config(overwrite), true, true);
   }

   private static XAssetConfig config(boolean overwrite) {
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwrite);
      return config;
   }

   // the data source export, carrying the data model with its folders as env A exports it
   private static String dataSourceXml(String name, String folder) {
      JDBCDataSource source = new JDBCDataSource();
      source.setName(name);
      source.setCustom(true);
      source.setCustomEditMode(true);
      source.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      source.setURL(URL);
      source.setCustomUrl(URL);
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      writer.println("<registry>");
      writer.println("<datasource name=\"" + name + "\" type=\"" + source.getType() + "\">");
      source.writeXML(writer);
      writer.println("</datasource>");
      XDataModel model = new XDataModel(name);

      if(folder != null) {
         model.addFolder(folder, "admin", System.currentTimeMillis());
      }

      model.writeXML(writer);
      writer.println("</registry>");
      writer.flush();
      return buffer.toString();
   }

   static class Beans {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

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
