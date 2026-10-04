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
package inetsoft.sree.internal;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Drivers;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.dep.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77702: an import of a data source under the path of an existing data source, at a nested
 * path or at the path of one of its additional connections, is refused. The existing data source
 * isn't removed with its additional connections and data model, and no folder is created beside
 * it. The import runs as {@link DeployManagerService#importAssets} does: the asset is auto
 * renamed, the file rewritten, then the asset parsed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceImportUnderDataSourceTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceImportUnderDataSourceTest {
   private static final String URL = "jdbc:derby:memory:bug77702;create=true";

   @Autowired
   private DataSourceRegistry registry;
   private DeployManagerService service;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      service = new DeployManagerService(
         mock(SecurityEngine.class), mock(DependencyHandler.class), registry,
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));
   }

   @Test
   void dataSourceUnderDataSourceIsRefusedWithoutOverwrite() throws Exception {
      create("noOverF", "add");

      MessageException e = assertThrows(
         MessageException.class, () -> importDataSource("noOverF/x", false));

      assertTrue(e.getMessage().contains("noOverF"), e.getMessage());
      assertKept("noOverF", "add");
      assertFalse(containsDataSource("noOverF/x"));
   }

   @Test
   void dataSourceUnderDataSourceIsRefusedWithOverwrite() throws Exception {
      create("overF", "add");

      assertThrows(MessageException.class, () -> importDataSource("overF/x", true));

      assertKept("overF", "add");
      assertFalse(containsDataSource("overF/x"));
   }

   @Test
   void dataSourceUnderAdditionalConnectionIsRefused() throws Exception {
      create("nestA", "B");

      assertThrows(MessageException.class, () -> importDataSource("nestA/B/nestX", false));

      assertKept("nestA", "B");
      assertFalse(containsFolder("nestA/B"));
      assertFalse(containsDataSource("nestA/B/nestX"));
   }

   @Test
   void dataSourceAtAdditionalConnectionPathIsRefusedWithOverwrite() throws Exception {
      create("addF", "add");

      assertThrows(MessageException.class, () -> importDataSource("addF/add", true));

      assertKept("addF", "add");
   }

   @Test
   void dataSourceAtAdditionalConnectionPathIsRefusedWithoutOverwrite() throws Exception {
      create("addOffF", "add");

      assertThrows(MessageException.class, () -> importDataSource("addOffF/add", false));

      assertKept("addOffF", "add");
   }

   // the connector of the data source isn't installed, getDataSource() returns null for it
   @Test
   void dataSourceUnderUnsupportedDataSourceIsRefused() throws Exception {
      JDBCDataSource unsupported = new JDBCDataSource() {
         @Override
         public String getType() {
            return "unsupported77702";
         }
      };
      unsupported.setName("unsupF");
      registry.setDataSource(unsupported, false);
      assertNull(registry.getDataSource("unsupF"));

      assertThrows(MessageException.class, () -> importDataSource("unsupF/x", false));

      assertTrue(containsDataSource("unsupF"));
      assertFalse(containsFolder("unsupF"));
      assertFalse(containsDataSource("unsupF/x"));
   }

   // a partition exported with the refused data source isn't added to the model of the data
   // source the import was refused under
   @Test
   void partitionOfRefusedDataSourceIsNotImported() throws Exception {
      create("partF", "add");
      assertThrows(MessageException.class, () -> importDataSource("partF/add", true));

      importPartition("partF/add", "part77702");
      importPartition("partF/x", "part77702");

      assertKept("partF", "add");
      assertNull(registry.getDataModel("partF").getPartition("part77702"));
      assertNull(registry.getDataModel("partF/add"));
      assertNull(registry.getDataModel("partF/x"));
   }

   @Test
   void parseXDataSourceRefusesDataSourceUnderDataSource() throws Exception {
      create("regF", "add");
      Element elem = Tool.parseXML(new ByteArrayInputStream(
         xml("regF/x", null).getBytes(StandardCharsets.UTF_8))).getDocumentElement();

      assertThrows(MessageException.class, () -> registry.parseXDataSource(elem, true));

      assertKept("regF", "add");
      assertFalse(containsDataSource("regF/x"));
   }

   @Test
   void dataSourceInFolderIsImported() throws Exception {
      registry.setDataSourceFolder(new DataSourceFolder("okF", LocalDateTime.now(), null));

      importDataSource("okF/okX", false);
      importDataSource("okG/okY", false);

      assertNotNull(registry.getDataSource("okF/okX"));
      assertNotNull(registry.getDataSource("okG/okY"));
      assertTrue(containsFolder("okG"));
   }

   private void assertKept(String name, String additional) {
      registry.clearCache();
      XDataSource source = registry.getDataSource(name);
      assertNotNull(source, name);
      assertArrayEquals(new String[] { additional },
                        ((JDBCDataSource) source).getDataSourceNames());
      assertTrue(containsDataSource(name + "/" + additional));
      assertNotNull(registry.getDataModel(name));
      assertFalse(containsFolder(name));
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null));
   }

   private boolean containsFolder(String path) {
      return registry.containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, path, null));
   }

   private void create(String name, String additional) throws Exception {
      new XDataSourceAsset(name).parseContent(
         new ByteArrayInputStream(xml(name, additional).getBytes(StandardCharsets.UTF_8)),
         config(false), true, true);
      assertKept(name, additional);
   }

   private void importDataSource(String name, boolean overwrite) throws Exception {
      XAsset asset = new XDataSourceAsset(name);
      XAsset nAsset = service.getChangeRootFolderAsset(
         asset, null, new HashSet<>(), null, true, new HashSet<>(), new HashMap<>(), false);
      assertEquals(name, nAsset.getPath());
      File file = Files.createTempFile("bug77702", ".xml").toFile();

      try {
         Files.writeString(file.toPath(), xml(name, null));
         UpdateDependencyHandler.replaceDataSourceInfo(file, asset, nAsset);

         try(InputStream input = new FileInputStream(file)) {
            nAsset.parseContent(input, config(overwrite), true, true);
         }
      }
      finally {
         Files.deleteIfExists(file.toPath());
      }
   }

   private void importPartition(String dataSource, String partition) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      new XPartition(partition).writeXML(writer);
      writer.flush();
      new XPartitionAsset(dataSource + "^" + partition).parseContent(
         new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)),
         config(true), true, true);
   }

   private static XAssetConfig config(boolean overwrite) {
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwrite);
      return config;
   }

   private static String xml(String name, String additional) {
      JDBCDataSource source = source(name);
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      writer.println("<registry>");
      writer.println("<datasource name=\"" + name + "\" type=\"" + source.getType() + "\">");
      source.writeXML(writer);
      writer.println("</datasource>");

      if(additional != null) {
         JDBCDataSource child = source(additional);
         writer.println("<additional name=\"" + additional + "\" type=\"" + child.getType() +
                        "\" parent=\"" + name + "\">");
         child.writeXML(writer);
         writer.println("</additional>");
      }

      new XDataModel(name).writeXML(writer);
      writer.println("</registry>");
      writer.flush();
      return buffer.toString();
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
