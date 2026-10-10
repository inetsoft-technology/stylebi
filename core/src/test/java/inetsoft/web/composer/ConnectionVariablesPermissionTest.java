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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.model.LoadAssetTreeNodesEvent;
import inetsoft.web.composer.model.LoadAssetTreeNodesValidator;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.viewsheet.event.CollectParametersOverEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77401: the composer connection-variable endpoints look up, test and connect a data
 * source named by the client, and the lookup layer checks nothing, so the endpoints must check
 * DATA_SOURCE READ on every name they pass to a lookup. Labels:
 * <ul>
 *    <li>A POST /api/composer/asset_tree/set-connection-variables (throws when denied)</li>
 *    <li>B POST /api/composer/asset_tree, CUBE_TABLE entry (skips the lookup when denied)</li>
 *    <li>C POST /api/vs/bindingtree/getConnectionParameters (throws on the source, skips the
 *        name)</li>
 * </ul>
 * A server-built cube entry has path {@code ds/cube}. DATA_SOURCE READ on {@code cube}, or on
 * {@code ds/cube} as it is checked ({@code ds::cube}, Bug #78249), is not granted by these tests,
 * so the legit cases grant READ on {@code ds} only and must still work.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  ConnectionVariablesPermissionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConnectionVariablesPermissionTest {
   private static final String ALLOWED = "DS_OK";
   private static final String DENIED = "DS1";
   private static final String CUBE = "Cube";
   private static final String RID = "vs-1";

   private SecurityEngine securityEngine;
   private XRepository repository;
   private AssetRepository assetRepository;
   private AssetTreeController treeController;
   private AssetTreeService treeService;
   private final Object session = new Object();
   private XPrincipal principal;

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      repository = mock(XRepository.class);
      assetRepository = mock(AssetRepository.class);
      QueryManagerService queryManager = new QueryManagerService(
         mock(RuntimeQueryService.class), repository, mock(DataSourceService.class),
         securityEngine, mock(ColumnCache.class));
      treeController = new AssetTreeController(
         assetRepository, mock(AssetTreeServiceProxy.class), securityEngine,
         mock(LibManagerProvider.class), repository, queryManager);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(viewsheetService.getViewsheet(eq(RID), any())).thenReturn(rvs);
      treeService = new AssetTreeService(viewsheetService, assetRepository, repository,
                                         securityEngine, queryManager);

      principal = new XPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()));
      when(assetRepository.getSession()).thenReturn(session);
      when(assetRepository.getEntries(any(), any(), any(), any())).thenReturn(new AssetEntry[0]);
      JDBCDataSource allowedSource = jdbc(ALLOWED);
      JDBCDataSource deniedSource = jdbc(DENIED);
      when(repository.getDataSource(ALLOWED)).thenReturn(allowedSource);
      when(repository.getDataSource(DENIED)).thenReturn(deniedSource);
      // an unknown name misses, as XEngine.getConnectionParameters does for ds/cube or cube
      when(repository.getConnectionParameters(any(), anyString()))
         .thenThrow(new RemoteException("Data source not found"));
      when(repository.getConnectionParameters(any(), eq(":" + ALLOWED)))
         .thenReturn(new UserVariable[] { variable(XUtil.DB_PASSWORD_PREFIX + ALLOWED) });
      when(repository.getConnectionParameters(any(), eq(":" + DENIED)))
         .thenReturn(new UserVariable[] { variable(XUtil.DB_PASSWORD_PREFIX + DENIED) });
      grantRead(ALLOWED);
   }

   private void grantRead(String... names) throws Exception {
      Set<String> readable = Set.of(names);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), nullable(String.class), eq(ResourceAction.READ)))
         .thenAnswer(inv -> readable.contains(inv.<String>getArgument(2)));
   }

   private static JDBCDataSource jdbc(String name) {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getName()).thenReturn(name);
      when(ds.getFullName()).thenReturn(name);
      return ds;
   }

   private static UserVariable variable(String name) {
      UserVariable var = new UserVariable(name);
      var.setPrompt(true);
      return var;
   }

   private static void assertDenied(Executable call) {
      assertThrows(java.lang.SecurityException.class, call);
   }

   // ---- A: set-connection-variables ----

   private static VariableAssemblyModelInfo info(String name, String value) {
      VariableAssemblyModelInfo info = new VariableAssemblyModelInfo();
      info.setName(name);
      info.setType(XSchema.STRING);
      info.setValue(new Object[] { value });
      return info;
   }

   private static CollectParametersOverEvent credentials(String... sources) {
      List<VariableAssemblyModelInfo> vars = new ArrayList<>();

      for(String source : sources) {
         vars.add(info(XUtil.DB_USER_PREFIX + source, "u"));
         vars.add(info(XUtil.DB_PASSWORD_PREFIX + source, "p"));
      }

      return CollectParametersOverEvent.builder().variables(vars).build();
   }

   private void verifyNotTested(String source) throws Exception {
      verify(repository, never()).getDataSource(source);
      verify(repository, never()).testDataSource(any(), argThat(ds -> ds != null &&
         source.equals(ds.getFullName())), any());
      verify(repository, never()).connect(any(), eq(":" + source), any());
      assertNull(principal.getProperty(XUtil.DB_USER_PREFIX + source));
      assertNull(principal.getProperty(XUtil.DB_PASSWORD_PREFIX + source));
   }

   @Test
   void aDeniedSourceNotTestedOrConnected() throws Exception {
      assertDenied(() -> treeController.setConnectionVariables(credentials(DENIED), principal));
      verifyNotTested(DENIED);
   }

   @Test
   void aDeniedTextSourceNameNotTestedOrConnected() throws Exception {
      CollectParametersOverEvent event = CollectParametersOverEvent.builder()
         .variables(List.of(info(XUtil.DB_NAME_PARAMETER_NAME, DENIED)))
         .build();

      assertDenied(() -> treeController.setConnectionVariables(event, principal));
      verifyNotTested(DENIED);
   }

   @Test
   void aDeniedSourceStopsTheWholeRequest() throws Exception {
      assertDenied(() -> treeController.setConnectionVariables(
         credentials(ALLOWED, DENIED), principal));
      verifyNotTested(DENIED);
      verifyNotTested(ALLOWED);
   }

   @Test
   void aAllowedSourceTestedAndConnected() throws Exception {
      treeController.setConnectionVariables(credentials(ALLOWED), principal);

      verify(repository).testDataSource(same(session), argThat(ds -> ds != null &&
         ALLOWED.equals(ds.getFullName())), any());
      verify(repository).connect(same(session), eq(":" + ALLOWED), any());
      assertEquals("u", principal.getProperty(XUtil.DB_USER_PREFIX + ALLOWED));
   }

   // ---- Bug #77423: identity variables sent to set-connection-variables ----

   private static final String[] IDENTITY_NAMES =
      { "_USER_", "_ROLES_", "_GROUPS_", "__principal__" };
   private static final String CUSTOM = "region";

   private static List<VariableAssemblyModelInfo> identityAndCustom() {
      List<VariableAssemblyModelInfo> vars = new ArrayList<>();

      for(String name : IDENTITY_NAMES) {
         vars.add(info(name, "alice"));
      }

      vars.add(info(CUSTOM, "east"));
      return vars;
   }

   @Test
   void identityVariablesNotWrittenToPrincipal() throws Exception {
      treeController.setConnectionVariables(
         CollectParametersOverEvent.builder().variables(identityAndCustom()).build(), principal);

      for(String name : IDENTITY_NAMES) {
         assertNull(principal.getParameter(name), name);
      }

      assertEquals("east", principal.getParameter(CUSTOM));
   }

   @Test
   void identityVariablesNotPassedToTestOrConnect() throws Exception {
      List<VariableAssemblyModelInfo> vars = identityAndCustom();
      vars.add(info(XUtil.DB_USER_PREFIX + ALLOWED, "u"));
      vars.add(info(XUtil.DB_PASSWORD_PREFIX + ALLOWED, "p"));

      treeController.setConnectionVariables(
         CollectParametersOverEvent.builder().variables(vars).build(), principal);

      ArgumentCaptor<VariableTable> tested = ArgumentCaptor.forClass(VariableTable.class);
      ArgumentCaptor<VariableTable> connected = ArgumentCaptor.forClass(VariableTable.class);
      verify(repository).testDataSource(same(session), any(), tested.capture());
      verify(repository).connect(same(session), eq(":" + ALLOWED), connected.capture());

      for(VariableTable vtable : List.of(tested.getValue(), connected.getValue())) {
         for(String name : IDENTITY_NAMES) {
            assertFalse(vtable.contains(name), name);
         }

         assertEquals("east", vtable.get(CUSTOM));
         assertEquals("p", vtable.get(XUtil.DB_PASSWORD_PREFIX + ALLOWED));
      }

      for(String name : IDENTITY_NAMES) {
         assertNull(principal.getParameter(name), name);
      }

      assertEquals("east", principal.getParameter(CUSTOM));
   }

   @Test
   void identityVariablesNotPrompted() throws Exception {
      // a tabular source with $(_USER_) in a property lists _USER_ as a connection parameter
      when(repository.getConnectionParameters(any(), eq(":" + ALLOWED)))
         .thenReturn(new UserVariable[] { variable("_USER_"), variable(CUSTOM) });
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        ALLOWED, null);
      entry.setProperty("prefix", ALLOWED);

      LoadAssetTreeNodesValidator result = expand(entry);

      assertNotNull(result.parameters());
      assertEquals(List.of(CUSTOM),
                   result.parameters().stream().map(VariableAssemblyModelInfo::getName).toList());
   }

   @Test
   void onlyIdentityVariablesExpandWithoutPrompt() throws Exception {
      // a tabular source whose only variable is $(_USER_) is expanded, not prompted
      when(repository.getConnectionParameters(any(), eq(":" + ALLOWED)))
         .thenReturn(new UserVariable[] { variable("_USER_") });
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        ALLOWED, null);
      entry.setProperty("prefix", ALLOWED);

      LoadAssetTreeNodesValidator result = expand(entry);

      assertTrue(result.parameters() == null || result.parameters().isEmpty());
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   @Test
   void identityVariablesResolveToCallerInTestAndConnect() throws Exception {
      List<VariableAssemblyModelInfo> vars = identityAndCustom();
      vars.add(info(XUtil.DB_PASSWORD_PREFIX + ALLOWED, "p"));
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         treeController.setConnectionVariables(
            CollectParametersOverEvent.builder().variables(vars).build(), principal);

         ArgumentCaptor<VariableTable> tested = ArgumentCaptor.forClass(VariableTable.class);
         ArgumentCaptor<VariableTable> connected = ArgumentCaptor.forClass(VariableTable.class);
         verify(repository).testDataSource(same(session), any(), tested.capture());
         verify(repository).connect(same(session), eq(":" + ALLOWED), connected.capture());

         // $(_USER_) in the source resolves to the session user, not the client value
         assertEquals("bob", tested.getValue().get("_USER_"));
         assertEquals("bob", connected.getValue().get("_USER_"));
         assertSame(principal, connected.getValue().get("__principal__"));
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
      }
   }

   // ---- B: asset tree, CUBE_TABLE entry ----

   private static AssetEntry cubeTable(String path) {
      // as AssetEventUtil.getCubeNode builds it for the worksheet tree
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.TABLE,
                                        path, null);
      entry.setProperty("CUBE_TABLE", "true");
      entry.setProperty("source", "baseWorksheet");
      return entry;
   }

   private LoadAssetTreeNodesValidator expand(AssetEntry target) throws Exception {
      LoadAssetTreeNodesEvent event = LoadAssetTreeNodesEvent.builder()
         .targetEntry(target)
         .build();

      try(MockedStatic<MVManager> mvManager = mockStatic(MVManager.class)) {
         mvManager.when(MVManager::getManager).thenReturn(mock(MVManager.class));
         return treeController.getNodes0(true, true, true, true, true, true, true, false, false,
                                         true, event, principal, true, true, true);
      }
   }

   @Test
   void bCubeTableNamingDeniedSourceSkipsLookup() throws Exception {
      AssetEntry entry = cubeTable(DENIED);

      LoadAssetTreeNodesValidator result = expand(entry);

      verify(repository, never()).getConnectionParameters(any(), anyString());
      assertTrue(result.parameters() == null || result.parameters().isEmpty());
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   @Test
   void bCubeTableNamingAllowedSourceReturnsParameters() throws Exception {
      LoadAssetTreeNodesValidator result = expand(cubeTable(ALLOWED));

      assertNotNull(result.parameters());
      assertEquals(XUtil.DB_PASSWORD_PREFIX + ALLOWED, result.parameters().get(0).getName());
   }

   @Test
   void bServerCubeTableWithReadOnSourceOnlyExpands() throws Exception {
      // READ on ds only; ds/cube is neither granted nor a data source
      AssetEntry entry = cubeTable(ALLOWED + "/" + CUBE);

      LoadAssetTreeNodesValidator result = assertDoesNotThrow(() -> expand(entry));

      assertTrue(result.parameters() == null || result.parameters().isEmpty());
      verify(assetRepository).getEntries(same(entry), same(principal), eq(ResourceAction.READ), any());
   }

   // ---- C: binding tree getConnectionParameters ----

   private static AssetEntry vsCubeTable(String path) {
      // as AssetEventUtil.getCubeNode builds it for the viewsheet binding tree
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.TABLE,
                                        path, null);
      entry.setProperty("CUBE_TABLE", "true");
      return entry;
   }

   private static String cubeData(String path) {
      return Assembly.CUBE_VS + path;
   }

   private void verifyNoSourceOperation() throws Exception {
      verify(repository, never()).getDataSource(anyString());
      verify(repository, never()).connect(any(), anyString(), any());
      verify(repository, never()).getConnectionParameters(any(), anyString());
   }

   @Test
   void cEntryWithDeniedSourceDenied() throws Exception {
      assertDenied(() -> treeService.getConnectionParameters(
         RID, null, vsCubeTable(DENIED + "/" + DENIED), principal));
      verifyNoSourceOperation();
   }

   @Test
   void cCubeDataWithDeniedSourceDenied() throws Exception {
      assertDenied(() -> treeService.getConnectionParameters(
         RID, cubeData(DENIED + "/" + DENIED), null, principal));
      verifyNoSourceOperation();
   }

   @Test
   void cDeniedSourceNotConnectedWithStoredCredentials() throws Exception {
      principal.setProperty(XUtil.DB_USER_PREFIX + DENIED, "u");
      principal.setProperty(XUtil.DB_PASSWORD_PREFIX + DENIED, "p");

      assertDenied(() -> treeService.getConnectionParameters(
         RID, null, vsCubeTable(DENIED + "/" + DENIED), principal));
      verifyNoSourceOperation();
   }

   @Test
   void cDeniedNameUnderAllowedSourceSkipsLookup() throws Exception {
      LoadAssetTreeNodesValidator result = treeService.getConnectionParameters(
         RID, null, vsCubeTable(ALLOWED + "/" + DENIED), principal);

      assertNull(result);
      verify(repository, never()).getConnectionParameters(any(), anyString());
   }

   @Test
   void cReadableNameReturnsParameters() throws Exception {
      LoadAssetTreeNodesValidator result = treeService.getConnectionParameters(
         RID, null, vsCubeTable(ALLOWED + "/" + ALLOWED), principal);

      assertNotNull(result);
      assertEquals(XUtil.DB_PASSWORD_PREFIX + ALLOWED, result.parameters().get(0).getName());
   }

   @Test
   void cServerCubeEntryWithReadOnSourceOnlyUnchanged() throws Exception {
      LoadAssetTreeNodesValidator fromEntry = assertDoesNotThrow(() ->
         treeService.getConnectionParameters(RID, null, vsCubeTable(ALLOWED + "/" + CUBE),
                                             principal));
      LoadAssetTreeNodesValidator fromCubeData = assertDoesNotThrow(() ->
         treeService.getConnectionParameters(RID, cubeData(ALLOWED + "/" + CUBE), null,
                                             principal));

      // the cube name is not a data source, so there are no parameters, as before the fix
      assertNull(fromEntry);
      assertNull(fromCubeData);
      verify(repository, times(2)).getDataSource(ALLOWED);
   }

   @Test
   void cServerCubeEntryWithStoredCredentialsConnects() throws Exception {
      principal.setProperty(XUtil.DB_USER_PREFIX + ALLOWED, "u");
      principal.setProperty(XUtil.DB_PASSWORD_PREFIX + ALLOWED, "p");

      assertDoesNotThrow(() -> treeService.getConnectionParameters(
         RID, cubeData(ALLOWED + "/" + CUBE), null, principal));
      verify(repository).connect(same(session), eq(":" + ALLOWED), any());
   }

   // ---- legit round trip for a source inside a data source folder ----

   private static final String FOLDER_SOURCE = "Folder/DS_F";

   private void addFolderSource() throws Exception {
      JDBCDataSource folderSource = jdbc(FOLDER_SOURCE);
      when(repository.getDataSource(FOLDER_SOURCE)).thenReturn(folderSource);
      when(repository.getConnectionParameters(any(), eq(":" + FOLDER_SOURCE)))
         .thenReturn(new UserVariable[] {
            variable(XUtil.DB_USER_PREFIX + FOLDER_SOURCE),
            variable(XUtil.DB_PASSWORD_PREFIX + FOLDER_SOURCE) });
      // READ on the folder source itself only; nothing on "Folder" or the root folder
      grantRead(ALLOWED, FOLDER_SOURCE);
   }

   @Test
   void legitDataSourceNodeInFolderPromptsAndItsVariablesAreAccepted() throws Exception {
      // the worksheet asset tree data source node (isDataSource arm of getNodes0), as the
      // asset engine lists it: path and prefix are the data source full name
      addFolderSource();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        FOLDER_SOURCE, null);
      entry.setProperty("prefix", FOLDER_SOURCE);

      LoadAssetTreeNodesValidator result = assertDoesNotThrow(() -> expand(entry));

      assertNotNull(result.parameters());
      assertEquals(2, result.parameters().size());

      // the UI sends back exactly the prompted names; set-connection-variables must accept them
      List<VariableAssemblyModelInfo> answers = new ArrayList<>();

      for(VariableAssemblyModelInfo prompted : result.parameters()) {
         answers.add(info(prompted.getName(),
                          prompted.getName().startsWith(XUtil.DB_USER_PREFIX) ? "u" : "p"));
      }

      assertDoesNotThrow(() -> treeController.setConnectionVariables(
         CollectParametersOverEvent.builder().variables(answers).build(), principal));
      verify(repository).testDataSource(same(session), argThat(ds -> ds != null &&
         FOLDER_SOURCE.equals(ds.getFullName())), any());
      verify(repository).connect(same(session), eq(":" + FOLDER_SOURCE), any());
      assertEquals("u", principal.getProperty(XUtil.DB_USER_PREFIX + FOLDER_SOURCE));
   }

   @Test
   void legitCubeDataInFolderSourceWithReadOnSourceOnlyUnchanged() throws Exception {
      // viewsheet cube node data is CUBE_VS + "<ds full name>/<cube>"; the source is split at
      // the last "/", so a folder source must be checked as "Folder/DS_F", not "Folder"
      addFolderSource();

      LoadAssetTreeNodesValidator result = assertDoesNotThrow(() ->
         treeService.getConnectionParameters(RID, cubeData(FOLDER_SOURCE + "/" + CUBE), null,
                                             principal));

      assertNull(result);
      verify(repository).getDataSource(FOLDER_SOURCE);
   }

   @Test
   void cubeDataInUnreadableFolderSourceDenied() throws Exception {
      // READ on a same-named source outside the folder must not open Folder/DS_OK
      assertDenied(() -> treeService.getConnectionParameters(
         RID, cubeData("Folder/" + ALLOWED + "/" + CUBE), null, principal));
      verifyNoSourceOperation();
   }

   // the READ checks look up the data sources of the registry to tell an additional connection
   // path from a data source in a folder (Bug #78249)
   @Configuration
   static class Beans {
      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         DataSourceRegistry registry = mock(DataSourceRegistry.class);
         when(registry.getDataSourceFullNames())
            .thenReturn(new String[] { ALLOWED, DENIED, FOLDER_SOURCE });
         return registry;
      }
   }
}
