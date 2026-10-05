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
package inetsoft.web.portal.data;

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.portal.model.database.events.CheckDependenciesEvent;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77460: the Data-tab dependency checks for a data source, a data source folder and a
 * multi-selection, and the multi-selection delete, had no permission check, so any user could
 * read the dependency text of any data source and delete any data source or folder. Each now
 * requires the permission the delete needs: DELETE on the data source or the folder, by its full
 * path. The selection delete checks every item before deleting any.
 * <p>
 * The repository is mocked here, but this hides no real gate: the permission checks in
 * {@link DataSourceRegistry} (removeDataSource, removeDataSourceFolder, getDataModel) are no-ops.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceDeletePermissionTest {
   private static final String FOLDER = "F";
   private static final String DS = "F/ds";
   private static final String OTHER_DS = "F/other";
   private static final String ROOT_DS = "ds";

   private final Principal principal = () -> "bob";
   private final Set<String> grants = new HashSet<>();

   private XRepository repository;
   private DataSourceRegistry registry;
   private DatasourcesService datasourcesService;
   private DataSourceBrowserService browserService;
   private DataSourceController controller;

   @BeforeEach
   void setUp() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          any(String.class), any(ResourceAction.class)))
         .thenAnswer(i -> grants.contains(key(i.getArgument(1), i.getArgument(2),
                                              i.getArgument(3))));

      repository = mock(XRepository.class);
      XDataModel model = dataModel("LM", "Hidden/SecretWorksheet");
      when(repository.getDataModel(DS)).thenReturn(model);
      XDataModel otherModel = dataModel("OtherLM", "Hidden/OtherWorksheet");
      when(repository.getDataModel(OTHER_DS)).thenReturn(otherModel);
      registry = mock(DataSourceRegistry.class);
      when(registry.getFolderTreeDataSourceNames(FOLDER)).thenReturn(List.of(DS, OTHER_DS));

      datasourcesService = spy(new DatasourcesService(
         repository, securityEngine, mock(DataSourceStatusService.class), registry,
         mock(Config.class)));
      doReturn(null).when(datasourcesService).deleteDataSource(any(), any(), anyBoolean());
      browserService = mock(DataSourceBrowserService.class);
      controller = new DataSourceController(
         datasourcesService, browserService, mock(DatabaseDatasourcesService.class),
         securityEngine, mock(DataSourceStatusService.class), mock(FileSystemService.class));
   }

   // ---- checkOuterDependencies ----

   @Test
   void sourceDependencies_withoutSourceDelete_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.READ);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.WRITE);

      assertDenied(() -> controller.checkOuterDependencies(sourceEvent(DS), principal));
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void sourceDependencies_foldered_withSourceDelete_returnsText() throws Exception {
      // the deleter of a foldered source holds DELETE on the full path, not on the leaf name
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      StringWrapper result = controller.checkOuterDependencies(sourceEvent(DS), principal);

      assertNotNull(result);
      assertTrue(result.getBody().contains("Hidden/SecretWorksheet"), result.getBody());
   }

   @Test
   void sourceDependencies_leafName_checksLeafNotFolderedSource() throws Exception {
      // the leaf name is a different resource (a root-level source of the same name)
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      assertDenied(() -> controller.checkOuterDependencies(sourceEvent(ROOT_DS), principal));
      verify(repository, never()).getDataModel(anyString());
   }

   // ---- browser/folder/checkOuterDependencies ----

   @Test
   void folderDependencies_withoutFolderDelete_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.READ);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.WRITE);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      assertDenied(() -> controller.checkDsFolderOuterDependencies(folderEvent(), principal));
      verify(registry, never()).getFolderTreeDataSourceNames(anyString());
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void folderDependencies_withFolderDelete_skipsSourcesTheUserCannotDelete() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      StringWrapper result = controller.checkDsFolderOuterDependencies(folderEvent(), principal);

      assertNotNull(result);
      assertTrue(result.getBody().contains("Hidden/SecretWorksheet"), result.getBody());
      verify(repository, never()).getDataModel(OTHER_DS);
   }

   @Test
   void folderDependencies_withFolderDelete_omitsTextOfDeniedSourceListedFirst() throws Exception {
      // the first source with dependencies ends the scan, so put the denied one first to show
      // that its text is skipped rather than merely never reached
      when(registry.getFolderTreeDataSourceNames(FOLDER)).thenReturn(List.of(OTHER_DS, DS));
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      StringWrapper result = controller.checkDsFolderOuterDependencies(folderEvent(), principal);

      assertNotNull(result);
      assertFalse(result.getBody().contains("Hidden/OtherWorksheet"), result.getBody());
      assertTrue(result.getBody().contains("Hidden/SecretWorksheet"), result.getBody());
   }

   // ---- checkOuterDependencies/selected ----

   @Test
   void selectedDependencies_withoutSourceDelete_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);

      assertDenied(() -> controller.checkDsOuterDependenciesSelected(
         request(List.of(item("ds", DS)), List.of()), principal));
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void selectedDependencies_withoutFolderDelete_isDenied() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      assertDenied(() -> controller.checkDsOuterDependenciesSelected(
         request(List.of(item("ds", DS)), List.of(item(FOLDER, FOLDER))), principal));
      verify(registry, never()).getFolderTreeDataSourceNames(anyString());
      verify(repository, never()).getDataModel(anyString());
   }

   @Test
   void selectedDependencies_withDelete_looksUpByFullPath() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);

      StringWrapper result = controller.checkDsOuterDependenciesSelected(
         request(List.of(item("ds", DS)), List.of()), principal);

      assertNotNull(result);
      assertTrue(result.getBody().contains("Hidden/SecretWorksheet"), result.getBody());
      verify(repository, never()).getDataModel(ROOT_DS);
   }

   // ---- deleteDataSources ----

   @Test
   void deleteSelected_withoutSourceDelete_deletesNothing() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);

      // the denied source comes after an allowed one, which must not be deleted either
      assertDenied(() -> controller.deleteDataSources(
         request(List.of(item("ds", DS), item("other", OTHER_DS)), List.of(item(FOLDER, FOLDER))),
         principal));
      verify(datasourcesService, never()).deleteDataSource(any(), any(), anyBoolean());
      verify(browserService, never()).deleteDataSourceFolder(any(), any(), anyBoolean(), any());
   }

   @Test
   void deleteSelected_emptyFolderWithoutFolderDelete_isDenied() throws Exception {
      assertDenied(() -> controller.deleteDataSources(
         request(List.of(), List.of(item("Empty", "Empty"))), principal));
      verify(browserService, never()).deleteDataSourceFolder(any(), any(), anyBoolean(), any());
   }

   @Test
   void deleteSelected_withDelete_deletesAll() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE, OTHER_DS, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);

      controller.deleteDataSources(
         request(List.of(item("ds", DS)), List.of(item(FOLDER, FOLDER))), principal);

      verify(datasourcesService).deleteDataSource(eq(DS), any(), eq(true));
      verify(browserService).deleteDataSourceFolder(eq(FOLDER), any(), eq(true), eq(principal));
   }

   // Bug #77731: a data source in the selected folder that the user can't delete refuses the
   // whole selection, before anything is deleted
   @Test
   void deleteSelected_withoutNestedSourceDelete_deletesNothing() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE, ROOT_DS, ResourceAction.DELETE);

      assertDenied(() -> controller.deleteDataSources(
         request(List.of(item("ds", ROOT_DS)), List.of(item(FOLDER, FOLDER))), principal));
      verify(datasourcesService, never()).deleteDataSource(any(), any(), anyBoolean());
      verify(browserService, never()).deleteDataSourceFolder(any(), any(), anyBoolean(), any());
   }

   // Bug #77731: a folder delete refused after the check isn't reported as a success
   @Test
   void deleteSelected_folderDeleteRefused_throws() throws Exception {
      grant(ResourceType.DATA_SOURCE, DS, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE, OTHER_DS, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER, ResourceAction.DELETE);
      when(browserService.deleteDataSourceFolder(eq(FOLDER), any(), eq(true), eq(principal)))
         .thenReturn(new ConnectionStatus("Permission denied to delete datasource folder"));

      MessageException thrown = assertThrows(MessageException.class, () ->
         controller.deleteDataSources(request(List.of(), List.of(item(FOLDER, FOLDER))),
                                      principal));
      assertEquals("Permission denied to delete datasource folder", thrown.getMessage());
   }

   private static XDataModel dataModel(String lmName, Object dependency) {
      XLogicalModel lm = mock(XLogicalModel.class);
      when(lm.getName()).thenReturn(lmName);
      when(lm.getOuterDependencies()).thenReturn(new Object[] { dependency });
      XDataModel model = mock(XDataModel.class);
      when(model.getLogicalModelNames()).thenReturn(new String[] { lmName });
      when(model.getLogicalModel(lmName)).thenReturn(lm);
      return model;
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

   private static CheckDependenciesEvent sourceEvent(String path) {
      CheckDependenciesEvent event = new CheckDependenciesEvent();
      event.setDatabaseName(path);
      return event;
   }

   private static CheckDependenciesEvent folderEvent() {
      CheckDependenciesEvent event = new CheckDependenciesEvent();
      event.setDatasourceFolderPath(FOLDER);
      return event;
   }

   private static SelectedDataSourceItem item(String name, String path) {
      return ImmutableSelectedDataSourceItem.builder().name(name).path(path).build();
   }

   private static SelectedDataSourcesRequest request(List<SelectedDataSourceItem> dataSources,
                                                     List<SelectedDataSourceItem> folders)
   {
      return ImmutableSelectedDataSourcesRequest.builder()
         .dataSources(dataSources).folders(folders).build();
   }
}
