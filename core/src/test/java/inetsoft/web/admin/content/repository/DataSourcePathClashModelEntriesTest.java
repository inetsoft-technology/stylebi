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
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import inetsoft.uql.xmla.Domain;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.util.dep.XDataSourceAsset;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.model.database.LogicalModel;
import inetsoft.web.portal.model.database.PhysicalModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77820: a data source P and a data source folder P at the same path (a "clash", older
 * data) share the prefix {@code P/}. A listing, delete, rename or import of P's entries must
 * take only the entries that are P's: its additional connections (bare names), its logical
 * models, physical views and VPMs (one level, or deeper if stored with that name) and their
 * extended models. A delete or rename of a data source {@code P/x} of the folder must leave P's
 * entries stored under {@code P/x/}. Without a clash nothing changes, also for model names with
 * "/" in them.
 * <p>
 * The registry, the repository and the models are the real ones. The permissions are kept in a
 * map behind a mocked security engine and provider. The state is read back from the storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourcePathClashTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourcePathClashModelEntriesTest {
   private static final String URL = "jdbc:derby:memory:bug77820;create=true";

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
      doAnswer(inv -> store.remove(key(inv.getArgument(0), inv.getArgument(1))))
         .when(provider).removePermission(any(ResourceType.class), anyString());
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

   // S3: removing P's data model takes P's side only: its additional connection and its model
   // and their grant, not the folder's data sources, their models and its subfolder
   @Test
   void removeDataModelKeepsFolderSide() {
      clash("smF", true, true);
      addLogicalModel("smF", "smLm");
      // a grant of an additional connection named like the data source smF/smFX of the folder,
      // which the data source has none of: not removed
      grant(ResourceType.DATA_SOURCE, "smF::smFX");
      List<String> before = state("smF");

      ((XEngine) repository).removeDataModel("smF");

      List<String> removed = diff(before, state("smF"));
      assertEquals(List.of("DATA_MODEL smF [smF [smLm]]", "DATA_SOURCE smF/smFAdd [smFAdd]",
                           "LOGIC_MODEL smF/smLm", "grant DATA_SOURCE|smF::smFAdd"), removed);
   }

   // S3 control: without a clash the data model is everything under P/
   @Test
   void removeDataModelNoClash() {
      addParent("snP", "snPAdd");
      grant(ResourceType.DATA_SOURCE, "snP");
      grant(ResourceType.DATA_SOURCE, "snP::snPAdd");
      addLogicalModel("snP", "snLm");
      addLogicalModel("snP", "a/b");

      ((XEngine) repository).removeDataModel("snP");

      assertEquals(List.of("DATA_SOURCE snP [snP]", "grant DATA_SOURCE|snP"), state("snP"));
   }

   // A1: P's models, view, VPM, extended model and additional connection named like the
   // folder's subfolder and data source are P's, the folder's entries under them are not
   @Test
   void entriesNamedLikeTheFolderSideArePs() {
      clash("a1F", false, false);
      addFolder("a1F/Sales");
      addSource("a1F/Sales/a1Y");
      addSource("a1F/a1X");
      addLogicalModel("a1F/a1X", "mA");
      ((JDBCDataSource) registry.getDataSource("a1F")).addDatasource(source("Sales"));
      addLogicalModel("a1F", "Sales");
      addLogicalModel("a1F", "a1X");
      registry.getDataModel("a1F").getLogicalModel("a1X")
         .addLogicalModel(new XLogicalModel("ext"), true);
      addPartition("a1F", "Sales");
      registry.getDataModel("a1F").addVirtualPrivateModel(new VirtualPrivateModel("a1X"), true);
      registry.clearCache();
      XDataModel model = registry.getDataModel("a1F");

      assertEquals(List.of("Sales", "a1X"), List.of(model.getLogicalModelNames()));
      assertEquals(List.of("Sales"), List.of(model.getPartitionNames()));
      assertEquals(List.of("a1X"), List.of(model.getVirtualPrivateModelNames()));
      assertEquals(List.of("ext"), List.of(model.getLogicalModel("a1X").getLogicalModelNames()));

      List<String> before = state("a1F");
      assertTrue(before.contains("DATA_SOURCE a1F/Sales [Sales]"), "not seeded: " + before);
      ((XEngine) repository).removeDataModel("a1F");
      List<String> removed = diff(before, state("a1F"));

      assertEquals(List.of("DATA_MODEL a1F [a1F [Sales, a1X]]", "DATA_SOURCE a1F/Sales [Sales]",
                           "DATA_SOURCE a1F/a1FAdd [a1FAdd]", "EXTENDED_LOGIC_MODEL a1F/a1X/ext", "LOGIC_MODEL a1F/Sales",
                           "LOGIC_MODEL a1F/a1X", "PARTITION a1F/Sales", "VPM a1F/a1X",
                           "grant DATA_SOURCE|a1F::a1FAdd"), removed);
   }

   // S5: an overwriting import of P keeps the domain of an XMLA data source of the folder
   @Test
   void importOverwriteKeepsFolderDataSourceDomain() throws Exception {
      clash("iaF", true, false);
      XMLADataSource olap = new XMLADataSource();
      olap.setName("iaF/iaFO");
      registry.setDataSource(olap, false);
      Domain domain = new Domain();
      domain.setDataSource("iaF/iaFO");
      registry.setDomain(domain);
      List<String> before = state("iaF");
      assertTrue(before.contains("DOMAIN iaF/iaFO"), "not seeded: " + before);

      importOverwrite("iaF");

      assertEquals(before, state("iaF"));
   }

   // S5 control: without a clash the same import changes nothing
   @Test
   void importOverwriteNoClash() throws Exception {
      addParent("inP", "inPAdd");
      List<String> before = state("inP");

      importOverwrite("inP");

      assertEquals(before, state("inP"));
   }

   // S6: removing P's logical model "lxM" keeps the extended model of data source lxF/lxM's
   // logical model, and P doesn't list lxF/lxM's model
   @Test
   void removeLogicalModelKeepsFolderDataSourceExtendedModel() {
      clash("lxF", false, false);
      addSource("lxF/lxM");
      addLogicalModel("lxF", "lxM");
      addLogicalModel("lxF/lxM", "lmA");
      registry.getDataModel("lxF/lxM").getLogicalModel("lmA")
         .addLogicalModel(new XLogicalModel("ext"), true);
      registry.clearCache();
      assertEquals(List.of("lxM"), List.of(registry.getDataModel("lxF").getLogicalModelNames()));
      assertEquals(1, registry.getDataModel("lxF").getLogicalModelCount());
      assertEquals(List.of(), List.of(
         registry.getDataModel("lxF").getLogicalModel("lxM").getLogicalModelNames()));
      List<String> before = state("lxF");

      registry.getDataModel("lxF").removeLogicalModel("lxM");

      assertEquals(List.of("DATA_MODEL lxF [lxF [lxM]]", "LOGIC_MODEL lxF/lxM"),
                   diff(before, state("lxF")));
   }

   // S6, the physical view variant
   @Test
   void removePartitionKeepsFolderDataSourceExtendedView() {
      clash("pxF", false, false);
      addSource("pxF/pxM");
      addPartition("pxF", "pxM");
      addPartition("pxF/pxM", "pA");
      registry.getDataModel("pxF/pxM").getPartition("pA").addPartition(new XPartition("pE"), false);
      registry.clearCache();
      List<String> before = state("pxF");
      assertTrue(before.contains("EXTENDED_PARTITION pxF/pxM/pA/pE"), "not seeded: " + before);

      registry.getDataModel("pxF").removePartition("pxM");

      assertEquals(List.of("PARTITION pxF/pxM"), diff(before, state("pxF")));
   }

   // S6b: renaming P's logical model "r6X" moves its extended model, and leaves data source
   // r6F/r6X's additional connection, logical model and physical view
   @Test
   void renameLogicalModelKeepsFolderDataSourceEntries() {
      clash("r6F", false, false);
      addParent("r6F/r6X", "r6XAdd");
      addLogicalModel("r6F", "r6X");
      registry.getDataModel("r6F").getLogicalModel("r6X")
         .addLogicalModel(new XLogicalModel("ext"), true);
      addLogicalModel("r6F/r6X", "mA");
      addPartition("r6F/r6X", "pA");
      List<String> before = state("r6F");

      registry.getDataModel("r6F").renameLogicalModel("r6X", "r6Y", null);

      List<String> after = state("r6F");
      assertEquals(List.of("DATA_MODEL r6F [r6F [r6X]]", "EXTENDED_LOGIC_MODEL r6F/r6X/ext",
                           "LOGIC_MODEL r6F/r6X"), diff(before, after));
      assertEquals(List.of("DATA_MODEL r6F [r6F [r6Y]]", "EXTENDED_LOGIC_MODEL r6F/r6Y/ext",
                           "LOGIC_MODEL r6F/r6Y"), diff(after, before));
   }

   // S6b, the physical view variant
   @Test
   void renamePartitionKeepsFolderDataSourceEntries() {
      clash("r7F", false, false);
      addParent("r7F/r7X", "r7XAdd");
      addPartition("r7F", "r7X");
      registry.getDataModel("r7F").getPartition("r7X").addPartition(new XPartition("pe"), false);
      addLogicalModel("r7F/r7X", "mA");
      addPartition("r7F/r7X", "pA");
      List<String> before = state("r7F");

      registry.getDataModel("r7F").renamePartition("r7X", "r7Y", null);

      List<String> after = state("r7F");
      assertEquals(List.of("EXTENDED_PARTITION r7F/r7X/pe", "PARTITION r7F/r7X"),
                   diff(before, after));
      assertEquals(List.of("EXTENDED_PARTITION r7F/r7Y/pe", "PARTITION r7F/r7Y"),
                   diff(after, before));
   }

   // S6b control: without a clash a rename moves everything under the model, as before
   @Test
   void renameLogicalModelNoClash() {
      addSource("rcP");
      addLogicalModel("rcP", "m");
      registry.getDataModel("rcP").getLogicalModel("m")
         .addLogicalModel(new XLogicalModel("ext"), true);
      List<String> before = state("rcP");

      registry.getDataModel("rcP").renameLogicalModel("m", "n", null);

      List<String> after = state("rcP");
      assertEquals(List.of("DATA_MODEL rcP [rcP [m]]", "EXTENDED_LOGIC_MODEL rcP/m/ext",
                           "LOGIC_MODEL rcP/m"), diff(before, after));
      assertEquals(List.of("DATA_MODEL rcP [rcP [n]]", "EXTENDED_LOGIC_MODEL rcP/n/ext",
                           "LOGIC_MODEL rcP/n"), diff(after, before));
   }

   // Bug #77920: the two-argument rename renames the model and keeps its description
   @Test
   void renameLogicalModelTwoArgKeepsDescription() {
      addSource("r2P");
      XLogicalModel model = new XLogicalModel("m");
      model.setDescription("desc77920");
      model("r2P").addLogicalModel(model);
      registry.clearCache();

      registry.getDataModel("r2P").renameLogicalModel("m", "n");

      assertNull(registry.getDataModel("r2P").getLogicalModel("m"));
      XLogicalModel renamed = registry.getDataModel("r2P").getLogicalModel("n");
      assertNotNull(renamed);
      assertEquals("desc77920", renamed.getDescription());
   }

   // S7: P lists only its own views, VPMs and extended views, and the portal lists them
   @Test
   void modelListingsOnClash() throws Exception {
      clash("pvF", false, false);
      addSource("pvF/pvX");
      addPartition("pvF", "pvX");
      registry.getDataModel("pvF").getPartition("pvX").addPartition(new XPartition("own"), false);
      addPartition("pvF/pvX", "pA");
      registry.getDataModel("pvF/pvX").getPartition("pA").addPartition(new XPartition("pE"), false);
      registry.getDataModel("pvF/pvX").addVirtualPrivateModel(new VirtualPrivateModel("vA"), true);
      registry.clearCache();
      XDataModel model = registry.getDataModel("pvF");

      assertEquals(List.of("pvX"), List.of(model.getPartitionNames()));
      assertEquals(1, model.getPartitionCount());
      assertEquals(List.of(), List.of(model.getVirtualPrivateModelNames()));
      assertEquals(List.of("own"), List.of(model.getPartition("pvX").getPartitionNames()));
      // the data source of the folder lists its own
      XDataModel member = registry.getDataModel("pvF/pvX");
      assertEquals(List.of("pA"), List.of(member.getPartitionNames()));
      assertEquals(List.of("vA"), List.of(member.getVirtualPrivateModelNames()));
      assertEquals(List.of("pE"), List.of(member.getPartition("pA").getPartitionNames()));

      List<PhysicalModel> views = portal().getPhysicalModels("pvF", null, principal, false);
      assertEquals(List.of("pvX"), views.stream().map(PhysicalModel::getName).toList());
      assertEquals(List.of("own"), views.get(0).getExtendViews().stream()
         .map(PhysicalModel::getName).toList());
      assertEquals(0, portal().getVpmBrowseModel("pvF", principal).getItems().length);
   }

   // R1: deleting data source rvF/rvX of the folder keeps P's extended model and view
   // stored under its path, those of P's logical model and physical view "rvX"
   @Test
   void deleteFolderDataSourceKeepsPsExtendedModels() throws Exception {
      clash("rvF", false, false);
      addSource("rvF/rvX");
      addLogicalModel("rvF", "rvX");
      registry.getDataModel("rvF").getLogicalModel("rvX")
         .addLogicalModel(new XLogicalModel("ext"), true);
      addPartition("rvF", "rvX");
      registry.getDataModel("rvF").getPartition("rvX").addPartition(new XPartition("pe"), false);
      addLogicalModel("rvF/rvX", "mA");
      List<String> before = state("rvF");

      repository.removeDataSource("rvF/rvX", true);

      assertEquals(List.of("DATA_MODEL rvF/rvX [rvF/rvX [mA]]", "DATA_SOURCE rvF/rvX [rvF/rvX]",
                           "LOGIC_MODEL rvF/rvX/mA"), diff(before, state("rvF")));
   }

   // R1b: renaming data source rnF/rnX of the folder leaves P's extended model under the old
   // path
   @Test
   void renameFolderDataSourceKeepsPsExtendedModel() throws Exception {
      clash("rnF", false, false);
      addSource("rnF/rnX");
      addLogicalModel("rnF", "rnX");
      registry.getDataModel("rnF").getLogicalModel("rnX")
         .addLogicalModel(new XLogicalModel("ext"), true);
      addLogicalModel("rnF/rnX", "mA");
      List<String> before = state("rnF");
      XDataSource ds = (XDataSource) registry.getDataSource("rnF/rnX").clone();
      ds.setName("rnF/rnZ");

      repository.updateDataSource(ds, "rnF/rnX");

      List<String> after = state("rnF");
      assertEquals(List.of("DATA_MODEL rnF/rnX [rnF/rnX [mA]]", "DATA_SOURCE rnF/rnX [rnF/rnX]",
                           "LOGIC_MODEL rnF/rnX/mA"), diff(before, after));
      assertEquals(List.of("DATA_MODEL rnF/rnZ [rnF/rnZ [mA]]", "DATA_SOURCE rnF/rnZ [rnF/rnZ]",
                           "LOGIC_MODEL rnF/rnZ/mA"), diff(after, before));
   }

   // A2: P's logical model "a2X/lm" is stored at the path of data source a2F/a2X's models and
   // is told apart by its stored name, from both sides
   @Test
   void slashNamedModelOnClashIsToldApartByItsName() throws Exception {
      clash("a2F", false, false);
      addSource("a2F/a2X");
      addSource("a2F/a2Y");
      addLogicalModel("a2F", "a2X/lm");
      addLogicalModel("a2F/a2Y", "lm");
      addLogicalModel("a2F/a2X", "own");
      registry.clearCache();

      assertEquals(List.of("a2X/lm"), List.of(registry.getDataModel("a2F").getLogicalModelNames()));
      assertEquals(List.of("own"), List.of(registry.getDataModel("a2F/a2X").getLogicalModelNames()));
      assertEquals(List.of("lm"), List.of(registry.getDataModel("a2F/a2Y").getLogicalModelNames()));
      List<String> before = state("a2F");

      repository.removeDataSource("a2F/a2X", true);

      assertEquals(List.of("DATA_MODEL a2F/a2X [a2F/a2X [own]]", "DATA_SOURCE a2F/a2X [a2F/a2X]",
                           "LOGIC_MODEL a2F/a2X/own"), diff(before, state("a2F")));
   }

   // a model under P/ that can't be read can't be told apart: a delete is refused before
   // anything is removed, and a listing leaves it out
   @Test
   void unreadableModelOnClashIsRefused() {
      clash("urF", false, false);
      addSource("urF/urX");
      addLogicalModel("urF", "mine");
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                        "urF/urX/bad", null),
                         new DataSourceFolder("bad", LocalDateTime.now(), null));
      registry.clearCache();
      List<String> before = state("urF");
      assertTrue(before.contains("LOGIC_MODEL urF/urX/bad"), "not seeded: " + before);

      assertEquals(List.of("mine"), List.of(registry.getDataModel("urF").getLogicalModelNames()));
      assertThrows(MessageException.class, () -> ((XEngine) repository).removeDataModel("urF"));
      assertEquals(before, state("urF"));
   }

   // r1 important-1 (a): the EM delete of folder udF together with data source udF, with a
   // model that can't be read under data source udF/udX of the folder. It is deleted completely,
   // never partly (it stopped after deleting udF/udA)
   @Test
   void emDeleteTogetherWithUnreadableModel() throws Exception {
      seedUnreadableMember("udF");

      Throwable thrown = null;

      try {
         emService().removeDataSourceFolder("udF", true, principal, true);
      }
      catch(Throwable e) {
         thrown = e;
      }

      assertNull(thrown, "the delete was refused or failed: " + thrown + ", left " + state("udF"));
      assertEquals(List.of(), state("udF"));
   }

   // r1 important-1 (a), the registry's own delete-together
   @Test
   void registryDeleteTogetherWithUnreadableModel() {
      seedUnreadableMember("rdF");

      registry.removeDataSourceFolder("rdF", true);

      assertEquals(List.of(), state("rdF"));
   }

   // r1 important-1 (a), a folder above a clash deleted through XEngine, which deletes the clash
   // folder together with its data source
   @Test
   void xengineDeleteFolderAboveClashWithUnreadableModel() throws Exception {
      addFolder("xdG");
      seedUnreadableMember("xdG/xdF");

      ((XEngine) repository).removeDataSourceFolder("xdG");

      assertEquals(List.of(), state("xdG"));
   }

   // r1 important-1 (a): deleting data source udF/udX on its own leaves the model that can't be
   // told apart (it may be data source udF's model "udX/bad") and deletes the rest
   @Test
   void deleteFolderDataSourceLeavesUnreadableModel() throws Exception {
      seedUnreadableMember("ulF");
      List<String> before = state("ulF");

      repository.removeDataSource("ulF/ulX", true);

      assertEquals(List.of("DATA_MODEL ulF/ulX [ulF/ulX [own]]", "DATA_SOURCE ulF/ulX [ulF/ulX]",
                           "LOGIC_MODEL ulF/ulX/own"), diff(before, state("ulF")));
   }

   // r1 important-1 (b): moving subfolder mvF/G of clash folder mvF out of it, with a model that
   // can't be read under data source mvF/G/y, is refused before anything is moved (it may be
   // data source mvF's model "G/y/lm"), never half done
   @Test
   void moveSubfolderOutOfClashWithUnreadableModel() {
      clash("mvF", false, false);
      addFolder("mvF/G");
      addSource("mvF/G/a");
      addSource("mvF/G/y");
      setUnreadableModel("mvF/G/y/lm");
      addFolder("mvQ");
      List<String> before = state("mvF", "mvQ");

      assertThrows(MessageException.class,
                   () -> registry.renameDataSourceFolder("mvF/G", "mvQ/G"));
      assertEquals(before, state("mvF", "mvQ"));
   }

   // verify r2: the EM and portal folder move (XEngine.updateDataSourceFolder) of subfolder G out
   // of clash folder v5F, with a model that can't be read under v5F/G/y, is refused before
   // anything is moved (it moved v5F/G/a first and then stopped)
   @Test
   void xengineMoveSubfolderOutOfClashWithUnreadableModel() {
      // the model under the first and under the last data source, so that one of the two catches
      // a check that comes too late whatever order the data sources are moved in
      for(String member : new String[] { "a", "y" }) {
         String clash = "v5" + member + "F";
         String target = "v5" + member + "Q";
         seedUnreadableSubfolder(clash, member);
         addFolder(target);
         List<String> before = state(clash, target);

         assertThrows(MessageException.class, () -> repository.updateDataSourceFolder(
            new DataSourceFolder(target + "/G", LocalDateTime.now(), null), clash + "/G"));
         assertEquals(before, state(clash, target), "model under " + member);
      }
   }

   // verify r2, the same for a rename of the subfolder inside the clash folder
   @Test
   void xengineRenameSubfolderInClashWithUnreadableModel() {
      for(String member : new String[] { "a", "y" }) {
         String clash = "v5b" + member + "F";
         seedUnreadableSubfolder(clash, member);
         List<String> before = state(clash);

         assertThrows(MessageException.class, () -> repository.updateDataSourceFolder(
            new DataSourceFolder(clash + "/H", LocalDateTime.now(), null), clash + "/G"));
         assertEquals(before, state(clash), "model under " + member);
      }
   }

   // r3: the portal data source editor renames data source peF/peX, which has an additional
   // connection, with a model that can't be read under it. Refused before anything is written,
   // by the editor's own check that the new path is not under a data source
   @Test
   void portalEditorRenameWithUnreadableModel() throws Exception {
      clash("peF", false, false);
      tabParent("peF/peX", "peAdd");
      grant(ResourceType.DATA_SOURCE, "peF/peX");
      grant(ResourceType.DATA_SOURCE, "peF/peX::peAdd");
      setUnreadableModel("peF/peX/lm");
      List<String> before = state("peF");
      assertTrue(before.contains("DATA_SOURCE peF/peX/peAdd [peAdd]"), "not seeded: " + before);

      assertThrows(MessageException.class,
                   () -> portalSave("peX", "peZ", "peF", "peAdd"));
      assertEquals(before, state("peF"));
   }

   // r3: the portal data source editor renames data source pcF, which shares its path with a
   // folder holding a data source, and has an additional connection. Refused before anything is
   // written (it wrote pcG/pcAdd and moved the pcF::pcAdd grant before the #77725 refusal)
   @Test
   void portalEditorRenameOfClashDataSource() throws Exception {
      addFolder("pcF");
      grant(ResourceType.DATA_SOURCE_FOLDER, "pcF");
      addSource("pcF/pcX");
      grant(ResourceType.DATA_SOURCE, "pcF/pcX");
      tabParent("pcF", "pcAdd");
      grant(ResourceType.DATA_SOURCE, "pcF");
      grant(ResourceType.DATA_SOURCE, "pcF::pcAdd");
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains("pcF"), "not seeded");
      List<String> before = state("pcF", "pcG");

      assertThrows(MessageException.class, () -> portalSave("pcF", "pcG", "", "pcAdd"));
      assertEquals(before, state("pcF", "pcG"));
   }


   // r2 sweep: an EM move of two data sources of clash folder emF, the second with a model that
   // can't be read, is refused before anything is moved
   @Test
   void emMoveDataSourcesOutOfClashWithUnreadableModel() throws Exception {
      seedUnreadableMember("emF");
      addFolder("emQ");
      List<String> before = state("emF", "emQ");

      assertThrows(MessageException.class, () -> emService().moveFiles(
         MoveCopyTreeNodesRequest.builder()
            .source(List.of(treeNode("emF/emA", inetsoft.sree.RepositoryEntry.DATA_SOURCE),
                            treeNode("emF/emX", inetsoft.sree.RepositoryEntry.DATA_SOURCE)))
            .destination(treeNode("emQ", inetsoft.sree.RepositoryEntry.DATA_SOURCE_FOLDER))
            .build(), true, principal));
      assertEquals(before, state("emF", "emQ"));
   }

   // moving subfolder mgF/G out of clash folder mgF leaves the clash data source's own entries
   // stored under the subfolder's path: the extended model of its model "G" and its model
   // "G/y/lm", and moves the folder's
   @Test
   void moveSubfolderOutOfClashKeepsPsEntries() {
      clash("mgF", false, false);
      addFolder("mgF/G");
      addSource("mgF/G/y");
      addLogicalModel("mgF/G/y", "own");
      addLogicalModel("mgF", "G");
      registry.getDataModel("mgF").getLogicalModel("G")
         .addLogicalModel(new XLogicalModel("ext"), true);
      addLogicalModel("mgF", "G/y/lm");
      addFolder("mgQ");
      List<String> before = state("mgF", "mgQ");

      registry.renameDataSourceFolder("mgF/G", "mgQ/G");

      List<String> after = state("mgF", "mgQ");
      assertEquals(List.of("DATA_MODEL mgF/G/y [mgF/G/y [own]]", "DATA_SOURCE mgF/G/y [mgF/G/y]",
                           "DATA_SOURCE_FOLDER mgF/G", "LOGIC_MODEL mgF/G/y/own"),
                   diff(before, after));
      assertEquals(List.of("DATA_MODEL mgQ/G/y [mgQ/G/y [own]]", "DATA_SOURCE mgQ/G/y [mgQ/G/y]",
                           "DATA_SOURCE_FOLDER mgQ/G", "LOGIC_MODEL mgQ/G/y/own"),
                   diff(after, before));
   }

   // r1 minor: a slash-named model of P at depth 2 is the data source's side in the #77725
   // guards: P may be deleted on its own, the folder may not; one that can't be read refuses
   // both, with the model wording
   @Test
   void guardsCountSlashNamedModelOnDataSourceSide() {
      addFolder("gsF");
      addSource("gsF");
      addLogicalModel("gsF", "a/b");
      registry.clearCache();

      assertDoesNotThrow(() -> registry.checkDataSourcePathClash("gsF"));
      assertThrows(MessageException.class,
                   () -> registry.checkDataSourceFolderDeletePathClash("gsF"));

      setUnreadableModel("gsF/c/d");
      MessageException e = assertThrows(MessageException.class,
                                        () -> registry.checkDataSourcePathClash("gsF"));
      assertTrue(e.getMessage().contains("gsF/c/d") && !e.getMessage().contains("connector"),
                 e.getMessage());
   }

   // no clash: model names with "/" are listed, renamed and removed as before, and the portal
   // lists such a view and VPM (it threw an NPE: the entry name is only the last part)
   @Test
   void slashNamedModelsNoClash() throws Exception {
      addSource("slP");
      addLogicalModel("slP", "a/b");
      addPartition("slP", "p/q");
      registry.getDataModel("slP").addVirtualPrivateModel(new VirtualPrivateModel("v/w"), true);
      registry.clearCache();
      XDataModel model = registry.getDataModel("slP");

      assertEquals(List.of("a/b"), List.of(model.getLogicalModelNames()));
      assertEquals(List.of("p/q"), List.of(model.getPartitionNames()));
      assertEquals(List.of("v/w"), List.of(model.getVirtualPrivateModelNames()));
      assertEquals(List.of("slP/p/q"), portal().getPhysicalModels("slP", null, principal, false)
         .stream().map(PhysicalModel::getPath).toList());
      assertEquals(1, portal().getVpmBrowseModel("slP", principal).getItems().length);

      model.renameLogicalModel("a/b", "a/c", null);
      model.renamePartition("p/q", "p/r", null);
      registry.clearCache();
      assertEquals(List.of("a/c"), List.of(registry.getDataModel("slP").getLogicalModelNames()));
      assertEquals(List.of("p/r"), List.of(registry.getDataModel("slP").getPartitionNames()));

      registry.getDataModel("slP").removeLogicalModel("a/c");
      registry.getDataModel("slP").removePartition("p/r");
      registry.clearCache();
      assertEquals(List.of("DATA_MODEL slP [slP []]", "DATA_SOURCE slP [slP]", "VPM slP/v/w"),
                   state("slP"));
   }

   // no clash: a data source in a folder is deleted and renamed with everything under it
   @Test
   void folderDataSourceNoClash() throws Exception {
      addFolder("ncF");
      addSource("ncF/ncX");
      addLogicalModel("ncF/ncX", "m");
      registry.getDataModel("ncF/ncX").getLogicalModel("m")
         .addLogicalModel(new XLogicalModel("ext"), true);
      addPartition("ncF/ncX", "p");
      XDataSource ds = (XDataSource) registry.getDataSource("ncF/ncX").clone();
      ds.setName("ncF/ncZ");

      repository.updateDataSource(ds, "ncF/ncX");

      assertEquals(List.of("DATA_MODEL ncF/ncZ [ncF/ncZ [m]]", "DATA_SOURCE ncF/ncZ [ncF/ncZ]",
                           "DATA_SOURCE_FOLDER ncF", "EXTENDED_LOGIC_MODEL ncF/ncZ/m/ext",
                           "LOGIC_MODEL ncF/ncZ/m", "PARTITION ncF/ncZ/p"), state("ncF"));

      repository.removeDataSource("ncF/ncZ", true);

      assertEquals(List.of("DATA_SOURCE_FOLDER ncF"), state("ncF"));
   }

   // nested clash: data source nP/s of folder nP is a clash itself (folder nP/s holds nP/s/y).
   // P's logical model "s" has its extended model at nP/s/e, under data source nP/s. Each data
   // source lists, renames and removes only its own entries
   @Test
   void nestedClash() throws Exception {
      clash("nP", false, false);
      addParent("nP/s", "sAdd");
      grant(ResourceType.DATA_SOURCE, "nP/s");
      grant(ResourceType.DATA_SOURCE, "nP/s::sAdd");
      addFolder("nP/s");
      addSource("nP/s/y");
      grant(ResourceType.DATA_SOURCE, "nP/s/y");
      addLogicalModel("nP", "s");
      model("nP").getLogicalModel("s").addLogicalModel(new XLogicalModel("e"), true);
      addLogicalModel("nP/s", "lm");
      model("nP/s").getLogicalModel("lm").addLogicalModel(new XLogicalModel("le"), true);
      addLogicalModel("nP/s/y", "ym");
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains("nP/s"), "not seeded: nP/s");

      assertEquals(List.of("s"), List.of(model("nP").getLogicalModelNames()));
      assertEquals(List.of("e"), List.of(model("nP").getLogicalModel("s").getLogicalModelNames()));
      assertEquals(List.of("lm"), List.of(model("nP/s").getLogicalModelNames()));
      assertEquals(List.of("le"),
                   List.of(model("nP/s").getLogicalModel("lm").getLogicalModelNames()));
      assertEquals(List.of("ym"), List.of(model("nP/s/y").getLogicalModelNames()));
      assertEquals(List.of("e"), extendedModels(portal().getLogicalModels("nP", null, principal,
                                                                          false).get(0)));
      assertEquals(List.of("le"), extendedModels(
         portal().getLogicalModels("nP/s", null, principal, false).get(0)));

      List<String> before = state("nP");
      model("nP").renameLogicalModel("s", "s2", null);
      List<String> after = state("nP");

      assertEquals(List.of("DATA_MODEL nP [nP [s]]", "EXTENDED_LOGIC_MODEL nP/s/e",
                           "LOGIC_MODEL nP/s"), diff(before, after));
      assertEquals(List.of("DATA_MODEL nP [nP [s2]]", "EXTENDED_LOGIC_MODEL nP/s2/e",
                           "LOGIC_MODEL nP/s2"), diff(after, before));
      model("nP").renameLogicalModel("s2", "s", null);

      before = state("nP");
      ((XEngine) repository).removeDataModel("nP/s");

      assertEquals(List.of("DATA_MODEL nP/s [nP/s [lm]]", "DATA_SOURCE nP/s/sAdd [sAdd]",
                           "EXTENDED_LOGIC_MODEL nP/s/lm/le", "LOGIC_MODEL nP/s/lm",
                           "grant DATA_SOURCE|nP/s::sAdd"), diff(before, state("nP")));

      before = state("nP");
      repository.removeDataSource("nP/s/y", true);

      assertEquals(List.of("DATA_MODEL nP/s/y [nP/s/y [ym]]", "DATA_SOURCE nP/s/y [nP/s/y]",
                           "LOGIC_MODEL nP/s/y/ym", "grant DATA_SOURCE|nP/s/y"),
                   diff(before, state("nP")));
   }

   // A1: P's logical model and physical view named like subfolders ("Sales", "East") are
   // renamed and removed without the subfolders' data sources and their models
   @Test
   void modelNamedLikeSubfolderRenameAndRemove() {
      clash("sfF", false, false);
      addFolder("sfF/Sales");
      addSource("sfF/Sales/y");
      addLogicalModel("sfF/Sales/y", "m");
      model("sfF/Sales/y").getLogicalModel("m").addLogicalModel(new XLogicalModel("e"), true);
      addFolder("sfF/East");
      addSource("sfF/East/z");
      addPartition("sfF/East/z", "p");
      model("sfF/East/z").getPartition("p").addPartition(new XPartition("pe"), false);
      addLogicalModel("sfF", "Sales");
      model("sfF").getLogicalModel("Sales").addLogicalModel(new XLogicalModel("own"), true);
      addPartition("sfF", "East");
      model("sfF").getPartition("East").addPartition(new XPartition("pown"), false);
      registry.clearCache();
      assertEquals(List.of("own"),
                   List.of(model("sfF").getLogicalModel("Sales").getLogicalModelNames()));
      assertEquals(List.of("pown"), List.of(model("sfF").getPartition("East").getPartitionNames()));
      List<String> before = state("sfF");

      model("sfF").renameLogicalModel("Sales", "S2", null);
      model("sfF").renamePartition("East", "E2", null);

      List<String> after = state("sfF");
      assertEquals(List.of("DATA_MODEL sfF [sfF [Sales]]", "EXTENDED_LOGIC_MODEL sfF/Sales/own",
                           "EXTENDED_PARTITION sfF/East/pown", "LOGIC_MODEL sfF/Sales",
                           "PARTITION sfF/East"), diff(before, after));
      assertEquals(List.of("DATA_MODEL sfF [sfF [S2]]", "EXTENDED_LOGIC_MODEL sfF/S2/own",
                           "EXTENDED_PARTITION sfF/E2/pown", "LOGIC_MODEL sfF/S2",
                           "PARTITION sfF/E2"), diff(after, before));

      model("sfF").removeLogicalModel("S2", true);
      model("sfF").removePartition("E2");

      assertEquals(List.of("DATA_MODEL sfF [sfF [S2]]", "EXTENDED_LOGIC_MODEL sfF/S2/own",
                           "EXTENDED_PARTITION sfF/E2/pown", "LOGIC_MODEL sfF/S2",
                           "PARTITION sfF/E2"), diff(after, state("sfF")));
   }

   // A2: P's logical model "x/lm" has its extended model at seF/x/lm/e, under data source seF/x
   // of the folder, which has its own model, extended model and VPM. Deleting seF/x keeps P's
   @Test
   void slashNamedModelWithExtendedModelOnClash() throws Exception {
      clash("seF", false, false);
      addSource("seF/x");
      addLogicalModel("seF/x", "own");
      model("seF/x").getLogicalModel("own").addLogicalModel(new XLogicalModel("oe"), true);
      addLogicalModel("seF", "x/lm");
      model("seF").getLogicalModel("x/lm").addLogicalModel(new XLogicalModel("e"), true);
      model("seF").addVirtualPrivateModel(new VirtualPrivateModel("x"), true);
      model("seF/x").addVirtualPrivateModel(new VirtualPrivateModel("v"), true);
      registry.clearCache();

      assertEquals(List.of("x/lm"), List.of(model("seF").getLogicalModelNames()));
      assertEquals(List.of("e"),
                   List.of(model("seF").getLogicalModel("x/lm").getLogicalModelNames()));
      assertEquals(List.of("own"), List.of(model("seF/x").getLogicalModelNames()));
      assertEquals(List.of("oe"),
                   List.of(model("seF/x").getLogicalModel("own").getLogicalModelNames()));
      assertEquals(List.of("x"), List.of(model("seF").getVirtualPrivateModelNames()));
      assertEquals(List.of("v"), List.of(model("seF/x").getVirtualPrivateModelNames()));
      assertEquals(1, portal().getVpmBrowseModel("seF", principal).getItems().length);
      assertEquals(1, portal().getVpmBrowseModel("seF/x", principal).getItems().length);
      assertEquals(List.of("e"), extendedModels(portal().getLogicalModels("seF", null, principal,
                                                                          false).get(0)));
      List<String> before = state("seF");

      repository.removeDataSource("seF/x", true);

      assertEquals(List.of("DATA_MODEL seF/x [seF/x [own]]", "DATA_SOURCE seF/x [seF/x]",
                           "EXTENDED_LOGIC_MODEL seF/x/own/oe", "LOGIC_MODEL seF/x/own",
                           "VPM seF/x/v"), diff(before, state("seF")));
   }

   // R1b: P's logical model "x" has extended model "e" at mbF/x/e, and data source mbF/x of the
   // folder has logical model "e" at the same path with extended model "f" at mbF/x/e/f.
   // Renaming mbF/x moves its own and leaves P's
   @Test
   void renameFolderDataSourceWithExtendedModelsOnBothSides() throws Exception {
      clash("mbF", false, false);
      addSource("mbF/x");
      addLogicalModel("mbF", "x");
      model("mbF").getLogicalModel("x").addLogicalModel(new XLogicalModel("e"), true);
      addLogicalModel("mbF/x", "e");
      model("mbF/x").getLogicalModel("e").addLogicalModel(new XLogicalModel("f"), true);
      registry.clearCache();
      assertEquals(List.of("e"), List.of(model("mbF").getLogicalModel("x").getLogicalModelNames()));
      assertEquals(List.of("f"),
                   List.of(model("mbF/x").getLogicalModel("e").getLogicalModelNames()));
      List<String> before = state("mbF");
      XDataSource ds = (XDataSource) registry.getDataSource("mbF/x").clone();
      ds.setName("mbF/z");

      repository.updateDataSource(ds, "mbF/x");

      List<String> after = state("mbF");
      assertEquals(List.of("DATA_MODEL mbF/x [mbF/x [e]]", "DATA_SOURCE mbF/x [mbF/x]",
                           "EXTENDED_LOGIC_MODEL mbF/x/e/f", "LOGIC_MODEL mbF/x/e"),
                   diff(before, after));
      assertEquals(List.of("DATA_MODEL mbF/z [mbF/z [e]]", "DATA_SOURCE mbF/z [mbF/z]",
                           "EXTENDED_LOGIC_MODEL mbF/z/e/f", "LOGIC_MODEL mbF/z/e"),
                   diff(after, before));
      assertEquals(List.of("e"), List.of(model("mbF").getLogicalModel("x").getLogicalModelNames()));
   }

   // R2: a copy of a folder above a clash anF/anP copies data source anF/anP with its own
   // logical model only, not the model of data source anF/anP/anX of the folder
   @Test
   void copyOfFolderAboveClash() throws Exception {
      addFolder("anF");
      clash("anF/anP", false, false);
      addSource("anF/anP/anX");
      addLogicalModel("anF/anP/anX", "mLm");
      addLogicalModel("anF/anP", "pLm");

      repository.updateDataSourceFolder(
         new DataSourceFolder("anG", LocalDateTime.now(), null), "anF", true);

      List<String> copy = state("anG");
      assertTrue(copy.contains("DATA_MODEL anG/Copy of anP [anG/Copy of anP [pLm]]"),
                 "copy: " + copy);
      assertTrue(copy.contains("DATA_MODEL anG/anP/Copy of anX [anG/anP/Copy of anX [mLm]]"),
                 "copy: " + copy);
      assertFalse(copy.contains("LOGIC_MODEL anG/Copy of anP/anX/mLm") ||
                  copy.contains("LOGIC_MODEL anG/Copy of anP/mLm"), "copy: " + copy);
   }

   // r1 important-1 (a): the delete of folder ndF together with data source ndF, where data
   // source ndF/s of the folder is a clash itself and models that can't be read are under data
   // sources of both folders, removes everything, models of both data sources and grants too
   @Test
   void deleteTogetherNestedClashWithUnreadableModels() {
      seedUnreadableModels("ndF");
      addParent("ndF/s", "sAdd");
      grant(ResourceType.DATA_SOURCE, "ndF/s");
      addFolder("ndF/s");
      addSource("ndF/s/y");
      setUnreadableModel("ndF/s/y/bad");
      addLogicalModel("ndF", "s");
      model("ndF").getLogicalModel("s").addLogicalModel(new XLogicalModel("se"), true);
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains("ndF/s"), "not seeded: ndF/s");

      registry.removeDataSourceFolder("ndF", true);

      assertEquals(List.of(), state("ndF"));
   }

   // r1 important-1 (a): deleting data source lcF/x and subfolder lcF/S on their own leaves
   // the models that can't be told apart. While they are there, the data source or the folder
   // alone is refused, and the delete of both together removes them
   @Test
   void unreadableModelsLeftByDeletesAreRemovedWithBothSides() throws Exception {
      seedUnreadableModels("lcF");

      repository.removeDataSource("lcF/x", true);
      registry.removeDataSourceFolder("lcF/S");

      assertEquals(List.of("DATA_MODEL lcF [lcF [S/y/lm, x]]", "DATA_SOURCE lcF [lcF]",
                           "DATA_SOURCE lcF/lcFAdd [lcFAdd]", "DATA_SOURCE_FOLDER lcF",
                           "EXTENDED_LOGIC_MODEL lcF/x/e", "LOGIC_MODEL lcF/S/y/bad",
                           "LOGIC_MODEL lcF/S/y/lm", "LOGIC_MODEL lcF/x", "LOGIC_MODEL lcF/x/bad",
                           "PARTITION lcF/x/pbad", "grant DATA_SOURCE_FOLDER|lcF",
                           "grant DATA_SOURCE|lcF", "grant DATA_SOURCE|lcF::lcFAdd"),
                   state("lcF"));
      List<String> before = state("lcF");
      assertThrows(MessageException.class, () -> registry.removeDataSourceFolder("lcF"));
      assertThrows(MessageException.class, () -> repository.removeDataSource("lcF", true));
      assertEquals(before, state("lcF"));

      emService().removeDataSourceFolder("lcF", true, principal, true);

      assertEquals(List.of(), state("lcF"));
   }

   // R1b, the folder move through XEngine (EM and portal folder rename): moving subfolder mxF/G
   // out of clash folder mxF leaves data source mxF's entries stored under the subfolder's
   // path, the extended model of its model "G" and its models "G/y/lm" (with an extended model)
   // and "G/H/z/m2", and moves the folder's data sources with their own models
   @Test
   void xengineMoveSubfolderOutOfClashKeepsPsEntries() throws Exception {
      clash("mxF", false, false);
      addFolder("mxF/G");
      addSource("mxF/G/y");
      addLogicalModel("mxF/G/y", "own");
      model("mxF/G/y").getLogicalModel("own").addLogicalModel(new XLogicalModel("oe"), true);
      addFolder("mxF/G/H");
      addSource("mxF/G/H/z");
      addLogicalModel("mxF", "G");
      model("mxF").getLogicalModel("G").addLogicalModel(new XLogicalModel("ext"), true);
      addLogicalModel("mxF", "G/y/lm");
      model("mxF").getLogicalModel("G/y/lm").addLogicalModel(new XLogicalModel("le"), true);
      addLogicalModel("mxF", "G/H/z/m2");
      addFolder("mxQ");
      List<String> before = state("mxF", "mxQ");

      repository.updateDataSourceFolder(
         new DataSourceFolder("mxQ/G", LocalDateTime.now(), null), "mxF/G");

      List<String> after = state("mxF", "mxQ");
      assertEquals(List.of("DATA_MODEL mxF/G/H/z [mxF/G/H/z []]",
                           "DATA_MODEL mxF/G/y [mxF/G/y [own]]",
                           "DATA_SOURCE mxF/G/H/z [mxF/G/H/z]", "DATA_SOURCE mxF/G/y [mxF/G/y]",
                           "DATA_SOURCE_FOLDER mxF/G", "DATA_SOURCE_FOLDER mxF/G/H",
                           "EXTENDED_LOGIC_MODEL mxF/G/y/own/oe", "LOGIC_MODEL mxF/G/y/own"),
                   diff(before, after));
      assertEquals(List.of("DATA_MODEL mxQ/G/H/z [mxQ/G/H/z []]",
                           "DATA_MODEL mxQ/G/y [mxQ/G/y [own]]",
                           "DATA_SOURCE mxQ/G/H/z [mxQ/G/H/z]", "DATA_SOURCE mxQ/G/y [mxQ/G/y]",
                           "DATA_SOURCE_FOLDER mxQ/G", "DATA_SOURCE_FOLDER mxQ/G/H",
                           "EXTENDED_LOGIC_MODEL mxQ/G/y/own/oe", "LOGIC_MODEL mxQ/G/y/own"),
                   diff(after, before));
      assertEquals(List.of("G", "G/H/z/m2", "G/y/lm"),
                   List.of(model("mxF").getLogicalModelNames()));
      assertEquals(List.of("le"),
                   List.of(model("mxF").getLogicalModel("G/y/lm").getLogicalModelNames()));
   }

   // clash P with data source P/x of the folder (its own model, extended model and VPM, and a
   // logical model and a physical view under it that can't be read), subfolder P/S with data
   // source P/S/y and a model under it that can't be read, P's model "x" with an extended model
   // at P/x/e, and P's model "S/y/lm"
   private void seedUnreadableModels(String p) {
      clash(p, false, false);
      addSource(p + "/x");
      grant(ResourceType.DATA_SOURCE, p + "/x");
      addLogicalModel(p + "/x", "own");
      model(p + "/x").getLogicalModel("own").addLogicalModel(new XLogicalModel("oe"), true);
      model(p + "/x").addVirtualPrivateModel(new VirtualPrivateModel("v"), true);
      setUnreadableModel(p + "/x/bad");
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
                                        p + "/x/pbad", null),
                         new DataSourceFolder("pbad", LocalDateTime.now(), null));
      addFolder(p + "/S");
      grant(ResourceType.DATA_SOURCE_FOLDER, p + "/S");
      addSource(p + "/S/y");
      grant(ResourceType.DATA_SOURCE, p + "/S/y");
      setUnreadableModel(p + "/S/y/bad");
      addLogicalModel(p, "x");
      model(p).getLogicalModel("x").addLogicalModel(new XLogicalModel("e"), true);
      addLogicalModel(p, "S/y/lm");
      registry.clearCache();
   }

   // verify r3: every folder and data source move entry point (XEngine, registry, EM, portal),
   // with several member names and an entry that can't be told apart from clash P's (a logical
   // model, a physical view, a VPM, one in a nested subfolder) under one member of subfolder
   // P/G: each is refused before anything is moved, whatever order the members are moved in
   @Test
   void everyMoveOfFolderUnderClashWithUnreadableEntryIsRefusedUntouched() throws Exception {
      List<String> failed = new ArrayList<>();
      int count = 0;

      for(int shape = 0; shape < 4; shape++) {
         for(String operation : MOVES) {
            String p = "ur" + count++ + "P";
            String q = p + "Q";
            clash(p, false, false);
            addFolder(p + "/G");
            addFolder(p + "/G/H");
            addFolder(q);
            List<String> members = switch(shape) {
               case 0 -> List.of("a", "y", "m", "b2", "zz");
               case 1 -> List.of("q1", "q2", "q3", "q4", "q5", "q6");
               case 2 -> List.of("a", "b", "H/z");
               default -> List.of("k", "c", "w");
            };

            for(String member : members) {
               addSource(p + "/G/" + member);
               addLogicalModel(p + "/G/" + member, "own");
            }

            String last = members.get(members.size() - 1);
            AssetEntry.Type type = shape == 1 ? AssetEntry.Type.PARTITION :
               shape == 3 ? AssetEntry.Type.VPM : AssetEntry.Type.LOGIC_MODEL;
            // under the last member, the one a data source rename renames
            setUnreadableEntry(type, p + "/G/" + last + "/bad");
            List<String> before = state(p, q);
            Throwable thrown = null;

            try {
               move(operation, p, q, members);
            }
            catch(Throwable e) {
               thrown = e;
            }

            List<String> after = state(p, q);

            if(!(thrown instanceof MessageException) || !before.equals(after)) {
               failed.add(shape + " " + operation + ": " + thrown + ", removed " +
                          diff(before, after) + ", added " + diff(after, before));
            }
         }
      }

      assertEquals(List.of(), failed);
   }

   // verify r3: without a clash the same entry points refuse nothing, also with a model that
   // can't be read, and move everything
   @Test
   void everyMoveWithoutClashIsDone() throws Exception {
      List<String> failed = new ArrayList<>();
      int count = 0;

      for(String operation : MOVES) {
         String p = "nm" + count++ + "P";
         String q = p + "Q";
         addFolder(p);
         addFolder(p + "/G");
         addFolder(p + "/G/H");
         addFolder(q);
         List<String> members = List.of("a", "y", "H/z");

         for(String member : members) {
            addSource(p + "/G/" + member);
            addLogicalModel(p + "/G/" + member, "own");
         }

         model(p + "/G/a").getLogicalModel("own").addLogicalModel(new XLogicalModel("e"), true);
         addLogicalModel(p + "/G/y", "s/l");
         setUnreadableEntry(AssetEntry.Type.LOGIC_MODEL, p + "/G/H/z/bad");
         List<String> before = state(p, q);
         Throwable thrown = null;

         try {
            move(operation, p, q, members);
         }
         catch(Throwable e) {
            thrown = e;
         }

         List<String> after = state(p, q);
         // what is moved: subfolder G, the members, or member H/z
         String moved = operation.equals("dataSourceRename") ? p + "/G/H/z" : p + "/G";
         List<String> left = after.stream()
            .filter(line -> !line.startsWith("grant") && !line.startsWith("DATA_SOURCE_FOLDER"))
            .filter(line -> Tool.isSameOrDescendantPath(moved, line.split(" ")[1]))
            .toList();

         if(thrown != null || !left.isEmpty() || diff(after, before).isEmpty()) {
            failed.add(operation + ": " + thrown + ", left " + left);
         }
      }

      assertEquals(List.of(), failed);
   }

   private static final List<String> MOVES = List.of(
      "xengineFolderMove", "xengineFolderRename", "portalFolderRename", "registryFolderRename",
      "emFolderMove", "portalFolderMove", "portalDataSources", "emDataSources",
      "dataSourceRename");

   // a move of subfolder p/G to q, a rename of it to p/H2, a move of its members to q, or a
   // rename of its last member
   private void move(String operation, String p, String q, List<String> members)
      throws Exception
   {
      String last = members.get(members.size() - 1);

      switch(operation) {
      case "xengineFolderMove" -> repository.updateDataSourceFolder(
         new DataSourceFolder(q + "/G", LocalDateTime.now(), null), p + "/G");
      case "xengineFolderRename" -> repository.updateDataSourceFolder(
         new DataSourceFolder(p + "/H2", LocalDateTime.now(), null), p + "/G");
      case "portalFolderRename" ->
         browserService().renameFolder(p + "/G", "H2", p + "/G", p + "/H2", principal);
      case "registryFolderRename" -> registry.renameDataSourceFolder(p + "/G", q + "/G");
      case "emFolderMove" -> emService().moveFiles(MoveCopyTreeNodesRequest.builder()
         .source(List.of(treeNode(p + "/G", inetsoft.sree.RepositoryEntry.DATA_SOURCE_FOLDER)))
         .destination(treeNode(q, inetsoft.sree.RepositoryEntry.DATA_SOURCE_FOLDER))
         .build(), true, principal);
      case "portalFolderMove" -> browserService().moveDataSource(new MoveCommand[] {
         moveCommand(p + "/G", q + "/G", PortalDataType.DATA_SOURCE_FOLDER) }, principal);
      case "portalDataSources" -> browserService().moveDataSource(members.stream()
         .map(member -> moveCommand(p + "/G/" + member, q + "/" + member.replace('/', '_'),
                                    PortalDataType.DATA_SOURCE))
         .toArray(MoveCommand[]::new), principal);
      case "emDataSources" -> emService().moveFiles(MoveCopyTreeNodesRequest.builder()
         .source(members.stream()
                    .map(member -> treeNode(p + "/G/" + member,
                                            inetsoft.sree.RepositoryEntry.DATA_SOURCE))
                    .toList())
         .destination(treeNode(q, inetsoft.sree.RepositoryEntry.DATA_SOURCE_FOLDER))
         .build(), true, principal);
      default -> {
         XDataSource dataSource = (XDataSource) registry.getDataSource(p + "/G/" + last).clone();
         dataSource.setName(p + "/G/" + last + "R");
         repository.updateDataSource(dataSource, p + "/G/" + last);
      }
      }
   }

   // an entry whose stored object is not what its type says
   private void setUnreadableEntry(AssetEntry.Type type, String path) {
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null),
                         new DataSourceFolder("bad", LocalDateTime.now(), null));
      registry.clearCache();
   }

   private DataSourceBrowserService browserService() throws Exception {
      return new DataSourceBrowserService(
         security, emService(), repository, mock(DataSourceService.class), registry,
         mock(inetsoft.uql.util.Config.class),
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class));
   }

   private static MoveCommand moveCommand(String oname, String nname, PortalDataType type) {
      MoveCommand move = new MoveCommand();
      move.setOldPath(oname);
      move.setPath(nname);
      move.setName(nname.substring(nname.lastIndexOf('/') + 1));
      move.setType(type.name());
      return move;
   }

   // verify r4: the portal editor renames clash data source erF, whose folder holds nothing, but
   // a model under it can't be told apart, and renames one of its additional connections.
   // Refused before anything is written: no additional connection under the new name, and no
   // grant moved
   @Test
   void portalEditorRenameOfClashDataSourceWithUnreadableModel() throws Exception {
      addFolder("erF");
      editorParent("erF");
      setUnreadableModel("erF/a/bad");
      assertTrue(registry.getDataSourcePathClashes().contains("erF"), "not seeded");
      List<String> before = state("erF", "erG");

      assertThrows(MessageException.class, () -> editorSave("erF", "erG", ""));
      assertEquals(before, state("erF", "erG"));
   }

   // verify r4: a save of clash data source esF in the portal editor without a new name still
   // renames its additional connection and moves the grant, and leaves the folder's data source
   @Test
   void portalEditorSaveOfClashDataSourceWithoutRename() throws Exception {
      addFolder("esF");
      addSource("esF/m");
      grant(ResourceType.DATA_SOURCE, "esF/m");
      editorParent("esF");
      setUnreadableModel("esF/m/bad");
      List<String> before = state("esF");

      editorSave("esF", "esF", "");

      List<String> after = state("esF");
      assertEquals(List.of("DATA_SOURCE esF/esFr1 [esFr1]", "grant DATA_SOURCE|esF::esFr1"),
                   diff(before, after));
      assertEquals(List.of("DATA_SOURCE esF/esFr2 [esFr2]", "grant DATA_SOURCE|esF::esFr2"),
                   diff(after, before));
   }

   // verify r4: without a clash the portal editor renames a data source with its additional
   // connections, renames one of them, and moves their grants
   @Test
   void portalEditorRenameWithoutClash() throws Exception {
      editorParent("enP");

      editorSave("enP", "enQ", "");

      List<String> after = state("enP", "enQ");
      assertTrue(after.containsAll(List.of(
         "DATA_SOURCE enQ [enQ]", "DATA_SOURCE enQ/enPk1 [enPk1]",
         "DATA_SOURCE enQ/enPr2 [enPr2]", "grant DATA_SOURCE|enQ",
         "grant DATA_SOURCE|enQ::enPk1", "grant DATA_SOURCE|enQ::enPr2")),
                 "" + after);
      assertTrue(after.stream().noneMatch(line -> line.contains(" enP") ||
         line.contains("|enP")), "" + after);
   }

   // verify r4: the portal XMLA editor, which saves through the same method, refuses a rename of
   // clash data source exF and renames one without a clash with its domain
   @Test
   void portalXmlaEditorRename() throws Exception {
      addFolder("exF");
      addSource("exF/m");
      xmlaSource("exF");
      List<String> before = state("exF", "exG");

      assertThrows(MessageException.class, () -> xmlaRename("exF", "exG"));
      assertEquals(before, state("exF", "exG"));

      xmlaSource("exP");
      xmlaRename("exP", "exQ");

      // (the old domain exP is also left behind, the same without the fix: not checked here)
      List<String> after = state("exP", "exQ");
      assertTrue(after.containsAll(
         List.of("DATA_SOURCE exQ [exQ]", "DOMAIN exQ", "grant DATA_SOURCE|exQ")), "" + after);
      assertTrue(after.stream().noneMatch(
         line -> line.startsWith("DATA_SOURCE exP") || line.startsWith("grant DATA_SOURCE|exP")),
                 "" + after);
   }

   // a tabular data source with additional connections <path>k1 and <path>r1 (names unique
   // across tests, the editor checks them against the other data sources), and grants of all
   private void editorParent(String path) {
      tabParent(path, path + "k1", path + "r1");
      grant(ResourceType.DATA_SOURCE, path);
      grant(ResourceType.DATA_SOURCE, path + "::" + path + "k1");
      grant(ResourceType.DATA_SOURCE, path + "::" + path + "r1");
   }

   // a save in the portal data source editor of data source <name> (seeded by editorParent) that
   // keeps additional connection <name>k1 and renames <name>r1 to <name>r2
   private void editorSave(String name, String newName, String parentPath) throws Exception {
      DataSourceDefinition definition = tabularDefinition(newName, parentPath);
      DataSourceDefinition renamed = tabularDefinition(name + "r2", null);
      renamed.setOldName(name + "r1");
      definition.setAdditionalConnections(
         new ArrayList<>(List.of(tabularDefinition(name + "k1", null), renamed)));
      new DatasourcesService(repository, security, mock(DataSourceStatusService.class), registry,
                             inetsoft.uql.util.Config.getConfig())
         .updateDataSource(name, definition, principal);
   }

   private void xmlaSource(String path) {
      XMLADataSource dataSource = new XMLADataSource();
      dataSource.setName(path);
      registry.setDataSource(dataSource, false);
      Domain domain = new Domain();
      domain.setDataSource(path);
      registry.setDomain(domain);
      grant(ResourceType.DATA_SOURCE, path);
      registry.clearCache();
   }

   private void xmlaRename(String name, String newName) throws Exception {
      inetsoft.web.portal.service.datasource.XmlaDatasourceService service =
         new inetsoft.web.portal.service.datasource.XmlaDatasourceService(
            repository, security, mock(DataSourceStatusService.class), registry,
            inetsoft.uql.util.Config.getConfig(), mock(inetsoft.uql.asset.DependencyHandler.class),
            mock(inetsoft.report.XSessionManager.class));
      inetsoft.web.portal.data.DataSourceXmlaDefinition definition =
         service.getDataSourceModel(name, principal);
      definition.setName(newName);
      service.updateDataSource(name, definition, principal);
   }

   private static List<String> extendedModels(LogicalModel model) {
      return model.getExtendModels().stream().map(LogicalModel::getName).toList();
   }

   private DataSourceService portal() {
      return new DataSourceService(mock(AssetRepository.class), security, repository, registry);
   }

   // clash P with data sources P/udA and P/udX of the folder, a readable model of P/udX and one
   // under P/udX that can't be read
   private void seedUnreadableMember(String path) {
      String name = path.substring(path.lastIndexOf('/') + 1);
      String member = path + "/" + name.substring(0, 2) + "X";
      clash(path, false, false);
      addSource(path + "/" + name.substring(0, 2) + "A");
      addSource(member);
      addLogicalModel(member, "own");
      setUnreadableModel(member + "/bad");
   }

   // clash P with subfolder P/G holding data sources P/G/a and P/G/y, and a model under P/G/y
   // that can't be read
   private void seedUnreadableSubfolder(String path) {
      seedUnreadableSubfolder(path, "y");
   }

   private void seedUnreadableSubfolder(String path, String member) {
      clash(path, false, false);
      addFolder(path + "/G");
      addSource(path + "/G/a");
      addSource(path + "/G/y");
      setUnreadableModel(path + "/G/" + member + "/lm");
   }

   // a tabular data source with additional connections, of the type the editor tests register
   private void tabParent(String path, String... additionals) {
      DataSourcePathClashTest.Tab77691 parent = new DataSourcePathClashTest.Tab77691();
      parent.setName(path);
      registry.setDataSource(parent, false);
      parent = (DataSourcePathClashTest.Tab77691) registry.getDataSource(path);

      for(String name : additionals) {
         DataSourcePathClashTest.Tab77691 child = new DataSourcePathClashTest.Tab77691();
         child.setName(name);
         parent.addDatasource(child);
      }

      registry.clearCache();
   }

   // a save in the portal data source editor that renames a data source and keeps its
   // additional connections
   private void portalSave(String name, String newName, String parentPath,
                           String... additionals) throws Exception
   {
      DataSourceDefinition definition = tabularDefinition(newName, parentPath);
      List<DataSourceDefinition> children = new ArrayList<>();

      for(String additional : additionals) {
         children.add(tabularDefinition(additional, null));
      }

      definition.setAdditionalConnections(children);
      new DatasourcesService(repository, security, mock(DataSourceStatusService.class), registry,
                             inetsoft.uql.util.Config.getConfig())
         .updateDataSource(name, definition, principal);
   }

   private static DataSourceDefinition tabularDefinition(String name, String parentPath) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setType("Bug77691Tab");
      definition.setName(name);
      definition.setParentPath(parentPath);
      definition.setTabularView(new inetsoft.uql.tabular.TabularView());
      return definition;
   }

   private static ContentRepositoryTreeNode treeNode(String path, int type) {
      int index = path.lastIndexOf('/');
      return ContentRepositoryTreeNode.builder()
         .label(index < 0 ? path : path.substring(index + 1))
         .path(path)
         .type(type)
         .build();
   }

   // a logical model entry whose stored object is not a model
   private void setUnreadableModel(String path) {
      registry.setObject(new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                        path, null),
                         new DataSourceFolder("bad", LocalDateTime.now(), null));
      registry.clearCache();
      assertTrue(state(path).contains("LOGIC_MODEL " + path), "not seeded: " + path);
   }

   private RepositoryObjectService emService() throws Exception {
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      inetsoft.sree.RepletRegistryManager repletRegistries =
         mock(inetsoft.sree.RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(inetsoft.sree.RepletRegistry.class));
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.<String>getArgument(1)));
      return new RepositoryObjectService(
         mock(RepletRegistryService.class),
         mock(ContentRepositoryTreeService.class), provider,
         permissions, repository,
         mock(RepositoryDashboardService.class),
         mock(inetsoft.web.admin.content.database.model.DataModelFolderManagerService.class),
         registry, mock(inetsoft.report.LibManagerProvider.class),
         mock(inetsoft.web.RecycleBin.class), mock(inetsoft.uql.asset.DependencyHandler.class),
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class), repletRegistries,
         mock(inetsoft.sree.web.dashboard.DashboardRegistryManager.class));
   }

   private void importOverwrite(String path) throws Exception {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      JarOutputStream jar = new JarOutputStream(bos);
      new XDataSourceAsset(path).writeContent(jar);
      jar.finish();
      JarInputStream in = new JarInputStream(new ByteArrayInputStream(bos.toByteArray()));
      in.getNextEntry();
      byte[] xml = in.readAllBytes();
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);
      new XDataSourceAsset(path).parseContent(new ByteArrayInputStream(xml), config, true, true);
   }

   private static List<String> diff(List<String> a, List<String> b) {
      List<String> result = new ArrayList<>(a);
      result.removeAll(b);
      return result;
   }

   private void addLogicalModel(String ds, String name) {
      model(ds).addLogicalModel(new XLogicalModel(name));
      registry.clearCache();
   }

   private void addPartition(String ds, String name) {
      model(ds).addPartition(new XPartition(name));
      registry.clearCache();
   }

   private XDataModel model(String ds) {
      XDataModel model = registry.getDataModel(ds);

      if(model == null) {
         model = new XDataModel(ds);
         registry.setDataModel(model);
      }

      return model;
   }

   // the stored entries at or under the roots and the grants of them, read back from storage
   private List<String> state(String... roots) {
      registry.clearCache();
      List<String> lines = new ArrayList<>();

      for(AssetEntry entry : registry.getEntries("")) {
         String path = entry.getPath();

         if(Arrays.stream(roots).noneMatch(root -> Tool.isSameOrDescendantPath(root, path))) {
            continue;
         }

         String line = entry.getType() + " " + path;

         if(entry.getType() == AssetEntry.Type.DATA_SOURCE) {
            XDataSource dataSource = registry.getDataSource(path);
            line += " [" + (dataSource != null ? dataSource.getFullName() :
               registry.containObject(entry) ? "unreadable" : "missing") + "]";
         }
         else if(entry.getType() == AssetEntry.Type.DATA_MODEL) {
            XDataModel model = registry.getDataModel(path);
            line += model == null ? " [missing]" : " [" + model.getDataSource() + " " +
               new TreeSet<>(List.of(model.getLogicalModelNames())) + "]";
         }

         lines.add(line);
      }

      for(String key : store.keySet()) {
         String resource = key.substring(key.indexOf('|') + 1).replaceFirst("::.*", "");

         if(Arrays.stream(roots).anyMatch(root -> Tool.isSameOrDescendantPath(root, resource))) {
            lines.add("grant " + key);
         }
      }

      Collections.sort(lines);
      return lines;
   }

   // a data source and a folder at the path, a data source of the folder and one in a
   // subfolder if asked, an additional connection of the data source, and grants of all
   private void clash(String path, boolean member, boolean subfolder) {
      String name = path.substring(path.lastIndexOf('/') + 1);
      addFolder(path);
      grant(ResourceType.DATA_SOURCE_FOLDER, path);

      if(member) {
         addSource(path + "/" + name + "X");
         grant(ResourceType.DATA_SOURCE, path + "/" + name + "X");
      }

      if(subfolder) {
         addFolder(path + "/" + name + "Sub");
         addSource(path + "/" + name + "Sub/" + name + "Y");
         grant(ResourceType.DATA_SOURCE_FOLDER, path + "/" + name + "Sub");
         grant(ResourceType.DATA_SOURCE, path + "/" + name + "Sub/" + name + "Y");
      }

      addParent(path, name + "Add");
      grant(ResourceType.DATA_SOURCE, path);
      grant(ResourceType.DATA_SOURCE, path + "::" + name + "Add");
      registry.clearCache();
      assertTrue(registry.getDataSourcePathClashes().contains(path), "not seeded: " + path);
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private void addSource(String path) {
      registry.setDataSource(source(path), false);
   }

   private void addParent(String path, String... additionals) {
      addSource(path);
      JDBCDataSource parent = (JDBCDataSource) registry.getDataSource(path);

      for(String name : additionals) {
         parent.addDatasource(source(name));
      }
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
