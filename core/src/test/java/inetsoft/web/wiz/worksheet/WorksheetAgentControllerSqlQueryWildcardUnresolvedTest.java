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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.JDBCSelection;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.MetadataApiService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80023 item 3 (WSQ-030): {@code add_sql_query}/{@code edit_sql_query} on SQL whose wildcard
 * (e.g. {@code SELECT * FROM SA.NO_SUCH_TABLE_ZZ}) cannot be expanded because the referenced
 * table doesn't exist. {@code JDBCUtil.fixUniformSQLInfo} forces {@code getParseResult()} to
 * {@code PARSE_FAILED} for this case too (Bug #77617), so the controller now also reads the new
 * {@code UniformSQL.isWildcardUnresolved()} signal to tell it apart from a genuine grammar
 * failure and fall through to the driver-error/generic branch instead of the hard "could not be
 * parsed -- check syntax" message.
 * <p>
 * Uses the same {@code mockConstruction(UniformSQL.class)} technique as
 * {@link WorksheetAgentControllerTest}'s WSQ-006 tests (see its {@code stubUniformSqlConstruction}
 * javadoc for why real SQL-string parsing isn't used to reach these branches). This is a
 * standalone file rather than an addition to {@code WorksheetAgentControllerTest} because the
 * tester role commits test files only and does not edit existing production or test sources.
 */
@WizAgentTestSupport
class WorksheetAgentControllerSqlQueryWildcardUnresolvedTest {

   // ---------------------------------------------------------------------------
   // add_sql_query
   // ---------------------------------------------------------------------------

   /**
    * WSQ-030: a wildcard unresolved because its table doesn't exist must not be refused as a
    * syntax error; with a captured driver error it surfaces that instead.
    */
   @Test
   void addSqlQueryNamesTheDriverErrorInsteadOfParseFailedWhenWildcardIsUnresolved() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(mock(JDBCDataSource.class));

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-WSQ030"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            JDBCQuery q = inv.getArgument(0);
            q.setLastQueryError(new Exception("Table/View 'SA.NO_SUCH_TABLE_ZZ' does not exist"));
            return new ColumnSelection();
         });

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body = new WorksheetAgentController.SqlQueryRequest(
         "MyDatasource", "SELECT * FROM SA.NO_SUCH_TABLE_ZZ", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> {
            stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED);
            when(m.isWildcardUnresolved()).thenReturn(true);
         }))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.addSqlQuery("TOK-SQ-WSQ030", body, agent));
         assertFalse(ex.getMessage().contains("check syntax (punctuation, keywords, clause structure)"),
                    "must not be refused as a syntax error, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("syntactically valid but could not be resolved"),
                    "should surface the driver-error variant, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("SA.NO_SUCH_TABLE_ZZ"),
                    "should name the missing table, got: " + ex.getMessage());
      }
   }

   /**
    * WSQ-030: same as above but with no captured driver error -- the refusal must still avoid
    * the hard syntax message and fall all the way back to the softer generic one.
    */
   @Test
   void addSqlQueryFallsBackToGenericMessageWhenWildcardIsUnresolvedAndNoDriverError() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(mock(JDBCDataSource.class));

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-WSQ030B"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      // lastQueryError is left unset (defaults to null on a freshly-constructed JDBCQuery)
      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body = new WorksheetAgentController.SqlQueryRequest(
         "MyDatasource", "SELECT * FROM SA.NO_SUCH_TABLE_ZZ", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> {
            stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED);
            when(m.isWildcardUnresolved()).thenReturn(true);
         }))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.addSqlQuery("TOK-SQ-WSQ030B", body, agent));
         assertFalse(ex.getMessage().contains("check syntax (punctuation, keywords, clause structure)"),
                    "must not be refused as a syntax error, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("SQL could not be parsed or no columns detected"),
                    "should fall back to the original generic message, got: " + ex.getMessage());
      }
   }

   /**
    * WSQ-030 revision (the refuter's finding on this bug): a {@code $(@var)} raw-text wildcard
    * placeholder is itself an unresolvable wildcard, so {@code isWildcardUnresolved()} is
    * {@code true} for it too. The pre-existing {@code wildcardOverRawTextPlaceholderMessage}
    * check (Bug #79707) must still fire -- and not be silently swallowed by the new gate -- when
    * that signal is set, proving the fix reorders the check (runs it first, unconditionally)
    * rather than merely nesting the new gate inside the old one.
    */
   @Test
   void addSqlQueryStillNamesPlaceholderWildcardWhenWildcardUnresolvedSignalIsAlsoSet()
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);

      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ), eq(agent)))
         .thenReturn(true);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(mock(JDBCDataSource.class));

      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK-SQ-WSQ030C"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc,
         dataSourceService, securityEngine, mock(MetadataApiService.class),
         xrepository, queryManagerService);

      WorksheetAgentController.SqlQueryRequest body = new WorksheetAgentController.SqlQueryRequest(
         "MyDatasource", "SELECT * FROM $(@srcTable)", null);

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> {
            stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED);
            when(m.isWildcardUnresolved()).thenReturn(true);
         }))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.addSqlQuery("TOK-SQ-WSQ030C", body, agent));
         assertTrue(ex.getMessage().contains("wildcard") && ex.getMessage().contains("$(@var)"),
                    "should still surface the #79707 placeholder message, got: " + ex.getMessage());
         assertFalse(ex.getMessage().contains("check syntax (punctuation, keywords, clause structure)"),
                    "must not be the hard syntax message, got: " + ex.getMessage());
         assertFalse(ex.getMessage().contains("could not be resolved against the datasource"),
                    "must not be swallowed into the driver-error/generic branch, got: "
                       + ex.getMessage());
      }
   }

   // ---------------------------------------------------------------------------
   // edit_sql_query
   // ---------------------------------------------------------------------------

   /** WSQ-030, {@code edit_sql_query} twin of the add_sql_query driver-error case above. */
   @Test
   void editSqlQueryNamesTheDriverErrorInsteadOfParseFailedWhenWildcardIsUnresolved() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ESQ-WSQ030"), any())).thenReturn(session("TOK-ESQ-WSQ030"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            JDBCQuery q = inv.getArgument(0);
            q.setLastQueryError(new Exception("Table/View 'SA.NO_SUCH_TABLE_ZZ' does not exist"));
            return new ColumnSelection();
         });

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      WorksheetAgentController.EditSqlQueryRequest body =
         new WorksheetAgentController.EditSqlQueryRequest(
            "SqlTable1", "SELECT * FROM SA.NO_SUCH_TABLE_ZZ");

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> {
            stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED);
            when(m.isWildcardUnresolved()).thenReturn(true);
         }))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.editSqlQuery("TOK-ESQ-WSQ030", body, agent));
         assertFalse(ex.getMessage().contains("check syntax (punctuation, keywords, clause structure)"),
                    "must not be refused as a syntax error, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("syntactically valid but could not be resolved"),
                    "should surface the driver-error variant, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("SA.NO_SUCH_TABLE_ZZ"),
                    "should name the missing table, got: " + ex.getMessage());
      }
   }

   /** WSQ-030, {@code edit_sql_query} twin of the add_sql_query generic-fallback case above. */
   @Test
   void editSqlQueryFallsBackToGenericMessageWhenWildcardIsUnresolvedAndNoDriverError() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ESQ-WSQ030B"), any())).thenReturn(session("TOK-ESQ-WSQ030B"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      WorksheetAgentController.EditSqlQueryRequest body =
         new WorksheetAgentController.EditSqlQueryRequest(
            "SqlTable1", "SELECT * FROM SA.NO_SUCH_TABLE_ZZ");

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> {
            stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED);
            when(m.isWildcardUnresolved()).thenReturn(true);
         }))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.editSqlQuery("TOK-ESQ-WSQ030B", body, agent));
         assertFalse(ex.getMessage().contains("check syntax (punctuation, keywords, clause structure)"),
                    "must not be refused as a syntax error, got: " + ex.getMessage());
         assertTrue(ex.getMessage().contains("SQL could not be parsed or no columns detected"),
                    "should fall back to the original generic message, got: " + ex.getMessage());
      }
   }

   /** WSQ-030 revision, {@code edit_sql_query} twin of the $(@var) interaction case above. */
   @Test
   void editSqlQueryStillNamesPlaceholderWildcardWhenWildcardUnresolvedSignalIsAlsoSet()
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly sqlt = new SQLBoundTableAssembly(ws, "SqlTable1");
      ((SQLBoundTableAssemblyInfo) sqlt.getInfo()).setQuery(new JDBCQuery());
      ws.addAssembly(sqlt);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-ESQ-WSQ030C"), any())).thenReturn(session("TOK-ESQ-WSQ030C"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      QueryManagerService queryManagerService = mock(QueryManagerService.class);
      when(queryManagerService.getColumnSelection(any(), any(), any(), any(), any()))
         .thenReturn(new ColumnSelection());

      WorksheetAgentController ctrl = securityController(editSvc, mock(DataSourceService.class),
         securityEngine, mock(MetadataApiService.class), mock(XRepository.class),
         queryManagerService);

      WorksheetAgentController.EditSqlQueryRequest body =
         new WorksheetAgentController.EditSqlQueryRequest(
            "SqlTable1", "SELECT * FROM $(@srcTable)");

      try(MockedConstruction<UniformSQL> ignored = mockConstruction(UniformSQL.class,
         (m, ctx) -> {
            stubUniformSqlConstruction(m, UniformSQL.PARSE_FAILED);
            when(m.isWildcardUnresolved()).thenReturn(true);
         }))
      {
         PairingException ex = assertThrows(PairingException.class,
            () -> ctrl.editSqlQuery("TOK-ESQ-WSQ030C", body, agent));
         assertTrue(ex.getMessage().contains("wildcard") && ex.getMessage().contains("$(@var)"),
                    "should still surface the #79707 placeholder message, got: " + ex.getMessage());
         assertFalse(ex.getMessage().contains("check syntax (punctuation, keywords, clause structure)"),
                    "must not be the hard syntax message, got: " + ex.getMessage());
         assertFalse(ex.getMessage().contains("could not be resolved against the datasource"),
                    "must not be swallowed into the driver-error/generic branch, got: "
                       + ex.getMessage());
      }
   }

   // ---------------------------------------------------------------------------
   // Helpers (duplicated from WorksheetAgentControllerTest -- that file isn't modified here)
   // ---------------------------------------------------------------------------

   private static JoinSession session(String token) {
      return new JoinSession(token, "Worksheet/ws-1", "alice~;~host-org",
                             SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                             JoinSession.ConnectionMode.PAIRED, null, null, null);
   }

   private static SheetAgentFeature featureOn() {
      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(true);
      return f;
   }

   private static WorksheetAgentController securityController(
      WorksheetEditService edit,
      DataSourceService dataSourceService,
      SecurityEngine securityEngine,
      MetadataApiService metadataApiService,
      XRepository xrepository,
      QueryManagerService queryManagerService)
   {
      return new WorksheetAgentController(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), edit, mock(WorksheetService.class),
         mock(WorksheetPreviewService.class), mock(SheetAgentBroadcastService.class),
         xrepository, mock(inetsoft.uql.asset.AssetRepository.class), metadataApiService,
         queryManagerService, mock(LayoutGraphService.class),
         dataSourceService, securityEngine,
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /**
    * See {@code WorksheetAgentControllerTest.stubUniformSqlConstruction} for the full rationale.
    * Stubs a {@code mockConstruction}-produced {@code UniformSQL} so {@code getParseResult()} is
    * set directly rather than reached via a real SQL string's grammar parse, and
    * {@code setSQLString} doesn't block {@code editSqlQuery}'s 10s wait.
    */
   private static void stubUniformSqlConstruction(UniformSQL mock, int parseResult) {
      when(mock.getParseResult()).thenReturn(parseResult);
      when(mock.getSelection()).thenReturn(new JDBCSelection());
      doAnswer(inv -> {
         new Thread(() -> {
            synchronized(mock) {
               mock.notifyAll();
            }
         }).start();
         return null;
      }).when(mock).setSQLString(anyString(), anyBoolean());
   }
}
