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
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.util.Catalog;
import inetsoft.util.FileSystemService;
import inetsoft.util.log.LogManager;
import inetsoft.web.composer.ComposerControllerErrorHandler;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77149: every tabular query dialog entry point that uses a stored data source by
 * name must require DATA_SOURCE READ on that name before the source is loaded or bound.
 * <ul>
 *    <li>E1 POST /api/composer/ws/tabular-query-dialog/refreshView</li>
 *    <li>E2 POST /api/composer/ws/tabular-query-dialog/oauth-params</li>
 *    <li>E3 POST /api/composer/ws/tabular-query-dialog/oauth-tokens</li>
 *    <li>E4 POST /api/composer/ws/tabular-query-dialog/browse</li>
 *    <li>E5 STOMP /events/ws/dialog/tabular-query-dialog-model (setModel), both the new
 *        assembly branch and the existing assembly (same-name rebind) branch</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  TabularQueryDialogPermissionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TabularQueryDialogPermissionTest {
   private static final String DS = "DS1";

   private SecurityEngine securityEngine;
   private TabularQueryDialogServiceProxy serviceProxy;
   private TabularQueryDialogController controller;
   private HttpServletRequest request;
   private final Principal principal = () -> "bob";

   private MockedStatic<TabularUtil> tabularUtil;
   private TabularQuery query;

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      serviceProxy = mock(TabularQueryDialogServiceProxy.class);
      controller = new TabularQueryDialogController(
         serviceProxy, mock(FileSystemService.class), securityEngine);
      request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(request.getSession()).thenReturn(session);
      when(session.getId()).thenReturn("session1");

      query = mock(TabularQuery.class);
      tabularUtil = mockStatic(TabularUtil.class);
      tabularUtil.when(() -> TabularUtil.createQuery(DS)).thenReturn(query);
   }

   @AfterEach
   void tearDown() {
      tabularUtil.close();
   }

   private void grantRead(boolean granted) throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), anyString(), eq(ResourceAction.READ)))
         .thenReturn(granted);
   }

   private void verifyReadChecked(String name) throws Exception {
      verify(securityEngine).checkPermission(
         principal, ResourceType.DATA_SOURCE, name, ResourceAction.READ);
   }

   private void assertDeniedBeforeLoad(Executable call) {
      assertThrows(java.lang.SecurityException.class, call);
      tabularUtil.verify(() -> TabularUtil.createQuery(any()), never());
   }

   private TabularQueryOAuthParamsRequest oauthParamsRequest() {
      return mock(TabularQueryOAuthParamsRequest.class);
   }

   private TabularQueryOAuthTokens oauthTokens(TabularView view) {
      TabularQueryOAuthTokens tokens = mock(TabularQueryOAuthTokens.class);
      when(tokens.method()).thenReturn("m");
      when(tokens.view()).thenReturn(view);
      return tokens;
   }

   // ---- E1-E4: denied ----

   @Test
   void refreshViewDeniedWithoutRead() throws Exception {
      grantRead(false);
      assertDeniedBeforeLoad(() -> controller.refreshTabularView(
         new TabularView(), DS, null, null, principal, request));
      verifyReadChecked(DS);
      tabularUtil.verify(
         () -> TabularUtil.refreshView(any(), any(), any(), any()), never());
   }

   @Test
   void oauthParamsDeniedWithoutRead() throws Exception {
      grantRead(false);
      assertDeniedBeforeLoad(
         () -> controller.getOAuthParameters(oauthParamsRequest(), DS, principal, request));
      verifyReadChecked(DS);
   }

   @Test
   void oauthTokensDeniedWithoutRead() throws Exception {
      grantRead(false);
      assertDeniedBeforeLoad(() -> controller.setOAuthTokens(
         oauthTokens(new TabularView()), DS, principal, request));
      verifyReadChecked(DS);
      tabularUtil.verify(() -> TabularUtil.setOAuthTokens(any(), any(), any(), any()), never());
   }

   @Test
   void browseDeniedWithoutRead() throws Exception {
      grantRead(false);
      assertDeniedBeforeLoad(
         () -> controller.browse(DS, "p", "/", false, new TabularView(), principal));
      verifyReadChecked(DS);
   }

   @Test
   void checkFailureIsTreatedAsDenied() throws Exception {
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), anyString(), eq(ResourceAction.READ)))
         .thenThrow(new inetsoft.sree.security.SecurityException("boom"));
      assertDeniedBeforeLoad(() -> controller.refreshTabularView(
         new TabularView(), DS, null, null, principal, request));
   }

   @ParameterizedTest
   @NullSource
   @ValueSource(strings = { "", "   " })
   void controllerRejectsMissingName(String name) throws Exception {
      grantRead(true);
      assertDeniedBeforeLoad(() -> controller.refreshTabularView(
         new TabularView(), name, null, null, principal, request));
      assertDeniedBeforeLoad(
         () -> controller.getOAuthParameters(oauthParamsRequest(), name, principal, request));
      assertDeniedBeforeLoad(() -> controller.setOAuthTokens(
         oauthTokens(new TabularView()), name, principal, request));
      assertDeniedBeforeLoad(
         () -> controller.browse(name, "p", "/", false, new TabularView(), principal));
      verifyNoInteractions(securityEngine);
   }

   // ---- E1-E4: granted, behavior unchanged ----

   @Test
   void refreshViewAllowedWithRead() throws Exception {
      grantRead(true);
      TabularView view = new TabularView();
      assertSame(view, controller.refreshTabularView(view, DS, null, null, principal, request));
      verifyReadChecked(DS);
      tabularUtil.verify(() -> TabularUtil.refreshView(same(view), same(query), any(), same(principal)));
   }

   @Test
   void oauthParamsAllowedWithRead() throws Exception {
      grantRead(true);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("license.key")).thenReturn("key1,key2");
         TabularOAuthParams params =
            controller.getOAuthParameters(oauthParamsRequest(), DS, principal, request);
         assertEquals("key1", params.license());
      }

      verifyReadChecked(DS);
      tabularUtil.verify(() -> TabularUtil.getOAuthParameters(
         any(), any(), any(), any(), any(), any(), any(), any(), same(query)));
   }

   /**
    * Bug #77399: TabularUtil.getOAuthParameters stores the token URI under "tokenUri", and the
    * composer response must carry it like the data source level endpoint does.
    */
   @Test
   void oauthParamsCopiesTokenUri() throws Exception {
      grantRead(true);
      Map<String, String> oauth = new HashMap<>();
      oauth.put("clientId", "id");
      oauth.put("clientSecret", "secret");
      oauth.put("authorizationUri", "https://auth");
      oauth.put("tokenUri", "https://token");
      tabularUtil.when(() -> TabularUtil.getOAuthParameters(
         any(), any(), any(), any(), any(), any(), any(), any(), same(query))).thenReturn(oauth);

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("license.key")).thenReturn("key1");
         TabularOAuthParams params =
            controller.getOAuthParameters(oauthParamsRequest(), DS, principal, request);
         assertEquals("https://token", params.tokenUri());
         assertEquals("https://auth", params.authorizationUri());
         assertEquals("id", params.clientId());
      }
   }

   @Test
   void oauthTokensAllowedWithRead() throws Exception {
      grantRead(true);
      TabularView view = new TabularView();
      assertSame(view, controller.setOAuthTokens(oauthTokens(view), DS, principal, request));
      verifyReadChecked(DS);
      tabularUtil.verify(() -> TabularUtil.setOAuthTokens(any(), same(query), eq("m"), same(view)));
   }

   @Test
   void browseAllowedWithRead() throws Exception {
      grantRead(true);
      tabularUtil.when(() -> TabularUtil.createQuery(DS)).thenReturn(null);
      assertNotNull(controller.browse(DS, "p", "/", false, new TabularView(), principal));
      verifyReadChecked(DS);
      tabularUtil.verify(() -> TabularUtil.createQuery(DS));
   }

   // ---- E5: setModel ----

   private final class ServiceFixture implements AutoCloseable {
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final XRepository repository = mock(XRepository.class);
      final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      final TabularQueryDialogService service = new TabularQueryDialogService(
         viewsheetService, securityEngine, repository, mock(DataSourceRegistry.class));
      final MockedStatic<WorksheetEventUtil> wsEventUtil = mockStatic(WorksheetEventUtil.class);
      final MockedStatic<AssetEventUtil> assetEventUtil = mockStatic(AssetEventUtil.class);

      ServiceFixture(Worksheet ws) throws Exception {
         when(viewsheetService.getWorksheet("rid", principal)).thenReturn(rws);
         when(rws.getWorksheet()).thenReturn(ws);
         when(rws.getID()).thenReturn("rid");
         AssetEntry entry = mock(AssetEntry.class);
         when(entry.getPath()).thenReturn("ws1");
         when(rws.getEntry()).thenReturn(entry);
         when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));
      }

      TabularQueryDialogModel model(String dataSource, String tableName) {
         TabularQueryDialogModel model = new TabularQueryDialogModel();
         model.setDataSource(dataSource);
         model.setTableName(tableName);
         model.setTabularView(new TabularView());
         return model;
      }

      void setModel(String dataSource, String tableName) throws Exception {
         service.setModel("rid", model(dataSource, tableName), principal, dispatcher);
      }

      void verifyNothingBound() {
         verifyNoInteractions(viewsheetService, repository);
         tabularUtil.verify(() -> TabularUtil.createQuery(any()), never());
         wsEventUtil.verify(() -> WorksheetEventUtil.loadTableData(any(), any(), anyBoolean(),
                                                                   anyBoolean()), never());
      }

      @Override
      public void close() {
         assetEventUtil.close();
         wsEventUtil.close();
      }
   }

   private static XDataSource dataSource(String name, long lastModified) {
      XDataSource ds = mock(XDataSource.class);
      when(ds.getFullName()).thenReturn(name);
      when(ds.getLastModified()).thenReturn(lastModified);
      return ds;
   }

   private record ExistingTable(Worksheet ws, TabularTableAssemblyInfo info, TabularQuery query) {
   }

   private static ExistingTable existingTable() {
      Worksheet ws = mock(Worksheet.class);
      TabularTableAssembly assembly = mock(TabularTableAssembly.class);
      TabularTableAssemblyInfo info = mock(TabularTableAssemblyInfo.class);
      TabularQuery existingQuery = mock(TabularQuery.class);
      XDataSource bound = dataSource(DS, 1L);
      when(ws.getAssembly("T1")).thenReturn(assembly);
      when(assembly.getName()).thenReturn("T1");
      when(assembly.getTableInfo()).thenReturn(info);
      when(info.getQuery()).thenReturn(existingQuery);
      when(existingQuery.getDataSource()).thenReturn(bound);
      return new ExistingTable(ws, info, existingQuery);
   }

   @Test
   void setModelNewAssemblyDeniedWithoutRead() throws Exception {
      grantRead(false);

      try(ServiceFixture f = new ServiceFixture(new Worksheet())) {
         assertThrows(java.lang.SecurityException.class, () -> f.setModel(DS, "T1"));
         verifyReadChecked(DS);
         f.verifyNothingBound();
      }
   }

   @Test
   void setModelSameNameRebindDeniedWithoutRead() throws Exception {
      grantRead(false);
      ExistingTable t = existingTable();

      try(ServiceFixture f = new ServiceFixture(t.ws())) {
         assertThrows(java.lang.SecurityException.class, () -> f.setModel(DS, "T1"));
         verifyReadChecked(DS);
         f.verifyNothingBound();
         verify(t.query(), never()).setDataSource(any());
      }
   }

   @ParameterizedTest
   @NullSource
   @ValueSource(strings = { "", "   " })
   void setModelRejectsMissingName(String name) throws Exception {
      grantRead(true);
      ExistingTable t = existingTable();

      try(ServiceFixture f = new ServiceFixture(t.ws())) {
         assertThrows(java.lang.SecurityException.class, () -> f.setModel(name, "T1"));
         f.verifyNothingBound();
         verifyNoInteractions(securityEngine);
      }
   }

   @Test
   void setModelNewAssemblyAllowedWithRead() throws Exception {
      grantRead(true);
      Worksheet ws = new Worksheet();

      try(ServiceFixture f = new ServiceFixture(ws)) {
         f.setModel(DS, "T1");
         verifyReadChecked(DS);
         TabularTableAssembly assembly = (TabularTableAssembly) ws.getAssembly("T1");
         assertNotNull(assembly);
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
         assertSame(query, info.getQuery());
         assertEquals(DS, info.getSourceInfo().getSource());
         f.wsEventUtil.verify(() -> WorksheetEventUtil.createAssembly(
            same(f.rws), same(assembly), same(f.dispatcher), same(principal)));
      }
   }

   @Test
   void setModelSameNameRebindAllowedWithRead() throws Exception {
      grantRead(true);
      ExistingTable t = existingTable();
      XDataSource updated = dataSource(DS, 2L);

      try(ServiceFixture f = new ServiceFixture(t.ws())) {
         when(f.repository.getDataSource(DS)).thenReturn(updated);
         f.setModel(DS, "T1");
         verifyReadChecked(DS);
         verify(t.query()).setDataSource(updated);
         f.wsEventUtil.verify(() -> WorksheetEventUtil.loadTableData(f.rws, "T1", true, true));
      }
   }

   // ---- getModel on an existing table: refreshView runs connector code ----

   @Test
   void getModelExistingTableDeniedWithoutRead() throws Exception {
      grantRead(false);
      ExistingTable t = existingTable();

      try(ServiceFixture f = new ServiceFixture(t.ws())) {
         assertThrows(java.lang.SecurityException.class,
                      () -> f.service.getModel("rid", "T1", null, principal));
         verifyReadChecked(DS);
         tabularUtil.verify(
            () -> TabularUtil.refreshView(any(), any(), any(), any()), never());
         verifyNoInteractions(f.repository);
      }
   }

   @Test
   void getModelExistingTableAllowedWithRead() throws Exception {
      grantRead(true);
      ExistingTable t = existingTable();

      try(ServiceFixture f = new ServiceFixture(t.ws())) {
         when(f.repository.getDataSourceFullNames()).thenReturn(new String[0]);
         TabularQueryDialogModel model = f.service.getModel("rid", "T1", null, principal);
         assertEquals(DS, model.getDataSource());
         verifyReadChecked(DS);
         tabularUtil.verify(
            () -> TabularUtil.refreshView(any(), same(t.query()), any(), same(principal)));
      }
   }

   @Test
   void getModelExistingTableWithoutSourceIsNotDenied() throws Exception {
      ExistingTable t = existingTable();
      when(t.query().getDataSource()).thenReturn(null);

      try(ServiceFixture f = new ServiceFixture(t.ws())) {
         when(f.repository.getDataSourceFullNames()).thenReturn(new String[0]);
         f.service.getModel("rid", "T1", null, principal);
         verifyNoInteractions(securityEngine);
         tabularUtil.verify(
            () -> TabularUtil.refreshView(any(), same(t.query()), any(), same(principal)));
      }
   }

   // ---- Tester additions (04-verify) ----

   /**
    * Data sources inside folders: the dialog list (getModel) and the actions (refreshView,
    * setModel) must make the same decision for the same full name, and the name is checked
    * verbatim (folder path kept, no "::" mapping, no leaf-only name), so folder-level grants
    * that SecurityEngine resolves through DATA_SOURCE_FOLDER parents still apply.
    */
   @Test
   void folderedNamesListAndActionsAgree() throws Exception {
      String denied = "Folder/DS1";
      String nestedAllowed = "Folder/Sub/DS2";
      String rootAllowed = "DS3";
      Set<String> readable = Set.of(nestedAllowed, rootAllowed);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), anyString(), eq(ResourceAction.READ)))
         .thenAnswer(inv -> readable.contains(inv.<String>getArgument(2)));

      // raw folder path resolves to the folder resource, which is what makes folder grants
      // inherit; a "Folder::DS1" form would resolve to a DATA_SOURCE parent instead
      Resource parent = ResourceType.DATA_SOURCE.getParent(denied);
      assertEquals(ResourceType.DATA_SOURCE_FOLDER, parent.getType());
      assertEquals("Folder", parent.getPath());

      try(ServiceFixture f = new ServiceFixture(new Worksheet())) {
         Map<String, XDataSource> sources = new LinkedHashMap<>();

         for(String name : List.of(denied, nestedAllowed, rootAllowed)) {
            XDataSource ds = dataSource(name, 1L);
            sources.put(name, ds);
            when(f.repository.getDataSource(name)).thenReturn(ds);
         }

         when(f.repository.getDataSourceFullNames()).thenReturn(sources.keySet().toArray(new String[0]));
         TabularQueryDialogModel model = f.service.getModel("rid", null, rootAllowed, principal);
         assertEquals(List.of(rootAllowed, nestedAllowed), model.getDataSources());

         for(String name : sources.keySet()) {
            boolean offered = model.getDataSources().contains(name);
            TabularView view = new TabularView();

            if(offered) {
               assertSame(view, controller.refreshTabularView(view, name, null, null, principal, request),
                          name);
            }
            else {
               assertThrows(java.lang.SecurityException.class, () -> controller.refreshTabularView(
                  view, name, null, null, principal, request), name);
               assertThrows(java.lang.SecurityException.class, () -> f.setModel(name, "T_" + name),
                            name);
               tabularUtil.verify(() -> TabularUtil.createQuery(name), never());
            }
         }

         verify(securityEngine, never()).checkPermission(
            any(Principal.class), eq(ResourceType.DATA_SOURCE), eq("Folder::DS1"), any());
         verify(securityEngine, never()).checkPermission(
            any(Principal.class), eq(ResourceType.DATA_SOURCE), eq("DS1"), any());
      }
   }

   /**
    * The deny type is java.lang.SecurityException, so over REST the composer error handler
    * returns 403 with the catalog message (the data source name and user are not echoed),
    * and over STOMP (E5) the generic handler sends an ERROR command and rethrows (fails
    * closed). The STOMP message text is deliberately not pinned (reviewer M2/M3).
    */
   @Test
   void denyMapsToForbiddenOverRestAndErrorOverStomp() throws Exception {
      grantRead(false);
      java.lang.SecurityException restDeny = assertThrows(java.lang.SecurityException.class,
         () -> controller.refreshTabularView(new TabularView(), DS, null, null, principal, request));

      java.lang.SecurityException stompDeny;

      try(ServiceFixture f = new ServiceFixture(new Worksheet())) {
         stompDeny = assertThrows(java.lang.SecurityException.class, () -> f.setModel(DS, "T1"));
      }

      assertSame(java.lang.SecurityException.class, restDeny.getClass());
      assertSame(java.lang.SecurityException.class, stompDeny.getClass());

      ComposerControllerErrorHandler handler = new ComposerControllerErrorHandler();
      handler.setLogManager(mock(LogManager.class));

      ResponseEntity<Map<String, String>> response = handler.handleSecurityException(restDeny);
      assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
      assertEquals(Catalog.getCatalog().getString("http.error.unauthorized"),
                   response.getBody().get("message"));
      assertFalse(response.getBody().get("message").contains("bob"));

      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      assertThrows(java.lang.SecurityException.class,
                   () -> handler.handleException(stompDeny, dispatcher));
      ArgumentCaptor<MessageCommand> command = ArgumentCaptor.forClass(MessageCommand.class);
      verify(dispatcher).sendCommand(command.capture());
      assertEquals(MessageCommand.Type.ERROR, command.getValue().getType());
   }

   // the READ check looks up the data sources of the registry to tell an additional connection
   // path from a data source in a folder (Bug #78249)
   @Configuration
   static class Beans {
      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         DataSourceRegistry registry = mock(DataSourceRegistry.class);
         when(registry.getDataSourceFullNames())
            .thenReturn(new String[] { DS, "Folder/DS1", "Folder/Sub/DS2", "DS3" });
         return registry;
      }
   }
}
