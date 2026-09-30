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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.data.DatabaseDatasourcesController;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.portal.service.database.PhysicalGraphService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77402: the portal Data-tab model-editor endpoints below took the data source, model or
 * folder from the request and were gated only by the Data portal tab. Each now applies the
 * rule the Data tab applies before it shows or edits that object type:
 * <ul>
 *    <li>physical views: WRITE on the folder the view is stored in (or the source);</li>
 *    <li>logical models: the logical model permission, on the stored model;</li>
 *    <li>VPMs: WRITE on the source;</li>
 *    <li>helpers that name only a source (inline-view SQL, cardinality, auto-join, new-model
 *        tables): READ on the source and WRITE on it or one of its model folders;</li>
 *    <li>endpoints with a physical-view runtime: the source must be the runtime's source;</li>
 *    <li>data model folder rename: WRITE and DELETE on the folder, WRITE on the new name.</li>
 * </ul>
 * Each case checks that a caller without the permission gets a permission error before any
 * side effect, and that an authorized caller still gets through the check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ModelEditorPermissionTest {
   private static final String DS = "DS";
   private static final String OTHER_DS = "DS_X";
   private static final String VIEW = "View";
   private static final String LM = "LM";
   private static final String FOLDER = "F";

   private final Principal principal = () -> "bob";
   private final Set<String> grants = new HashSet<>();
   private final Set<String> lmGrants = new HashSet<>();

   private SecurityEngine securityEngine;
   private XRepository repository;
   private AssetRepository assetRepository;
   private XDataModel dataModel;
   private DataSourceService dataSourceService;

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          any(String.class), any(ResourceAction.class)))
         .thenAnswer(i -> grants.contains(key(i.getArgument(1), i.getArgument(2),
                                              i.getArgument(3))));
      assetRepository = mock(AssetRepository.class);
      doAnswer(i -> {
         AssetEntry entry = i.getArgument(1);

         if(!lmGrants.contains(entry.getPath() + ":" + i.getArgument(2))) {
            throw new SecurityException("denied " + entry.getPath());
         }

         return null;
      }).when(assetRepository).checkAssetPermission(any(), any(AssetEntry.class),
                                                    any(ResourceAction.class));

      dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(DS);
      when(dataModel.getFolders()).thenReturn(new String[] { FOLDER });
      repository = mock(XRepository.class);
      when(repository.getDataModel(DS)).thenReturn(dataModel);
      dataSourceService = spy(new DataSourceService(
         assetRepository, securityEngine, repository, mock(DataSourceRegistry.class)));
   }

   // ---- physical view helpers that name only a source: #33, #50, #57 ----

   @Test
   void viewColumns_withoutSourcePermission_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertDenied(() -> physicalModelService().getViewColumns(DS, null, "select 1", principal));
      verify(dataSourceService, never()).getDataSource(anyString(), any());
   }

   @Test
   void viewColumns_withFolderWrite_reachesSource() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      doReturn(null).when(dataSourceService).getDataSource(DS, null);

      assertThrows(FileNotFoundException.class,
                   () -> physicalModelService().getViewColumns(DS, null, "select 1", principal));
      verify(dataSourceService).getDataSource(DS, null);
   }

   @Test
   void viewColumns_unreadableAdditionalConnection_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE);

      assertDenied(() -> physicalModelService().getViewColumns(DS, "extra", "select 1", principal));
      verify(dataSourceService, never()).getDataSource(anyString(), any());
   }

   @Test
   void cardinality_withoutSourcePermission_isDenied() throws Exception {
      CardinalityHelper helper = new CardinalityHelper();
      helper.setJoin(new JoinModel());

      assertDenied(() -> physicalModelService().getCardinality(DS, null, helper, principal));
      verify(dataSourceService, never()).getDataSource(anyString(), any());
   }

   @Test
   void cardinality_withSourceWrite_reachesSource() throws Exception {
      grantSourceReadWrite(DS);
      doReturn(null).when(dataSourceService).getDataSource(anyString(), any());
      CardinalityHelper helper = new CardinalityHelper();
      helper.setJoin(new JoinModel());

      assertNotNull(physicalModelService().getCardinality(DS, null, helper, principal));
      verify(dataSourceService).getDataSource(eq(DS), any());
   }

   @Test
   void autoJoinColumns_withoutSourcePermission_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertDenied(() -> manager(mock(RuntimePartitionService.class))
         .getAutoJoinColumns(DS, new PhysicalModelDefinition(), principal));
      verify(dataSourceService, never()).getDataSource(anyString(), any());
   }

   @Test
   void autoJoinColumns_withSourceWrite_reachesSource() throws Exception {
      grantSourceReadWrite(DS);
      doThrow(new IllegalStateException("reached")).when(dataSourceService)
         .getDataSource(anyString(), any());

      assertReached(() -> manager(mock(RuntimePartitionService.class))
         .getAutoJoinColumns(DS, new PhysicalModelDefinition(), principal));
   }

   // ---- source not tied to the runtime: #32, #61, #62 ----

   @Test
   void tableColumns_sourceOtherThanRuntime_isDenied() throws Exception {
      RuntimePartitionService runtimes = runtimes("rt", DS);
      PhysicalModelService physical = mock(PhysicalModelService.class);

      assertDenied(() -> manager(runtimes, physical)
         .loadTableColumns(OTHER_DS, "rt", "T", false));
      verify(physical, never()).getMetaDataProvider0(any(), any(), any());
   }

   @Test
   void tableColumns_runtimeSource_reachesMetadata() throws Exception {
      RuntimePartitionService runtimes = runtimes("rt", DS);
      PhysicalModelService physical = mock(PhysicalModelService.class);
      when(physical.getMetaDataProvider0(any(), any(), any()))
         .thenThrow(new IllegalStateException("reached"));

      assertReached(() -> manager(runtimes, physical).loadTableColumns(DS, "rt", "T", false));
   }

   @Test
   void graphModel_sourceOtherThanRuntime_isDenied() throws Exception {
      RuntimePartitionService runtimes = runtimes("rt", DS);
      PhysicalModelService physical = mock(PhysicalModelService.class);
      PhysicalGraphModelController controller =
         new PhysicalGraphModelController(runtimes, physical, null, null);

      assertDenied(() -> controller.physicalGraphModel(graphEvent(OTHER_DS)));
      verify(physical, never()).getDataModel(any(), any());
      verify(physical, never()).createModel(any(), any(), any(), anyBoolean());
   }

   @Test
   void graphModel_missingRuntime_isNotFound() throws Exception {
      PhysicalModelService physical = mock(PhysicalModelService.class);
      PhysicalGraphModelController controller = new PhysicalGraphModelController(
         mock(RuntimePartitionService.class), physical, null, null);

      assertThrows(FileNotFoundException.class,
                   () -> controller.physicalGraphModel(graphEvent(DS)));
      verify(physical, never()).createModel(any(), any(), any(), anyBoolean());
   }

   @Test
   void graphModel_runtimeSource_reachesModel() throws Exception {
      RuntimePartitionService runtimes = runtimes("rt", DS);
      PhysicalModelService physical = mock(PhysicalModelService.class);
      when(physical.getDataModel(any(), any())).thenThrow(new IllegalStateException("reached"));
      PhysicalGraphModelController controller =
         new PhysicalGraphModelController(runtimes, physical, null, null);

      assertReached(() -> controller.physicalGraphModel(graphEvent(DS)));
   }

   @Test
   void autoLayout_sourceOtherThanRuntime_isDenied() throws Exception {
      RuntimePartitionService runtimes = runtimes("rt", DS);
      PhysicalModelService physical = mock(PhysicalModelService.class);
      PhysicalGraphModelService graph = new PhysicalGraphModelService(runtimes, physical, null);

      assertDenied(() -> graph.layoutPhysicalModel("rt", OTHER_DS, VIEW, false));
      verify(physical, never()).createModel(any(), any(), any(), anyBoolean());
      verify(runtimes, never()).saveRuntimePartition(any());
   }

   @Test
   void autoLayout_runtimeSource_reachesModel() throws Exception {
      RuntimePartitionService runtimes = runtimes("rt", DS);
      PhysicalModelService physical = mock(PhysicalModelService.class);
      when(physical.createModel(any(), any(), any(), anyBoolean()))
         .thenThrow(new IllegalStateException("reached"));
      PhysicalGraphModelService graph = new PhysicalGraphModelService(runtimes, physical, null);

      assertReached(() -> graph.layoutPhysicalModel("rt", DS, VIEW, false));
   }

   // ---- physical view rename / save / remove use the stored folder: #37, #43, #44 ----

   @Test
   void renameView_folderFromRequestNotStored_isDenied() throws Exception {
      storedView(null);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);

      assertDenied(() -> manager(mock(RuntimePartitionService.class))
         .renameModel(DS, FOLDER, VIEW, "Renamed", null, principal));
      verify(dataModel, never()).renamePartition(any(), any(), any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void renameView_storedFolderWrite_passesCheck() throws Exception {
      storedView(FOLDER);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      when(dataModel.getPartition("Renamed")).thenReturn(new XPartition("Renamed"));

      // the name check that follows the permission check
      assertThrows(org.apache.commons.io.FileExistsException.class,
                   () -> manager(mock(RuntimePartitionService.class))
                      .renameModel(DS, null, VIEW, "Renamed", null, principal));
   }

   @Test
   void saveView_folderFromRequestNotStored_isDenied() throws Exception {
      storedView(null);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);

      assertDenied(() -> manager(mock(RuntimePartitionService.class))
         .updateAndSaveModel(DS, FOLDER, null, VIEW, viewDefinition(VIEW), principal));
      verify(dataSourceService, never()).getModelAssetEntry(any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void saveView_definitionNamesAnotherView_isDenied() throws Exception {
      storedView(null);
      grantSourceReadWrite(DS);

      assertDenied(() -> manager(mock(RuntimePartitionService.class))
         .updateAndSaveModel(DS, null, null, VIEW, viewDefinition("Other"), principal));
      verify(dataSourceService, never()).getModelAssetEntry(any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void saveView_authorized_passesCheck() throws Exception {
      storedView(null);
      grantSourceReadWrite(DS);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[0]);
      doThrow(new IllegalStateException("reached")).when(dataSourceService)
         .getModelAssetEntry(any());

      assertReached(() -> manager(runtimes("rt", DS))
         .updateAndSaveModel(DS, null, null, VIEW, viewDefinition(VIEW, "rt"), principal));
   }

   // review r1 I1 / verify F1: the save writes the runtime's partition, under its own name
   @Test
   void saveView_runtimeOfAnotherView_isDenied() throws Exception {
      storedView(FOLDER);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[0]);
      RuntimePartitionService runtimes = runtimes("rt", DS, "Secret", FOLDER);

      assertDenied(() -> manager(runtimes)
         .updateAndSaveModel(DS, FOLDER, null, VIEW, viewDefinition(VIEW, "rt"), principal));
      verify(dataSourceService, never()).getModelAssetEntry(any());
      verify(dataModel, never()).addPartition(any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void saveView_runtimeMovedToUnwritableFolder_isDenied() throws Exception {
      storedView(FOLDER);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = runtimes("rt", DS, VIEW, "G");

      assertDenied(() -> manager(runtimes)
         .updateAndSaveModel(DS, FOLDER, null, VIEW, viewDefinition(VIEW, "rt"), principal));
      verify(dataModel, never()).addPartition(any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void saveView_runtimeOfAnotherSource_isDenied() throws Exception {
      storedView(null);
      grantSourceReadWrite(DS);

      assertDenied(() -> manager(runtimes("rt", OTHER_DS))
         .updateAndSaveModel(DS, null, null, VIEW, viewDefinition(VIEW, "rt"), principal));
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void saveView_missingRuntime_isNotFound() throws Exception {
      storedView(null);
      grantSourceReadWrite(DS);

      assertThrows(FileNotFoundException.class, () -> manager(mock(RuntimePartitionService.class))
         .updateAndSaveModel(DS, null, null, VIEW, viewDefinition(VIEW, "rt"), principal));
      verify(repository, never()).updateDataModel(any());
   }

   // review r1 "same bug, other site": #36 create-and-save writes the runtime's partition too
   @Test
   void createView_runtimeOfAnotherView_isDenied() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = runtimes("rt", DS, "Secret", FOLDER);
      PhysicalModelDefinition model = viewDefinition("Unused", "rt");
      model.setFolder(FOLDER);

      assertDenied(() -> manager(runtimes).createAndSaveModel(DS, FOLDER, null, model, principal));
      verify(dataModel, never()).addPartition(any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void createView_authorized_saves() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = runtimes("rt", DS, "NewView", FOLDER);
      PhysicalModelDefinition model = viewDefinition("NewView", "rt");
      model.setFolder(FOLDER);
      doThrow(new IllegalStateException("reached")).when(dataSourceService)
         .getModelAssetEntry(any());

      assertReached(() -> manager(runtimes).createAndSaveModel(DS, FOLDER, null, model, principal));
      verify(dataModel).addPartition(argThat(p -> "NewView".equals(p.getName())));
   }

   @Test
   void createView_runtimeInOtherFolder_isDenied() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = runtimes("rt", DS, "NewView", "G");
      PhysicalModelDefinition model = viewDefinition("NewView", "rt");
      model.setFolder(FOLDER);

      assertDenied(() -> manager(runtimes).createAndSaveModel(DS, FOLDER, null, model, principal));
      verify(dataModel, never()).addPartition(any());
      verify(repository, never()).updateDataModel(any());
   }

   // review r2 R2-1: an extended view is written under its base view, so the base view's
   // stored folder is what needs WRITE, not the folder in the request
   @Test
   void createExtendedView_baseViewNotWritable_isDenied() throws Exception {
      XPartition base = baseView(null);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = runtimes("rt", DS, "Ext", FOLDER);
      PhysicalModelDefinition model = viewDefinition("Ext", "rt");
      model.setFolder(FOLDER);

      assertDenied(() -> manager(runtimes).createAndSaveModel(DS, FOLDER, VIEW, model, principal));
      verify(base, never()).addPartition(any(), anyBoolean());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void createExtendedViewRuntime_baseViewNotWritable_isDenied() throws Exception {
      baseView(null);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = mock(RuntimePartitionService.class);
      PhysicalModelDefinition model = viewDefinition("Ext");
      model.setFolder(FOLDER);

      assertDenied(() -> manager(runtimes).createModel(DS, VIEW, model, principal));
      verify(runtimes, never()).createModel(any(), any());
   }

   @Test
   void createExtendedView_baseViewWritable_saves() throws Exception {
      XPartition base = baseView(FOLDER);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      RuntimePartitionService runtimes = runtimes("rt", DS, "Ext", null);
      PhysicalModelDefinition model = viewDefinition("Ext", "rt");
      doThrow(new IllegalStateException("reached")).when(dataSourceService)
         .getModelAssetEntry(any());

      // the request names no folder, the base view's folder is what is checked
      assertReached(() -> manager(runtimes).createAndSaveModel(DS, null, VIEW, model, principal));
      verify(base).addPartition(argThat(p -> "Ext".equals(p.getName())), eq(false));
      verify(repository).updateDataModel(dataModel);
   }

   @Test
   void removeView_folderFromRequestNotStored_isDenied() throws Exception {
      storedView(null);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);

      assertDenied(() -> manager(mock(RuntimePartitionService.class))
         .removeModel(DS, FOLDER, VIEW, null, principal));
      verify(dataModel, never()).removePartition(any());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void removeView_storedFolderWrite_passesCheck() throws Exception {
      storedView(FOLDER);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      when(dataModel.partitionIsUsed(VIEW)).thenReturn(true);

      // passes the check, then stops because the view is in use
      assertFalse(manager(mock(RuntimePartitionService.class))
                     .removeModel(DS, null, VIEW, null, principal));
   }

   // ---- logical models: #12, #13, #23 ----

   @Test
   void updateLogicalModel_definitionNamesAnotherModel_isDenied() throws Exception {
      storedLogicalModel(null);
      lmGrants.add(DS + "/" + LM + ":WRITE");

      assertDenied(() -> logicalModelService()
         .updateModel(DS, null, LM, lmDefinition("Other", null), null, principal));
      verify(repository, never()).updateDataModel(any());
      verify(dataModel, never()).addLogicalModel(any());
   }

   @Test
   void updateLogicalModel_moveToUnwritableFolder_isDenied() throws Exception {
      storedLogicalModel(null);
      lmGrants.add(DS + "/" + LM + ":WRITE");

      assertDenied(() -> logicalModelService()
         .updateModel(DS, FOLDER, LM, lmDefinition(LM, FOLDER), null, principal));
      verify(repository, never()).updateDataModel(any());
      verify(dataModel, never()).addLogicalModel(any());
   }

   @Test
   void updateLogicalModel_moveWithoutDelete_isDenied() throws Exception {
      storedLogicalModel(null);
      lmGrants.add(DS + "/" + LM + ":WRITE");
      lmGrants.add(DS + "/" + FOLDER + "/" + LM + ":WRITE");

      assertDenied(() -> logicalModelService()
         .updateModel(DS, FOLDER, LM, lmDefinition(LM, FOLDER), null, principal));
      verify(repository, never()).updateDataModel(any());
      verify(dataModel, never()).addLogicalModel(any());
   }

   @Test
   void updateLogicalModel_authorized_passesCheck() throws Exception {
      storedLogicalModel(FOLDER);
      lmGrants.add(DS + "/" + FOLDER + "/" + LM + ":WRITE");
      DataSourceService sources = mock(DataSourceService.class);
      when(sources.getModelAssetEntry(any())).thenThrow(new IllegalStateException("reached"));

      assertReached(() -> logicalModelService(sources)
         .updateModel(DS, FOLDER, LM, lmDefinition(LM, FOLDER), null, principal));
   }

   // same rule for an extended logical model: it is written under its base model
   @Test
   void createExtendedLogicalModel_baseModelFolderNotWritable_isDenied() throws Exception {
      XLogicalModel base = baseLogicalModel(null);
      lmGrants.add(DS + "/" + FOLDER + "/Ext:WRITE");
      DataSourceService sources = mock(DataSourceService.class);

      assertDenied(() -> logicalModelService(sources)
         .createModel(DS, FOLDER, VIEW, lmDefinition("Ext", FOLDER), LM, principal));
      verify(base, never()).addLogicalModel(any(), anyBoolean());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void createExtendedLogicalModel_baseModelFolderWritable_saves() throws Exception {
      XLogicalModel base = baseLogicalModel(null);
      lmGrants.add(DS + "/Ext:WRITE");
      DataSourceService sources = mock(DataSourceService.class);
      when(sources.getModelAssetEntry(any())).thenThrow(new IllegalStateException("reached"));

      assertReached(() -> logicalModelService(sources)
         .createModel(DS, FOLDER, VIEW, lmDefinition("Ext", FOLDER), LM, principal));
      verify(base).addLogicalModel(argThat(m -> "Ext".equals(m.getName())), eq(false));
      verify(repository).updateDataModel(dataModel);
   }

   @Test
   void logicalModelTables_withoutPermission_isDenied() throws Exception {
      storedLogicalModel(null);
      LogicalModelTreeService tree = mock(LogicalModelTreeService.class);

      assertDenied(() -> logicalModelController(tree)
         .getPhysicalModelTablesTree(tablesEvent(LM, VIEW), principal));
      verify(tree, never()).getPhysicalModelTree(any(), any(), any(), any(), any());
   }

   @Test
   void logicalModelTables_logicalModelRead_isAllowed() throws Exception {
      storedLogicalModel(null);
      lmGrants.add(DS + "/" + LM + ":READ");
      LogicalModelTreeService tree = mock(LogicalModelTreeService.class);

      logicalModelController(tree).getPhysicalModelTablesTree(tablesEvent(LM, VIEW), principal);
      verify(tree).getPhysicalModelTree(DS, VIEW, LM, null, null);
   }

   @Test
   void logicalModelTables_otherViewThanModel_needsEditPermission() throws Exception {
      storedLogicalModel(null);
      lmGrants.add(DS + "/" + LM + ":READ");
      LogicalModelTreeService tree = mock(LogicalModelTreeService.class);

      assertDenied(() -> logicalModelController(tree)
         .getPhysicalModelTablesTree(tablesEvent(LM, "OtherView"), principal));
      verify(tree, never()).getPhysicalModelTree(any(), any(), any(), any(), any());
   }

   @Test
   void logicalModelTables_newModelWithSourceWrite_isAllowed() throws Exception {
      grantSourceReadWrite(DS);
      LogicalModelTreeService tree = mock(LogicalModelTreeService.class);

      logicalModelController(tree).getPhysicalModelTablesTree(tablesEvent("NewLM", VIEW), principal);
      verify(tree).getPhysicalModelTree(DS, VIEW, "NewLM", null, null);
   }

   @Test
   void logicalModelDependencies_withoutRead_isDenied() throws Exception {
      storedLogicalModel(null);
      DatasourcesService datasources = mock(DatasourcesService.class);

      assertDenied(() -> logicalModelController(mock(LogicalModelTreeService.class), datasources)
         .checkOuterDependencies(dependenciesEvent(), principal));
      verify(datasources, never()).checkModelOuterDependencies(any());
   }

   @Test
   void logicalModelDependencies_withRead_isAllowed() throws Exception {
      storedLogicalModel(null);
      lmGrants.add(DS + "/" + LM + ":READ");
      DatasourcesService datasources = mock(DatasourcesService.class);

      assertNull(logicalModelController(mock(LogicalModelTreeService.class), datasources)
                    .checkOuterDependencies(dependenciesEvent(), principal));
      verify(datasources).checkModelOuterDependencies(any());
   }

   // ---- VPM editor: #79, #81, #87, #92 ----

   @Test
   void vpmGet_sourceReadOnly_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertDenied(() -> vpmController(mock(DatabaseTreeService.class))
         .getModel(DS, "VPM", principal));
      verify(dataModel, never()).getVirtualPrivateModel(any());
   }

   @Test
   void vpmGet_sourceWrite_passesCheck() throws Exception {
      grantSourceReadWrite(DS);

      // passes the check, then the VPM does not exist
      assertThrows(FileNotFoundException.class, () -> vpmController(
         mock(DatabaseTreeService.class)).getModel(DS, "VPM", principal));
      verify(dataModel).getVirtualPrivateModel("VPM");
   }

   @Test
   void vpmTablePath_sourceReadOnly_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertDenied(() -> vpmController(mock(DatabaseTreeService.class))
         .getTablePath(DS, new StringWrapper(), principal));
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   @Test
   void vpmTablePath_sourceWrite_reachesSource() throws Exception {
      grantSourceReadWrite(DS);
      doThrow(new IllegalStateException("reached")).when(dataSourceService).getDataSource(DS);

      assertReached(() -> vpmController(mock(DatabaseTreeService.class))
         .getTablePath(DS, new StringWrapper(), principal));
   }

   @Test
   void vpmTest_sourceReadOnly_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);

      assertDenied(() -> vpmController(mock(DatabaseTreeService.class))
         .test(vpmTestEvent(), principal));
      verify(repository, never()).getDataModel(any());
   }

   @Test
   void vpmTest_sourceWrite_reachesModel() throws Exception {
      grantSourceReadWrite(DS);
      when(repository.getDataModel(DS)).thenThrow(new IllegalStateException("reached"));

      assertReached(() -> vpmController(mock(DatabaseTreeService.class))
         .test(vpmTestEvent(), principal));
   }

   @Test
   void vpmHiddenColumnTree_aliasNodeOfUnwritableSource_isDenied() throws Exception {
      grantSourceReadWrite(DS);
      DatabaseTreeService tree = mock(DatabaseTreeService.class);
      when(tree.isAliasNode(any())).thenReturn(false);

      assertDenied(() -> vpmController(tree)
         .getAvailableTreeNodes(aliasNode(OTHER_DS, DS + "/T"), principal));
      verify(tree, never()).getAlias(any());
   }

   @Test
   void vpmHiddenColumnTree_physicalModelAndAliasTableNodesOfUnwritableSource_areDenied()
      throws Exception
   {
      grantSourceReadWrite(DS);
      DatabaseTreeService tree = mock(DatabaseTreeService.class);

      for(String type : new String[] { DatabaseTreeNodeType.PHYSICAL_MODEL,
                                       DatabaseTreeNodeType.ALIAS_TABLE })
      {
         DatabaseTreeNode node = aliasNode(OTHER_DS, DS + "/T");
         node.setType(type);
         assertDenied(() -> vpmController(tree).getAvailableTreeNodes(node, principal));
      }

      verify(tree, never()).getAlias(any());
   }

   @Test
   void vpmHiddenColumnTree_aliasNodeOfWritableSource_isAllowed() throws Exception {
      grantSourceReadWrite(DS);
      DatabaseTreeService tree = mock(DatabaseTreeService.class);
      when(tree.isAliasNode(any())).thenReturn(true);
      when(tree.getAlias(any())).thenReturn(new ArrayList<>());
      DatabaseTreeNode node = aliasNode(DS, DS + "/" + DatabaseTreeService.ALIAS_NODE_NAME);

      assertTrue(vpmController(tree).getAvailableTreeNodes(node, principal).isEmpty());
      verify(tree).getAlias(node);
   }

   // ---- data model folder rename: #103 ----

   @Test
   void renameFolder_withoutDelete_isDenied() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/G", ResourceAction.WRITE);

      assertDenied(() -> browserService().renameDataModelFolder(DS, "G", FOLDER, principal));
      verifyFolderNotRenamed();
   }

   @Test
   void renameFolder_withoutWriteOnNewName_isDenied() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.DELETE);

      assertDenied(() -> browserService().renameDataModelFolder(DS, "G", FOLDER, principal));
      verifyFolderNotRenamed();
   }

   @Test
   void renameFolder_authorized_renamesFolder() throws Exception {
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.WRITE);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/" + FOLDER, ResourceAction.DELETE);
      grant(ResourceType.DATA_MODEL_FOLDER, DS + "/G", ResourceAction.WRITE);
      when(dataModel.getPartitionNames()).thenReturn(new String[0]);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[0]);

      browserService().renameDataModelFolder(DS, "G", FOLDER, principal);
      verify(dataModel).renameFolder(FOLDER, "G");
      verify(repository).updateDataModel(dataModel);
   }

   @Test
   void dataModelFolderEndpoints_requireDataTab() {
      int count = 0;

      for(Method method : DatabaseDatasourcesController.class.getDeclaredMethods()) {
         String path = mappingPath(method);

         if(path == null || !path.contains("dataModelFolder")) {
            continue;
         }

         Secured secured = method.getAnnotation(Secured.class);
         assertNotNull(secured, method.getName());
         RequiredPermission permission = secured.value()[0];
         assertEquals(ResourceType.PORTAL_TAB, permission.resourceType(), method.getName());
         assertEquals("Data", permission.resource(), method.getName());
         count++;
      }

      assertEquals(5, count);
   }

   // ---- helpers ----

   private static String key(ResourceType type, String resource, ResourceAction action) {
      return type + ":" + resource + ":" + action;
   }

   private void grant(ResourceType type, String resource, ResourceAction action) {
      grants.add(key(type, resource, action));
   }

   private void grantSourceReadWrite(String ds) {
      grant(ResourceType.DATA_SOURCE, ds, ResourceAction.READ);
      grant(ResourceType.DATA_SOURCE, ds, ResourceAction.WRITE);
   }

   private static void assertDenied(Executable executable) {
      assertThrows(SecurityException.class, executable);
   }

   private static void assertReached(Executable executable) {
      IllegalStateException ex = assertThrows(IllegalStateException.class, executable);
      assertEquals("reached", ex.getMessage());
   }

   private XPartition storedView(String folder) {
      XPartition partition = new XPartition(VIEW);
      partition.setFolder(folder);
      when(dataModel.getPartition(VIEW)).thenReturn(partition);
      return partition;
   }

   private XPartition baseView(String folder) {
      XPartition base = mock(XPartition.class);
      when(base.getName()).thenReturn(VIEW);
      when(base.getFolder()).thenReturn(folder);
      when(dataModel.getPartition(VIEW)).thenReturn(base);
      return base;
   }

   private XLogicalModel baseLogicalModel(String folder) {
      XLogicalModel base = mock(XLogicalModel.class);
      when(base.getName()).thenReturn(LM);
      when(base.getPartition()).thenReturn(VIEW);
      when(base.getFolder()).thenReturn(folder);
      when(dataModel.getLogicalModel(LM)).thenReturn(base);
      when(dataModel.getPartition(VIEW)).thenReturn(new XPartition(VIEW));
      return base;
   }

   private void storedLogicalModel(String folder) {
      XLogicalModel model = new XLogicalModel(LM);
      model.setPartition(VIEW);
      model.setFolder(folder);
      when(dataModel.getLogicalModel(LM)).thenReturn(model);
      when(dataModel.getPartition(VIEW)).thenReturn(new XPartition(VIEW));
   }

   private static PhysicalModelDefinition viewDefinition(String name) {
      return viewDefinition(name, null);
   }

   private static PhysicalModelDefinition viewDefinition(String name, String runtimeId) {
      PhysicalModelDefinition model = new PhysicalModelDefinition();
      model.setName(name);
      model.setId(runtimeId);
      return model;
   }

   private static LogicalModelDefinition lmDefinition(String name, String folder) {
      LogicalModelDefinition model = new LogicalModelDefinition();
      model.setName(name);
      model.setPartition(VIEW);
      model.setFolder(folder);
      model.setEntities(new ArrayList<>());
      return model;
   }

   private static GetModelEvent tablesEvent(String logicalName, String physicalName) {
      GetModelEvent event = new GetModelEvent();
      event.setDatasource(DS);
      event.setPhysicalName(physicalName);
      event.setLogicalName(logicalName);
      return event;
   }

   private static GetGraphModelEvent graphEvent(String ds) {
      GetGraphModelEvent event = new GetGraphModelEvent();
      event.setDatasource(ds);
      event.setPhysicalName(VIEW);
      event.setRuntimeID("rt");
      return event;
   }

   private static CheckDependenciesEvent dependenciesEvent() {
      CheckDependenciesEvent event = new CheckDependenciesEvent();
      event.setDatabaseName(DS);
      event.setModelName(LM);
      return event;
   }

   private static VpmTestEvent vpmTestEvent() {
      VpmTestEvent event = new VpmTestEvent();
      event.setDatabase(DS);
      event.setType("role");
      event.setName("Everyone");
      event.setVpm(new VPMDefinition());
      return event;
   }

   private static DatabaseTreeNode aliasNode(String database, String path) {
      DatabaseTreeNode node = new DatabaseTreeNode();
      node.setType(DatabaseTreeNodeType.ALIAS_TABLE_FOLDER);
      node.setDatabase(database);
      node.setPath(path);
      return node;
   }

   private static RuntimePartitionService runtimes(String id, String ds) {
      return runtimes(id, ds, VIEW, null);
   }

   private static RuntimePartitionService runtimes(String id, String ds, String name,
                                                   String folder)
   {
      RuntimePartitionService runtimes = mock(RuntimePartitionService.class);
      XPartition partition = new XPartition(name);
      partition.setFolder(folder);
      when(runtimes.getRuntimePartition(id))
         .thenReturn(new RuntimePartitionService.RuntimeXPartition(partition, id, ds));
      when(runtimes.getPartition(id)).thenReturn(partition);
      return runtimes;
   }

   private PhysicalModelService physicalModelService() {
      return new PhysicalModelService(mock(RuntimePartitionService.class), repository,
                                      assetRepository, dataSourceService, null);
   }

   private PhysicalModelManagerService manager(RuntimePartitionService runtimes) {
      return manager(runtimes, mock(PhysicalModelService.class));
   }

   private PhysicalModelManagerService manager(RuntimePartitionService runtimes,
                                               PhysicalModelService physical)
   {
      return new PhysicalModelManagerService(
         dataSourceService, physical, runtimes, mock(PhysicalGraphService.class), repository, null,
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));
   }

   private LogicalModelService logicalModelService() {
      return logicalModelService(dataSourceService);
   }

   private LogicalModelService logicalModelService(DataSourceService sources) {
      return new LogicalModelService(
         securityEngine, repository, sources, mock(DataRefModelFactoryService.class),
         mock(LogicalModelTreeService.class), assetRepository, mock(DependencyHandler.class),
         mock(RenameTransformHandler.class));
   }

   private LogicalModelController logicalModelController(LogicalModelTreeService tree) {
      return logicalModelController(tree, mock(DatasourcesService.class));
   }

   private LogicalModelController logicalModelController(LogicalModelTreeService tree,
                                                         DatasourcesService datasources)
   {
      return new LogicalModelController(assetRepository, dataSourceService, logicalModelService(),
                                        datasources, tree, mock(AssetDataCache.class));
   }

   private VPMController vpmController(DatabaseTreeService tree) {
      return new VPMController(
         mock(DataRefModelFactoryService.class), tree, dataSourceService, repository,
         securityEngine, mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         mock(ColumnCache.class), mock(QueryManagerService.class));
   }

   private DatabaseModelBrowserService browserService() {
      return new DatabaseModelBrowserService(
         dataSourceService, repository, null, null, securityEngine,
         mock(DataModelFolderManagerService.class), mock(RenameTransformHandler.class));
   }

   private void verifyFolderNotRenamed() throws Exception {
      verify(dataModel, never()).renameFolder(any(), any());
      verify(repository, never()).updateDataModel(any());
      verify(securityEngine, never()).setPermission(any(ResourceType.class), anyString(), any());
      verify(securityEngine, never()).removePermission(any(ResourceType.class), anyString());
   }

   private static String mappingPath(Method method) {
      String[] paths = null;

      if(method.isAnnotationPresent(PostMapping.class)) {
         paths = method.getAnnotation(PostMapping.class).value();
      }
      else if(method.isAnnotationPresent(PutMapping.class)) {
         paths = method.getAnnotation(PutMapping.class).value();
      }
      else if(method.isAnnotationPresent(DeleteMapping.class)) {
         paths = method.getAnnotation(DeleteMapping.class).value();
      }
      else if(method.isAnnotationPresent(GetMapping.class)) {
         paths = method.getAnnotation(GetMapping.class).value();
      }

      return paths == null || paths.length == 0 ? null : paths[0];
   }
}
