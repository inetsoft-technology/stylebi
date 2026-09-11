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

/*
 * General-purpose test file for RepositoryObjectService. Add further scenarios for this class here
 * rather than creating new per-scenario test classes -- keep each scenario's own rationale in a
 * comment block right above its test method(s), the way the dependency-cleanup scenario below does,
 * so the file-level comment doesn't have to be rewritten every time a new scenario is added.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.Resource;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  RepositoryObjectServiceTest.TestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryObjectServiceTest {
   /*
    * DependencyStorageService is a @Service picked up by component scan in production, so it is not
    * in the test context. The logical model path reaches it through the static
    * DependencyStorageService.getInstance() -> ConfigurationContext.getSpringBean(), which throws
    * when the bean is missing -- and deleteNodes() swallows that, silently skipping the deletion.
    */
   @Configuration
   static class TestConfiguration {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   @BeforeEach
   void setUp() {
      // the context is cached across test methods, so drop the previous method's stubbing
      reset(dependencyStorageService);
      dataSourceRegistry = mock(DataSourceRegistry.class);
      resourcePermissionService = mock(ResourcePermissionService.class);
      dependencyHandler = mock(DependencyHandler.class);
      dataModel = mock(XDataModel.class);
      securityProvider = mock(SecurityProvider.class);
      // most scenarios only care about a specific source's permission outcome -- default to
      // allowed so a test only has to stub the source(s) it wants to deny.
      when(securityProvider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      when(dataModel.getDataSource()).thenReturn(DATA_SOURCE);
      when(dataSourceRegistry.getDataModel(DATA_SOURCE)).thenReturn(dataModel);
      // the base logical model/physical view branch of checkPermission() dereferences the resource
      when(resourcePermissionService.getRepositoryResourceType(anyInt(), anyString()))
         .thenReturn(new Resource(ResourceType.DATA_SOURCE, DATA_SOURCE));

      service = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         securityProvider, resourcePermissionService, mock(XRepository.class),
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         dataSourceRegistry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         dependencyHandler, mock(RenameTransformHandler.class), mock(RepletRegistryManager.class),
         mock(DashboardRegistryManager.class));

      principal = new SRPrincipal(new IdentityID("admin", ORG_ID), new IdentityID[0],
                                  new String[0], ORG_ID, 1L);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
   }

   /*
    * Bug #75783: deleting a data model object from the EM content tree removed it from the registry
    * but left its reverse-dependency edge in DependencyStorageService behind, so the deleted object
    * was still reported as a dependent when its data source was deleted afterwards.
    *
    * These tests pin the two things that make the cleanup work, both of which are easy to break
    * silently:
    *
    * 1. The AssetEntry identifier -- QUERY_SCOPE, the object's own type and the "datasource/name"
    *    path form (no data-model-folder segment) -- must match what registers the edge, e.g.
    *    UpdateDependencyHandler.addVPMDependencies() and XDataModel.removeVirtualPrivateModel().
    *    A mismatch makes the cleanup a silent no-op, which is exactly the failure being fixed.
    * 2. deleteDependenciesKey() is called for physical views and logical models but NOT for VPMs,
    *    mirroring the portal (PhysicalModelManagerService/LogicalModelService vs. VPMController).
    */
   @Test
   void deleteVpmRemovesDependencyEdgeButNotKey() throws Exception {
      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(VPM_NAME)
         .path(DATA_SOURCE + "^" + VPM_NAME)
         .type(RepositoryEntry.VPM)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(dataModel).removeVirtualPrivateModel(VPM_NAME);
      assertEquals(identifier(AssetEntry.Type.VPM, VPM_NAME), capturedDeletedDependency());
      // the portal does not remove the VPM's own dependency key -- neither should the EM path
      verify(dependencyHandler, never()).deleteDependenciesKey(any());
   }

   @Test
   void deletePhysicalViewRemovesDependencyEdgeAndKey() throws Exception {
      XPartition physicalView = mock(XPartition.class);
      when(physicalView.getPartitionNames()).thenReturn(new String[0]);
      when(dataModel.getPartition(PHYSICAL_VIEW_NAME)).thenReturn(physicalView);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[0]);
      when(dataModel.getVirtualPrivateModelNames()).thenReturn(new String[0]);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(PHYSICAL_VIEW_NAME)
         .path(DATA_SOURCE + "^" + PHYSICAL_VIEW_NAME)
         .type(RepositoryEntry.PARTITION | RepositoryEntry.FOLDER)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(dataModel).removePartition(PHYSICAL_VIEW_NAME);
      String expected = identifier(AssetEntry.Type.PARTITION, PHYSICAL_VIEW_NAME);
      assertEquals(expected, capturedDeletedDependency());
      assertEquals(expected, capturedDeletedDependencyKey());
   }

   @Test
   void deleteLogicalModelRemovesDependencyEdgeAndKey() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getLogicalModelNames()).thenReturn(new String[0]);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(LOGICAL_MODEL_NAME)
         .path(DATA_SOURCE + "^" + LOGICAL_MODEL_NAME)
         .type(RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(dataModel).removeLogicalModel(LOGICAL_MODEL_NAME);
      String expected = identifier(AssetEntry.Type.LOGIC_MODEL, LOGICAL_MODEL_NAME);
      assertEquals(expected, capturedDeletedDependency());
      assertEquals(expected, capturedDeletedDependencyKey());
   }

   /*
    * The other half of the fix: a delete that is *rejected* because something still depends on the
    * object must not touch the dependency index. removeLogicalModel() returns the warning before it
    * reaches the cleanup, so this guards against the cleanup being hoisted above that check.
    */
   @Test
   void blockedLogicalModelDeleteLeavesDependencyIndexAlone() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getLogicalModelNames()).thenReturn(new String[0]);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      DependenciesInfo dependencies = new DependenciesInfo();
      dependencies.setDependencies(List.of(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "boundWorksheet", null)));
      when(dependencyStorageService.getWithOrg(anyString(), any())).thenReturn(dependencies);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(LOGICAL_MODEL_NAME)
         .path(DATA_SOURCE + "^" + LOGICAL_MODEL_NAME)
         .type(RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER)
         .build();

      assertNotNull(service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false),
                    "the delete should be rejected while a worksheet still depends on the model");
      verify(dataModel, never()).removeLogicalModel(anyString());
      verify(dependencyHandler, never()).deleteDependencies(any());
      verify(dependencyHandler, never()).deleteDependenciesKey(any());
   }

   /*
    * Bug #75816: the extended (child) logical model / physical view branch of deleteNodes() did no
    * dependency cleanup at all, so the #75783 fix stopped one level short.
    *
    * An extended object is identified by the three-segment "datasource/base name/extended name"
    * path -- the form used everywhere else the dependency index names one, e.g.
    * LocalDependencyHandler.getModelPath() when a backup schedule task references it, and
    * PhysicalModelManagerService.removeModel() in the portal. The flat "datasource/name" form used
    * for base objects cannot be used here: an extended name may collide with a base object's name,
    * in which case the cleanup would strip the *base* object's dependency information instead.
    */
   @Test
   void deleteExtendedLogicalModelRemovesDependencyEdgeAndKey() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(EXTENDED_NAME)
         .path(DATA_SOURCE + "^" + LOGICAL_MODEL_NAME + "^" + EXTENDED_NAME)
         .type(RepositoryEntry.LOGIC_MODEL)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(logicalModel).removeLogicalModel(EXTENDED_NAME);
      String expected = identifier(
         AssetEntry.Type.LOGIC_MODEL, LOGICAL_MODEL_NAME + "/" + EXTENDED_NAME);
      assertEquals(expected, capturedDeletedDependency());
      assertEquals(expected, capturedDeletedDependencyKey());
   }

   @Test
   void deleteExtendedPhysicalViewRemovesDependencyEdgeAndKey() throws Exception {
      XPartition physicalView = mock(XPartition.class);
      when(dataModel.getPartition(PHYSICAL_VIEW_NAME)).thenReturn(physicalView);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(EXTENDED_NAME)
         .path(DATA_SOURCE + "^" + PHYSICAL_VIEW_NAME + "^" + EXTENDED_NAME)
         .type(RepositoryEntry.PARTITION)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(physicalView).removePartition(EXTENDED_NAME);
      String expected = identifier(
         AssetEntry.Type.PARTITION, PHYSICAL_VIEW_NAME + "/" + EXTENDED_NAME);
      assertEquals(expected, capturedDeletedDependency());
      assertEquals(expected, capturedDeletedDependencyKey());
   }

   /*
    * The data model folder is not part of the dependency path: node.path() carries it as a
    * "datasource^__^folder" prefix, but the identifier stays "datasource/base name/extended name".
    */
   @Test
   void deleteExtendedLogicalModelInFolderIgnoresTheFolderSegment() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(EXTENDED_NAME)
         .path(DATA_SOURCE + XUtil.DATAMODEL_FOLDER_SPLITER + MODEL_FOLDER + "^" +
                  LOGICAL_MODEL_NAME + "^" + EXTENDED_NAME)
         .type(RepositoryEntry.LOGIC_MODEL)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(logicalModel).removeLogicalModel(EXTENDED_NAME);
      String expected = identifier(
         AssetEntry.Type.LOGIC_MODEL, LOGICAL_MODEL_NAME + "/" + EXTENDED_NAME);
      assertEquals(expected, capturedDeletedDependency());
      assertEquals(expected, capturedDeletedDependencyKey());
   }

   // ---- removeDataSourceFolder ----------------------------------------------------------------

   /*
    * Bug fix: removeDataSourceFolder used to check permission/dependencies only on a folder's
    * IMMEDIATE children, then unconditionally call the registry's own removeDataSourceFolder,
    * which recursively deletes every nested data source at ANY depth with no check of its own.
    * These tests pin the two-phase replacement: allNestedDataSourceNames() reaches every nested
    * data source regardless of depth, every one is checked before anything is deleted, and a
    * single failure anywhere refuses the whole operation instead of leaving a partial delete.
    */

   @Test
   void removeDataSourceFolder_allNestedSourcesPassChecksSoEverythingIsDeleted() {
      stubNestedDataSources(FOLDER, DIRECT_CHILD, NESTED_CHILD);
      XDataSource directDs = mock(XDataSource.class);
      when(directDs.getType()).thenReturn("JDBC");
      XDataSource nestedDs = mock(XDataSource.class);
      when(nestedDs.getType()).thenReturn("REST");
      when(dataSourceRegistry.getDataSource(DIRECT_CHILD)).thenReturn(directDs);
      when(dataSourceRegistry.getDataSource(NESTED_CHILD)).thenReturn(nestedDs);

      FolderDeleteResult result = service.removeDataSourceFolder(FOLDER, false, principal);

      assertNull(result.status());
      assertEquals(
         List.of(new DeletedDataSource(DIRECT_CHILD, "JDBC"),
                 new DeletedDataSource(NESTED_CHILD, "REST")),
         result.deletedDataSources());
      verify(dataSourceRegistry).removeDataSource(DIRECT_CHILD);
      verify(dataSourceRegistry).removeDataSource(NESTED_CHILD);
      verify(dataSourceRegistry).removeDataSourceFolder(FOLDER);
   }

   /*
    * The actual security-bug regression test: a data source nested TWO levels deep (not merely an
    * immediate child) that fails permission must refuse the WHOLE delete -- and nothing may have
    * been deleted, not even the direct child that passed its own check first. Today's
    * immediate-children-only loop would never even look at this nested item before the low-level
    * delete swept it up unconditionally; this pins that it is now checked and blocks the delete.
    */
   @Test
   void removeDataSourceFolder_aDeeplyNestedSourceFailingPermissionRefusesEverything() {
      stubNestedDataSources(FOLDER, DIRECT_CHILD, NESTED_CHILD);
      when(securityProvider.checkPermission(
         eq(principal), eq(ResourceType.DATA_SOURCE), eq(NESTED_CHILD), eq(ResourceAction.DELETE)))
         .thenReturn(false);

      FolderDeleteResult result = service.removeDataSourceFolder(FOLDER, false, principal);

      assertNotNull(result.status());
      assertTrue(result.deletedDataSources().isEmpty());
      verify(dataSourceRegistry, never()).removeDataSource(any());
      verify(dataSourceRegistry, never()).removeDataSourceFolder(any());
   }

   /*
    * Same shape as above, but the failure is a dependency conflict (force=false) on the deeply
    * nested item rather than a permission denial -- both check kinds must gate every nested item,
    * not just the permission check.
    */
   @Test
   void removeDataSourceFolder_aNestedSourceWithADependencyConflictRefusesEverything()
      throws Exception
   {
      stubNestedDataSources(FOLDER, DIRECT_CHILD, NESTED_CHILD);
      stubDependencyConflict(NESTED_CHILD);

      FolderDeleteResult result = service.removeDataSourceFolder(FOLDER, false, principal);

      assertNotNull(result.status());
      assertTrue(result.deletedDataSources().isEmpty());
      verify(dataSourceRegistry, never()).removeDataSource(any());
      verify(dataSourceRegistry, never()).removeDataSourceFolder(any());
   }

   @Test
   void removeDataSourceFolder_forceTrueBypassesADependencyConflict() throws Exception {
      stubNestedDataSources(FOLDER, DIRECT_CHILD, NESTED_CHILD);
      stubDependencyConflict(NESTED_CHILD);

      FolderDeleteResult result = service.removeDataSourceFolder(FOLDER, true, principal);

      assertNull(result.status());
      verify(dataSourceRegistry).removeDataSource(DIRECT_CHILD);
      verify(dataSourceRegistry).removeDataSource(NESTED_CHILD);
      verify(dataSourceRegistry).removeDataSourceFolder(FOLDER);
   }

   /*
    * A data source that fails to load (getDataSource throws, or returns null) must not block its
    * own deletion -- the type is only needed by callers doing cleanup keyed on it, mirroring
    * DataSourceBrowserService.getDataSources()'s own tolerance for the same failure.
    */
   @Test
   void removeDataSourceFolder_aSourceWhoseTypeCannotBeReadStillGetsDeletedWithANullType() {
      stubNestedDataSources(FOLDER, DIRECT_CHILD);
      when(dataSourceRegistry.getDataSource(DIRECT_CHILD)).thenThrow(new RuntimeException("boom"));

      FolderDeleteResult result = service.removeDataSourceFolder(FOLDER, false, principal);

      assertNull(result.status());
      assertEquals(List.of(new DeletedDataSource(DIRECT_CHILD, null)), result.deletedDataSources());
      verify(dataSourceRegistry).removeDataSource(DIRECT_CHILD);
   }

   // ---- deleteNodes / DATA_SOURCE_FOLDER --------------------------------------------------------

   /*
    * deleteNodes()'s DATA_SOURCE_FOLDER case had zero coverage before this change. These two tests
    * pin that the EM native delete path still sees a plain success/failure signal (a
    * ConnectionStatus or null) after removeDataSourceFolder's return type changed to
    * FolderDeleteResult -- the adaptation at the call site is a pure type unwrap, not a behavior
    * change.
    */
   @Test
   void deleteNodes_dataSourceFolderSuccessReturnsNull() throws Exception {
      stubNestedDataSources(FOLDER);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(FOLDER)
         .path(FOLDER)
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .build();

      assertNull(service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false));
      verify(dataSourceRegistry).removeDataSourceFolder(FOLDER);
   }

   @Test
   void deleteNodes_dataSourceFolderFailurePropagatesTheConnectionStatus() throws Exception {
      stubNestedDataSources(FOLDER, DIRECT_CHILD);
      when(securityProvider.checkPermission(
         eq(principal), eq(ResourceType.DATA_SOURCE), eq(DIRECT_CHILD), eq(ResourceAction.DELETE)))
         .thenReturn(false);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(FOLDER)
         .path(FOLDER)
         .type(RepositoryEntry.DATA_SOURCE_FOLDER)
         .build();

      assertNotNull(service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false));
      verify(dataSourceRegistry, never()).removeDataSourceFolder(any());
   }

   private void stubNestedDataSources(String folder, String... sources) {
      AssetEntry[] entries = new AssetEntry[sources.length];

      for(int i = 0; i < sources.length; i++) {
         entries[i] = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, sources[i], null);
      }

      when(dataSourceRegistry.getEntries(eq(folder + "/"), eq(AssetEntry.Type.DATA_SOURCE)))
         .thenReturn(entries);
   }

   private void stubDependencyConflict(String dataSourcePath) throws Exception {
      DependenciesInfo dependencies = new DependenciesInfo();
      dependencies.setDependencies(List.of(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "boundWorksheet", null)));
      String entryId = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, dataSourcePath, null)
         .toIdentifier();
      when(dependencyStorageService.getWithOrg(eq(entryId), any())).thenReturn(dependencies);
   }

   private String identifier(AssetEntry.Type type, String name) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, type, DATA_SOURCE + "/" + name, null)
         .toIdentifier();
   }

   private String capturedDeletedDependency() {
      ArgumentCaptor<AssetObject> captor = ArgumentCaptor.forClass(AssetObject.class);
      verify(dependencyHandler).deleteDependencies(captor.capture());
      return ((AssetEntry) captor.getValue()).toIdentifier();
   }

   private String capturedDeletedDependencyKey() {
      ArgumentCaptor<AssetObject> captor = ArgumentCaptor.forClass(AssetObject.class);
      verify(dependencyHandler).deleteDependenciesKey(captor.capture());
      return ((AssetEntry) captor.getValue()).toIdentifier();
   }

   private RepositoryObjectService service;
   private DataSourceRegistry dataSourceRegistry;
   private ResourcePermissionService resourcePermissionService;
   private DependencyHandler dependencyHandler;
   private SecurityProvider securityProvider;
   @Autowired private DependencyStorageService dependencyStorageService;
   private XDataModel dataModel;
   private Principal principal;

   private static final String ORG_ID = "bug75783_org";
   private static final String DATA_SOURCE = "Derby Embedded";
   private static final String VPM_NAME = "vpm";
   private static final String PHYSICAL_VIEW_NAME = "physicalView";
   private static final String LOGICAL_MODEL_NAME = "logicalModel";
   private static final String EXTENDED_NAME = "additionalConnection";
   private static final String MODEL_FOLDER = "modelFolder";
   private static final String FOLDER = "dsFolder";
   private static final String DIRECT_CHILD = "dsFolder/ds1";
   private static final String NESTED_CHILD = "dsFolder/sub/ds2";
}
