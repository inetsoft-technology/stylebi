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
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78203: deleting a data source P left the grants of its data model objects, which have
 * their own permission resources: QUERY "lm::P" and "lm::P^__^F" of its logical models,
 * DATA_MODEL_FOLDER "P/F" of its data model folders and "P::add/F" (the folder grant of the
 * extended models of an additional connection). A data source, model or folder created later
 * with the same name got them. They are now removed with the data source, by the registry
 * (EM, folder delete) and by the portal and public API, which remove the data model first; and
 * with a removed additional connection. Only exact keys: a data source "P2" keeps its grants.
 * <p>
 * The registry, the repository and the models are the real ones. The permissions are kept in a
 * map behind a mocked security engine and provider.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceModelPermissionRemovalTest {
   private static final String URL = "jdbc:derby:memory:bug78203;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final Map<String, Permission> store = new HashMap<>();
   private MockedStatic<SecurityEngine> securityStatic;
   private SecurityEngine security;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      SecurityProvider provider = mock(SecurityProvider.class);
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
      String org = Organization.getDefaultOrganizationID();
      principal = new SRPrincipal(new IdentityID("admin", org), new IdentityID[0], new String[0],
                                  org, 1L);
   }

   @AfterEach
   void tearDown() {
      registry.clearCache();
      securityStatic.close();
   }

   // the registry delete (EM, data source folder delete, a deleted user's resources)
   @Test
   void registryDeleteRemovesModelGrants() {
      seed("RgOrders");
      seed("RgOrders2");

      registry.removeDataSource("RgOrders");

      assertEquals(List.of(), grants("RgOrders"));
      assertEquals(keys("RgOrders2"), grants("RgOrders2"));
   }

   // the portal and public API delete, which removes the data model before the data source
   @Test
   void dataModelFirstDeleteRemovesModelGrants() throws Exception {
      seed("DmOrders");
      seed("DmOrders2");

      assertTrue(repository.removeDataSource("DmOrders", true));

      assertEquals(List.of(), grants("DmOrders"));
      assertEquals(keys("DmOrders2"), grants("DmOrders2"));
   }

   // a data source created again with the same models and folder doesn't get the old grants
   @Test
   void recreatedDataSourceHasNoOldGrants() throws Exception {
      seed("ReOrders");

      assertTrue(repository.removeDataSource("ReOrders", true));
      parent("ReOrders");
      XDataModel model = model("ReOrders");
      model.addFolder("F");
      registry.setDataModel(model);
      model.addLogicalModel(logicalModel("LMR", null));
      model.addLogicalModel(logicalModel("LMF", "F"));
      registry.clearCache();

      assertEquals(List.of(), grants("ReOrders"));
   }

   // a removed additional connection loses its folder grant, the data source keeps its own
   @Test
   void additionalConnectionDeleteRemovesItsFolderGrantOnly() {
      seed("AcOrders");

      registry.removeDataSource("AcOrders/add");

      List<String> expected = new ArrayList<>(keys("AcOrders"));
      expected.remove(key(ResourceType.DATA_SOURCE, "AcOrders::add"));
      expected.remove(key(ResourceType.DATA_MODEL_FOLDER, "AcOrders::add/F"));
      assertEquals(expected, grants("AcOrders"));
   }

   // a save in the portal editor that drops an additional connection removes its folder grant
   @Test
   void editorDropOfAdditionalConnectionRemovesItsFolderGrant() throws Exception {
      String p = "EdOrders";
      DataSourcePathClashTest.Tab77691 parent = new DataSourcePathClashTest.Tab77691();
      parent.setName(p);
      registry.setDataSource(parent, false);
      parent = (DataSourcePathClashTest.Tab77691) registry.getDataSource(p);

      for(String name : List.of(p + "k1", p + "r1")) {
         DataSourcePathClashTest.Tab77691 child = new DataSourcePathClashTest.Tab77691();
         child.setName(name);
         parent.addDatasource(child);
      }

      XDataModel model = model(p);
      model.addFolder("F");
      registry.setDataModel(model);
      registry.clearCache();
      grant(ResourceType.DATA_SOURCE, p + "::" + p + "k1");
      grant(ResourceType.DATA_SOURCE, p + "::" + p + "r1");
      grant(ResourceType.DATA_MODEL_FOLDER, p + "::" + p + "k1/F");
      grant(ResourceType.DATA_MODEL_FOLDER, p + "::" + p + "r1/F");

      DataSourceDefinition definition = tabularDefinition(p);
      definition.setParentPath("");
      definition.setAdditionalConnections(new ArrayList<>(List.of(tabularDefinition(p + "k1"))));
      new DatasourcesService(repository, security, mock(DataSourceStatusService.class), registry,
                             Config.getConfig())
         .updateDataSource(p, definition, principal);

      assertEquals(List.of(key(ResourceType.DATA_MODEL_FOLDER, p + "::" + p + "k1/F"),
                           key(ResourceType.DATA_SOURCE, p + "::" + p + "k1")),
                   grants(p));
   }

   // removing a single logical model doesn't go through the data source delete, the other
   // grants of the data source stay
   @Test
   void singleModelDeleteKeepsOtherGrants() {
      seed("SmOrders");

      model("SmOrders").removeLogicalModel("LMR");
      registry.clearCache();

      assertNull(registry.getDataModel("SmOrders").getLogicalModel("LMR"));
      assertEquals(keys("SmOrders"), grants("SmOrders"));
   }

   // a JDBC data source p with additional connection "add", data model folder F, logical models
   // LMR and a/b at the root and LMF in F, and a grant of each
   private void seed(String p) {
      parent(p);
      JDBCDataSource dataSource = (JDBCDataSource) registry.getDataSource(p);
      dataSource.addDatasource(source("add"));
      XDataModel model = model(p);
      model.addFolder("F");
      registry.setDataModel(model);
      model.addLogicalModel(logicalModel("LMR", null));
      model.addLogicalModel(logicalModel("a/b", null));
      model.addLogicalModel(logicalModel("LMF", "F"));
      registry.clearCache();

      assertNotNull(registry.getDataModel(p).getLogicalModel("LMF"), "not seeded: " + p);
      assertTrue(List.of(registry.getDataModel(p).getLogicalModelNames()).contains("a/b"),
                 "not seeded: " + p);

      for(String key : keys(p)) {
         int index = key.indexOf('|');
         grant(ResourceType.valueOf(key.substring(0, index)), key.substring(index + 1));
      }
   }

   // the grants seeded for a data source p, sorted
   private static List<String> keys(String p) {
      List<String> keys = new ArrayList<>(List.of(
         key(ResourceType.DATA_SOURCE, p),
         key(ResourceType.DATA_SOURCE, p + "::add"),
         key(ResourceType.QUERY, "LMR::" + p),
         key(ResourceType.QUERY, "a/b::" + p),
         key(ResourceType.QUERY, "LMF::" + p + "^__^F"),
         key(ResourceType.DATA_MODEL_FOLDER, p + "/F"),
         key(ResourceType.DATA_MODEL_FOLDER, p + "::add/F")));
      Collections.sort(keys);
      return keys;
   }

   // the stored grants of a data source p and its children, by their exact keys, sorted
   private List<String> grants(String p) {
      List<String> result = new ArrayList<>();

      for(String key : store.keySet()) {
         String resource = key.substring(key.indexOf('|') + 1);

         if(resource.equals(p) || resource.startsWith(p + "::") || resource.startsWith(p + "/") ||
            resource.endsWith("::" + p) || resource.endsWith("::" + p + "^__^F"))
         {
            result.add(key);
         }
      }

      Collections.sort(result);
      return result;
   }

   private void parent(String path) {
      registry.setDataSource(source(path), false);
   }

   private XDataModel model(String ds) {
      XDataModel model = registry.getDataModel(ds);

      if(model == null) {
         model = new XDataModel(ds);
         registry.setDataModel(model);
         model = registry.getDataModel(ds);
      }

      return model;
   }

   private static XLogicalModel logicalModel(String name, String folder) {
      XLogicalModel model = new XLogicalModel(name);
      model.setFolder(folder);
      return model;
   }

   private static DataSourceDefinition tabularDefinition(String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType("Bug77691Tab");
      definition.setName(name);
      definition.setTabularView(new inetsoft.uql.tabular.TabularView());
      return definition;
   }

   private static String key(ResourceType type, String path) {
      return type + "|" + path;
   }

   private void grant(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(key(type, path), permission);
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
