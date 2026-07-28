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
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

      when(dataModel.getDataSource()).thenReturn(DATA_SOURCE);
      when(dataSourceRegistry.getDataModel(DATA_SOURCE)).thenReturn(dataModel);
      // the base logical model/physical view branch of checkPermission() dereferences the resource
      when(resourcePermissionService.getRepositoryResourceType(anyInt(), anyString()))
         .thenReturn(new Resource(ResourceType.DATA_SOURCE, DATA_SOURCE));

      service = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         mock(SecurityProvider.class), resourcePermissionService, mock(XRepository.class),
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
   @Autowired private DependencyStorageService dependencyStorageService;
   private XDataModel dataModel;
   private Principal principal;

   private static final String ORG_ID = "bug75783_org";
   private static final String DATA_SOURCE = "Derby Embedded";
   private static final String VPM_NAME = "vpm";
   private static final String PHYSICAL_VIEW_NAME = "physicalView";
   private static final String LOGICAL_MODEL_NAME = "logicalModel";
}
