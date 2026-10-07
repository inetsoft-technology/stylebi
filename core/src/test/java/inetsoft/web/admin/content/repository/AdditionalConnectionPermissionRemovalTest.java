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
import inetsoft.report.XSessionManager;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDomainWrapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularView;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.xmla.Cube;
import inetsoft.uql.xmla.Domain;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.util.dep.XDataSourceAsset;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.data.DataSourceXmlaDefinition;
import inetsoft.web.portal.model.database.cube.xmla.CubeModel;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77671: the permission of an additional connection is stored under "parent::name". When
 * the additional connection, its parent or the parent's folder is removed, or the additional
 * connection is dropped or renamed in the editor of its parent, the old key must be removed, so
 * that an additional connection created later with that name doesn't get the old grants. The
 * registry and the repository are the real ones, the permissions are kept in a map.
 * <p>
 * Bug #77700: the permission of a removed data source itself, "folder/name", is removed by the
 * registry, so that the data sources of subfolders, removed by the registry alone, don't keep it.
 * <p>
 * Bug #77843: a portal editor save that renames the parent keeps the additional connections, their
 * settings and permissions as the save leaves them, and the XMLA editor saves the domain under the
 * new name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  AdditionalConnectionPermissionRemovalTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AdditionalConnectionPermissionRemovalTest {
   private static final String URL = "jdbc:derby:memory:bug77671;create=true";
   private static final String TABULAR = "permRemovalTabular";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private Config config;
   private final Map<String, Permission> store = new HashMap<>();
   private SecurityProvider provider;
   private MockedStatic<SecurityEngine> securityStatic;
   private RepositoryObjectService objectService;
   private DatabaseDatasourcesService databaseService;
   private DatasourcesService tabularService;
   private XmlaDatasourceService xmlaService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      store.clear();
      provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      doAnswer(inv -> store.remove(inv.<String>getArgument(1)))
         .when(provider).removePermission(eq(ResourceType.DATA_SOURCE), anyString());
      // a non-virtual engine over the map, which the registry also gets from getSecurity()
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(security.getSecurityProvider()).thenReturn(provider);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.getPermission(eq(ResourceType.DATA_SOURCE), anyString()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(1)));
      doAnswer(inv -> store.put(inv.getArgument(1), inv.getArgument(2)))
         .when(security).setPermission(eq(ResourceType.DATA_SOURCE), anyString(), any());
      doAnswer(inv -> store.remove(inv.<String>getArgument(1)))
         .when(security).removePermission(eq(ResourceType.DATA_SOURCE), anyString());
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(security);

      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), provider,
         mock(ResourcePermissionService.class), repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      tabularService = new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry, config);
      xmlaService = new XmlaDatasourceService(
         repository, security, mock(DataSourceStatusService.class), registry, config,
         mock(DependencyHandler.class), mock(XSessionManager.class));
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      securityStatic.close();
   }

   // EM Delete of an additional connection removes its permission, and one created again with
   // its name doesn't get it. The other additional connection keeps its permission.
   @Test
   void emDeleteOfAnAdditionalConnection() throws Exception {
      addParent("emDel", "emDelOld", "emDelKeep");
      grant("emDel::emDelOld");
      Permission kept = grant("emDel::emDelKeep");

      emDelete("emDel/emDelOld");

      assertChildren("emDel", "emDelKeep");
      assertNull(perm("emDel::emDelOld"));
      assertSame(kept, perm("emDel::emDelKeep"));

      addAdditional("emDel", "emDelOld");
      assertEquals("emDel::emDelOld",
                   ResourcePermissionService.getDataSourceResourceName("emDel/emDelOld", registry));
      assertNull(perm("emDel::emDelOld"), "the recreated additional connection got the old grant");
   }

   // EM Delete of a top-level parent removes the permissions of its additional connections
   @Test
   void emDeleteOfAParent() throws Exception {
      addParent("pDel", "pDelA", "pDelB");
      grant("pDel::pDelA");
      grant("pDel::pDelB");

      emDelete("pDel");

      assertNull(registry.getDataSource("pDel"));
      assertNull(perm("pDel::pDelA"));
      assertNull(perm("pDel::pDelB"));
   }

   // EM Delete of a parent in a folder removes "folder/parent::name"
   @Test
   void emDeleteOfAParentInAFolder() throws Exception {
      addFolder("pfDelF");
      addParent("pfDelF/pfDel", "pfDelA");
      grant("pfDelF/pfDel::pfDelA");

      emDelete("pfDelF/pfDel");

      assertNull(registry.getDataSource("pfDelF/pfDel"));
      assertNull(perm("pfDelF/pfDel::pfDelA"));
   }

   // EM Delete of a folder removes the permissions of the additional connections of its sources
   @Test
   void emDeleteOfAFolder() throws Exception {
      addFolder("fDel");
      addParent("fDel/fDelP", "fDelA", "fDelB");
      grant("fDel/fDelP::fDelA");
      grant("fDel/fDelP::fDelB");

      assertNull(objectService.removeDataSourceFolder("fDel", true, principal));

      assertNull(registry.getDataSourceFolder("fDel"));
      assertNull(perm("fDel/fDelP::fDelA"));
      assertNull(perm("fDel/fDelP::fDelB"));
   }

   // portal Delete of an additional connection and of a parent
   @Test
   void portalDelete() throws Exception {
      addParent("ptDel", "ptDelA", "ptDelB");
      grant("ptDel::ptDelA");
      grant("ptDel::ptDelB");

      tabularService.deleteDataSource("ptDel/ptDelA", "ptDelA", true);

      assertChildren("ptDel", "ptDelB");
      assertNull(perm("ptDel::ptDelA"));
      assertNotNull(perm("ptDel::ptDelB"));

      tabularService.deleteDataSource("ptDel", "ptDel", true);

      assertNull(perm("ptDel::ptDelB"));
   }

   // a folder delete through XEngine.removeDataSourceFolder, which removes the data model of
   // each data source, and so its additional connections, before the data source
   @Test
   void repositoryFolderDelete() throws Exception {
      addFolder("rfDel");
      addParent("rfDel/rfDelP", "rfDelA", "rfDelB");
      grant("rfDel/rfDelP::rfDelA");
      grant("rfDel/rfDelP::rfDelB");

      assertTrue(repository.removeDataSourceFolder("rfDel"));

      assertNull(registry.getDataSourceFolder("rfDel"));
      assertNull(perm("rfDel/rfDelP::rfDelA"));
      assertNull(perm("rfDel/rfDelP::rfDelB"));
   }

   // a provider without permissions of its own is left alone
   @Test
   void virtualProviderIsLeftAlone() throws Exception {
      addParent("vDel", "vDelA");
      Permission permission = grant("vDel::vDelA");
      when(provider.isVirtual()).thenReturn(true);

      emDelete("vDel/vDelA");

      assertChildren("vDel");
      assertSame(permission, perm("vDel::vDelA"));
   }

   // IdentityService.removeSelfResource removes a data source by a permission resource
   @Test
   void removeByAPermissionResourceDoesNotThrow() throws Exception {
      addParent("resDel", "resDelA");
      Permission permission = grant("resDel::resDelA");

      assertDoesNotThrow(() -> registry.removeDataSource("resDel::resDelA"));

      assertChildren("resDel", "resDelA");
      assertSame(permission, perm("resDel::resDelA"));
   }

   // JDBC editor: an additional connection dropped from the parent loses its permission, a kept
   // one keeps its permission
   @Test
   void jdbcEditorDrop() throws Exception {
      addParent("edDrop", "edDropOld", "edDropKeep");
      grant("edDrop::edDropOld");
      Permission kept = grant("edDrop::edDropKeep");

      saveJdbc("edDrop", "edDrop", additional("edDrop", "edDropKeep", "edDropKeep"));

      assertChildren("edDrop", "edDropKeep");
      assertNull(perm("edDrop::edDropOld"));
      assertSame(kept, perm("edDrop::edDropKeep"));
   }

   // JDBC editor: a renamed additional connection takes its permission with it
   @Test
   void jdbcEditorRename() throws Exception {
      addParent("edRn", "edRnOld", "edRnKeep");
      Permission old = grant("edRn::edRnOld");
      Permission kept = grant("edRn::edRnKeep");

      saveJdbc("edRn", "edRn", additional("edRn", "edRnOld", "edRnNew"),
               additional("edRn", "edRnKeep", "edRnKeep"));

      assertChildren("edRn", "edRnNew", "edRnKeep");
      assertSame(old, perm("edRn::edRnNew"));
      assertNull(perm("edRn::edRnOld"));
      assertSame(kept, perm("edRn::edRnKeep"));
   }

   // JDBC editor: two additional connections that swap names keep their own permissions
   @Test
   void jdbcEditorSwap() throws Exception {
      addParent("edSw", "edSwA", "edSwB");
      Permission a = grant("edSw::edSwA");
      Permission b = grant("edSw::edSwB");

      saveJdbc("edSw", "edSw", additional("edSw", "edSwA", "edSwB"),
               additional("edSw", "edSwB", "edSwA"));

      assertChildren("edSw", "edSwA", "edSwB");
      assertSame(a, perm("edSw::edSwB"));
      assertSame(b, perm("edSw::edSwA"));
   }

   // JDBC editor: the parent renamed P -> Q in the same save as a child rename, a drop and a
   // kept child. The parent rename has moved the keys to Q before the children are saved.
   @Test
   void jdbcEditorRenameWithParentRename() throws Exception {
      addParent("edPP", "edPA", "edPC", "edPK");
      Permission a = grant("edPP::edPA");
      grant("edPP::edPC");
      Permission k = grant("edPP::edPK");

      saveJdbc("edPP", "edPQ", additional("edPP", "edPA", "edPB"),
               additional("edPP", "edPK", "edPK"));

      assertNull(registry.getDataSource("edPP"));
      assertChildren("edPQ", "edPB", "edPK");
      assertSame(a, perm("edPQ::edPB"));
      assertSame(k, perm("edPQ::edPK"));
      assertNull(perm("edPQ::edPA"));
      assertNull(perm("edPQ::edPC"));
      assertNoKeys("edPP::");
   }

   // JDBC editor of the additional connection itself: a rename takes its permission with it
   @Test
   void jdbcDirectEditRename() throws Exception {
      addParent("edDir", "edDirOld", "edDirKeep");
      Permission old = grant("edDir::edDirOld");

      JDBCDataSource source = parent("edDir").getDataSource("edDirOld");
      DatabaseDefinition definition = edit(source);
      definition.setName("edDirNew");
      assertNull(databaseService.saveDatabase(
         "edDir/edDirOld",
         DataSourceSettingsModel.builder().uploadEnabled(false).dataSource(definition).build(),
         ActionRecord.ACTION_NAME_EDIT, principal));

      assertChildren("edDir", "edDirNew", "edDirKeep");
      assertSame(old, perm("edDir::edDirNew"));
      assertNull(perm("edDir::edDirOld"));
   }

   // JDBC editor: an additional connection deleted and added again with its name in one save is
   // a new one, sent without an old name, and doesn't get the old permission
   @Test
   void jdbcEditorDeleteAndAddAgain() throws Exception {
      addParent("edRe", "edReA", "edReKeep");
      grant("edRe::edReA");
      Permission kept = grant("edRe::edReKeep");
      DatabaseDefinition added = edit(source("edReA"));
      added.setOldName(null);

      saveJdbc("edRe", "edRe", added, additional("edRe", "edReKeep", "edReKeep"));

      assertChildren("edRe", "edReA", "edReKeep");
      assertNull(perm("edRe::edReA"));
      assertSame(kept, perm("edRe::edReKeep"));
   }

   // JDBC editor: the additional connections loaded the way the editor loads them and saved
   // unchanged keep their permissions
   @Test
   void jdbcEditorUnchangedSave() throws Exception {
      addParent("edSame", "edSameA", "edSameB");
      Permission a = grant("edSame::edSameA");
      Permission b = grant("edSame::edSameB");
      DatabaseDefinition[] loaded =
         databaseService.getAdditionalDatabaseDefinition("edSame", principal);
      assertEquals(2, loaded.length);

      saveJdbc("edSame", "edSame", loaded);

      assertChildren("edSame", "edSameA", "edSameB");
      assertSame(a, perm("edSame::edSameA"));
      assertSame(b, perm("edSame::edSameB"));
   }

   // tabular editor, top-level parent: a rename and a drop in one save. The rename used to
   // build the key "/P::old", so the renamed additional connection lost its permission.
   @Test
   void tabularEditorRenameAndDrop() throws Exception {
      addTabularParent("", "tbP", "tbA", "tbC", "tbK");
      Permission a = grant("tbP::tbA");
      grant("tbP::tbC");
      Permission k = grant("tbP::tbK");

      saveTabular("tbP", tabular("", null, "tbP"), "tbP",
                  tabular("", "tbA", "tbB"), tabular("", null, "tbK"));

      assertChildren("tbP", "tbB", "tbK");
      assertSame(a, perm("tbP::tbB"));
      assertSame(k, perm("tbP::tbK"));
      assertNull(perm("tbP::tbA"));
      assertNull(perm("tbP::tbC"));
      assertNull(perm("/tbP::tbB"));
   }

   // tabular editor: the parent renamed P -> Q and a child dropped in one save. The dropped
   // child's permission is still under P, where the parent rename would carry it to Q.
   @Test
   void tabularEditorDropWithParentRename() throws Exception {
      addTabularParent("", "tpP", "tpD", "tpK");
      grant("tpP::tpD");
      Permission k = grant("tpP::tpK");

      saveTabular("tpP", tabular("", "tpP", "tpQ"), "tpQ", tabular("", null, "tpK"));

      // Bug #77843, the parent rename moved the dropped child back under Q
      assertChildren("tpQ", "tpK");
      assertNull(registry.getDataSource("tpP"));
      assertNull(perm("tpP::tpD"));
      assertNull(perm("tpQ::tpD"));
      assertSame(k, perm("tpQ::tpK"));
      assertNoKeys("tpP::");
   }

   // tabular editor: b deleted and a, which has no permission of its own, renamed to b in one
   // save. The renamed connection doesn't get the permission of the deleted one.
   @Test
   void tabularEditorDeleteAndRenameOntoItsName() throws Exception {
      addTabularParent("", "tdP", "tdA", "tdB");
      grant("tdP::tdB");

      saveTabular("tdP", tabular("", null, "tdP"), "tdP", tabular("", "tdA", "tdB"));

      assertChildren("tdP", "tdB");
      assertNull(perm("tdP::tdB"));
      assertNull(perm("tdP::tdA"));
   }

   // tabular editor, parent in a folder: a rename takes its permission with it
   @Test
   void tabularEditorRenameInAFolder() throws Exception {
      addFolder("tfF");
      addTabularParent("tfF", "tfP", "tfA", "tfK");
      Permission a = grant("tfF/tfP::tfA");
      Permission k = grant("tfF/tfP::tfK");

      saveTabular("tfP", tabular("tfF", null, "tfP"), "tfP",
                  tabular("tfF", "tfA", "tfB"), tabular("tfF", null, "tfK"));

      assertChildren("tfF/tfP", "tfB", "tfK");
      assertSame(a, perm("tfF/tfP::tfB"));
      assertSame(k, perm("tfF/tfP::tfK"));
      assertNull(perm("tfF/tfP::tfA"));
   }

   // tabular editor: the parent renamed P -> Q and a child a -> b in one save
   @Test
   void tabularEditorRenameWithParentRename() throws Exception {
      addTabularParent("", "tqP", "tqA");
      Permission a = grant("tqP::tqA");

      saveTabular("tqP", tabular("", "tqP", "tqQ"), "tqQ",
                  tabular("", "tqA", "tqB"));

      // Bug #77843, the parent rename moved the renamed child back under Q by its old name
      assertChildren("tqQ", "tqB");
      assertNull(registry.getDataSource("tqP"));
      assertSame(a, perm("tqQ::tqB"));
      assertNull(perm("tqP::tqA"));
      assertNull(perm("tqP::tqB"));
      assertNull(perm("tqQ::tqA"));
   }

   // Bug #77843, the reported case: the parent renamed P -> Q, a child kept and a child renamed
   // r1 -> r2 in one save. Q has exactly the kept and the renamed child, with their permissions,
   // and nothing is left under P.
   @Test
   void tabularEditorRenameAndKeepWithParentRename() throws Exception {
      addTabularParent("", "rkP", "rkK", "rkR1");
      Permission k = grant("rkP::rkK");
      Permission r = grant("rkP::rkR1");

      saveTabular("rkP", tabular("", "rkP", "rkQ"), "rkQ",
                  tabular("", null, "rkK"), tabular("", "rkR1", "rkR2"));

      assertChildren("rkQ", "rkK", "rkR2");
      assertNull(registry.getDataSource("rkP"));
      assertFalse(registry.containObject(dataSourceEntry("rkP/rkK")));
      assertFalse(registry.containObject(dataSourceEntry("rkP/rkR1")));
      assertSame(k, perm("rkQ::rkK"));
      assertSame(r, perm("rkQ::rkR2"));
      assertNull(perm("rkQ::rkR1"));
      assertNoKeys("rkP::");
   }

   // Bug #77843, an edit of a kept child in a save that renames the parent is kept, and so is
   // what the definition doesn't carry, as in a save without a parent rename
   @Test
   void tabularEditorEditWithParentRename() throws Exception {
      addTabularParent("", "reP");
      addTabularChild("reP", "reK", "old description", "carol");
      Permission k = grant("reP::reK");
      DataSourceDefinition kept = tabular("", null, "reK");
      kept.setDescription("new description");

      saveTabular("reP", tabular("", "reP", "reQ"), "reQ", kept);

      assertChildren("reQ", "reK");
      assertEquals("new description", child("reQ", "reK").getDescription());
      assertEquals("carol", child("reQ", "reK").getCreatedBy());
      assertSame(k, perm("reQ::reK"));
      assertNoKeys("reP::");
   }

   // Bug #77843, the control without a parent rename: the same edit is kept
   @Test
   void tabularEditorEditWithoutParentRename() throws Exception {
      addTabularParent("", "rcP");
      addTabularChild("rcP", "rcK", "old description", "carol");
      DataSourceDefinition kept = tabular("", null, "rcK");
      kept.setDescription("new description");

      saveTabular("rcP", tabular("", null, "rcP"), "rcP", kept);

      assertChildren("rcP", "rcK");
      assertEquals("new description", child("rcP", "rcK").getDescription());
      assertEquals("carol", child("rcP", "rcK").getCreatedBy());
   }

   // Bug #77843, the parent renamed P -> Q, b dropped and a renamed to b in one save. Q has one
   // child b, which has a's settings and a's permission, not those of the dropped b.
   @Test
   void tabularEditorRenameOntoADroppedNameWithParentRename() throws Exception {
      addTabularParent("", "rdP");
      addTabularChild("rdP", "rdA", "a", null);
      addTabularChild("rdP", "rdB", "b", null);
      Permission a = grant("rdP::rdA");
      grant("rdP::rdB");
      DataSourceDefinition renamed = tabular("", "rdA", "rdB");
      renamed.setDescription("a");

      saveTabular("rdP", tabular("", "rdP", "rdQ"), "rdQ", renamed);

      assertChildren("rdQ", "rdB");
      assertEquals("a", child("rdQ", "rdB").getDescription());
      assertSame(a, perm("rdQ::rdB"));
      assertNull(perm("rdQ::rdA"));
      assertNoKeys("rdP::");
   }

   // Bug #77843, renamed P -> Q with r1 -> r2, then back Q -> P with r2 -> r1 and a new
   // description. The second save keeps its edit and doesn't bring r2 back.
   @Test
   void tabularEditorRenameBackWithParentRename() throws Exception {
      addTabularParent("", "rbP");
      addTabularChild("rbP", "rbR1", "first", null);
      Permission r = grant("rbP::rbR1");
      DataSourceDefinition first = tabular("", "rbR1", "rbR2");
      first.setDescription("first");

      saveTabular("rbP", tabular("", "rbP", "rbQ"), "rbQ", first);

      DataSourceDefinition second = tabular("", "rbR2", "rbR1");
      second.setDescription("second");
      saveTabular("rbQ", tabular("", "rbQ", "rbP"), "rbP", second);

      assertChildren("rbP", "rbR1");
      assertEquals("second", child("rbP", "rbR1").getDescription());
      assertNull(registry.getDataSource("rbQ"));
      assertSame(r, perm("rbP::rbR1"));
      assertNull(perm("rbP::rbR2"));
      assertNoKeys("rbQ::");
   }

   // Bug #77843, the parent in a folder renamed F/P -> F/Q with a child renamed in the same save
   @Test
   void tabularEditorRenameWithParentRenameInAFolder() throws Exception {
      addFolder("rfF");
      addTabularParent("rfF", "rfP", "rfR1", "rfK");
      Permission r = grant("rfF/rfP::rfR1");
      Permission k = grant("rfF/rfP::rfK");

      saveTabular("rfP", tabular("rfF", "rfP", "rfQ"), "rfQ",
                  tabular("rfF", "rfR1", "rfR2"), tabular("rfF", null, "rfK"));

      assertChildren("rfF/rfQ", "rfK", "rfR2");
      assertNull(registry.getDataSource("rfF/rfP"));
      assertSame(r, perm("rfF/rfQ::rfR2"));
      assertSame(k, perm("rfF/rfQ::rfK"));
      assertNull(perm("rfF/rfQ::rfR1"));
      assertNoKeys("rfF/rfP::");
   }

   // Bug #77843, the portal XMLA editor renamed X -> X2 with an edited cube list. The domain is
   // saved under X2 with the edit, none is left under X, and a new XMLA data source X gets an
   // empty domain, not the old one.
   @Test
   void xmlaEditorRenameMovesTheDomain() throws Exception {
      addXmla("xrX");
      DataSourceXmlaDefinition definition = xmlaService.getDataSourceModel("xrX", principal);
      assertEquals("xrX", definition.getDomain().getDatasource());
      editCubes(definition, "xrEdited");
      definition.setName("xrX2");

      xmlaService.updateDataSource("xrX", definition, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("xrX"));
      assertNotNull(registry.getDataSource("xrX2"));
      assertFalse(registry.containObject(domainEntry("xrX")), "a domain is left under xrX");
      assertDomain("xrX2", "xrEdited");

      addXmla("xrX");
      assertDomain("xrX");
   }

   // Bug #77843, the same in a folder
   @Test
   void xmlaEditorRenameInAFolderMovesTheDomain() throws Exception {
      addFolder("xfF");
      addXmla("xfF/xfX");
      DataSourceXmlaDefinition definition = xmlaService.getDataSourceModel("xfF/xfX", principal);
      editCubes(definition, "xfEdited");
      definition.setName("xfX2");

      xmlaService.updateDataSource("xfX", definition, principal);

      registry.clearCache();
      assertNull(registry.getDataSource("xfF/xfX"));
      assertFalse(registry.containObject(domainEntry("xfF/xfX")),
                  "a domain is left under xfF/xfX");
      assertDomain("xfF/xfX2", "xfEdited");
   }

   // Bug #77843, the control without a rename: the edit is saved under the same name
   @Test
   void xmlaEditorSaveWithoutRenameKeepsTheDomain() throws Exception {
      addXmla("xsX");
      DataSourceXmlaDefinition definition = xmlaService.getDataSourceModel("xsX", principal);
      editCubes(definition, "xsEdited");

      xmlaService.updateDataSource("xsX", definition, principal);

      registry.clearCache();
      assertDomain("xsX", "xsEdited");
   }

   // the symptom through the real permission check: the parent is restricted to bob, alice is
   // granted READ on an additional connection only. After the additional connection is removed
   // and created again with its name, alice must not read it and bob must, through the parent.
   @Test
   void recreatedAdditionalConnectionIsNotGrantedThroughTheRealCheck() throws Exception {
      DefaultCheckPermissionStrategy strategy = new DefaultCheckPermissionStrategy(checkProvider());

      // EM Delete
      addParent("chEm", "chEmOld");
      restrictToBob("chEm");
      grant("chEm::chEmOld");
      assertTrue(canRead(strategy, "alice", "chEm::chEmOld"), "the harness grants alice");
      assertFalse(canRead(strategy, "alice", "chEm::chEmFresh"), "the parent denies alice");
      emDelete("chEm/chEmOld");
      addAdditional("chEm", "chEmOld");
      assertRecreatedIsNotGranted(strategy, "chEm::chEmOld");

      // JDBC editor drop
      addParent("chJd", "chJdOld", "chJdKeep");
      restrictToBob("chJd");
      grant("chJd::chJdOld");
      saveJdbc("chJd", "chJd", additional("chJd", "chJdKeep", "chJdKeep"));
      addAdditional("chJd", "chJdOld");
      assertRecreatedIsNotGranted(strategy, "chJd::chJdOld");
      assertFalse(canRead(strategy, "alice", "chJd::chJdKeep"));
      assertTrue(canRead(strategy, "bob", "chJd::chJdKeep"));

      // tabular editor drop
      addTabularParent("", "chTb", "chTbOld", "chTbKeep");
      restrictToBob("chTb");
      grant("chTb::chTbOld");
      saveTabular("chTb", tabular("", null, "chTb"), "chTb", tabular("", null, "chTbKeep"));
      addTabularAdditional("chTb", "chTbOld");
      assertRecreatedIsNotGranted(strategy, "chTb::chTbOld");

      // portal Delete of the parent (XEngine.removeDataSource), then the parent and the
      // additional connection created again with their names
      addParent("chPt", "chPtOld");
      grant("chPt::chPtOld");
      tabularService.deleteDataSource("chPt", "chPt", true);
      addParent("chPt", "chPtOld");
      restrictToBob("chPt");
      assertRecreatedIsNotGranted(strategy, "chPt::chPtOld");

      // EM Delete of a folder, then the parent and the additional connection created again
      addFolder("chF");
      addParent("chF/chFP", "chFOld");
      grant("chF/chFP::chFOld");
      assertNull(objectService.removeDataSourceFolder("chF", true, principal));
      addFolder("chF");
      addParent("chF/chFP", "chFOld");
      restrictToBob("chF/chFP");
      assertRecreatedIsNotGranted(strategy, "chF/chFP::chFOld");
   }

   // removing an additional connection or a parent doesn't remove the permissions of
   // other data sources, including ones whose names share a prefix or that have the same
   // additional connection name under another parent
   @Test
   void unrelatedPermissionsAreUntouched() throws Exception {
      addParent("unP", "unA", "unAB");
      addParent("unPX", "unA");
      addFolder("unF");
      addParent("unF/unP", "unA");
      addParent("unOther");
      Map<String, Permission> others = new HashMap<>();

      for(String key : List.of("unPX", "unPX::unA", "unF/unP", "unF/unP::unA", "unOther",
                               "unP::unAB", "unP:unA"))
      {
         others.put(key, grant(key));
      }

      grant("unP::unA");

      // EM Delete of an additional connection
      emDelete("unP/unA");
      assertNull(perm("unP::unA"));
      assertKept(others);

      // portal Delete of the parent through XEngine
      others.remove("unP::unAB");
      tabularService.deleteDataSource("unP", "unP", true);
      assertNull(perm("unP::unAB"));
      assertKept(others);

      // EM Delete of another parent
      emDelete("unPX");
      assertNull(perm("unPX::unA"));
      others.remove("unPX::unA");
      others.remove("unPX");
      assertKept(others);
   }

   // Bug #77700: EM or portal Delete of a folder removes the permissions of the data sources in
   // its subfolders, which are removed by the registry alone, and data sources created again at
   // their paths don't get them
   @Test
   void folderDeleteRemovesThePermissionsOfNestedDataSources() throws Exception {
      addFolder("ndF");
      addFolder("ndF/ndG");
      addParent("ndF/ndP");
      addParent("ndF/ndG/ndQ", "ndA");
      grant("ndF/ndP");
      grant("ndF/ndG/ndQ");
      grant("ndF/ndG/ndQ::ndA");

      assertNull(objectService.removeDataSourceFolder("ndF", true, principal));

      assertNull(registry.getDataSource("ndF/ndG/ndQ"));
      assertNull(perm("ndF/ndP"));
      assertNull(perm("ndF/ndG/ndQ"));
      assertNull(perm("ndF/ndG/ndQ::ndA"));

      addFolder("ndF");
      addFolder("ndF/ndG");
      addParent("ndF/ndP");
      addParent("ndF/ndG/ndQ", "ndA");
      assertNull(perm("ndF/ndP"), "the recreated data source got the old grant");
      assertNull(perm("ndF/ndG/ndQ"), "the recreated nested data source got the old grant");
   }

   // Bug #77700: a folder delete through XEngine.removeDataSourceFolder removes the permissions
   // of its data sources
   @Test
   void repositoryFolderDeleteRemovesThePermissionsOfItsDataSources() throws Exception {
      addFolder("nrF");
      addFolder("nrF/nrG");
      addParent("nrF/nrP");
      addParent("nrF/nrG/nrQ");
      grant("nrF/nrP");
      grant("nrF/nrG/nrQ");

      assertTrue(repository.removeDataSourceFolder("nrF"));

      assertNull(registry.getDataSourceFolder("nrF"));
      assertNull(perm("nrF/nrP"));
      assertNull(perm("nrF/nrG/nrQ"));
   }

   // Bug #77700: a permission resource of an additional connection, as
   // IdentityService.removeSelfResource passes it, has no data source at its path. Neither the
   // permission of the additional connection nor that of its parent is removed.
   @Test
   void removeByAPermissionResourceKeepsTheParentPermission() throws Exception {
      addParent("resOwn", "resOwnA");
      Permission parent = grant("resOwn");
      Permission child = grant("resOwn::resOwnA");

      assertDoesNotThrow(() -> registry.removeDataSource("resOwn::resOwnA"));

      assertChildren("resOwn", "resOwnA");
      assertSame(parent, perm("resOwn"));
      assertSame(child, perm("resOwn::resOwnA"));
   }

   // Bug #77700: EM Delete of an additional connection by its registry path, "P/add" or
   // "F/P/add", has no data source entry at that path, so the parent keeps its own permission
   @Test
   void additionalConnectionDeleteKeepsTheParentPermission() throws Exception {
      addFolder("acF");

      for(String path : List.of("acP", "acF/acP")) {
         addParent(path, "acA", "acB");
         Permission parent = grant(path);
         Permission kept = grant(path + "::acB");
         grant(path + "::acA");

         emDelete(path + "/acA");

         assertChildren(path, "acB");
         assertNull(perm(path + "::acA"), path);
         assertSame(parent, perm(path), path);
         assertSame(kept, perm(path + "::acB"), path);
      }
   }

   // Bug #77700: an import that overwrites a data source with its own export, at the top level
   // and in a folder, keeps the permissions of the data source and of its additional connections
   @Test
   void overwriteImportKeepsThePermissions() throws Exception {
      addFolder("ovF");

      for(String path : List.of("ovO", "ovF/ovO")) {
         addParent(path, "ovA", "ovB");
         Permission own = grant(path);
         Permission a = grant(path + "::ovA");
         Permission b = grant(path + "::ovB");
         String exported = export(path);

         importDataSource(path, exported, true);

         assertChildren(path, "ovA", "ovB");
         assertSame(own, perm(path), path);
         assertSame(a, perm(path + "::ovA"), path);
         assertSame(b, perm(path + "::ovB"), path);
      }
   }

   // Bug #77700: an import of R/S where R is a data source. The import either replaces R with a
   // folder, which must remove the permission of R, or is refused (Bug #77702), which must keep
   // R and its permission.
   @Test
   void importUnderADataSourceKeepsNoPermissionWithoutTheDataSource() throws Exception {
      addParent("imR", "imRA");
      Permission own = grant("imR");
      addParent("imSrc");
      String exported = export("imSrc").replace("\"imSrc\"", "\"imR/imS\"");
      registry.removeDataSource("imSrc");

      try {
         importDataSource("imR/imS", exported, false);
      }
      catch(MessageException ignore) {
         // refused
      }

      registry.clearCache();

      if(registry.getDataSource("imR") != null) {
         assertSame(own, perm("imR"));
      }
      else {
         assertNotNull(registry.getDataSourceFolder("imR"));
         assertNull(perm("imR"), "the permission of the replaced data source was kept");
         assertNull(perm("imR::imRA"));
      }
   }

   // the export of a data source, as XDataSourceAsset writes it
   private String export(String path) throws Exception {
      Method method = XDataSourceAsset.class.getDeclaredMethod("writeXML", PrintWriter.class);
      method.setAccessible(true);
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      method.invoke(new XDataSourceAsset(path), writer);
      writer.flush();
      return buffer.toString();
   }

   private void importDataSource(String path, String xml, boolean overwrite) throws Exception {
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwrite);
      new XDataSourceAsset(path).parseContent(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), config, true, true);
      registry.clearCache();
   }

   private void assertKept(Map<String, Permission> permissions) {
      permissions.forEach((key, permission) ->
         assertSame(permission, perm(key), () -> "the permission of " + key + " was changed"));
   }

   private void assertRecreatedIsNotGranted(DefaultCheckPermissionStrategy strategy,
                                            String resource)
   {
      assertFalse(canRead(strategy, "alice", resource),
                  "alice reads the recreated " + resource + " by the old grant");
      assertTrue(canRead(strategy, "bob", resource),
                 "bob doesn't read the recreated " + resource + " through its parent");
   }

   private static boolean canRead(DefaultCheckPermissionStrategy strategy, String user,
                                  String resource)
   {
      String org = Organization.getDefaultOrganizationID();
      // a principal without roles or groups has no identity in the check
      SRPrincipal principal = new SRPrincipal(new IdentityID(user, org),
                                              new IdentityID[] { new IdentityID("Everyone", org) },
                                              new String[0], org,
                                              Tool.getSecureRandom().nextLong());
      return strategy.checkPermission(principal, ResourceType.DATA_SOURCE, resource,
                                      ResourceAction.READ);
   }

   // grants READ on the data source to bob only, with "grant all" edited, so the check doesn't
   // fall back above it
   private void restrictToBob(String path) {
      String org = Organization.getDefaultOrganizationID();
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ,
                               Set.of(new Permission.PermissionIdentity("bob", org)));
      permission.updateGrantAllByOrg(org, true);
      store.put(path, permission);
   }

   // a provider for the real DefaultCheckPermissionStrategy that reads the permissions of the map
   private SecurityProvider checkProvider() {
      String org = Organization.getDefaultOrganizationID();
      SecurityProvider checkProvider = mock(SecurityProvider.class);
      lenient().when(checkProvider.getUser(any())).thenReturn(null);
      lenient().when(checkProvider.getGroup(any())).thenReturn(null);
      lenient().when(checkProvider.getRole(any())).thenReturn(null);
      lenient().when(checkProvider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(checkProvider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(checkProvider.getAllGroups(any(IdentityID[].class)))
         .thenReturn(new IdentityID[0]);
      lenient().when(checkProvider.isSystemAdministratorRole(any())).thenReturn(false);
      lenient().when(checkProvider.isOrgAdministratorRole(any())).thenReturn(false);
      lenient().when(checkProvider.getAllRoles(any(IdentityID[].class)))
         .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(checkProvider.getOrgNameFromID(anyString())).thenReturn(org);
      lenient().when(checkProvider.getPermission(any(ResourceType.class), anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0) == ResourceType.DATA_SOURCE ?
            store.get(inv.<String>getArgument(1)) : null);
      lenient().when(checkProvider.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> inv.getArgument(0) == ResourceType.DATA_SOURCE ?
            store.get(inv.<String>getArgument(1)) : null);
      lenient().when(checkProvider.getPermission(any(ResourceType.class), any(IdentityID.class)))
         .thenReturn(null);
      lenient().when(checkProvider.getAuthenticationProvider())
         .thenReturn(mock(AuthenticationProvider.class));
      Organization organization = mock(Organization.class);
      lenient().when(organization.getOrganizationID()).thenReturn(org);
      lenient().when(organization.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(checkProvider.getOrganization(anyString())).thenReturn(organization);
      return checkProvider;
   }

   private void addTabularAdditional(String parentPath, String name) {
      TestTabularDataSource parent = (TestTabularDataSource) registry.getDataSource(parentPath);
      TestTabularDataSource child = new TestTabularDataSource();
      child.setName(name);
      parent.addDatasource(child);
   }

   private void emDelete(String path) throws Exception {
      Method method = RepositoryObjectService.class.getDeclaredMethod(
         "deleteDataSource", String.class, boolean.class, Principal.class);
      method.setAccessible(true);
      assertNull(method.invoke(objectService, path, true, principal));
   }

   private void saveJdbc(String path, String newName, DatabaseDefinition... additionals)
      throws Exception
   {
      DatabaseDefinition definition = edit(parent(path));
      definition.setName(newName);
      assertNull(databaseService.saveDatabase(
         path, DataSourceSettingsModel.builder().uploadEnabled(false).dataSource(definition)
            .additionalDataSources(additionals).build(),
         ActionRecord.ACTION_NAME_EDIT, principal));
   }

   private DatabaseDefinition additional(String parent, String oldName, String newName) {
      DatabaseDefinition definition = edit(parent(parent).getDataSource(oldName));
      definition.setOldName(oldName);
      definition.setName(newName);
      return definition;
   }

   private void saveTabular(String name, DataSourceDefinition definition, String newPath,
                            DataSourceDefinition... additionals) throws Exception
   {
      definition.setAdditionalConnections(new ArrayList<>(List.of(additionals)));
      tabularService.updateDataSource(name, definition, principal);
      registry.clearCache();
      String newFullName = definition.getParentPath().isEmpty() ?
         newPath : definition.getParentPath() + "/" + newPath;
      assertNotNull(registry.getDataSource(newFullName), newFullName);
   }

   // a tabular definition as the editor sends it. Kept additional connections have no old name.
   private static DataSourceDefinition tabular(String parentPath, String oldName, String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType(TABULAR);
      definition.setParentPath(parentPath);
      definition.setName(name);
      definition.setOldName(oldName);
      definition.setTabularView(new TabularView());
      return definition;
   }

   // the names start with a prefix of their own, since every test of the class saves to the
   // same storage
   private void addParent(String path, String... additionals) {
      registry.setDataSource(source(path), false);

      for(String name : additionals) {
         addAdditional(path, name);
      }
   }

   private void addAdditional(String path, String name) {
      parent(path).addDatasource(source(name));
   }

   private void addTabularParent(String folder, String name, String... additionals) {
      String path = folder.isEmpty() ? name : folder + "/" + name;
      TestTabularDataSource parent = new TestTabularDataSource();
      parent.setName(path);
      registry.setDataSource(parent, false);
      parent = (TestTabularDataSource) registry.getDataSource(path);

      for(String additional : additionals) {
         TestTabularDataSource child = new TestTabularDataSource();
         child.setName(additional);
         parent.addDatasource(child);
      }
   }

   private void addTabularChild(String parentPath, String name, String description,
                                String createdBy)
   {
      TestTabularDataSource parent = (TestTabularDataSource) registry.getDataSource(parentPath);
      TestTabularDataSource child = new TestTabularDataSource();
      child.setName(name);
      child.setDescription(description);
      child.setCreatedBy(createdBy);
      parent.addDatasource(child);
   }

   // an additional connection read from the storage
   private TestTabularDataSource child(String parentPath, String name) {
      registry.clearCache();
      TestTabularDataSource child =
         ((TestTabularDataSource) registry.getDataSource(parentPath)).getDataSource(name);
      assertNotNull(child, parentPath + "/" + name);
      return child;
   }

   // an XMLA data source, which the registry gives an empty domain
   private void addXmla(String path) {
      XMLADataSource dataSource = new XMLADataSource();
      dataSource.setName(path);
      dataSource.setURL("http://localhost/xmla");
      registry.setDataSource(dataSource, false);
      registry.clearCache();
      assertTrue(registry.containObject(domainEntry(path)), path);
   }

   // a cube list edited in the editor
   private static void editCubes(DataSourceXmlaDefinition definition, String cube) {
      CubeModel cubeModel = new CubeModel();
      cubeModel.setName(cube);
      definition.getDomain().setCubes(new ArrayList<>(List.of(cubeModel)));
   }

   // the stored domain of a data source, read from the storage
   private void assertDomain(String path, String... cubes) {
      registry.clearCache();
      XDomainWrapper wrapper = (XDomainWrapper) registry.getObject(domainEntry(path), false);
      assertNotNull(wrapper, "no domain under " + path);
      Domain domain = (Domain) wrapper.getDomain();
      assertEquals(path, domain.getDataSource());
      List<String> names = new ArrayList<>();

      for(Enumeration<?> e = domain.getCubes(); e.hasMoreElements(); ) {
         names.add(((Cube) e.nextElement()).getName());
      }

      assertEquals(List.of(cubes), names, "cubes of the domain of " + path);
   }

   private static AssetEntry domainEntry(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DOMAIN, path, null);
   }

   private static AssetEntry dataSourceEntry(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null);
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private JDBCDataSource parent(String path) {
      return (JDBCDataSource) registry.getDataSource(path);
   }

   private Permission grant(String resource) {
      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, Set.of(new Permission.PermissionIdentity(
         "alice", Organization.getDefaultOrganizationID())));
      store.put(resource, permission);
      return permission;
   }

   private Permission perm(String resource) {
      return store.get(resource);
   }

   private void assertNoKeys(String prefix) {
      assertTrue(store.keySet().stream().noneMatch(key -> key.startsWith(prefix)),
                 () -> "keys under " + prefix + ": " + store.keySet());
   }

   private void assertChildren(String parentPath, String... names) {
      // read from the storage, not from instances cached by the save
      registry.clearCache();
      String[] expected = names.clone();
      Arrays.sort(expected);
      String[] children =
         ((inetsoft.uql.AdditionalConnectionDataSource<?>) registry.getDataSource(parentPath))
            .getDataSourceNames();
      Arrays.sort(children);
      assertArrayEquals(expected, children, "additional connections of " + parentPath);
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

   private static DatabaseDefinition edit(JDBCDataSource dataSource) {
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }

   public static class TestTabularDataSource extends TabularDataSource<TestTabularDataSource> {
      public TestTabularDataSource() {
         super(TABULAR, TestTabularDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
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

      // knows the tabular data source type of this test
      @Bean
      @Primary
      public Config testConfig(Plugins plugins) throws Exception {
         Config config = spy(new Config(plugins));
         doReturn(TestTabularDataSource.class.getName()).when(config).getDataSourceClass(TABULAR);
         doReturn(TestTabularDataSource.class).when(config)
            .getClass(TABULAR, TestTabularDataSource.class.getName());
         return config;
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
