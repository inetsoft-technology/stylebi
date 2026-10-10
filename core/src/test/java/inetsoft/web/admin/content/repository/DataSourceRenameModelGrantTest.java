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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.DataSourceRenameException;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.uql.tabular.TabularView;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78223, a data source rename or move moves the grants of its data model objects: QUERY
 * "model::P" of a model at the root, "a/b::P" of a model with a "/" in its name and
 * "model::P^__^F" of a model in a folder, and DATA_MODEL_FOLDER "P/F" and "P::add/F". Run with
 * the production security engine and file permission store. Axis: key type x rename path
 * (registry rename of a top-level and an in-folder data source, a move to another folder, a
 * folder rename and move including a member that can't be loaded, the JDBC editor of the parent
 * and of an additional connection). Each asserts the grant is absent at the old key and present
 * at the new one, and that the grants of a sibling data source "P2" are untouched.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceRenameModelGrantTest {
   private static final String URL = "jdbc:derby:memory:bug78223;create=true";
   private static final String TABULAR = "folderMoveTabular";
   private static final String ORG = Organization.getDefaultOrganizationID();

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private Config config;
   private SecurityEngine engine;
   private SecurityProvider provider;
   private DatabaseDatasourcesService databaseService;
   private DatasourcesService tabularService;
   private Principal admin;
   private final List<String[]> written = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(new FileAuthenticationProvider()));
      authcChain.saveConfiguration();
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();
      engine = SecurityEngine.getSecurity();
      engine.init();
      provider = engine.getSecurityProvider();
      registry.init();

      SecurityEngine adminEngine =
         mock(SecurityEngine.class, org.mockito.AdditionalAnswers.delegatesTo(engine));
      doReturn(true).when(adminEngine).checkPermission(any(), any(), anyString(), any());
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         adminEngine, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      tabularService = new DatasourcesService(
         repository, adminEngine, mock(DataSourceStatusService.class), registry, config);
      admin = new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0], new String[0], ORG,
                              Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      for(String[] key : written) {
         provider.removePermission(ResourceType.valueOf(key[0]), key[1]);
      }

      written.clear();
      registry.clearCache();
      SreeEnv.remove("security.enabled");
   }

   @Test
   void registryRenameOfATopLevelDataSource() throws Exception {
      seed("RnA");
      seed("RnA2");

      registry.renameDatasource("RnA", "RnAx");

      assertMoved("RnA", "RnAx");
      assertUntouched("RnA2");
   }

   @Test
   void registryRenameOfADataSourceInAFolder() throws Exception {
      addFolder("RnF");
      seed("RnF/RnB");
      seed("RnF/RnB2");

      registry.renameDatasource("RnF/RnB", "RnF/RnBx");

      assertMoved("RnF/RnB", "RnF/RnBx");
      assertUntouched("RnF/RnB2");
   }

   @Test
   void registryMoveOfADataSourceToAnotherFolder() throws Exception {
      addFolder("MvF");
      addFolder("MvG");
      seed("MvF/MvC");
      seed("MvF/MvC2");

      registry.renameDatasource("MvF/MvC", "MvG/MvC");

      assertMoved("MvF/MvC", "MvG/MvC");
      assertUntouched("MvF/MvC2");
   }

   // a grant already at the new key is overwritten by the grant of the old key, as the grant of
   // the data source itself is
   @Test
   void movedGrantOverwritesAStaleOneAtTheNewKey() throws Exception {
      seed("OvA");
      grantTo(ResourceType.QUERY, "LMF::OvAx^__^F", "bob");

      registry.renameDatasource("OvA", "OvAx");

      assertMoved("OvA", "OvAx");
      Permission moved = provider.getPermission(ResourceType.QUERY, "LMF::OvAx^__^F");
      assertTrue(moved.getUserGrants(ResourceAction.READ).stream()
                    .anyMatch(id -> "alice".equals(id.getName())));
      assertFalse(moved.getUserGrants(ResourceAction.READ).stream()
                     .anyMatch(id -> "bob".equals(id.getName())));
   }

   // the parent editor, a rename of the parent, its additional connection kept
   @Test
   void jdbcEditorRenameOfTheParent() throws Exception {
      seed("EdA");
      seed("EdA2");

      saveJdbc("EdA", "EdAx", additional("EdA", "EdAAd", "EdAAd"));

      assertMoved("EdA", "EdAx");
      assertUntouched("EdA2");
   }

   // the parent and its additional connection are renamed in one save: the registry rename moves
   // "P::ad/F" to "Px::ad/F", the editor then to "Px::ad2/F"
   @Test
   void jdbcEditorRenameOfTheParentAndTheAdditionalConnection() throws Exception {
      seed("CbA");
      seed("CbA2");

      saveJdbc("CbA", "CbAx", additional("CbA", "CbAAd", "CbAAd2"));

      registry.clearCache();
      assertNotNull(((JDBCDataSource) registry.getDataSource("CbAx")).getDataSource("CbAAd2"));
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "CbA::CbAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "CbAx::CbAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "CbAx::CbAAd2/F", true);
      assertGrant(ResourceType.DATA_SOURCE, "CbAx::CbAAd2", true);
      assertGrant(ResourceType.DATA_SOURCE, "CbAx::CbAAd", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "CbAx/F", true);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "CbA/F", false);
      assertGrant(ResourceType.QUERY, "LMF::CbAx^__^F", true);
      assertUntouched("CbA2");
   }

   // the parent editor, an additional connection renamed, the parent kept
   @Test
   void jdbcEditorRenameOfTheAdditionalConnection() throws Exception {
      seed("AdA");
      seed("AdA2");

      saveJdbc("AdA", "AdA", additional("AdA", "AdAAd", "AdAAd2"));

      assertGrant(ResourceType.DATA_MODEL_FOLDER, "AdA::AdAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "AdA::AdAAd2/F", true);
      assertGrant(ResourceType.DATA_SOURCE, "AdA::AdAAd2", true);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "AdA/F", true);
      assertGrant(ResourceType.QUERY, "LMF::AdA^__^F", true);
      assertUntouched("AdA2");
   }

   // the editor of the additional connection itself
   @Test
   void jdbcDirectEditOfTheAdditionalConnection() throws Exception {
      seed("DrA");
      seed("DrA2");
      DatabaseDefinition definition =
         edit(((JDBCDataSource) registry.getDataSource("DrA")).getDataSource("DrAAd"));
      definition.setName("DrAAd2");

      assertNull(databaseService.saveDatabase(
         "DrA/DrAAd",
         DataSourceSettingsModel.builder().uploadEnabled(false).dataSource(definition).build(),
         ActionRecord.ACTION_NAME_EDIT, admin));

      assertGrant(ResourceType.DATA_MODEL_FOLDER, "DrA::DrAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "DrA::DrAAd2/F", true);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "DrA/F", true);
      assertUntouched("DrA2");
   }

   // two additional connections swap names in one save: each keeps its own folder grant
   @Test
   void jdbcEditorSwapOfAdditionalConnections() throws Exception {
      seed("SwA");
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource("SwA");
      parent.addDatasource(source("SwAOther"));
      registry.setDataSource(parent, false);
      registry.clearCache();
      grantTo(ResourceType.DATA_MODEL_FOLDER, "SwA::SwAOther/F", "carol");

      saveJdbc("SwA", "SwA", additional("SwA", "SwAAd", "SwAOther"),
               additional("SwA", "SwAOther", "SwAAd"));

      assertTrue(canRead("alice", ResourceType.DATA_MODEL_FOLDER, "SwA::SwAOther/F"));
      assertFalse(canRead("carol", ResourceType.DATA_MODEL_FOLDER, "SwA::SwAOther/F"));
      assertTrue(canRead("carol", ResourceType.DATA_MODEL_FOLDER, "SwA::SwAAd/F"));
      assertFalse(canRead("alice", ResourceType.DATA_MODEL_FOLDER, "SwA::SwAAd/F"));
   }

   // the portal editor of a tabular data source (the XMLA editor shares its save): an additional
   // connection is renamed, the parent kept
   @Test
   void portalEditorRenameOfTheAdditionalConnection() throws Exception {
      seedTabular("PtA");
      seedTabular("PtA2");

      saveTabular("PtA", tabular(null, "PtA"), "PtA", tabular("PtAAd", "PtAAd2"));

      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PtA::PtAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PtA::PtAAd2/F", true);
      assertGrant(ResourceType.DATA_SOURCE, "PtA::PtAAd", false);
      assertGrant(ResourceType.DATA_SOURCE, "PtA::PtAAd2", true);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PtA/F", true);
      assertGrant(ResourceType.QUERY, "LMF::PtA^__^F", true);
      assertUntouched("PtA2");
   }

   // the parent and its additional connection are renamed in one save
   @Test
   void portalEditorRenameOfTheParentAndTheAdditionalConnection() throws Exception {
      seedTabular("PcA");
      seedTabular("PcA2");

      saveTabular("PcA", tabular("PcA", "PcAx"), "PcAx", tabular("PcAAd", "PcAAd2"));

      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PcA::PcAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PcAx::PcAAd/F", false);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PcAx::PcAAd2/F", true);
      assertGrant(ResourceType.DATA_SOURCE, "PcAx::PcAAd2", true);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PcAx/F", true);
      assertGrant(ResourceType.DATA_MODEL_FOLDER, "PcA/F", false);
      assertGrant(ResourceType.QUERY, "LMF::PcAx^__^F", true);
      assertUntouched("PcA2");
   }

   // folder rename with loadable members
   @Test
   void folderRenameOfLoadableMembers() throws Exception {
      addFolder("FrF");
      seed("FrF/FrP");
      seed("FrF/FrP2");

      repository.updateDataSourceFolder(
         new DataSourceFolder("FrG", LocalDateTime.now(), null), "FrF");

      assertMoved("FrF/FrP", "FrG/FrP");
      assertMoved("FrF/FrP2", "FrG/FrP2");
   }

   // folder move with a member whose connector isn't installed, which renameDatasource skips
   @Test
   void folderMoveWithAMemberThatCantBeLoaded() throws Exception {
      addFolder("UnF");
      addFolder("UnDest");
      seedTabular("UnF/UnP");
      seed("UnF/UnJ");
      doReturn(null).when(config).getDataSourceClass(TABULAR);

      try {
         registry.clearCache();
         assertNull(registry.getDataSource("UnF/UnP"), "the connector is still installed");
         registry.renameDataSourceFolder("UnF", "UnDest/UnF");
      }
      finally {
         doReturn(DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource.class
                     .getName()).when(config).getDataSourceClass(TABULAR);
         registry.clearCache();
      }

      assertMoved("UnF/UnP", "UnDest/UnF/UnP");
      assertMoved("UnF/UnJ", "UnDest/UnF/UnJ");
   }

   // a failure to read the models of the data source stops the rename before anything is moved,
   // rather than moving the data source and losing the grants of its models
   @Test
   void unreadableModelsMoveNothing() throws Exception {
      seed("RfA");
      DataSourceRegistry spy = spy(registry);
      doThrow(new IllegalStateException("not readable")).when(spy).getDataSourceEntries(
         eq("RfA"), eq("RfA/"), eq(AssetEntry.Type.LOGIC_MODEL), eq(false));

      assertThrows(DataSourceRenameException.class, () -> spy.renameDatasource("RfA", "RfAx"));

      registry.clearCache();
      assertNotNull(registry.getDataSource("RfA"));
      assertNull(registry.getDataSource("RfAx"));

      for(String[] key : keys("RfA", adName("RfA"))) {
         assertGrant(ResourceType.valueOf(key[0]), key[1], true);
      }

      for(String[] key : keys("RfAx", adName("RfA"))) {
         assertGrant(ResourceType.valueOf(key[0]), key[1], false);
      }
   }

   // the stored data model can't be read: a rename that went on would leave the grants of its
   // folders and of the models in them at the old name
   @Test
   void unreadableDataModelMovesNothing() throws Exception {
      seed("RdA");
      DataSourceRegistry spy = spy(registry);
      doReturn(null).when(spy).getObject(
         argThat(entry -> entry != null && entry.getType() == AssetEntry.Type.DATA_MODEL &&
                          "RdA".equals(entry.getPath())), eq(false));

      assertThrows(DataSourceRenameException.class, () -> spy.renameDatasource("RdA", "RdAx"));

      registry.clearCache();
      assertNotNull(registry.getDataSource("RdA"));
      assertNull(registry.getDataSource("RdAx"));
      assertNotNull(registry.getDataModel("RdA"));

      for(String[] key : keys("RdA", adName("RdA"))) {
         assertGrant(ResourceType.valueOf(key[0]), key[1], true);
      }

      for(String[] key : keys("RdAx", adName("RdA"))) {
         assertGrant(ResourceType.valueOf(key[0]), key[1], false);
      }
   }

   // a rename of a logical model inside a data model keeps the grant it kept before
   @Test
   void renameOfALogicalModelStillMovesTheRootModelGrant() throws Exception {
      seed("LmA");
      XDataModel model = registry.getDataModel("LmA");
      model.renameLogicalModel("LMR", "LMRx");
      registry.setDataModel(model);
      registry.clearCache();

      assertGrant(ResourceType.QUERY, "LMR::LmA", false);
      assertGrant(ResourceType.QUERY, "LMRx::LmA", true);
   }

   private void assertMoved(String oldPath, String newPath) {
      List<Executable> checks = new ArrayList<>();
      String ad = adName(oldPath);
      List<String[]> oldKeys = keys(oldPath, ad);
      List<String[]> newKeys = keys(newPath, ad);

      for(int i = 0; i < oldKeys.size(); i++) {
         ResourceType type = ResourceType.valueOf(oldKeys.get(i)[0]);
         String oldKey = oldKeys.get(i)[1];
         String newKey = newKeys.get(i)[1];
         checks.add(() -> assertNull(provider.getPermission(type, oldKey),
                                     "left at the old key " + type + " " + oldKey));
         checks.add(() -> assertNotNull(provider.getPermission(type, newKey),
                                        "missing at the new key " + type + " " + newKey));
         checks.add(() -> assertTrue(canRead("alice", type, newKey),
                                     "alice can't read " + type + " " + newKey));
      }

      assertAll(checks);
   }

   private void assertUntouched(String path) {
      for(String[] key : keys(path, adName(path))) {
         assertGrant(ResourceType.valueOf(key[0]), key[1], true);
      }
   }

   private void assertGrant(ResourceType type, String resource, boolean present) {
      if(present) {
         assertNotNull(provider.getPermission(type, resource), "missing " + type + " " + resource);
      }
      else {
         assertNull(provider.getPermission(type, resource), "left " + type + " " + resource);
      }
   }

   // the grants of the data model objects, and the grant of the additional connection
   private static List<String[]> keys(String p, String ad) {
      return List.of(
         new String[] { "QUERY", "LMR::" + p },
         new String[] { "QUERY", "a/b::" + p },
         new String[] { "QUERY", "LMF::" + p + "^__^F" },
         new String[] { "DATA_MODEL_FOLDER", p + "/F" },
         new String[] { "DATA_MODEL_FOLDER", p + "::" + ad + "/F" },
         new String[] { "DATA_SOURCE", p + "::" + ad });
   }

   // unique among the data sources of the storage, which checks the names of additional
   // connections for duplicates
   private static String adName(String path) {
      return path.substring(path.lastIndexOf('/') + 1) + "Ad";
   }

   private void seed(String p) {
      registry.setDataSource(source(p), false);
      JDBCDataSource dataSource = (JDBCDataSource) registry.getDataSource(p);
      dataSource.addDatasource(source(adName(p)));
      registry.setDataSource(dataSource, false);
      seedModels(p);
      assertNotNull(((JDBCDataSource) registry.getDataSource(p)).getDataSource(adName(p)),
                    "not created: " + p + "::" + adName(p));
   }

   private void seedTabular(String p) {
      DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource dataSource =
         new DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource();
      dataSource.setName(p);
      registry.setDataSource(dataSource, false);
      dataSource = (DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource)
         registry.getDataSource(p);
      DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource child =
         new DataSourceFolderMoveAdditionalConnectionTest.TestTabularDataSource();
      child.setName(adName(p));
      dataSource.addDatasource(child);
      registry.setDataSource(dataSource, false);
      seedModels(p);
   }

   // data model folder F, logical models LMR and "a/b" at the root and LMF in F, a grant of alice
   // at each key
   private void seedModels(String p) {
      XDataModel model = registry.getDataModel(p);

      if(model == null) {
         registry.setDataModel(new XDataModel(p));
         model = registry.getDataModel(p);
      }

      model.addFolder("F");
      registry.setDataModel(model);
      model.addLogicalModel(logicalModel("LMR", null));
      model.addLogicalModel(logicalModel("a/b", null));
      model.addLogicalModel(logicalModel("LMF", "F"));
      registry.clearCache();
      assertNotNull(registry.getDataModel(p).getLogicalModel("LMF"), "not created: " + p);

      for(String[] key : keys(p, adName(p))) {
         grantTo(ResourceType.valueOf(key[0]), key[1], "alice");
      }
   }

   private void saveJdbc(String path, String newName, DatabaseDefinition... additionals)
      throws Exception
   {
      DatabaseDefinition definition = edit((JDBCDataSource) registry.getDataSource(path));
      definition.setName(newName.substring(newName.lastIndexOf('/') + 1));
      assertNull(databaseService.saveDatabase(
         path, DataSourceSettingsModel.builder().uploadEnabled(false).dataSource(definition)
            .additionalDataSources(additionals).build(),
         ActionRecord.ACTION_NAME_EDIT, admin));
      registry.clearCache();
   }

   private DatabaseDefinition additional(String parent, String oldName, String newName) {
      DatabaseDefinition definition =
         edit(((JDBCDataSource) registry.getDataSource(parent)).getDataSource(oldName));
      definition.setOldName(oldName);
      definition.setName(newName);
      return definition;
   }

   private void saveTabular(String path, DataSourceDefinition definition, String newPath,
                            DataSourceDefinition... additionals) throws Exception
   {
      definition.setAdditionalConnections(new ArrayList<>(List.of(additionals)));
      tabularService.updateDataSource(path, definition, admin);
      registry.clearCache();
      assertNotNull(registry.getDataSource(newPath), newPath);
   }

   // a tabular definition as the editor sends it, with its old name if it is renamed
   private static DataSourceDefinition tabular(String oldName, String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType(TABULAR);
      definition.setParentPath("");
      definition.setName(name);
      definition.setOldName(oldName);
      definition.setTabularView(new TabularView());
      return definition;
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private boolean canRead(String user, ResourceType type, String resource) {
      SRPrincipal principal = new SRPrincipal(new IdentityID(user, ORG),
                                              new IdentityID[] { new IdentityID("Everyone", ORG) },
                                              new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      return provider.checkPermission(principal, type, resource, ResourceAction.READ);
   }

   private void grantTo(ResourceType type, String resource, String user) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ,
                               Set.of(new Permission.PermissionIdentity(user, ORG)));
      permission.updateGrantAllByOrg(ORG, true);
      provider.setPermission(type, resource, permission);
      written.add(new String[] { type.name(), resource });
   }

   private static XLogicalModel logicalModel(String name, String folder) {
      XLogicalModel model = new XLogicalModel(name);
      model.setFolder(folder);
      return model;
   }

   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
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
