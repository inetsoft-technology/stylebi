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
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.AuthorizationProvider;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.Permission;
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
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.MoveCopyTreeNodesRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
   void setUp() throws Exception {
      // the context is cached across test methods, so drop the previous method's stubbing
      reset(dependencyStorageService);
      dataSourceRegistry = mock(DataSourceRegistry.class);
      resourcePermissionService = mock(ResourcePermissionService.class);
      dependencyHandler = mock(DependencyHandler.class);
      dataModel = mock(XDataModel.class);
      securityProvider = mock(SecurityProvider.class);
      xRepository = mock(XRepository.class);
      renameTransformHandler = mock(RenameTransformHandler.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      authorizationProvider = mock(AuthorizationProvider.class);

      when(dataModel.getDataSource()).thenReturn(DATA_SOURCE);
      when(dataSourceRegistry.getDataModel(DATA_SOURCE)).thenReturn(dataModel);
      // moveDataModel() looks the data model up through XRepository, not DataSourceRegistry
      when(xRepository.getDataModel(DATA_SOURCE)).thenReturn(dataModel);
      // the base logical model/physical view branch of checkPermission() dereferences the resource
      when(resourcePermissionService.getRepositoryResourceType(anyInt(), anyString()))
         .thenReturn(new Resource(ResourceType.DATA_SOURCE, DATA_SOURCE));
      // moveFiles()'s data-model branch renames the permission through the authorization provider,
      // not through SecurityProvider's own setPermission()/removePermission() (unlike deleteNodes())
      when(securityProvider.getAuthorizationProvider()).thenReturn(authorizationProvider);

      service = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         securityProvider, resourcePermissionService, xRepository,
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         dataSourceRegistry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         dependencyHandler, renameTransformHandler, repletRegistryManager,
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
      verify(securityProvider, never()).removePermission(eq(ResourceType.QUERY), anyString());
   }

   /*
    * Bug #77459: deleting a base logical model from the EM content tree left its explicit QUERY
    * permission behind (the registry's removeObject() never removes one), so a model re-created
    * with the same name in the same place inherited the old grants/denies. The permission is
    * stored as XUtil.getLogicalModelResourceName(ds, folder, name), so the folder must be read
    * from the stored model before it is removed, as the portal delete does.
    */
   @Test
   void deleteLogicalModelRemovesItsPermission() throws Exception {
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
      verify(securityProvider).removePermission(
         ResourceType.QUERY, LOGICAL_MODEL_NAME + "::" + DATA_SOURCE);
   }

   @Test
   void deleteLogicalModelInFolderRemovesItsFolderQualifiedPermission() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getLogicalModelNames()).thenReturn(new String[0]);
      when(logicalModel.getFolder()).thenReturn(MODEL_FOLDER);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      TreeNodeInfo node = TreeNodeInfo.builder()
         .label(LOGICAL_MODEL_NAME)
         .path(DATA_SOURCE + XUtil.DATAMODEL_FOLDER_SPLITER + MODEL_FOLDER + "^" +
                  LOGICAL_MODEL_NAME)
         .type(RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER)
         .build();

      service.deleteNodes(new TreeNodeInfo[]{ node }, principal, false, false);

      verify(dataModel).removeLogicalModel(LOGICAL_MODEL_NAME);
      verify(securityProvider).removePermission(
         ResourceType.QUERY,
         LOGICAL_MODEL_NAME + "::" + DATA_SOURCE + XUtil.DATAMODEL_FOLDER_SPLITER + MODEL_FOLDER);
      verify(securityProvider, never()).removePermission(
         ResourceType.QUERY, LOGICAL_MODEL_NAME + "::" + DATA_SOURCE);
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
      // an extended model has no QUERY permission of its own (Bug #77459)
      verify(securityProvider, never()).removePermission(eq(ResourceType.QUERY), anyString());
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

      FolderDeleteResult result = service.removeDataSourceFolderResult(FOLDER, false, principal, false);

      assertNull(result.status());
      assertEquals(
         List.of(new DeletedDataSource(DIRECT_CHILD, "JDBC"),
                 new DeletedDataSource(NESTED_CHILD, "REST")),
         result.deletedDataSources());
      verify(dataSourceRegistry).removeDataSource(DIRECT_CHILD);
      verify(dataSourceRegistry).removeDataSource(NESTED_CHILD);
      verify(dataSourceRegistry).removeDataSourceFolder(FOLDER, false);
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

      FolderDeleteResult result = service.removeDataSourceFolderResult(FOLDER, false, principal, false);

      assertNotNull(result.status());
      assertTrue(result.deletedDataSources().isEmpty());
      verify(dataSourceRegistry, never()).removeDataSource(any());
      verify(dataSourceRegistry, never()).removeDataSourceFolder(any(), anyBoolean());
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

      FolderDeleteResult result = service.removeDataSourceFolderResult(FOLDER, false, principal, false);

      assertNotNull(result.status());
      assertTrue(result.deletedDataSources().isEmpty());
      verify(dataSourceRegistry, never()).removeDataSource(any());
      verify(dataSourceRegistry, never()).removeDataSourceFolder(any(), anyBoolean());
   }

   @Test
   void removeDataSourceFolder_forceTrueBypassesADependencyConflict() throws Exception {
      stubNestedDataSources(FOLDER, DIRECT_CHILD, NESTED_CHILD);
      stubDependencyConflict(NESTED_CHILD);

      FolderDeleteResult result = service.removeDataSourceFolderResult(FOLDER, true, principal, false);

      assertNull(result.status());
      verify(dataSourceRegistry).removeDataSource(DIRECT_CHILD);
      verify(dataSourceRegistry).removeDataSource(NESTED_CHILD);
      verify(dataSourceRegistry).removeDataSourceFolder(FOLDER, false);
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

      FolderDeleteResult result = service.removeDataSourceFolderResult(FOLDER, false, principal, false);

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
      verify(dataSourceRegistry).removeDataSourceFolder(FOLDER, false);
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
      verify(dataSourceRegistry, never()).removeDataSourceFolder(any(), anyBoolean());
   }

   private void stubNestedDataSources(String folder, String... sources) {
      when(dataSourceRegistry.getFolderTreeDataSourceNames(folder))
         .thenReturn(List.of(sources));
      // these scenarios only care about a specific source's permission outcome -- default to
      // allowed so a test only has to stub the source(s) it wants to deny.
      when(securityProvider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
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

   /*
    * Bug #78204: moveFiles()'s LOGIC_MODEL/PARTITION branch guards a cross-data-source drag-and-drop
    * with moveDataModel()'s dsName/pathTo check, then (if that check accepted the move)
    * unconditionally renamed the moved model's permission.
    *
    * Two independent defects there let a logical model dropped onto an unrelated data source
    * corrupt state instead of being refused:
    *
    * 1. The dsName/pathTo check was a bare String.startsWith(), which wrongly accepts a destination
    *    whose name merely begins with the same characters as the source data source's name (e.g.
    *    "Derby EmbeddedX/g" against "Derby Embedded"). isSameOrDescendantPath() requires pathTo to
    *    equal dsName or be dsName + "/" + <folder>, so it tells "Derby Embedded's own folder" apart
    *    from "a different data source whose name happens to start the same way".
    * 2. The permission rename ran even when moveDataModel() had just recorded a refusal in the
    *    "error" list, and handleDataModelToContainsFolder()'s unguarded pathTo.substring(dsName
    *    .length()) then threw StringIndexOutOfBoundsException for any pathTo shorter than dsName --
    *    turning a should-be-refused move into an HTTP 500 instead of the refusal message. Gating the
    *    rename on info.get("error").isEmpty() fixes this for every refusal path inside
    *    moveDataModel(), not just this one.
    *
    * These three tests exercise moveFiles() through its public entry point (not the private
    * moveDataModel()/renameDataModelPermission() directly) so a regression in either defect's fix
    * is caught the same way the real EM drag-and-drop would trigger it.
    */
   @Test
   void moveOntoDifferentDataSourceWithSharedNamePrefixIsRefused() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      MessageException ex = assertThrows(MessageException.class,
         () -> moveLogicalModel(DATA_SOURCE + "X/g"),
         "a destination data source whose name merely starts with the source's name must be " +
            "refused, not silently accepted");

      assertNotNull(ex.getMessage());
      // the model's folder/permission must be left completely alone
      verify(logicalModel, never()).setFolder(any());
      verify(dataModel, never()).addLogicalModel(any());
      verify(authorizationProvider, never()).setPermission(any(), anyString(), any());
      verify(authorizationProvider, never()).removePermission(any(), anyString());
   }

   @Test
   void moveOntoUnrelatedDataSourceIsRefusedCleanlyNotWithAnException() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);

      // "Qz/g" shares no prefix with DATA_SOURCE and is shorter than it -- the exact shape that
      // made handleDataModelToContainsFolder()'s unguarded substring() throw
      // StringIndexOutOfBoundsException before the fix (Bug #78204, defect 2).
      MessageException ex = assertThrows(MessageException.class,
         () -> moveLogicalModel("Qz/g"),
         "a move to a completely unrelated data source must be refused with the normal " +
            "MessageException refusal, never an uncaught StringIndexOutOfBoundsException");

      assertNotNull(ex.getMessage());
      verify(logicalModel, never()).setFolder(any());
      verify(dataModel, never()).addLogicalModel(any());
      verify(authorizationProvider, never()).setPermission(any(), anyString(), any());
      verify(authorizationProvider, never()).removePermission(any(), anyString());
   }

   @Test
   void moveToSameDataSourceSubfolderStillSucceeds() throws Exception {
      XLogicalModel logicalModel = mock(XLogicalModel.class);
      when(logicalModel.getFolder()).thenReturn(null);
      when(logicalModel.getDataSource()).thenReturn(DATA_SOURCE);
      when(dataModel.getLogicalModel(LOGICAL_MODEL_NAME)).thenReturn(logicalModel);
      Permission permission = new Permission();
      when(authorizationProvider.getPermission(eq(ResourceType.QUERY), anyString()))
         .thenReturn(permission);
      // the destination node's owner() is null in this test, same as the real tree node for a
      // plain data-model folder target -- stub the exact (null) IdentityID overload moveFiles()
      // calls at the very end to save the destination registry
      when(repletRegistryManager.getRegistry((IdentityID) null))
         .thenReturn(mock(RepletRegistry.class));

      moveLogicalModel(DATA_SOURCE + "/sub");

      ArgumentCaptor<String> folder = ArgumentCaptor.forClass(String.class);
      verify(logicalModel).setFolder(folder.capture());
      assertEquals("sub", folder.getValue());
      verify(dataModel).addLogicalModel(logicalModel);
      verify(xRepository).updateDataModel(dataModel);
      verify(authorizationProvider).setPermission(eq(ResourceType.QUERY), anyString(), eq(permission));
      verify(authorizationProvider).removePermission(eq(ResourceType.QUERY), anyString());
   }

   private void moveLogicalModel(String pathTo) throws Exception {
      ContentRepositoryTreeNode source = ContentRepositoryTreeNode.builder()
         .label(LOGICAL_MODEL_NAME)
         .path(DATA_SOURCE + "^" + LOGICAL_MODEL_NAME)
         .type(RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER)
         .build();
      ContentRepositoryTreeNode destination = ContentRepositoryTreeNode.builder()
         .label("destination")
         .path(pathTo)
         .type(RepositoryEntry.DATA_MODEL | RepositoryEntry.FOLDER)
         .build();
      MoveCopyTreeNodesRequest request = MoveCopyTreeNodesRequest.builder()
         .source(List.of(source))
         .destination(destination)
         .build();

      service.moveFiles(request, true, principal);
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
   private XRepository xRepository;
   private RenameTransformHandler renameTransformHandler;
   private RepletRegistryManager repletRegistryManager;
   private AuthorizationProvider authorizationProvider;

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
