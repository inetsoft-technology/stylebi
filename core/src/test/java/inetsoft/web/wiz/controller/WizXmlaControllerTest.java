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

package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.DataSourceXmlaDefinition;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizXmlaCreateRequest;
import inetsoft.web.wiz.request.WizXmlaDraftRequest;
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The XMLA bridge endpoints: route contract, the per-resource gates, the password rules and the
 * audited-service delegation. The service is mocked, so every assertion is about what this
 * controller decides, not what StyleBI's service does with it.
 */
@Tag("core")
class WizXmlaControllerTest {
   // ---- contract ---------------------------------------------------------------------------

   @Test
   void mappingsMatchThePublishedContract() {
      Set<String> paths = new HashSet<>();
      String prefix = WizXmlaController.class.getAnnotation(RequestMapping.class).value()[0];

      for(Method method : WizXmlaController.class.getDeclaredMethods()) {
         GetMapping get = method.getAnnotation(GetMapping.class);
         PostMapping post = method.getAnnotation(PostMapping.class);

         if(get != null) {
            paths.add(prefix + get.value()[0]);
         }

         if(post != null) {
            paths.add(prefix + post.value()[0]);
         }
      }

      assertEquals(Set.of("/api/wiz/xmla/definition", "/api/wiz/xmla/create",
                          "/api/wiz/xmla/update", "/api/wiz/xmla/catalogs", "/api/wiz/xmla/test"),
                   paths);
   }

   @Test
   void definitionAndUpdateCarryAWiredWriteGate() throws Exception {
      assertWriteGate(WizXmlaController.class.getDeclaredMethod(
         "getXmlaDefinition", String.class, Principal.class));
      assertWriteGate(WizXmlaController.class.getDeclaredMethod(
         "updateXmla", String.class, WizXmlaDefinition.class, Principal.class));
   }

   // ---- definition -------------------------------------------------------------------------

