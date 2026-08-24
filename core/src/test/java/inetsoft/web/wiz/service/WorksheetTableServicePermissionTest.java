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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.model.DeleteWorksheetTablesRequest;
import inetsoft.web.wiz.model.WorksheetTable;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
 *       physical/sql-query tables bound to a {@code physicalSource.datasourcePath} and
 *       tabular tables bound to a {@code tabularSource.datasourcePath}.</li>
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

   // ─── createTable -> buildTable: tabular table datasource READ gate ────────

   /**
    * The same gate, reached through {@code tabularSource} instead of {@code physicalSource}.
    *
    * <p>Worth its own pair rather than trusting the physical-table pair: the gate reads the path off
    * whichever of the two fields carries one, and a tabular table supplies it in the other field. An
    * earlier shape of this check looked only at {@code physicalSource}, which let a tabular table
    * reach the connector — and dial its remote endpoint — with no READ check at all.</p>
    */
   @Test
   void createTableTabularTableFailsWhenDatasourceReadDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(false);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds", "endpoint": "Charges" }
         }
         """);

      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("no READ permission on datasource myds"),
                 "error should name the denied datasource, got: " + table.getErrorMessage());

      // Denial short-circuits before the data source is even resolved, so nothing can reach the
      // connector. This is the assertion that matters: past this point the next step dials a
      // remote, metered endpoint.
      verifyNoInteractions(deps.xrepository);
   }

   @Test
   void createTableTabularTableProceedsWhenDatasourceReadGranted() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds", "endpoint": "Charges" }
         }
         """);

      // xrepository is an unstubbed mock, so getDataSource returns null and the table fails with
      // "Data source not found" — proof execution proceeded past the datasource gate into
      // buildTabularTable. Asserted on the message rather than on success, because a mock
      // repository can never produce a real connector.
      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("Data source not found"),
                 "should fail past the gate, got: " + table.getErrorMessage());

      verify(deps.xrepository).getDataSource(eq("myds"));
      verify(deps.dataSourceService).checkPermission(eq("myds"), eq(ResourceAction.READ), eq(USER));
   }

   /**
    * The bypass the field-presence version of this gate allowed.
    *
    * <p>A tabular request carrying BOTH sources was checked against its {@code physicalSource} — a path
    * nothing on the tabular path ever reads — and then reached the connector on its unchecked
    * {@code tabularSource} path. Both halves are asserted: the gate must consult the tabular path
    * (denied here), and the data source must never be resolved, because the step after that dials a
    * remote, metered endpoint.</p>
    */
   @Test
   void createTableTabularTableChecksTabularPathEvenWhenPhysicalSourceIsAlsoPresent() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      // Readable: the path the old gate would have checked.
      when(deps.dataSourceService.checkPermission(eq("readable"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);
      // Denied: the path the connector would actually be reached on.
      when(deps.dataSourceService.checkPermission(eq("denied"), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(false);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "physicalSource": { "datasourcePath": "readable", "tableName": "CUSTOMERS" },
           "tabularSource": { "datasourcePath": "denied", "endpoint": "Charges" }
         }
         """);

      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("no READ permission on datasource denied"),
                 "the tabular path must be the one checked, got: " + table.getErrorMessage());

      verify(deps.dataSourceService).checkPermission(eq("denied"), eq(ResourceAction.READ), eq(USER));
      verifyNoInteractions(deps.xrepository);
   }

   /**
    * And with the tabular path readable, the pair is still refused — by the builder this time, because
    * naming two sources gets one of them silently ignored whichever way the gate is written.
    */
   @Test
   void createTableTabularTableRejectsAPhysicalSourceEvenWhenBothPathsAreReadable() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "physicalSource": { "datasourcePath": "ds", "tableName": "CUSTOMERS" },
           "tabularSource": { "datasourcePath": "ds", "endpoint": "Charges" }
         }
         """);

      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("cannot carry physicalSource"),
                 "expected the mismatched-pair rejection, got: " + table.getErrorMessage());

      verifyNoInteractions(deps.xrepository);
   }

   // ─── createTable -> buildTabularTable: which contract the target is read under ─────────────

   /**
    * The kind selects the contract — which properties carry the target, whether a row cap is
    * demanded, how a failure is worded — so an unrecognized value cannot be resolved to either
    * side. Refused by name rather than defaulted, which would build the table against a contract
    * the caller did not ask for and report success.
    *
    * <p>Asserted before the data source is resolved, because the check has to precede everything
    * the contract then does with it.</p>
    */
   @Test
   void createTableTabularTableRejectsAnUnknownTargetKind() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds", "targetKind": "table", "target": "q1.csv" }
         }
         """);

      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("targetKind"),
                 "the error should name the field, got: " + table.getErrorMessage());

      verifyNoInteractions(deps.xrepository);
   }

   /** Case and surrounding space are the caller's spelling, not a different request. */
   @Test
   void createTableTabularTableAcceptsATargetKindInAnyCase() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetTableRequest request = batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds", "targetKind": " File ", "target": "q1.csv" }
         }
         """);

      // Past the kind check and into the build, where the unstubbed repository ends it.
      WorksheetTableResponse table = only(deps.service().createTables(request, USER));
      assertFalse(table.isSuccess());
      assertTrue(table.getErrorMessage().contains("Data source not found"),
                 "should fail past the kind check, got: " + table.getErrorMessage());

      verify(deps.xrepository).getDataSource(eq("myds"));
   }

   /**
    * A target is required whichever kind it is, and the message has to say which THING is missing —
    * an endpoint name and a file path are not the same thing to go and find.
    */
   @Test
   void createTableTabularTableRequiresATargetAndSaysWhatKindOfOne() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      WorksheetTableResponse asFile = only(deps.service().createTables(batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds", "targetKind": "file" }
         }
         """), USER));
      assertFalse(asFile.isSuccess());
      assertTrue(asFile.getErrorMessage().contains("tabularSource.target is required") &&
                    asFile.getErrorMessage().contains("file path"),
                 "the file wording should name a path, got: " + asFile.getErrorMessage());

      WorksheetTableResponse asEndpoint = only(deps.service().createTables(batchOf("""
         {
           "tableName": "t1",
           "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds" }
         }
         """), USER));
      assertFalse(asEndpoint.isSuccess());
      assertTrue(asEndpoint.getErrorMessage().contains("tabularSource.target is required") &&
                    asEndpoint.getErrorMessage().contains("endpoint"),
                 "an absent kind is the endpoint one, got: " + asEndpoint.getErrorMessage());
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

   // ─── probeTable: the annotation probe, and the sample it exists for ────────────────────────

   @Test
   void probeTableThrowsWhenWorksheetAccessDenied() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WorksheetTable table = MAPPER.readValue(
         "{ \"tableName\": \"t1\", \"tableType\": \"tabular table\" }", WorksheetTable.class);

      assertThrows(SecurityException.class,
                   () -> deps.service().probeTable("Worksheet-1", table, USER));

      // Nothing is opened or looked up before the gate answers.
      verifyNoInteractions(deps.viewsheetService);
   }

   /**
    * A build failure comes back as a failed RESULT, not an exception. The caller is a loop over a
    * directory and one unreadable file must not end the walk — the same contract a table inside a
    * createTables batch has.
    */
   @Test
   void probeTableReportsABuildFailureWithoutThrowingAndLeavesNoAssembly() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(deps.dataSourceService.checkPermission(anyString(), eq(ResourceAction.READ), eq(USER)))
         .thenReturn(true);

      Worksheet worksheet = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(deps.viewsheetService.getWorksheet(eq("Worksheet-1"), eq(USER))).thenReturn(rws);

      // xrepository is unstubbed, so the data source resolves to null and the build fails.
      WorksheetTable table = MAPPER.readValue("""
         { "tableName": "t1", "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "myds", "targetKind": "file",
                              "target": "q1.csv", "sampleRows": 5 } }
         """, WorksheetTable.class);

      WorksheetTableResponse response = deps.service().probeTable("Worksheet-1", table, USER);

      assertFalse(response.isSuccess());
      assertTrue(response.getErrorMessage().contains("Data source not found"),
                 "expected the build's own reason, got: " + response.getErrorMessage());

      // Each file is an independent question; nothing may accumulate in the shared worksheet.
      assertNull(worksheet.getAssembly("t1"),
                 "the probe assembly must not survive the call that built it");
   }

   /** A caller that did not name the table gets one, because the name has no consequence. */
   @Test
   void probeTableNamesAnUnnamedTableRatherThanFailing() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      Worksheet worksheet = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(worksheet);
      when(deps.viewsheetService.getWorksheet(eq("Worksheet-1"), eq(USER))).thenReturn(rws);

      WorksheetTable table = MAPPER.readValue(
         "{ \"tableType\": \"tabular table\" }", WorksheetTable.class);

      WorksheetTableResponse response = deps.service().probeTable("Worksheet-1", table, USER);

      assertNotNull(response.getTableName(), "an unnamed probe table must still be named");
      assertFalse(response.isSuccess());
   }

   @Test
   void probeTableRequiresARuntimeId() throws Exception {
      Deps deps = new Deps();
      when(deps.securityEngine.checkPermission(eq(USER), eq(ResourceType.WORKSHEET), eq("*"),
                                               eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      WorksheetTable table = MAPPER.readValue(
         "{ \"tableName\": \"t1\", \"tableType\": \"tabular table\" }", WorksheetTable.class);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> deps.service().probeTable("  ", table, USER));
      assertTrue(ex.getMessage().contains("runtimeId"), ex.getMessage());
   }

   // ─── sandboxSampleLimit: the rule that keeps the endpoint path untouched ───────────────────

   private static WorksheetTableResponse built() {
      WorksheetTableResponse response = new WorksheetTableResponse();
      response.setSuccess(true);
      return response;
   }

   private static WorksheetTable tabular(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
   }

   /**
    * THE assertion of this section. An endpoint's sample is taken by the runner from the one
    * request it had to make; asking for it again here would send a second request to a metered API
    * for data already accounted for. Keyed on the kind, not on "the response has no sample yet" —
    * an endpoint whose first page came back empty also has none.
    */
   @Test
   void sampleLimitIsZeroForAnEndpointEvenWhenRowsWereAskedForAndNoneCameBack() throws Exception {
      WorksheetTable explicit = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "endpoint",
                              "target": "Charges", "sampleRows": 10 } }
         """);
      assertEquals(0, WorksheetTableService.sandboxSampleLimit(explicit, built()));

      // And with the kind omitted, which is how every pre-existing endpoint caller spells it.
      WorksheetTable implied = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "endpoint": "Charges", "sampleRows": 10 } }
         """);
      assertEquals(0, WorksheetTableService.sandboxSampleLimit(implied, built()));
   }

   @Test
   void sampleLimitIsTheRequestedCountForAFileTarget() throws Exception {
      WorksheetTable table = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "file",
                              "target": "q1.csv", "sampleRows": 7 } }
         """);

      assertEquals(7, WorksheetTableService.sandboxSampleLimit(table, built()));
   }

   /** Opt-in, unchanged: a caller that only wanted the column list runs no extra query. */
   @Test
   void sampleLimitIsZeroWhenNoRowsWereAskedFor() throws Exception {
      WorksheetTable absent = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "file", "target": "q1.csv" } }
         """);
      assertEquals(0, WorksheetTableService.sandboxSampleLimit(absent, built()));

      WorksheetTable zero = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "file",
                              "target": "q1.csv", "sampleRows": 0 } }
         """);
      assertEquals(0, WorksheetTableService.sandboxSampleLimit(zero, built()));
   }

   /** A sample already on the response is never replaced, whatever produced it. */
   @Test
   void sampleLimitIsZeroWhenTheResponseAlreadyCarriesASample() throws Exception {
      WorksheetTable table = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "file",
                              "target": "q1.csv", "sampleRows": 7 } }
         """);

      WorksheetTableResponse response = built();
      response.setSampleRows(List.of(Map.of("a", 1)));

      assertEquals(0, WorksheetTableService.sandboxSampleLimit(table, response));
   }

   /** Nothing to sample from a table that was not built. */
   @Test
   void sampleLimitIsZeroForAFailedTable() throws Exception {
      WorksheetTable table = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "file",
                              "target": "q1.csv", "sampleRows": 7 } }
         """);

      WorksheetTableResponse failed = new WorksheetTableResponse();
      failed.setSuccess(false);

      assertEquals(0, WorksheetTableService.sandboxSampleLimit(table, failed));
   }

   /**
    * {@code rest.sample.rows} is the deployment's ceiling on sampled customer data leaving a
    * tabular connector, and 0 is the switch that stops it. A different KIND of target reaching the
    * same data must not get around it.
    */
   @Test
   void sampleLimitIsClampedByTheDeploymentCeilingAndSwitchedOffAtZero() throws Exception {
      WorksheetTable table = tabular("""
         { "tableType": "tabular table",
           "tabularSource": { "datasourcePath": "ds", "targetKind": "file",
                              "target": "q1.csv", "sampleRows": 50 } }
         """);

      String original = SreeEnv.getProperty("rest.sample.rows");

      try {
         SreeEnv.setProperty("rest.sample.rows", "3");
         assertEquals(3, WorksheetTableService.sandboxSampleLimit(table, built()));

         SreeEnv.setProperty("rest.sample.rows", "0");
         assertEquals(0, WorksheetTableService.sandboxSampleLimit(table, built()));

         // Not a number: no sample, rather than a guess about a knob governing customer data.
         SreeEnv.setProperty("rest.sample.rows", "lots");
         assertEquals(0, WorksheetTableService.sandboxSampleLimit(table, built()));
      }
      finally {
         SreeEnv.setProperty("rest.sample.rows", original);
      }
   }
}
