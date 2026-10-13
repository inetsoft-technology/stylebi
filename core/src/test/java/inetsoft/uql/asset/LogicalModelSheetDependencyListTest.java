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
package inetsoft.uql.asset;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
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
import inetsoft.web.portal.data.DatasourcesBaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78430: a logical model and each of its attributes list the sheets that use them (the
 * {@code <dependencies>} of the model XML). The lists hold the full entry of each sheet, but only a
 * save in place kept them, so a sheet that was renamed, moved, moved to the Recycle Bin, restored
 * or purged stayed listed under an entry that no longer exists, and deleting or renaming the model
 * warned that the missing sheet depended on it.
 *
 * <p>A real {@link LocalDependencyHandler} runs over a mocked repository that returns a real
 * logical model, and a real asset engine over blob storage does the repository operations.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LogicalModelSheetDependencyListTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LogicalModelSheetDependencyListTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      principal = ThreadContext.getContextPrincipal();
      orgId = Organization.getDefaultOrganizationID();
      prefix = "b78430" + info.getTestMethod().orElseThrow().getName();

      model = new XLogicalModel(MODEL) {
         // the extended models come from the data source registry
         @Override
         public String[] getLogicalModelNames() {
            return new String[0];
         }
      };
      XEntity entity = new XEntity(ENTITY);
      name = new XAttribute(NAME, "CUSTOMERS", "NAME");
      city = new XAttribute(CITY, "CUSTOMERS", "CITY");
      entity.addAttribute(name);
      entity.addAttribute(city);
      model.addEntity(entity);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModel(MODEL)).thenReturn(model);
      reset(repository);
      when(repository.getDataModel(DATA_SOURCE)).thenReturn(dataModel);

      engine = new TestEngine(new BlobIndexedStorage(blobStorageManager));
      AssetUtil.setAssetRepository(false, engine);

      for(AssetEntry.Type type : List.of(AssetEntry.Type.VIEWSHEET, AssetEntry.Type.WORKSHEET)) {
         AssetEntry recycleBin = recycleBin(type);

         if(!engine.containsEntry(recycleBin)) {
            engine.addFolder(recycleBin, null);
         }
      }
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
      AssetUtil.setAssetRepository(false, null);
   }

   @Test
   void renamedAndMovedViewsheetIsListedUnderItsNewPath() throws Exception {
      AssetEntry folder = addFolder(prefix + "F");
      AssetEntry other = addFolder(prefix + "G");
      AssetEntry dash = sheet(AssetEntry.Type.VIEWSHEET, folder.getPath() + "/dash");
      save(dash, viewsheet(NAME));
      assertListed(dash.getPath());

      AssetEntry renamed = sheet(AssetEntry.Type.VIEWSHEET, folder.getPath() + "/dash2");
      engine.changeSheet(dash, renamed, null, true);
      assertListed(renamed.getPath());

      AssetEntry moved = sheet(AssetEntry.Type.VIEWSHEET, other.getPath() + "/dash2");
      engine.changeSheet(renamed, moved, null, true);
      assertListed(moved.getPath());

      // a save at the new path doesn't bring the old path back
      save(moved, viewsheet(NAME));
      assertListed(moved.getPath());
   }

   @Test
   void renamedAndDeletedWorksheetIsListedUnderItsNewPathAndThenRemoved() throws Exception {
      AssetEntry ws = sheet(AssetEntry.Type.WORKSHEET, prefix + "ws1");
      save(ws, worksheet(NAME));
      assertListed(ws.getPath());

      AssetEntry renamed = sheet(AssetEntry.Type.WORKSHEET, prefix + "ws2");
      engine.changeSheet(ws, renamed, null, true);
      assertListed(renamed.getPath());

      engine.removeSheet(renamed, null, true);
      assertListed();
   }

   /**
    * Worksheets and viewsheets are kept in two folder trees, so each tree has its own folder.
    */
   @Test
   void foldersMovedToRecycleBinAndPurgedAreRemoved() throws Exception {
      AssetEntry vsFolder = addFolder(AssetEntry.Type.VIEWSHEET, prefix + "F");
      AssetEntry wsFolder = addFolder(AssetEntry.Type.WORKSHEET, prefix + "F");
      AssetEntry dash = sheet(AssetEntry.Type.VIEWSHEET, vsFolder.getPath() + "/dash");
      AssetEntry ws = sheet(AssetEntry.Type.WORKSHEET, wsFolder.getPath() + "/ws");
      save(dash, viewsheet(NAME));
      save(ws, worksheet(NAME));
      assertListed(dash.getPath(), ws.getPath());

      String binPath = RecycleUtils.getRecycleBinPath(prefix + "BF");
      AssetEntry vsBinFolder = folder(vsFolder.getType(), binPath);
      AssetEntry wsBinFolder = folder(wsFolder.getType(), binPath);
      engine.changeFolder(vsFolder, vsBinFolder, null, true);
      engine.changeFolder(wsFolder, wsBinFolder, null, true);
      assertListed(binPath + "/dash", binPath + "/ws");

      engine.removeFolder(vsBinFolder, null, true);
      assertListed(binPath + "/ws");
      engine.removeFolder(wsBinFolder, null, true);
      assertListed();
   }

   @Test
   void sheetMovedToRecycleBinIsRestoredAndPurged() throws Exception {
      AssetEntry dash = sheet(AssetEntry.Type.VIEWSHEET, prefix + "dash");
      save(dash, viewsheet(NAME));

      AssetEntry recycled = moveToRecycleBin(dash);
      assertListed(recycled.getPath());

      // what RecycleUtils.restoreSheet does once it found the recycled sheet
      engine.changeSheet(recycled, dash, null, true);
      assertListed(dash.getPath());

      // purge, which is a remove of the recycled sheet (RepositoryObjectService.deleteNodes)
      recycled = moveToRecycleBin(dash);
      engine.removeSheet(recycled, null, true);
      assertListed();
   }

   /**
    * Restore with overwrite removes the sheet saved at the original path since. The attributes
    * only that sheet used must not list the path any more.
    */
   @Test
   void restoreWithOverwriteKeepsOnlyTheRestoredSheet() throws Exception {
      AssetEntry dash = sheet(AssetEntry.Type.VIEWSHEET, prefix + "dash");
      save(dash, viewsheet(NAME));
      AssetEntry recycled = moveToRecycleBin(dash);
      save(dash, viewsheet(CITY));
      assertEquals(Set.of(dash.getPath()), paths(city.getOuterDependencies()));

      engine.removeSheet(dash, null, true);
      engine.changeSheet(recycled, dash, null, true);

      assertListed(dash.getPath());
      assertEquals(Set.of(), paths(city.getOuterDependencies()));
   }

   /**
    * Models saved before the fix still list missing sheets. The dependency warnings leave them
    * out and still report the sheets that exist.
    */
   @Test
   void dependencyWarningsLeaveOutMissingSheets() throws Exception {
      AssetEntry gone = sheet(AssetEntry.Type.VIEWSHEET, prefix + "Gone");
      model.addOuterDependency(gone);
      name.addOuterDependency(gone);
      XEntity entity = model.getEntity(ENTITY);

      assertNull(name.getDependencyException());
      assertNull(entity.getDependencyException());
      assertDoesNotThrow(() -> modelCheck().checkModelOuterDependencies(model));

      AssetEntry live = sheet(AssetEntry.Type.VIEWSHEET, prefix + "Live");
      save(live, viewsheet(NAME));

      assertReportsOnlyLive(name.getDependencyException(), live, gone);
      assertReportsOnlyLive(entity.getDependencyException(), live, gone);
      DependencyException ex = assertThrows(DependencyException.class,
                                            () -> modelCheck().checkModelOuterDependencies(model));
      assertReportsOnlyLive(ex, live, gone);
   }

   private static void assertReportsOnlyLive(DependencyException ex, AssetEntry live,
                                             AssetEntry gone)
   {
      assertNotNull(ex);
      assertEquals(Set.of(live.getPath()), paths(ex.getEntries().toArray()));
      assertFalse(ex.getMessage().contains(gone.getName()), ex.getMessage());
   }

   private static DatasourcesBaseService modelCheck() {
      return new DatasourcesBaseService(null, null, null, null, null) {
         @Override
         public XDataSource createDataSource(
            inetsoft.web.portal.data.BaseDataSourceDefinition definition,
            XDataSource ds)
         {
            return ds;
         }
      };
   }

   private void assertListed(String... expected) {
      Set<String> paths = Set.of(expected);
      assertEquals(paths, paths(model.getOuterDependencies()), "model dependencies");
      assertEquals(paths, paths(name.getOuterDependencies()), "attribute dependencies");
   }

   private static Set<String> paths(Object[] entries) {
      return Arrays.stream(entries).map(e -> ((AssetEntry) e).getPath())
         .collect(Collectors.toSet());
   }

   private AssetEntry moveToRecycleBin(AssetEntry entry) throws Exception {
      RecycleBin recycleBin = mock(RecycleBin.class);
      RecycleUtils.moveSheetToRecycleBin(entry, null, recycleBin, true);
      ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
      verify(recycleBin).addEntry(path.capture(), anyString(), anyString(), any(), anyInt(),
                                  anyInt(), any());
      return sheet(entry.getType(), path.getValue());
   }

   private void save(AssetEntry entry, AbstractSheet sheet) throws Exception {
      engine.setSheet(entry, sheet, null, true, false, true);
   }

   private AssetEntry addFolder(String path) throws Exception {
      return addFolder(AssetEntry.Type.VIEWSHEET, path);
   }

   private AssetEntry addFolder(AssetEntry.Type sheetType, String path) throws Exception {
      AssetEntry folder = sheet(sheetType, path + "/x").getParent();
      engine.addFolder(folder, null);
      return folder;
   }

   private AssetEntry folder(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null, orgId);
   }

   private AssetEntry sheet(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null, orgId);
   }

   private AssetEntry recycleBin(AssetEntry.Type sheetType) {
      return sheet(sheetType, RecycleUtils.getRecycleBinPath("x")).getParent();
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

      // the real handler, over the mock dependency handler of BaseTestConfiguration
      @Bean
      @Primary
      DependencyHandler localDependencyHandler(XRepository repository) {
         return new LocalDependencyHandler(repository);
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

   private Principal principal;
   private String orgId;
   private String prefix;
   private TestEngine engine;
   private XLogicalModel model;
   private XAttribute name;
   private XAttribute city;
}
