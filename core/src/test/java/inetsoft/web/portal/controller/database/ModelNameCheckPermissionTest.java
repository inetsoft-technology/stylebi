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
package inetsoft.web.portal.controller.database;

import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.data.DatabaseDatasourcesController;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.portal.model.database.events.CheckDependenciesEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77430: the Data-tab name checks, dialect settings and folder dependency check took the
 * data source (and folder) from the request and were gated only by the Data portal tab, so they
 * leaked whether sources, models and folders exist, dialect support and dependency text. Each
 * now requires:
 * <ul>
 *    <li>name checks and logical model settings: READ on the source, the right every user of
 *        the calling dialogs has (renaming a logical model needs only DELETE on it, and the New
 *        Physical View dialog calls the VPM name check);</li>
 *    <li>full outer join support: the physical view editor rule, READ on the source and WRITE
 *        on it or one of its model folders, and READ on a named additional connection;</li>
 *    <li>folder outer dependencies: DELETE on the folder, which the delete it precedes needs.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ModelNameCheckPermissionTest {
   private static final String DS = "DS";
   private static final String MISSING_DS = "DS_MISSING";
   private static final String VIEW = "View";
   private static final String LM = "LM";
   private static final String FOLDER = "F";
   private static final String CONNECTION = "extra";

   private final Principal principal = () -> "bob";
   private final Set<String> grants = new HashSet<>();

   private XRepository repository;
   private XDataModel dataModel;
   private DataSourceService dataSourceService;

   @BeforeEach
   void setUp() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          any(String.class), any(ResourceAction.class)))
         .thenAnswer(i -> grants.contains(key(i.getArgument(1), i.getArgument(2),
                                              i.getArgument(3))));
      dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(DS);
      when(dataModel.getFolders()).thenReturn(new String[] { FOLDER });
      when(dataModel.getLogicalModel(LM)).thenReturn(mock(XLogicalModel.class));
      repository = mock(XRepository.class);
      when(repository.getDataModel(DS)).thenReturn(dataModel);
      dataSourceService = spy(new DataSourceService(
         mock(AssetRepository.class), securityEngine, repository, mock(DataSourceRegistry.class)));
   }

   // ---- logicalModel/checkDuplicate ----

   @Test
   void logicalModelNameCheck_withoutSourceRead_isDenied() throws Exception {
      assertDenied(() -> logicalModelController().checkLogicalModelDuplicate(DS, LM, principal));
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void logicalModelNameCheck_missingSource_isDeniedNotNotFound() throws Exception {
      assertDenied(() -> logicalModelController()
         .checkLogicalModelDuplicate(MISSING_DS, LM, principal));
   }

   @Test
   void logicalModelNameCheck_withSourceRead_returnsResult() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertTrue(logicalModelController().checkLogicalModelDuplicate(DS, LM, principal));
      assertFalse(logicalModelController().checkLogicalModelDuplicate(DS, "New", principal));
   }

   @Test
   void logicalModelNameCheck_renameWithModelDeleteOnly_isAllowed() throws Exception {
      // renaming needs only DELETE on the logical model (a per-model grant, checked on the
      // asset), so the name check must pass without WRITE on the source or any folder
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      assertTrue(logicalModelController().checkLogicalModelDuplicate(DS, LM, principal));
   }

   // ---- logicalModel/extended/checkDuplicate ----

   @Test
   void extendedLogicalModelNameCheck_withoutSourceRead_isDenied() throws Exception {
      assertDenied(() -> logicalModelController()
         .checkExtendedModelDuplicate(DS, VIEW, LM, CONNECTION, principal));
      verify(dataSourceService, never())
         .isUniqueExtendedLogicalModelName(any(), any(), any(), any());
   }

   @Test
   void extendedLogicalModelNameCheck_withSourceRead_returnsResult() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      doReturn(true).when(dataSourceService)
         .isUniqueExtendedLogicalModelName(DS, VIEW, LM, CONNECTION);

      assertFalse(logicalModelController()
         .checkExtendedModelDuplicate(DS, VIEW, LM, CONNECTION, principal));
   }

   // ---- physicalModel/checkDuplicate ----

   @Test
   void physicalViewNameCheck_withoutSourceRead_isDenied() throws Exception {
      assertDenied(() -> physicalModelController().checkLogicalModelDuplicate(DS, LM, principal));
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void physicalViewNameCheck_withSourceRead_returnsResult() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertTrue(physicalModelController().checkLogicalModelDuplicate(DS, LM, principal));
   }

   // ---- physicalModel/extended/checkDuplicate ----

   @Test
   void extendedPhysicalViewNameCheck_withoutSourceRead_isDenied() throws Exception {
      assertDenied(() -> physicalModelController()
         .checkExtendedModelDuplicate(DS, VIEW, CONNECTION, principal));
      verify(dataSourceService, never()).isUniqueExtendedPhysicalModelName(any(), any(), any());
   }

   @Test
   void extendedPhysicalViewNameCheck_withSourceRead_returnsResult() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      doReturn(false).when(dataSourceService)
         .isUniqueExtendedPhysicalModelName(DS, VIEW, CONNECTION);

      assertTrue(physicalModelController()
         .checkExtendedModelDuplicate(DS, VIEW, CONNECTION, principal));
   }

   // ---- vpm/checkDuplicate ----

   @Test
   void vpmNameCheck_withoutSourceRead_isDenied() throws Exception {
      assertDenied(() -> vpmController().checkLogicalModelDuplicate(DS, LM, principal));
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void vpmNameCheck_newPhysicalViewWithFolderWriteOnly_isAllowed() throws Exception {
      // the New Physical View dialog calls vpm/checkDuplicate; its user may write only a folder
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);

      assertTrue(vpmController().checkLogicalModelDuplicate(DS, LM, principal));
   }

   // ---- dataModelFolder/duplicateCheck ----

   @Test
   void folderNameCheck_withoutSourceRead_isDenied() throws Exception {
      DatabaseModelBrowserService browser = mock(DatabaseModelBrowserService.class);

      assertDenied(() -> datasourcesController(browser, null)
         .dataModelFolderDuplicateCheck(DS, FOLDER, principal));
      verify(browser, never()).dataModelFolderDuplicateCheck(any(), any());
   }

   @Test
   void folderNameCheck_withSourceRead_returnsResult() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      DatabaseModelBrowserService browser = mock(DatabaseModelBrowserService.class);
      when(browser.dataModelFolderDuplicateCheck(DS, FOLDER)).thenReturn(true);

      assertTrue(datasourcesController(browser, null)
         .dataModelFolderDuplicateCheck(DS, FOLDER, principal));
   }

   // ---- logicalmodel/settings ----

   @Test
   void logicalModelSettings_withoutSourceRead_isDenied() throws Exception {
      assertDenied(() -> logicalModelController().getLMHierarchyEnableProperty(DS, principal));
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   @Test
   void logicalModelSettings_withSourceReadOnly_reachesSource() throws Exception {
      // the logical model editor is also open to read-only users
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      doThrow(new IllegalStateException("reached")).when(dataSourceService).getDataSource(DS);

      assertReached(() -> logicalModelController().getLMHierarchyEnableProperty(DS, principal));
   }

   // ---- physicalmodel/fullOuterJoin ----

   @Test
   void fullOuterJoin_withSourceReadOnly_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertDenied(() -> physicalModelController().supportFullOuterJoin(DS, null, principal));
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   @Test
   void fullOuterJoin_withFolderWrite_reachesSource() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      doReturn(null).when(dataSourceService).getDataSource(DS);

      assertTrue(physicalModelController().supportFullOuterJoin(DS, null, principal));
      verify(dataSourceService).getDataSource(DS);
   }

   @Test
   void fullOuterJoin_unreadableAdditionalConnection_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE);

      assertDenied(() -> physicalModelController()
         .supportFullOuterJoin(DS, CONNECTION, principal));
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   // ---- dataModelFolder/checkOuterDependencies ----

   @Test
   void folderDependencies_withoutFolderDelete_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.READ);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      DataModelFolderManagerService folders = mock(DataModelFolderManagerService.class);

      assertDenied(() -> datasourcesController(null, folders)
         .checkOuterDependencies(folderEvent(), principal));
      verify(folders, never()).checkOuterDependencies(any(), any());
   }

   @Test
   void folderDependencies_withFolderDelete_returnsResult() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.DELETE);
      DataModelFolderManagerService folders = mock(DataModelFolderManagerService.class);
      StringWrapper text = new StringWrapper();
      when(folders.checkOuterDependencies(DS, FOLDER)).thenReturn(text);

      assertSame(text, datasourcesController(null, folders)
         .checkOuterDependencies(folderEvent(), principal));
   }

   private static String key(ResourceType type, String resource, ResourceAction action) {
      return type + ":" + resource + ":" + action;
   }

   private void grant(ResourceType type, String resource, ResourceAction action) {
      grants.add(key(type, resource, action));
   }

   private static void assertDenied(Executable executable) {
      assertThrows(SecurityException.class, executable);
   }

   private static void assertReached(Executable executable) {
      IllegalStateException e = assertThrows(IllegalStateException.class, executable);
      assertEquals("reached", e.getMessage());
   }

   private static CheckDependenciesEvent folderEvent() {
      CheckDependenciesEvent event = new CheckDependenciesEvent();
      event.setDatabaseName(DS);
      event.setDataModelFolder(FOLDER);
      return event;
   }

   private LogicalModelController logicalModelController() {
      return new LogicalModelController(
         mock(AssetRepository.class), dataSourceService, mock(LogicalModelService.class),
         mock(DatasourcesService.class), mock(LogicalModelTreeService.class),
         mock(AssetDataCache.class));
   }

   private PhysicalModelController physicalModelController() {
      return new PhysicalModelController(
         mock(RuntimePartitionService.class), mock(DatabaseTreeService.class),
         mock(PhysicalModelService.class), dataSourceService, repository,
         mock(PhysicalModelManagerService.class), mock(SecurityEngine.class));
   }

   private VPMController vpmController() {
      return new VPMController(
         mock(DataRefModelFactoryService.class), mock(DatabaseTreeService.class),
         dataSourceService, repository, mock(SecurityEngine.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         mock(ColumnCache.class), mock(QueryManagerService.class));
   }

   private DatabaseDatasourcesController datasourcesController(
      DatabaseModelBrowserService browser, DataModelFolderManagerService folders)
   {
      return new DatabaseDatasourcesController(
         mock(DatabaseDatasourcesService.class), browser, folders, dataSourceService, repository);
   }
}
