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
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.uql.xmla.Domain;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77732: a data model or an XMLA domain moved by the move of the objects under a prefix,
 * e.g. the model of a data source that can't be loaded in a moved or renamed folder, must get
 * its new path as its data source. A data model lists its logical models, partitions and VPMs by
 * its data source, so with the old one it lists none of them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderMoveDataModelTest {
   private static final String URL = "jdbc:derby:memory:bug77732;create=true";
   private static final String LM = "lm77732";
   private static final String PT = "pt77732";
   private static final String VPM = "vpm77732";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private Config config;
   @Autowired
   private IndexedStorage indexedStorage;
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
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
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

   // control: a loadable data source is moved by renameDatasource
   @Test
   void emFolderMoveWithALoadableDataSource() throws Exception {
      addFolder("ldF");
      addFolder("ldDest");
      addModel("ldF/ldJ");

      emMove("ldF", "ldDest");

      assertModel("ldDest/ldF/ldJ", "ldF/ldJ");
   }

   @Test
   void emFolderMoveWithACorruptDataSource() throws Exception {
      addFolder("cmF");
      addFolder("cmDest");
      addModel("cmF/cmJ");
      corrupt("cmF/cmJ");

      emMove("cmF", "cmDest");

      assertTrue(containsDataSource("cmDest/cmF/cmJ"));
      assertModel("cmDest/cmF/cmJ", "cmF/cmJ");
   }

   @Test
   void portalFolderMoveWithACorruptDataSource() throws Exception {
      addFolder("cpF");
      addFolder("cpDest");
      addModel("cpF/cpJ");
      corrupt("cpF/cpJ");
      DataSourceBrowserService service = new DataSourceBrowserService(
         security, objectService(), repository, mock(DataSourceService.class), registry,
         mock(Config.class), transformHandler);
      MoveCommand move = new MoveCommand();
      move.setOldPath("cpF");
      move.setPath("cpDest/cpF");
      move.setName("cpF");
      move.setType(PortalDataType.DATA_SOURCE_FOLDER.name());

      service.moveDataSource(new MoveCommand[] { move }, principal);

      assertTrue(containsDataSource("cpDest/cpF/cpJ"));
      assertModel("cpDest/cpF/cpJ", "cpF/cpJ");
   }

   // a folder rename through XEngine.updateDataSourceFolder (portal and EM rename), the data
   // source in a subfolder
   @Test
   void folderRenameWithACorruptDataSource() throws Exception {
      addFolder("crF");
      addFolder("crF/crS");
      addModel("crF/crS/crJ");
      corrupt("crF/crS/crJ");

      repository.updateDataSourceFolder(
         new DataSourceFolder("crG", LocalDateTime.now(), null), "crF");

      assertTrue(containsDataSource("crG/crS/crJ"));
      assertModel("crG/crS/crJ", "crF/crS/crJ");
   }

   @Test
   void emFolderMoveWithAnUninstalledConnector() throws Exception {
      addFolder("unF");
      addFolder("unDest");
      addModel("unF/unJ");
      doReturn(null).when(config).getDataSourceClass("jdbc");

      try {
         registry.clearCache();
         assertNull(registry.getDataSource("unF/unJ"), "the connector is still installed");
         emMove("unF", "unDest");
      }
      finally {
         doCallRealMethod().when(config).getDataSourceClass("jdbc");
         registry.clearCache();
      }

      assertNotNull(registry.getDataSource("unDest/unF/unJ"));
      assertModel("unDest/unF/unJ", "unF/unJ");
   }

   @Test
   void emFolderMoveWithACorruptXmlaDataSource() throws Exception {
      addFolder("xmF");
      addFolder("xmDest");
      addXmla("xmF/xmX");
      corrupt("xmF/xmX");

      emMove("xmF", "xmDest");

      registry.clearCache();
      XDomainWrapper wrapper = (XDomainWrapper) registry.getObject(
         entry(AssetEntry.Type.DOMAIN, "xmDest/xmF/xmX"), false);
      assertNotNull(wrapper);
      assertInstanceOf(Domain.class, wrapper.getDomain());
      assertEquals("xmDest/xmF/xmX", wrapper.getDomain().getDataSource());
      assertFalse(registry.containObject(entry(AssetEntry.Type.DOMAIN, "xmF/xmX")));
   }

   // a domain that can't be parsed is moved with its stored content, not written empty
   @Test
   void emFolderMoveWithADomainThatCantBeParsed() throws Exception {
      addFolder("dpF");
      addFolder("dpDest");
      addXmla("dpF/dpX");
      corrupt("dpF/dpX");
      String okey = entry(AssetEntry.Type.DOMAIN, "dpF/dpX").toIdentifier();
      String xml = "<Domain datasource=\"dpF/dpX\" version=\"10.1\" class=\"no.such.Domain\">" +
         "<Cubes></Cubes></Domain>";
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      indexedStorage.putDocument(okey, doc, XDomainWrapper.class.getName());
      registry.clearCache();
      XDomainWrapper old = (XDomainWrapper) registry.getObject(
         entry(AssetEntry.Type.DOMAIN, "dpF/dpX"), false);
      assertNotNull(old);
      assertNull(old.getDomain(), "the domain can be parsed");

      emMove("dpF", "dpDest");

      registry.clearCache();
      assertFalse(registry.containObject(entry(AssetEntry.Type.DOMAIN, "dpF/dpX")));
      String nkey = entry(AssetEntry.Type.DOMAIN, "dpDest/dpF/dpX").toIdentifier();
      Element root = indexedStorage.getDocument(nkey).getDocumentElement();
      assertNotNull(root, "the domain is stored with no content");
      assertEquals("no.such.Domain", root.getAttribute("class"));
   }

   // Bug #77691 clash data: a folder and a data source at the same path. The data source in the
   // folder is moved by renameDatasource's move of the objects under the data source.
   @Test
   void renameOfADataSourceSharingAFolderPath() throws Exception {
      addFolder("clP");
      addModel("clP/clJ");
      registry.setDataSource(source("clP"), false);

      registry.renameDatasource("clP", "clQ");

      registry.clearCache();
      assertEquals("clQ/clJ", registry.getDataModel("clQ/clJ").getDataSource());
      assertArrayEquals(new String[] { LM }, registry.getDataModel("clQ/clJ")
         .getLogicalModelNames());
   }

   // a failed write doesn't give the cached model at the old path the new data source
   @Test
   void failedWriteKeepsTheCachedModel() throws Exception {
      addFolder("fwF");
      addModel("fwF/fwJ");
      corrupt("fwF/fwJ");
      AssetEntry oentry = entry(AssetEntry.Type.DATA_MODEL, "fwF/fwJ");
      XDataModel cached = (XDataModel) registry.getObject(oentry, false);
      String failKey = entry(AssetEntry.Type.DATA_MODEL, "fwG/fwJ").toIdentifier();
      Field field = DataSourceRegistry.class.getDeclaredField("indexedStorage");
      field.setAccessible(true);
      IndexedStorage storage = (IndexedStorage) field.get(registry);
      IndexedStorage failing = mock(IndexedStorage.class, delegatesTo(storage));
      doAnswer(inv -> {
         if(inv.getArgument(0).equals(failKey)) {
            throw new IOException("simulated storage write failure");
         }

         storage.putXMLSerializable(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(failing).putXMLSerializable(anyString(), any());
      field.set(registry, failing);

      try {
         assertThrows(Exception.class, () -> registry.renameDataSourceFolder("fwF", "fwG"));
      }
      finally {
         field.set(registry, storage);
      }

      assertEquals("fwF/fwJ", cached.getDataSource());
      assertEquals("fwF/fwJ", registry.getDataModel("fwF/fwJ").getDataSource());
      assertModel("fwF/fwJ", null);
   }

   // the model is read from the storage, with its data source and its objects
   private void assertModel(String path, String oldPath) {
      registry.clearCache();
      XDataModel model = registry.getDataModel(path);
      assertNotNull(model, path);
      assertEquals(path, model.getDataSource(), "the data source of the model of " + path);
      assertArrayEquals(new String[] { LM }, model.getLogicalModelNames());
      assertArrayEquals(new String[] { PT }, model.getPartitionNames());
      assertArrayEquals(new String[] { VPM }, model.getVirtualPrivateModelNames());

      if(oldPath != null) {
         assertFalse(registry.containObject(entry(AssetEntry.Type.DATA_MODEL, oldPath)));
         assertEquals(0, registry.getEntries(oldPath + "/").length,
                      "objects left under " + oldPath);
      }
   }

   // a JDBC data source with a logical model, a partition and a VPM
   private void addModel(String path) throws Exception {
      registry.setDataSource(source(path), false);
      XDataModel model = registry.getDataModel(path);
      assertNotNull(model, path);
      model.addPartition(new XPartition(PT));
      XLogicalModel logicalModel = new XLogicalModel(LM);
      logicalModel.setPartition(PT);
      model.addLogicalModel(logicalModel);
      model.addVirtualPrivateModel(new VirtualPrivateModel(VPM), true);
      registry.clearCache();
      assertModel(path, null);
   }

   private void addXmla(String path) throws Exception {
      XMLADataSource dataSource = new XMLADataSource();
      dataSource.setName(path);
      dataSource.setURL("http://localhost/xmla");
      registry.setDataSource(dataSource, false);
      registry.clearCache();
      XDomainWrapper wrapper = (XDomainWrapper) registry.getObject(
         entry(AssetEntry.Type.DOMAIN, path), false);
      assertNotNull(wrapper, path);
      assertEquals(path, wrapper.getDomain().getDataSource());
   }

   // stores the data source as an empty wrapper, which can't be loaded
   private void corrupt(String path) throws Exception {
      indexedStorage.putXMLSerializable(
         entry(AssetEntry.Type.DATA_SOURCE, path).toIdentifier(), new XDataSourceWrapper());
      registry.clearCache();
      assertNull(registry.getDataSource(path));
      assertTrue(containsDataSource(path));
   }

   private void emMove(String folder, String destination) throws Exception {
      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(node(folder)))
         .destination(node(destination))
         .build();
      objectService().moveFiles(request, true, principal);
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
         .path(path)
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .build();
   }

   private boolean containsDataSource(String path) {
      return registry.containObject(entry(AssetEntry.Type.DATA_SOURCE, path));
   }

   private static AssetEntry entry(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
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