   @Test
   void definition_isDeniedWithoutWriteAndNeverReadsTheStore() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE, "olap/sales",
                                      ResourceAction.WRITE)).thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> f.controller.getXmlaDefinition("olap/sales", f.principal));

      verifyNoInteractions(f.service);
      verifyNoInteractions(f.repository);
   }

   @Test
   void definition_neverReturnsThePasswordAndReportsDeletable() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("secret"));
      DataSourceXmlaDefinition model = new DataSourceXmlaDefinition();
      model.setName("sales");
      model.setUrl("http://olap.example/xmla");
      model.setLogin(true);
      model.setUser("analyst");
      model.setPassword("secret");
      when(f.service.getDataSourceModel("olap/sales", f.principal)).thenReturn(model);

      WizXmlaDefinition result = f.controller.getXmlaDefinition("olap/sales", f.principal);

      assertEquals("analyst", result.authentication().userName());
      assertNull(result.authentication().password(), "the stored password must never be returned");
      assertEquals("http://olap.example/xmla", result.url());
      assertTrue(result.deletable());
      assertFalse(result.toString().contains("secret"));
   }

   @Test
   void definition_refusesANonXmlaPathWith422() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("db/orders", mock(JDBCDataSource.class));

      assertThrows(UnsupportedDatasourceException.class,
                   () -> f.controller.getXmlaDefinition("db/orders", f.principal));

      verifyNoInteractions(f.service);
   }

   @Test
   void definition_reportsAMissingSourceAs404NotAGeneric500() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> f.controller.getXmlaDefinition("olap/none", f.principal));

      assertEquals(404, ex.getStatusCode().value());
   }

   // ---- create -----------------------------------------------------------------------------

   @Test
   void create_isDeniedWithoutTheCreateRuleAndWritesNothing() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.createXmla(
         new WizXmlaCreateRequest("olap", definition("sales", null)), f.principal));

      verify(f.service, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   @Test
   void create_reachesTheAuditedServiceAsAnXmlaCreateAndReturnsThePath() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.checkDuplicate("olap/sales")).thenReturn(false, true);

      WizXmlaSaveResult result = f.controller.createXmla(
         new WizXmlaCreateRequest("olap", definition("sales", "pw")), f.principal);

      assertTrue(result.ok());
      assertEquals("olap/sales", result.path());

      ArgumentCaptor<DataSourceXmlaDefinition> model =
         ArgumentCaptor.forClass(DataSourceXmlaDefinition.class);
      verify(f.service).createNewDataSource(model.capture(), eq(true), eq(f.principal));
      assertEquals("sales", model.getValue().getName());
      assertEquals("olap", model.getValue().getParentPath());
      assertEquals("pw", model.getValue().getPassword());
      assertEquals("http://olap.example/xmla", model.getValue().getUrl());
   }

   @Test
   void create_refusesAnOccupiedNameBeforeWriting() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.checkDuplicate("sales")).thenReturn(true);

      WizXmlaSaveResult result = f.controller.createXmla(
         new WizXmlaCreateRequest("/", definition("sales", null)), f.principal);

      assertFalse(result.ok());
      assertEquals(WizXmlaSaveResult.DUPLICATE_NAME, result.reason());
      verify(f.service, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   @Test
   void create_refusesADottedNameWithAFieldNamedError() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> f.controller.createXmla(
            new WizXmlaCreateRequest(null, definition("a.b", null)), f.principal));

      assertTrue(ex.getMessage().toLowerCase().contains("name"), ex.getMessage());
      verify(f.service, never()).createNewDataSource(any(), anyBoolean(), any());
   }

   @Test
   void create_carriesTheServersRefusalTextInMessage() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.checkDuplicate(anyString())).thenReturn(false);
      doThrow(new MessageException("the credential id is not allowed for the caller"))
         .when(f.service).createNewDataSource(any(), anyBoolean(), any());

      WizXmlaSaveResult result = f.controller.createXmla(
         new WizXmlaCreateRequest(null, definition("sales", null)), f.principal);

      assertFalse(result.ok());
      assertEquals(WizXmlaSaveResult.UNKNOWN, result.reason());
      assertEquals("the credential id is not allowed for the caller", result.message());
   }

   @Test
   void create_doesNotReportSuccessWhenNothingWasWritten() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.checkDuplicate(anyString())).thenReturn(false);

      WizXmlaSaveResult result = f.controller.createXmla(
         new WizXmlaCreateRequest(null, definition("sales", null)), f.principal);

      assertFalse(result.ok());
      assertNull(result.path());
   }

   // ---- update -----------------------------------------------------------------------------

   @Test
   void update_withoutAPasswordKeepsTheStoredOneByPassingItToTheService() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("stored-pw"));
      when(f.service.checkDuplicate("olap/sales")).thenReturn(true);

      WizXmlaSaveResult result = f.controller.updateXmla(
         "olap/sales", definition("sales", null), f.principal);

      assertTrue(result.ok());
      ArgumentCaptor<DataSourceXmlaDefinition> model =
         ArgumentCaptor.forClass(DataSourceXmlaDefinition.class);
      verify(f.service).updateDataSource(eq("sales"), model.capture(), eq(f.principal));
      assertEquals("stored-pw", model.getValue().getPassword(),
                   "createDataSource sets the definition's password unconditionally, so a null " +
                      "must be replaced by the stored one or the save clears it");
      assertNull(model.getValue().getDomain(), "no domain is sent: the stored one stays");
   }

   @Test
   void update_withAPasswordSetsItAndWithAnEmptyOneClearsIt() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("stored-pw"));
      when(f.service.checkDuplicate("olap/sales")).thenReturn(true);

      f.controller.updateXmla("olap/sales", definition("sales", "new-pw"), f.principal);
      f.controller.updateXmla("olap/sales", definition("sales", ""), f.principal);

      ArgumentCaptor<DataSourceXmlaDefinition> model =
         ArgumentCaptor.forClass(DataSourceXmlaDefinition.class);
      verify(f.service, times(2)).updateDataSource(eq("sales"), model.capture(), any());
      assertEquals("new-pw", model.getAllValues().get(0).getPassword());
      assertEquals("", model.getAllValues().get(1).getPassword());
   }

   @Test
   void update_isDeniedWithoutWriteAndNeverReachesTheService() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.updateXmla(
         "olap/sales", definition("sales", null), f.principal));

      verify(f.service, never()).updateDataSource(any(), any(), any());
   }

   @Test
   void update_aRenameNeedsDeleteOnTopOfWrite() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE, "olap/sales",
                                      ResourceAction.DELETE)).thenReturn(false);
      f.stored("olap/sales", f.xmla("pw"));
      when(f.service.checkDuplicate("olap/sales")).thenReturn(true);
      when(f.service.checkDuplicate("olap/renamed")).thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.updateXmla(
         "olap/sales", definition("renamed", null), f.principal),
         "a denied rename must be a 403, not a 200 reading could-not-save");

      verify(f.service, never()).updateDataSource(any(), any(), any());
   }

   @Test
   void update_aRenameOntoAnOccupiedNameIsRefusedBeforeAnyChange() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("pw"));
      when(f.service.checkDuplicate(anyString())).thenReturn(true);

      WizXmlaSaveResult result = f.controller.updateXmla(
         "olap/sales", definition("taken", null), f.principal);

      assertEquals(WizXmlaSaveResult.DUPLICATE_NAME, result.reason());
      verify(f.service, never()).updateDataSource(any(), any(), any());
   }

   @Test
   void update_aRenameReturnsTheNewPath() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("pw"));
      when(f.service.checkDuplicate("olap/sales")).thenReturn(true);
      when(f.service.checkDuplicate("olap/renamed")).thenReturn(false);

      WizXmlaSaveResult result = f.controller.updateXmla(
         "olap/sales", definition("renamed", null), f.principal);

      assertTrue(result.ok());
      assertEquals("olap/renamed", result.path());
      verify(f.service).updateDataSource(eq("sales"), any(), eq(f.principal));
   }

   @Test
   void update_reportsAVanishedSourceAsDatasourceLost() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.checkDuplicate(anyString())).thenReturn(false);

      WizXmlaSaveResult result = f.controller.updateXmla(
         "olap/sales", definition("sales", null), f.principal);

      assertEquals(WizXmlaSaveResult.DATASOURCE_LOST, result.reason());
   }

   @Test
   void update_refusesANonXmlaPathWith422AndWritesNothing() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("db/orders", mock(JDBCDataSource.class));
      when(f.service.checkDuplicate("db/orders")).thenReturn(true);

      assertThrows(UnsupportedDatasourceException.class, () -> f.controller.updateXmla(
         "db/orders", definition("orders", null), f.principal));

      verify(f.service, never()).updateDataSource(any(), any(), any());
   }

   // ---- catalogs and test ------------------------------------------------------------------

   @Test
   void catalogsAndTest_withAPathNeedWriteOnThatPath() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.getCatalogs(
         new WizXmlaDraftRequest("olap/sales", definition("sales", null)), f.principal));
      assertThrows(SecurityException.class, () -> f.controller.testXmla(
         new WizXmlaDraftRequest("olap/sales", definition("sales", null)), f.principal));

      verify(f.service, never()).getCatalogs(any());
      verify(f.service, never()).testConnect(any());
   }

   @Test
   void catalogsAndTest_withoutAPathNeedTheCreateRuleAtTheRoot() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.getCatalogs(
         new WizXmlaDraftRequest(null, definition("sales", "pw")), f.principal));
      verify(f.security).checkPermission(f.principal, ResourceType.DATA_SOURCE_FOLDER, "/",
                                         ResourceAction.WRITE);
      verify(f.service, never()).getCatalogs(any());
   }

   @Test
   void test_withAPathAndNoPasswordUsesTheStoredPasswordOnlyAfterTheWriteGate() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("stored-pw"));
      when(f.service.testConnect(any())).thenReturn(new ConnectionStatus("ok", true));

      WizConnectionTestResult result = f.controller.testXmla(
         new WizXmlaDraftRequest("olap/sales", definition("sales", null)), f.principal);

      assertTrue(result.connected());
      ArgumentCaptor<DataSourceXmlaDefinition> model =
         ArgumentCaptor.forClass(DataSourceXmlaDefinition.class);
      verify(f.service).testConnect(model.capture());
      assertEquals("stored-pw", model.getValue().getPassword());
   }

   @Test
   void test_aDraftWithNoPathPersistsNothing() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.testConnect(any())).thenReturn(new ConnectionStatus("failed", false));

      WizConnectionTestResult result = f.controller.testXmla(
         new WizXmlaDraftRequest(null, definition("sales", "pw")), f.principal);

      assertFalse(result.connected());
      assertEquals("failed", result.message());
      verify(f.service, never()).createNewDataSource(any(), anyBoolean(), any());
      verify(f.service, never()).updateDataSource(any(), any(), any());
   }

   @Test
   void catalogs_returnTheListAndTurnAConnectionFailureInto400() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      when(f.service.getCatalogs(any())).thenReturn(List.of("Sales", "HR"));

      assertEquals(List.of("Sales", "HR"), f.controller.getCatalogs(
         new WizXmlaDraftRequest(null, definition("sales", "pw")), f.principal).catalogs());

      when(f.service.getCatalogs(any())).thenThrow(new MessageException("connection refused"));
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> f.controller.getCatalogs(
            new WizXmlaDraftRequest(null, definition("sales", "pw")), f.principal));

      assertEquals(400, ex.getStatusCode().value());
      assertEquals("connection refused", ex.getReason());
   }

   @Test
   void catalogs_withOnlyAPathUseTheStoredSettings() throws Exception {
      Fixture f = new Fixture();
      f.allowAll();
      f.stored("olap/sales", f.xmla("pw"));
      DataSourceXmlaDefinition stored = new DataSourceXmlaDefinition();
      stored.setUrl("http://stored");
      when(f.service.getDataSourceModel("olap/sales", f.principal)).thenReturn(stored);
      when(f.service.getCatalogs(stored)).thenReturn(List.of("A"));

      assertEquals(List.of("A"), f.controller.getCatalogs(
         new WizXmlaDraftRequest("olap/sales", null), f.principal).catalogs());
   }

   // ---- support ----------------------------------------------------------------------------

   private static WizXmlaDefinition definition(String name, String password) {
      return new WizXmlaDefinition(
         name, "desc", "ds", "dsName", "dsInfo", "Catalog1", "http://olap.example/xmla", true,
         new WizXmlaAuthentication("analyst", password), false, null, false);
   }

   private static void assertWriteGate(Method method) {
      Secured secured = method.getAnnotation(Secured.class);
      assertNotNull(secured, method.getName() + " must carry @Secured");
      RequiredPermission permission = secured.value()[0];
      assertEquals(ResourceType.DATA_SOURCE, permission.resourceType());
      assertArrayEquals(new ResourceAction[]{ ResourceAction.WRITE }, permission.actions());

      long annotated = Arrays.stream(method.getParameters())
         .filter(p -> p.getAnnotation(PermissionPath.class) != null).count();
      assertEquals(1, annotated);

      for(Parameter parameter : method.getParameters()) {
         if(parameter.getAnnotation(PermissionPath.class) != null) {
            assertEquals("path", parameter.getAnnotation(RequestParam.class).value());
         }
      }
   }

   private static final class Fixture {
      final XmlaDatasourceService service = mock(XmlaDatasourceService.class);
      final SecurityEngine security = mock(SecurityEngine.class);
      final XRepository repository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final WizXmlaController controller = new WizXmlaController(service, security, repository);

      void allowAll() throws Exception {
         when(security.checkPermission(any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
            .thenReturn(true);
      }

      void stored(String path, inetsoft.uql.XDataSource dataSource) throws Exception {
         when(repository.getDataSource(path)).thenReturn(dataSource);
      }

      XMLADataSource xmla(String password) {
         XMLADataSource ds = mock(XMLADataSource.class);
         when(ds.getPassword()).thenReturn(password);
         return ds;
      }
   }
}
