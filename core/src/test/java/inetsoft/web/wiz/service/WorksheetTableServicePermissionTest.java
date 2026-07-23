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
import inetsoft.web.wiz.model.WorksheetTableResponse;
import inetsoft.web.wiz.model.WorksheetTablesResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 *       gate checked at the top of {@code createTables} and {@code deleteTables}.</li>
 *   <li>The datasource READ check inside {@code createTables}'s {@code buildTable} step, gating
 *       physical/sql-query tables bound to a {@code physicalSource.datasourcePath}.</li>
 * </ul>
 *
 * <p>These tests only assert on the gate itself. The action-level WORKSHEET/ACCESS gate is checked
 * before the batch loop, so its denial throws. The per-table datasource-READ and FREE_FORM_SQL
 * gates live inside {@code buildTable}; {@code createTables} catches a failed table and records it
 * as {@code success=false} with an {@code errorMessage} rather than throwing — so the per-table
 * cases assert on that response entry (the table was not built) plus the mock interactions that
 * prove where execution stopped, regardless of what unrelated failure the deliberately-minimal
 * downstream mocking then produces.
 *
 * <p>Needs the full Sree bootstrap because {@code new Worksheet()} (constructed unconditionally by
 * {@code createTables} before {@code buildTable} runs) reads {@code SreeEnv} in its constructor.
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

   /** Wraps a single WorksheetTable JSON object into a one-table batch request. */
   private static WorksheetTableRequest batchOf(String tableJson) throws Exception {
      return MAPPER.readValue("{ \"tables\": [ " + tableJson + " ] }", WorksheetTableRequest.class);
   }

   /** Asserts the batch produced exactly one table result and returns it. */
   private static WorksheetTableResponse only(WorksheetTablesResponse response) {
      assertEquals(1, response.getTables().size(), "expected exactly one table result");
      return response.getTables().get(0);
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

      assertThrows(SecurityException.class, () -> deps.service().createTables(request, USER));

      verifyNoInteractions(deps.viewsheetService);
      verifyNoInteractions(deps.metadataApiService);
   }

   @Test
   void createTableProceedsWhenWorksheetAccessGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      // One table with no tableType => past the action gate, the per-table build fails and the
      // batch records it as a failure (rather than throwing) with the tableType reason.
      WorksheetTableRequest request = batchOf("{}");

      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("tableType"));

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
   void createTablePhysicalTableFailsWhenDatasourceReadDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(false);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "physical table",
           "physicalSource": { "datasourcePath": "myds", "tableName": "CUSTOMERS" }
         }
         """);

      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("myds"),
                 "error should name the denied datasource, got: " + table.getErrorMessage());

      // Denial short-circuits before any datasource metadata lookup.
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

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "physical table",
           "physicalSource": { "datasourcePath": "myds", "tableName": "CUSTOMERS" }
         }
         """);

      // metadataApiService is an unstubbed mock: getJDBCDatasource/getTableMetaData both return
      // null, so the table fails downstream with "Table not found" — proof execution proceeded
      // past the datasource gate all the way into buildPhysicalTable. The batch records the
      // failure rather than throwing.
      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());

      verify(deps.metadataApiService).getJDBCDatasource(eq("myds"));
      verify(deps.dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER));
   }

   // ─── createTable -> buildTable: sql query table FREE_FORM_SQL gate ─────────

   @Test
   void createTableSqlQueryFailsWhenFreeFormSqlDenied() throws Exception {
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

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "sql query table",
           "sqlExpression": "SELECT 1",
           "physicalSource": { "datasourcePath": "myds" }
         }
         """);

      // The Free-Form SQL denial (a SecurityException in buildTable) is caught by the batch and
      // recorded as a failed table — the table must not be built.
      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());

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

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "sql query table",
           "sqlExpression": "SELECT 1",
           "physicalSource": { "datasourcePath": "myds" }
         }
         """);

      // All three gates pass, so execution proceeds into buildSqlTable, which then fails on the
      // deliberately-minimal downstream mocks (getJDBCDatasource returns null). What matters is
      // that the FREE_FORM_SQL gate was passed and buildSqlTable was entered; the batch records
      // the downstream failure rather than throwing.
      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());

      verify(deps.securityEngine).checkPermission(eq(USER), eq(ResourceType.FREE_FORM_SQL), eq("*"),
                                                  eq(ResourceAction.ACCESS));
      verify(deps.metadataApiService).getJDBCDatasource(eq("myds"));
   }
}
