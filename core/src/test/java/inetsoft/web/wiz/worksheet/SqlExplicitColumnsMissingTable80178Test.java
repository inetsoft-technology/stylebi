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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.JDBCSelection;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.MetadataApiService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80178 (wsq-032): add_sql_query with EXPLICIT columns over a missing table was accepted
 * (only a type warning) because the missing-table refusal sits in the empty-columns branch.
 * The driver failure is recorded by JDBCQuery.getOutputTypeForNonParseableSQL in
 * lastQueryError, which the controller now reads after applySqlResultTypes.
 */
@Tag("core")
@WizAgentTestSupport
class SqlExplicitColumnsMissingTable80178Test {
   // ---- the classifier ----

   @Test
   void missingTableErrorsOfTheCommonDriversRefuse() {
      for(String msg : List.of(
         "Table/View 'SA.NO_SUCH_TABLE_ZZ' does not exist",
         "user lacks privilege or object not found: NO_SUCH_TABLE",
         "ORA-00942: table or view does not exist",
         "Table 'db.t' doesn't exist",
         "relation \"t\" does not exist",
         "Invalid object name 't'"))
      {
         String r = WorksheetMutationSupport.missingTableRefusal(
            new Exception(msg), "SELECT a FROM t");
         assertNotNull(r, msg);
         assertTrue(r.contains(msg), r);
      }
   }

   @Test
   void causeChainIsSearched() {
      Exception e = new Exception("query failed",
         new java.sql.SQLException("Table/View 'T' does not exist"));
      assertNotNull(WorksheetMutationSupport.missingTableRefusal(e, "SELECT a FROM t"));
   }

   @Test
   void otherMetadataFailuresStayAccepted() {
      assertNull(WorksheetMutationSupport.missingTableRefusal(null, "SELECT a FROM t"));
      assertNull(WorksheetMutationSupport.missingTableRefusal(
         new Exception("Parameter 1 is not set"), "SELECT a FROM t WHERE b = $(x)"));
      assertNull(WorksheetMutationSupport.missingTableRefusal(
         new Exception("OVER is not supported"), "SELECT row_number() over () FROM t"));
      assertNull(WorksheetMutationSupport.missingTableRefusal(
         new Exception((String) null), "SELECT 1"));
   }

   @Test
   void rawTextPlaceholderTableIsSkippedEvenWhenTheDriverSaysNotFound() {
      assertNull(WorksheetMutationSupport.missingTableRefusal(
         new Exception("Table '$(@srcTable)' does not exist"), "SELECT a FROM $(@srcTable)"));
   }

   // ---- the add path, end to end through the controller ----

   private static WorksheetAgentController controller(WorksheetEditService editSvc,
                                                       Principal agent) throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      XRepository xrepository = mock(XRepository.class);
      when(securityEngine.checkPermission(eq(agent), eq(ResourceType.FREE_FORM_SQL),
                                          eq("*"), eq(ResourceAction.ACCESS))).thenReturn(true);
      when(dataSourceService.checkPermission(eq("MyDatasource"), eq(ResourceAction.READ),
                                             eq(agent))).thenReturn(true);
      when(xrepository.getDataSource("MyDatasource")).thenReturn(mock(JDBCDataSource.class));

      QueryManagerService qms = mock(QueryManagerService.class);
      when(qms.getColumnSelection(any(), any(), any(), any(), any())).thenAnswer(inv -> {
         ColumnSelection cs = new ColumnSelection();
         ColumnRef c = new ColumnRef(new AttributeRef("x"));
         c.setDataType("string");
         cs.addAttribute(c);
         return cs;
      });

      SheetAgentFeature f = mock(SheetAgentFeature.class);
      when(f.isEnabled()).thenReturn(true);

