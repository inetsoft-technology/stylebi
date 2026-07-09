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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.DeleteWorksheetTablesRequest;
import inetsoft.web.wiz.model.WorksheetTableRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Coverage for the authorization gates added to {@link WorksheetTableService}:
 * <ul>
 *   <li>{@code checkWorksheetActionPermission} — the "Visual Composer -> Data Worksheet" action-level
 *       gate checked at the top of {@code createTable}, {@code getWorksheetModel} and
 *       {@code deleteTables}.</li>
 *   <li>The datasource READ check inside {@code createTable}'s {@code buildTable} step, gating
 *       physical/sql-query tables bound to a {@code physicalSource.datasourcePath}.</li>
 * </ul>
 *
 * <p>These tests only assert on the gate itself — denial short-circuits before any worksheet
 * load/mutation or datasource metadata lookup, and grant lets execution proceed past the check
 * (verified by the mock interaction actually happening), regardless of what unrelated exception
 * the deliberately-minimal downstream mocking then produces.
 *
 * <p>Needs the full Sree bootstrap because {@code new Worksheet()} (constructed unconditionally by
 * {@code createTable} before {@code buildTable} runs) reads {@code SreeEnv} in its constructor.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServicePermissionTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static class Deps {
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final MetadataApiService metadataApiService = mock(MetadataApiService.class);
      final InnerJoinService innerJoinService = mock(InnerJoinService.class);
      final LayoutGraphService layoutGraphService = mock(LayoutGraphService.class);
      final QueryManagerService queryManagerService = mock(QueryManagerService.class);
      final XRepository xrepository = mock(XRepository.class);
      final ObjectMapper objectMapper = new ObjectMapper();
      final DataSourceService dataSourceService = mock(DataSourceService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);

      WorksheetTableService service() {
         return new WorksheetTableService(viewsheetService, metadataApiService, innerJoinService,
                                          layoutGraphService, queryManagerService, xrepository,
                                          objectMapper, dataSourceService, securityEngine);
      }
   }

   private static WorksheetTableRequest tableRequest(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTableRequest.class);
   }

   private static final Principal USER = mock(Principal.class);

   // ─── createTable: WORKSHEET/ACCESS gate ────────────────────────────────────

   @Test
   void createTableThrowsWhenWorksheetAccessDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetTableRequest request = tableRequest("{ \"tableType\": \"physical table\" }");

      assertThrows(SecurityException.class, () -> deps.service().createTable(request, USER));

      verifyNoInteractions(deps.viewsheetService);
      verifyNoInteractions(deps.metadataApiService);
   }

   @Test
   void createTableProceedsWhenWorksheetAccessGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // No tableType => buildTable fails for an unrelated reason once past the gate.
      WorksheetTableRequest request = tableRequest("{}");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().createTable(request, USER));
      assertTrue(ex.getMessage().contains("tableType"));

      verify(deps.securityEngine).checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                                   eq(ResourceAction.ACCESS));
   }

   // ─── getWorksheetModel: WORKSHEET/ACCESS gate ──────────────────────────────

   @Test
   void getWorksheetModelThrowsWhenWorksheetAccessDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> deps.service().getWorksheetModel("1^128^__NULL__^ws1", USER));

      verifyNoInteractions(deps.viewsheetService);
   }

   @Test
   void getWorksheetModelProceedsWhenWorksheetAccessGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // Empty identifier => fails for an unrelated reason once past the gate, before touching
      // the asset repository.
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().getWorksheetModel("", USER));
      assertTrue(ex.getMessage().contains("wsIdentifier"));

      verify(deps.securityEngine).checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                                   eq(ResourceAction.ACCESS));
   }

   // ─── deleteTables: WORKSHEET/ACCESS gate ───────────────────────────────────

   @Test
   void deleteTablesThrowsWhenWorksheetAccessDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      DeleteWorksheetTablesRequest request = new DeleteWorksheetTablesRequest();
      request.setWorksheetId("1^128^__NULL__^ws1");

      assertThrows(SecurityException.class, () -> deps.service().deleteTables(request, USER));

      verifyNoInteractions(deps.viewsheetService);
   }

   @Test
   void deleteTablesProceedsWhenWorksheetAccessGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // No worksheetId => fails for an unrelated reason once past the gate.
      DeleteWorksheetTablesRequest request = new DeleteWorksheetTablesRequest();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().deleteTables(request, USER));
      assertTrue(ex.getMessage().contains("worksheetId"));

      verify(deps.securityEngine).checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                                   eq(ResourceAction.ACCESS));
   }

   // ─── createTable -> buildTable: datasource READ gate ───────────────────────

   @Test
   void createTablePhysicalTableThrowsWhenDatasourceReadDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(false);

      WorksheetTableRequest request = tableRequest("""
         {
           "tableName": "t1",
           "tableType": "physical table",
           "physicalSource": { "datasourcePath": "myds", "tableName": "CUSTOMERS" }
         }
         """);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().createTable(request, USER));
      assertTrue(ex.getMessage().contains("myds"),
                 "error should name the denied datasource, got: " + ex.getMessage());

      verifyNoInteractions(deps.metadataApiService);
   }

   @Test
   void createTablePhysicalTableProceedsWhenDatasourceReadGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetTableRequest request = tableRequest("""
         {
           "tableName": "t1",
           "tableType": "physical table",
           "physicalSource": { "datasourcePath": "myds", "tableName": "CUSTOMERS" }
         }
         """);

      // metadataApiService is an unstubbed mock: getJDBCDatasource/getTableMetaData both return
      // null, so this fails downstream with "Table not found" — proof execution proceeded past
      // the datasource gate all the way into buildPhysicalTable.
      assertThrows(IllegalArgumentException.class, () -> deps.service().createTable(request, USER));

      verify(deps.metadataApiService).getJDBCDatasource(eq("myds"));
      verify(deps.dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER));
   }

   // ─── createTable -> buildTable: sql query table FREE_FORM_SQL gate ─────────

   @Test
   void createTableSqlQueryThrowsWhenFreeFormSqlDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);
      // Free-Form SQL right is denied — even though worksheet access and datasource READ are granted.
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.FREE_FORM_SQL), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetTableRequest request = tableRequest("""
         {
           "tableName": "t1",
           "tableType": "sql query table",
           "sqlExpression": "SELECT 1",
           "physicalSource": { "datasourcePath": "myds" }
         }
         """);

      assertThrows(SecurityException.class, () -> deps.service().createTable(request, USER));

      // Denial must short-circuit before buildSqlTable resolves/executes anything.
      verifyNoInteractions(deps.metadataApiService);
   }

   @Test
   void createTableSqlQueryProceedsWhenFreeFormSqlGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.FREE_FORM_SQL), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      WorksheetTableRequest request = tableRequest("""
         {
           "tableName": "t1",
           "tableType": "sql query table",
           "sqlExpression": "SELECT 1",
           "physicalSource": { "datasourcePath": "myds" }
         }
         """);

      // All three gates pass, so execution proceeds into buildSqlTable, which then fails on the
      // deliberately-minimal downstream mocks (getJDBCDatasource returns null). The failure type
      // is irrelevant — what matters is that the FREE_FORM_SQL gate was passed and buildSqlTable
      // was entered.
      assertThrows(Exception.class, () -> deps.service().createTable(request, USER));

      verify(deps.securityEngine).checkPermission(eq(USER), eq(ResourceType.FREE_FORM_SQL), eq("*"),
                                                  eq(ResourceAction.ACCESS));
      verify(deps.metadataApiService).getJDBCDatasource(eq("myds"));
   }
}
