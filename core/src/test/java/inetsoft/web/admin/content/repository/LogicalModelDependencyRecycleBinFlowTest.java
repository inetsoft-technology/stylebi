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

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.gui.ObjectInfo;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.data.DatasourcesBaseService;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.sree.RepletRegistryManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78430: a logical model and its attributes list the sheets that use them. The lists must
 * follow a sheet through folder rename, rename, move, delete to the Recycle Bin, restore and purge,
 * as done by {@link RepositoryObjectService#deleteNodes} and {@link RecycleUtils}.
 *
 * <p>The model is kept as XML, so each reader gets a fresh copy and only what is written back is
 * kept. The lists are compared as lists, so a duplicate entry fails.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LogicalModelDependencyRecycleBinFlowTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LogicalModelDependencyRecycleBinFlowTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "t78430" + info.getTestMethod().orElseThrow().getName();

      XLogicalModel model = new StoredModel(MODEL);
      XEntity entity = new XEntity(ENTITY);
      entity.addAttribute(new XAttribute(NAME, "CUSTOMERS", "NAME"));
      entity.addAttribute(new XAttribute(CITY, "CUSTOMERS", "CITY"));
      model.addEntity(entity);
      store(model);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel(MODEL)).thenAnswer(i -> load());
      doAnswer(i -> {
         store(i.getArgument(0));
         return null;
      }).when(dataModel).addLogicalModel(any(XLogicalModel.class), anyBoolean());
      when(dataModel.clone()).thenReturn(dataModel);
      reset(repository);
      when(repository.getDataModel(DATA_SOURCE)).thenReturn(dataModel);

      engine = new TestEngine(new BlobIndexedStorage(blobStorageManager));
      AssetUtil.setAssetRepository(false, engine);

      for(AssetEntry.Type type : List.of(AssetEntry.Type.VIEWSHEET, AssetEntry.Type.WORKSHEET)) {
         AssetEntry bin = sheet(type, RecycleUtils.getRecycleBinPath("x")).getParent();

         if(!engine.containsEntry(bin)) {
            engine.addFolder(bin, null);
         }
      }
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      AssetUtil.setAssetRepository(false, null);
   }

   @Test
   void reporterScenarioViewsheet() throws Exception {
      AssetEntry other = addFolder(prefix + "Other");
      AssetEntry keep = sheet(AssetEntry.Type.VIEWSHEET, other.getPath() + "/keep");
      save(keep, viewsheet(NAME));

      AssetEntry folder = addFolder(prefix + "New");
      AssetEntry dash = sheet(AssetEntry.Type.VIEWSHEET, folder.getPath() + "/dash");
      AssetEntry dash3 = sheet(AssetEntry.Type.VIEWSHEET, folder.getPath() + "/dash3");
      AssetEntry plain = sheet(AssetEntry.Type.VIEWSHEET, folder.getPath() + "/plain");
      save(dash, viewsheet(NAME));
      save(dash3, viewsheet(NAME));
      save(plain, new Viewsheet());
      assertModel(keep.getPath(), dash.getPath(), dash3.getPath());

      // rename the folder with 3 children, 2 bound: one model write per bound child
      AssetEntry renamedFolder = folder(prefix + "Renamed");
      clearInvocations(repository);
      int before = storeWrites;
      engine.changeFolder(folder, renamedFolder, null, true);
      verify(repository, times(2)).updateDataModel(any());
      assertEquals(2, storeWrites - before, "model writes for the folder rename");
      AssetEntry rdash = child(renamedFolder, "dash");
      AssetEntry rdash3 = child(renamedFolder, "dash3");
      assertModel(keep.getPath(), rdash.getPath(), rdash3.getPath());

      // rename, then move the sheet
      AssetEntry dash2 = child(renamedFolder, "dash2");
      engine.changeSheet(rdash, dash2, null, true);
      AssetEntry moved = child(other, "dash2");
      engine.changeSheet(dash2, moved, null, true);
      assertModel(keep.getPath(), moved.getPath(), rdash3.getPath());

      // re-save at the new path, no duplicate
      save(moved, viewsheet(NAME));
      assertModel(keep.getPath(), moved.getPath(), rdash3.getPath());

      // delete the sheet from the EM tree (to the Bin), through RepositoryObjectService
      RecycleBin bin = mock(RecycleBin.class);
      RepositoryObjectService service = service(bin);
      service.deleteNodes(new TreeNodeInfo[] { node(moved.getPath()) }, admin(), true, false);
      String binPath = capturedBinPath(bin);
      assertFalse(engine.containsEntry(moved));
      assertModel(keep.getPath(), binPath, rdash3.getPath());

      // restore from the Bin through RecycleUtils.restoreSheet
      RecycleBin.Entry rEntry = new RecycleBin.Entry();
      rEntry.setPath(binPath);
      rEntry.setOriginalPath(moved.getPath());
      rEntry.setName(moved.getName());
      rEntry.setType(RepositoryEntry.VIEWSHEET);
      rEntry.setOriginalScope(AssetRepository.GLOBAL_SCOPE);
      RecycleUtils.restoreSheet(rEntry, false, admin(), mock(RecycleBin.class));
      assertTrue(engine.containsEntry(moved));
      assertModel(keep.getPath(), moved.getPath(), rdash3.getPath());

      // to the Bin again, then purge it from the Bin through RepositoryObjectService
      bin = mock(RecycleBin.class);
      service = service(bin);
      service.deleteNodes(new TreeNodeInfo[] { node(moved.getPath()) }, admin(), true, false);
      binPath = capturedBinPath(bin);
      assertModel(keep.getPath(), binPath, rdash3.getPath());
      service.deleteNodes(new TreeNodeInfo[] { node(binPath) }, admin(), true, false);
      assertFalse(engine.containsEntry(sheet(AssetEntry.Type.VIEWSHEET, binPath)));
      assertModel(keep.getPath(), rdash3.getPath());

      // the folder to the Bin (RepletEngine changeFolder) and its purge (removeFolder)
      AssetEntry binFolder = folder(RecycleUtils.getRecycleBinPath(prefix + "BF"));
      engine.changeFolder(renamedFolder, binFolder, null, true);
      assertModel(keep.getPath(), child(binFolder, "dash3").getPath());
      engine.removeFolder(binFolder, null, true);
      assertModel(keep.getPath());

      // the readers report only the sheet that is left
      XLogicalModel model = load();
      DependencyException ex = assertThrows(DependencyException.class,
                                            () -> modelCheck().checkModelOuterDependencies(model));
      assertEquals(List.of(keep.getPath()), paths(ex.getEntries().toArray()));
      assertNotNull(model.getEntity(ENTITY).getAttribute(NAME).getDependencyException());
      assertNull(model.getEntity(ENTITY).getAttribute(CITY).getDependencyException());
   }

   @Test
   void worksheetFolderToRecycleBinAndPurge() throws Exception {
      AssetEntry wsFolder = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                           prefix + "WF", null, orgId);
      engine.addFolder(wsFolder, null);
      AssetEntry ws1 = sheet(AssetEntry.Type.WORKSHEET, wsFolder.getPath() + "/ws1");
      AssetEntry ws2 = sheet(AssetEntry.Type.WORKSHEET, wsFolder.getPath() + "/ws2");
      save(ws1, worksheet(NAME));
      save(ws2, worksheet(CITY));
      assertEquals(List.of(ws1.getPath(), ws2.getPath()),
                   modelPaths().stream().sorted().toList());
      assertEquals(List.of(ws1.getPath()), attributePaths(NAME));
      assertEquals(List.of(ws2.getPath()), attributePaths(CITY));

      RecycleBin bin = mock(RecycleBin.class);
      RecycleUtils.moveAssetFolderToRecycleBin(wsFolder, null, bin, true);
      ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
      verify(bin).addEntry(path.capture(), anyString(), anyString(), any(), anyInt(), anyInt(),
                           any());
      String binPath = path.getValue();
      assertEquals(Set.of(binPath + "/ws1", binPath + "/ws2"), Set.copyOf(modelPaths()));
      assertEquals(List.of(binPath + "/ws1"), attributePaths(NAME));
      assertEquals(List.of(binPath + "/ws2"), attributePaths(CITY));

      engine.removeFolder(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                         binPath, null, orgId), null, true);
      assertModel();
      assertEquals(List.of(), attributePaths(CITY));
   }

   /**
    * A model saved before the fix lists missing sheets: no false prompt, live sheets still listed.
    */
   @Test
   void preFixStaleEntriesGiveNoPrompt() throws Exception {
      XLogicalModel model = load();
      XAttribute name = model.getEntity(ENTITY).getAttribute(NAME);

      for(int i = 0; i < 5; i++) {
         AssetEntry gone = sheet(AssetEntry.Type.VIEWSHEET, prefix + "gone" + i);
         model.addOuterDependency(gone);
         name.addOuterDependency(gone);
      }

      store(model);
      XLogicalModel stale = load();
      assertNull(stale.getEntity(ENTITY).getAttribute(NAME).getDependencyException());
      assertNull(stale.getEntity(ENTITY).getDependencyException());
      assertDoesNotThrow(() -> modelCheck().checkModelOuterDependencies(stale));

      AssetEntry live = sheet(AssetEntry.Type.VIEWSHEET, prefix + "live");
      save(live, viewsheet(NAME));
      XLogicalModel mixed = load();
      DependencyException ex = assertThrows(DependencyException.class,
                                            () -> modelCheck().checkModelOuterDependencies(mixed));
      assertEquals(List.of(live.getPath()), paths(ex.getEntries().toArray()));
      assertFalse(ex.getMessage().contains("gone"), ex.getMessage());
      assertEquals(List.of(live.getPath()), paths(
         mixed.getEntity(ENTITY).getAttribute(NAME).getDependencyException().getEntries()
            .toArray()));
   }

   private RepositoryObjectService service(RecycleBin bin) throws Exception {
      ContentRepositoryTreeService tree = mock(ContentRepositoryTreeService.class);
      when(tree.getAssetScope(anyString())).thenReturn(AssetRepository.GLOBAL_SCOPE);
      when(tree.getUnscopedPath(anyString())).thenAnswer(i -> i.getArgument(0));
      RepletRegistryService registryService = mock(RepletRegistryService.class);
      when(registryService.getAssetEntry(anyString(), any()))
         .thenAnswer(i -> AssetEntry.createAssetEntry((String) i.getArgument(0)));
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString()))
         .thenReturn(new Resource(ResourceType.REPORT, "x"));

      return new RepositoryObjectService(
         registryService, tree, mock(SecurityProvider.class), permissions, repository,
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         mock(inetsoft.uql.service.DataSourceRegistry.class), mock(LibManagerProvider.class), bin,
         DependencyHandler.getInstance(), mock(RenameTransformHandler.class),
         mock(RepletRegistryManager.class), mock(DashboardRegistryManager.class));
   }

   private static TreeNodeInfo node(String path) {
      return TreeNodeInfo.builder().label(path.substring(path.lastIndexOf('/') + 1)).path(path)
         .type(RepositoryEntry.VIEWSHEET).build();
   }

   private static String capturedBinPath(RecycleBin bin) {
      ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
      verify(bin).addEntry(path.capture(), anyString(), anyString(), any(), anyInt(), anyInt(),
                           any());
      return path.getValue();
   }

   private Principal admin() {
      return new SRPrincipal(new IdentityID("admin", orgId), new IdentityID[0], new String[0],
                             orgId, 1L);
   }

   private void assertModel(String... expected) throws Exception {
      List<String> want = Arrays.stream(expected).sorted().toList();
      assertEquals(want, modelPaths().stream().sorted().toList(), "model list: " + storedXml);
      assertEquals(want, attributePaths(NAME).stream().sorted().toList(), "attribute list");
   }

   // list, not set: a duplicate shows up
   private List<String> modelPaths() throws Exception {
      return paths(load().getOuterDependencies());
   }

   private List<String> attributePaths(String attr) throws Exception {
      return paths(load().getEntity(ENTITY).getAttribute(attr).getOuterDependencies());
   }

   private static List<String> paths(Object[] entries) {
      return Arrays.stream(entries).map(e -> ((AssetEntry) e).getPath())
         .collect(Collectors.toList());
   }

   private void store(XLogicalModel model) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      model.writeXML(writer);
      writer.flush();
      storedXml = out.toString();
      storeWrites++;
   }

   private XLogicalModel load() throws Exception {
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(storedXml.getBytes("UTF-8"))).getDocumentElement();
      XLogicalModel model = new StoredModel(MODEL);
      model.parseXML(root);
      return model;
   }

   private static DatasourcesBaseService modelCheck() {
      return new DatasourcesBaseService(null, null, null, null, null) {
         @Override
         public XDataSource createDataSource(
            inetsoft.web.portal.data.BaseDataSourceDefinition definition, XDataSource ds)
         {
            return ds;
         }
      };
   }

   private void save(AssetEntry entry, AbstractSheet sheet) throws Exception {
      engine.setSheet(entry, sheet, null, true, false, true);
   }

   private AssetEntry addFolder(String path) throws Exception {
      AssetEntry folder = folder(path);
      engine.addFolder(folder, null);
      return folder;
   }

   private AssetEntry folder(String path) {
      return sheet(AssetEntry.Type.VIEWSHEET, path + "/x").getParent();
   }

   private AssetEntry child(AssetEntry folder, String name) {
      return sheet(AssetEntry.Type.VIEWSHEET, folder.getPath() + "/" + name);
   }

   private AssetEntry sheet(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null, orgId);
   }

   private static ColumnSelection columns(String attribute) {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef(ENTITY, attribute)));
      return columns;
   }

   private static Viewsheet viewsheet(String attribute) {
      Viewsheet vs = new Viewsheet();
      AssetEntry base = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
                                       DATA_SOURCE + "/" + MODEL, null);
      base.setProperty("prefix", DATA_SOURCE);
      base.setProperty("source", MODEL);
      base.setProperty("mainType", ObjectInfo.LOGICAL_MODEL);
      vs.setBaseEntry(base);
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.setColumnSelection(columns(attribute));
      vs.addAssembly(table);
      return vs;
   }

   private static Worksheet worksheet(String attribute) {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = new BoundTableAssembly(ws, "Customers");
      table.setSourceInfo(new SourceInfo(XSourceInfo.MODEL, DATA_SOURCE, MODEL));
      table.setColumnSelection(columns(attribute));
      ws.addAssembly(table);
      return ws;
   }

   private static final class StoredModel extends XLogicalModel {
      StoredModel(String name) {
         super(name);
      }

      @Override
      public String[] getLogicalModelNames() {
         return new String[0];
      }
   }

   private static final class TestEngine extends AbstractAssetEngine {
      TestEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, Cluster.getInstance());
         istore = storage;
         scopes = new int[] { GLOBAL_SCOPE, REPORT_SCOPE, USER_SCOPE };
         Arrays.sort(scopes);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source,
                                                       Principal user)
      {
         return true;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return true;
      }
   }

   @Configuration
   static class Beans {
      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }

      @Bean
      @Primary
      DependencyHandler localDependencyHandler(XRepository repository) {
         return new LocalDependencyHandler(repository);
      }

      @Bean
      RepletRegistryManager repletRegistryManager() throws Exception {
         RepletRegistryManager manager = mock(RepletRegistryManager.class);
         when(manager.getRegistry()).thenReturn(mock(inetsoft.sree.RepletRegistry.class));
         return manager;
      }

      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      @Bean
      ScheduleManager scheduleManager() {
         return mock(ScheduleManager.class);
      }

      @Bean
      MVManager mvManager() {
         MVManager manager = mock(MVManager.class);
         when(manager.list(anyBoolean(), any(MVManager.MVFilter.class))).thenReturn(new MVDef[0]);
         return manager;
      }
   }

   private static final String DATA_SOURCE = "Orders";
   private static final String MODEL = "Order Model";
   private static final String ENTITY = "Customer";
   private static final String NAME = "Name";
   private static final String CITY = "City";

   @Autowired
   private BlobStorageManager blobStorageManager;
   @Autowired
   private XRepository repository;

   private Principal savedPrincipal;
   private String orgId;
   private String prefix;
   private TestEngine engine;
   private String storedXml;
   private int storeWrites;
}