      return new WorksheetAgentController(f,
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc,
         mock(inetsoft.report.composition.WorksheetService.class),
         mock(WorksheetPreviewService.class), mock(SheetAgentBroadcastService.class),
         xrepository, mock(inetsoft.uql.asset.AssetRepository.class),
         mock(MetadataApiService.class),
         qms, mock(LayoutGraphService.class), dataSourceService, securityEngine,
         mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
         mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
         mock(inetsoft.report.composition.execution.AssetDataCache.class),
         mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
         mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   private static void run(String sql, Exception metadataError, boolean expectRefusal)
      throws Exception
   {
      Principal agent = TestPrincipals.user("alice", "host-org");
      Worksheet ws = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      inetsoft.report.composition.execution.AssetQuerySandbox box =
         mock(inetsoft.report.composition.execution.AssetQuerySandbox.class);
      when(box.getVariableTable()).thenReturn(new inetsoft.uql.VariableTable());
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(rws.getEntry()).thenReturn(new inetsoft.uql.asset.AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE, inetsoft.uql.asset.AssetEntry.Type.WORKSHEET,
         "ws-1", null));
      WorksheetEditService editSvc = mock(WorksheetEditService.class);
      when(editSvc.applyOnRuntime(eq("TOK"), eq(agent), any())).thenAnswer(inv -> {
         WorksheetEditService.ThrowingFunction<RuntimeWorksheet, ?> fn = inv.getArgument(2);
         return fn.apply(rws);
      });
      WorksheetAgentController ctrl = controller(editSvc, agent);
      WorksheetAgentController.SqlQueryRequest body =
         new WorksheetAgentController.SqlQueryRequest("MyDatasource", sql, null);

      try(MockedConstruction<UniformSQL> u = mockConstruction(UniformSQL.class, (m, ctx) -> {
             when(m.getParseResult()).thenReturn(UniformSQL.PARSE_SUCCESS);
             JDBCSelection sel = new JDBCSelection();
             when(m.getSelection()).thenReturn(sel);
             doAnswer(inv -> {
                new Thread(() -> {
                   synchronized(m) {
                      m.notifyAll();
                   }
                }).start();
                return null;
             }).when(m).setSQLString(anyString(), anyBoolean());
          });
          MockedConstruction<JDBCQuery> q = mockConstruction(JDBCQuery.class, (m, ctx) ->
             when(m.getLastQueryError()).thenReturn(metadataError)))
      {
         if(expectRefusal) {
            PairingException ex = assertThrows(PairingException.class,
               () -> ctrl.addSqlQuery("TOK", body, agent));
            assertTrue(ex.getMessage().contains("NO_SUCH_TABLE_ZZ"), ex.getMessage());
            assertTrue(ex.getMessage().contains("could not be resolved against the datasource"),
                       ex.getMessage());
            assertEquals(0, ws.getAssemblies().length, "nothing may be created");
         }
         else {
            // Past the guard the controller scans variables against the live worksheet, which
            // this mock worksheet cannot serve; reaching that scan (an NPE) means the query was
            // not refused, so only a resolution refusal is a failure here.
            try {
               ctrl.addSqlQuery("TOK", body, agent);
            }
            catch(PairingException ex) {
               fail("must not be refused, got: " + ex.getMessage());
            }
            catch(NullPointerException expected) {
            }
         }
      }
   }

   @Test
   void explicitColumnsOverAMissingTableAreRefusedAndNothingIsCreated() throws Exception {
      run("SELECT x FROM SA.NO_SUCH_TABLE_ZZ",
          new Exception("Table/View 'SA.NO_SUCH_TABLE_ZZ' does not exist"), true);
   }

   @Test
   void explicitColumnsOverARawTextPlaceholderTableStayAccepted() throws Exception {
      run("SELECT x FROM $(@srcTable)",
          new Exception("Table/View 'NO_SUCH_TABLE_ZZ' does not exist"), false);
   }

   @Test
   void anUnrelatedMetadataFailureStaysAccepted() throws Exception {
      run("SELECT x FROM t WHERE y = $(p)", new Exception("Parameter 1 is not set"), false);
   }
}
