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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.model.database.AssetItem;
import inetsoft.web.portal.model.database.LogicalModel;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77459: the QUERY permission of a logical model is stored under
 * <tt>XUtil.getLogicalModelResourceName(ds, folder, name)</tt>, i.e. <tt>name::ds</tt> at the
 * root and <tt>name::ds^__^folder</tt> in a data model folder. The data source registry only
 * derives the folder-less form, so every site that renames a model or changes its folder has to
 * move the permission itself, from the folder of the stored model. Each case seeds an explicit
 * permission in a map-backed security engine and checks where it ends up, for root and foldered
 * models.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LogicalModelPermissionRekeyTest.TestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LogicalModelPermissionRekeyTest {
   /*
    * The folder move and folder rename look up the dependencies of each model through the static
    * DependencyStorageService.getInstance(), and the extended models of each model through the
    * static DataSourceRegistry.getRegistry(), which need the beans in the context.
    */
   @Configuration
   static class TestConfiguration {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         DataSourceRegistry registry = mock(DataSourceRegistry.class);
         when(registry.getEntries(anyString(), any(AssetEntry.Type.class)))
            .thenReturn(new AssetEntry[0]);
         return registry;
      }
   }

   private static final String DS = "DS";
   private static final String LM = "LM";
   private static final String VIEW = "View";
   private static final String FOLDER = "F";

   private final Principal principal = () -> "admin";
   private final Map<String, Permission> permissions = new HashMap<>();
   private final Map<String, XLogicalModel> models = new LinkedHashMap<>();

   private SecurityEngine securityEngine;
   private XRepository repository;
   private XDataModel dataModel;
   private DataSourceService dataSourceService;

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      when(securityEngine.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(i -> permissions.get(key(i.getArgument(0), i.getArgument(1))));
      doAnswer(i -> permissions.put(key(i.getArgument(0), i.getArgument(1)), i.getArgument(2)))
         .when(securityEngine).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(i -> permissions.remove(key(i.getArgument(0), i.getArgument(1))))
         .when(securityEngine).removePermission(any(ResourceType.class), anyString());
      // the same store behind the provider, which the data model browser move used to go through
      AuthorizationProvider authorization = mock(AuthorizationProvider.class);
      when(authorization.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(i -> permissions.get(key(i.getArgument(0), i.getArgument(1))));
      doAnswer(i -> permissions.put(key(i.getArgument(0), i.getArgument(1)), i.getArgument(2)))
         .when(authorization).setPermission(any(ResourceType.class), anyString(), any());
      doAnswer(i -> permissions.remove(key(i.getArgument(0), i.getArgument(1))))
         .when(authorization).removePermission(any(ResourceType.class), anyString());
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.getAuthorizationProvider()).thenReturn(authorization);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);

      dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(DS);
      when(dataModel.getFolders()).thenReturn(new String[] { FOLDER, "G", "Sales2024" });
      when(dataModel.getPartition(VIEW)).thenReturn(new XPartition(VIEW));
      when(dataModel.getPartitionNames()).thenReturn(new String[0]);
      when(dataModel.getLogicalModel(anyString())).thenAnswer(i -> models.get(i.getArgument(0)));
      when(dataModel.getLogicalModelNames())
         .thenAnswer(i -> models.keySet().toArray(new String[0]));
      doAnswer(i -> {
         XLogicalModel model = i.getArgument(0);
         models.put(model.getName(), model);
         return null;
      }).when(dataModel).addLogicalModel(any(XLogicalModel.class));
      doAnswer(i -> {
         XLogicalModel model = models.remove(i.getArgument(0));
         model.setName(i.getArgument(1));
         models.put(model.getName(), model);
         return null;
      }).when(dataModel).renameLogicalModel(anyString(), anyString(), any());

      repository = mock(XRepository.class);
      when(repository.getDataModel(DS)).thenReturn(dataModel);

      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataModel(DS)).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any()))
         .thenReturn(true);
      when(dataSourceService.isRootDataModelFolder(anyString()))
         .thenAnswer(i -> "/".equals(i.getArgument(0)) || "".equals(i.getArgument(0)));
   }

   // ---- portal rename (R1) ----

   @Test
   void renameModel_inFolder_movesFolderQualifiedPermission() throws Exception {
      Permission permission = storedModel(LM, FOLDER);

      logicalModelService().renameModel(DS, FOLDER, "New", LM, null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "New::DS^__^F")));
      assertFalse(permissions.containsKey(key(ResourceType.QUERY, "LM::DS^__^F")));
   }

   // the folder comes from the stored model, not from the request (e.g. the public API)
   @Test
   void renameModel_requestOmitsFolder_movesStoredFolderPermission() throws Exception {
      Permission permission = storedModel(LM, FOLDER);

      logicalModelService().renameModel(DS, null, "New", LM, null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "New::DS^__^F")));
      assertEquals(1, permissions.size());
   }

   // at the root the registry already moves the permission; the service must not lose it
   @Test
   void renameModel_atRoot_registryMoveIsKept() throws Exception {
      Permission permission = storedModel(LM, null);
      doAnswer(i -> {
         // what DataSourceRegistry.updateObject() does for the folder-less registry entry
         permissions.put(key(ResourceType.QUERY, "New::DS"),
                         permissions.remove(key(ResourceType.QUERY, "LM::DS")));
         XLogicalModel model = models.remove(LM);
         model.setName("New");
         models.put("New", model);
         return null;
      }).when(dataModel).renameLogicalModel(anyString(), anyString(), any());

      logicalModelService().renameModel(DS, null, "New", LM, null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "New::DS")));
      assertEquals(1, permissions.size());
   }

   @Test
   void renameModel_atRoot_movesPermission() throws Exception {
      Permission permission = storedModel(LM, null);

      logicalModelService().renameModel(DS, null, "New", LM, null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "New::DS")));
      assertEquals(1, permissions.size());
   }

   // ---- folder change on save (R3) ----

   @Test
   void updateModel_folderToFolder_movesPermission() throws Exception {
      Permission permission = storedModel(LM, FOLDER);

      logicalModelService().updateModel(DS, "G", LM, definition(LM, "G"), null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "LM::DS^__^G")));
      assertEquals(1, permissions.size());
   }

   @Test
   void updateModel_rootToFolder_movesPermission() throws Exception {
      Permission permission = storedModel(LM, null);

      logicalModelService().updateModel(DS, FOLDER, LM, definition(LM, FOLDER), null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "LM::DS^__^F")));
      assertEquals(1, permissions.size());
   }

   // a definition that omits the folder moves a foldered model to the root
   @Test
   void updateModel_folderToRoot_movesPermission() throws Exception {
      Permission permission = storedModel(LM, FOLDER);

      logicalModelService().updateModel(DS, null, LM, definition(LM, null), null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "LM::DS")));
      assertEquals(1, permissions.size());
   }

   @Test
   void updateModel_sameFolder_leavesPermission() throws Exception {
      Permission permission = storedModel(LM, FOLDER);

      logicalModelService().updateModel(DS, FOLDER, LM, definition(LM, FOLDER), null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "LM::DS^__^F")));
      assertEquals(1, permissions.size());
      verify(securityEngine, never()).setPermission(any(ResourceType.class), anyString(), any());
      verify(securityEngine, never()).removePermission(any(ResourceType.class), anyString());
   }

   // the public API renames and then saves with the new folder: both moves must compose
   @Test
   void renameThenUpdateWithNewFolder_endsUnderNewNameAndFolder() throws Exception {
      Permission permission = storedModel(LM, FOLDER);
      LogicalModelService service = logicalModelService();

      service.renameModel(DS, "G", "New", LM, null, principal);
      service.updateModel(DS, "G", "New", definition("New", "G"), null, principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "New::DS^__^G")));
      assertEquals(1, permissions.size());
   }

   // ---- data model folder rename (R2) ----

   @Test
   void renameDataModelFolder_movesPermissionOfEachModelInFolder() throws Exception {
      Permission inFolder = storedModel(LM, FOLDER);
      Permission atRoot = storedModel("Root", null);
      Permission otherFolder = storedModel("Other", "Sales2024");
      Permission folder = new Permission();
      permissions.put(key(ResourceType.DATA_MODEL_FOLDER, "DS/F"), folder);

      browserService().renameDataModelFolder(DS, "G", FOLDER, principal);

      verify(dataModel).renameFolder(FOLDER, "G");
      assertSame(inFolder, permissions.get(key(ResourceType.QUERY, "LM::DS^__^G")));
      assertFalse(permissions.containsKey(key(ResourceType.QUERY, "LM::DS^__^F")));
      assertSame(atRoot, permissions.get(key(ResourceType.QUERY, "Root::DS")));
      assertSame(otherFolder, permissions.get(key(ResourceType.QUERY, "Other::DS^__^Sales2024")));
      assertSame(folder, permissions.get(key(ResourceType.DATA_MODEL_FOLDER, "DS/G")));
      assertEquals(4, permissions.size());
   }

   // ---- data model browser move (C3) ----

   // the folder name starts with the model name, which the old path parsing mistook for an
   // extended model and so moved nothing
   @Test
   void moveModel_outOfFolderStartingWithModelName_movesPermission() throws Exception {
      Permission permission = storedModel("Sales", "Sales2024");
      Permission source = new Permission();
      permissions.put(key(ResourceType.DATA_SOURCE, "DS::Sales"), source);

      browserService().moveDataModels(DS, List.of(item("Sales")), "/", principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "Sales::DS")));
      assertFalse(permissions.containsKey(key(ResourceType.QUERY, "Sales::DS^__^Sales2024")));
      assertSame(source, permissions.get(key(ResourceType.DATA_SOURCE, "DS::Sales")));
   }

   @Test
   void moveModel_intoFolderNamedLikeModel_movesPermission() throws Exception {
      Permission permission = storedModel("Sales", null);

      browserService().moveDataModels(DS, List.of(item("Sales")), "Sales2024", principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "Sales::DS^__^Sales2024")));
      assertEquals(1, permissions.size());
   }

   @Test
   void moveModel_folderToFolder_movesPermission() throws Exception {
      Permission permission = storedModel(LM, FOLDER);

      browserService().moveDataModels(DS, List.of(item(LM)), "G", principal);

      assertSame(permission, permissions.get(key(ResourceType.QUERY, "LM::DS^__^G")));
      assertEquals(1, permissions.size());
   }

   // ---- logical model READ check (R4) ----

   // a root model's parent is the data source, not a data model folder named "null"
   @Test
   void readCheck_atRoot_checksDataSourceNotNullFolder() throws Exception {
      storedModel(LM, null);

      queryManagerService().checkLogicalModelReadPermission(DS, LM, principal);

      verify(securityEngine).checkPermission(principal, ResourceType.DATA_SOURCE, DS,
                                             ResourceAction.READ);
      verify(securityEngine).checkPermission(principal, ResourceType.QUERY, "LM::DS",
                                             ResourceAction.READ);
      verify(securityEngine, never()).checkPermission(any(Principal.class),
         eq(ResourceType.DATA_MODEL_FOLDER), anyString(), any(ResourceAction.class));
   }

   @Test
   void readCheck_inFolder_checksFolder() throws Exception {
      storedModel(LM, FOLDER);

      queryManagerService().checkLogicalModelReadPermission(DS, LM, principal);

      verify(securityEngine).checkPermission(principal, ResourceType.DATA_MODEL_FOLDER, "DS/F",
                                             ResourceAction.READ);
      verify(securityEngine).checkPermission(principal, ResourceType.QUERY, "LM::DS^__^F",
                                             ResourceAction.READ);
   }

   private Permission storedModel(String name, String folder) {
      XLogicalModel model = new XLogicalModel(name);
      model.setPartition(VIEW);
      model.setFolder(folder);
      model.setDataModel(dataModel);
      models.put(name, model);
      Permission permission = new Permission();
      String resource = name + "::" + DS + (folder == null ? "" : "^__^" + folder);
      permissions.put(key(ResourceType.QUERY, resource), permission);
      return permission;
   }

   private static String key(ResourceType type, String resource) {
      return type + ":" + resource;
   }

   private static LogicalModelDefinition definition(String name, String folder) {
      LogicalModelDefinition model = new LogicalModelDefinition();
      model.setName(name);
      model.setPartition(VIEW);
      model.setFolder(folder);
      model.setEntities(new ArrayList<>());
      return model;
   }

   private static AssetItem item(String name) {
      LogicalModel item = new LogicalModel();
      item.setName(name);
      return item;
   }

   private LogicalModelService logicalModelService() {
      return new LogicalModelService(
         securityEngine, repository, dataSourceService, mock(DataRefModelFactoryService.class),
         mock(LogicalModelTreeService.class), mock(AssetRepository.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));
   }

   private QueryManagerService queryManagerService() {
      return new QueryManagerService(mock(RuntimeQueryService.class), repository,
                                     dataSourceService, securityEngine, mock(ColumnCache.class));
   }

   private DatabaseModelBrowserService browserService() {
      return new DatabaseModelBrowserService(
         dataSourceService, repository, null, logicalModelService(), securityEngine,
         mock(DataModelFolderManagerService.class), mock(RenameTransformHandler.class));
   }
}
