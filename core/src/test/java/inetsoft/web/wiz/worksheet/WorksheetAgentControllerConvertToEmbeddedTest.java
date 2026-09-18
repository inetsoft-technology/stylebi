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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76730 (WBS-049): {@code convert_to_embedded} used to guard on
 * {@code instanceof BoundTableAssembly}, a type that excludes every composite table type
 * (JOIN/MIRROR/CONCAT), even though the delegate {@code AssetEventUtil.convertEmbeddedTable}
 * and the native Composer UI path accept any {@code TableAssembly}. These tests exercise the
 * broadened guard against a REAL (non-mocked) {@link AssetQuerySandbox} running actual
 * join/mirror query execution -- mirroring
 * {@code inetsoft.report.composition.execution.JoinDuplicateColumnAliasMechanismTest}'s fixture
 * pattern -- and assert the converted table's actual cell values, not just the absence of an
 * exception. Kept in a separate class (rather than {@link WorksheetAgentControllerTest}) because
 * exercising the real query-execution pipeline needs {@link IntegrationTestConfiguration} (for
 * the {@code AssetDataCache} Spring bean {@code box.getTableLens} resolves through), which the
 * rest of that file's tests don't need.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetAgentControllerConvertToEmbeddedTest {

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

   private static WorksheetAgentController controller(SheetAgentFeature feature,
                                                       SheetJoinService join,
                                                       SheetSessionService sessions,
                                                       WorksheetReadService read,
                                                       WorksheetEditService edit,
                                                       WorksheetService ws)
   {
      return new WorksheetAgentController(feature, join, sessions, read, edit, ws,
                                          mock(WorksheetPreviewService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(inetsoft.uql.XRepository.class),
                                          mock(inetsoft.uql.asset.AssetRepository.class),
                                          mock(inetsoft.web.wiz.service.MetadataApiService.class),
                                          mock(inetsoft.web.portal.controller.database.QueryManagerService.class),
                                          mock(inetsoft.web.composer.ws.LayoutGraphService.class),
                                          mock(inetsoft.web.portal.controller.database.DataSourceService.class),
                                          mock(inetsoft.sree.security.SecurityEngine.class),
                                          mock(inetsoft.uql.asset.sync.RenameTransformHandler.class),
                                          mock(inetsoft.web.wiz.viewsheet.SheetOpenService.class),
                                          mock(inetsoft.report.composition.execution.AssetDataCache.class),
                                          mock(inetsoft.web.composer.ws.dialog.AssemblyConditionDialogServiceProxy.class),
                                          mock(inetsoft.web.wiz.service.RawDataService.class));
   }

   /** Builds a {@code convert_to_embedded} EditRequest that routes to convertToEmbedded(). */
   private static EditRequest convertToEmbeddedRequest(String table) {
      return new EditRequest(
         "convert_to_embedded", table, null, null, null, null, null, null, null, null,
         null, null, null, false, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null,
         null, null,
         null, null,
         null, null, null, null, null, null, null
      );
   }

   /**
    * JOIN half: broadening the guard both lets the conversion through and produces correct
    * data for a real {@code RelationalJoinTableAssembly} via the actual production
    * query-execution pipeline, not a mock.
    */
   @Test
   void convertToEmbeddedConvertsJoinTableWithRealJoinedData() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      EmbeddedTableAssembly left = TestWorksheets.tableWithColumns(ws, "LEFT_T", "ID", "NAME");
      left.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "ID", "NAME" },
            { "1", "a" },
            { "2", "b" },
         }));
      ws.addAssembly(left);

      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "RIGHT_T", "ID", "CITY");
      right.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "ID", "CITY" },
            { "1", "NY" },
            { "2", "LA" },
         }));
      ws.addAssembly(right);

      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("LEFT_T");
      op.setRightTable("RIGHT_T");
      op.setLeftAttribute(new AttributeRef(null, "ID"));
      op.setRightAttribute(new AttributeRef(null, "ID"));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      RelationalJoinTableAssembly joined = new RelationalJoinTableAssembly(
         ws, "JOINED", new TableAssembly[]{ left, right }, new TableAssemblyOperator[]{ top });
      ws.addAssembly(joined);

      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      box.refreshColumnSelection("JOINED", false);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-CTE-JOIN"), any())).thenReturn(session("TOK-CTE-JOIN"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-CTE-JOIN", convertToEmbeddedRequest("JOINED"), agent);

      Assembly result = ws.getAssembly("JOINED");
      assertInstanceOf(EmbeddedTableAssembly.class, result);
      XEmbeddedTable data = ((EmbeddedTableAssembly) result).getEmbeddedData();

      Map<Object, Object[]> byId = new HashMap<>();
      for(int r = 1; r < data.getRowCount(); r++) {
         Object[] row = new Object[data.getColCount()];
         for(int c = 0; c < data.getColCount(); c++) {
            row[c] = data.getObject(r, c);
         }
         byId.put(row[0], row);
      }

      assertEquals(2, byId.size());
      assertTrue(Arrays.stream(byId.get("1")).anyMatch("a"::equals));
      assertTrue(Arrays.stream(byId.get("1")).anyMatch("NY"::equals));
      assertTrue(Arrays.stream(byId.get("2")).anyMatch("b"::equals));
      assertTrue(Arrays.stream(byId.get("2")).anyMatch("LA"::equals));
   }

   /**
    * MIRROR half: same broadened-guard fix, exercised against a real {@link MirrorTableAssembly}
    * wrapping an embedded source table.
    */
   @Test
   void convertToEmbeddedConvertsMirrorTableWithRealMirroredData() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      EmbeddedTableAssembly src = TestWorksheets.tableWithColumns(ws, "SRC", "K", "V");
      src.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "double" },
         new Object[][]{
            { "K", "V" },
            { "x1", 1.0 },
            { "x2", 2.0 },
         }));
      ws.addAssembly(src);

      MirrorTableAssembly mir = new MirrorTableAssembly(ws, "MIR", src);
      ws.addAssembly(mir);

      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      box.refreshColumnSelection("MIR", false);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-CTE-MIR"), any())).thenReturn(session("TOK-CTE-MIR"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      ctrl.edit("TOK-CTE-MIR", convertToEmbeddedRequest("MIR"), agent);

      Assembly result = ws.getAssembly("MIR");
      assertInstanceOf(EmbeddedTableAssembly.class, result);
      XEmbeddedTable data = ((EmbeddedTableAssembly) result).getEmbeddedData();

      Map<Object, Object> byKey = new HashMap<>();
      for(int r = 1; r < data.getRowCount(); r++) {
         byKey.put(data.getObject(r, 0), data.getObject(r, 1));
      }

      assertEquals(1.0, byKey.get("x1"));
      assertEquals(2.0, byKey.get("x2"));
   }

   /**
    * The broadened guard must still reject something that is genuinely not a
    * {@link TableAssembly} at all, guarding against a future regression back to "any assembly
    * name works".
    */
   @Test
   void convertToEmbeddedStillRejectsNonTableAssembly() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V1");
      ws.addAssembly(variable);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(mock(AssetQuerySandbox.class));

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-CTE-REJ"), any())).thenReturn(session("TOK-CTE-REJ"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-CTE-REJ", convertToEmbeddedRequest("V1"), agent));
      assertTrue(ex.getMessage().contains("Not a bound table: V1"));
   }

   /**
    * Refuter-flagged gap: {@code AssetEventUtil.convertEmbeddedTable}'s delegate,
    * {@code XEmbeddedTable}'s constructor, throws {@code EmptyTableToEmbeddedException} when the
    * resolved {@code TableLens} has zero columns -- which happens for a join table whose column
    * selection was never refreshed/resolved before conversion (the exact gap the refuter's own
    * scratch fixture hit by omitting {@code box.refreshColumnSelection}, matching a real caller
    * sequence where {@code convert_to_embedded} is invoked before any op that would have
    * populated the join's merged column selection). Today this must surface as a clean,
    * field-named {@link PairingException}, not the raw {@code EmptyTableToEmbeddedException} --
    * matching the native UI path's own handling of the identical exception.
    */
   @Test
   void convertToEmbeddedThrowsPairingExceptionForEmptyJoinResult() throws Exception {
      Principal agent = TestPrincipals.user("alice", "host-org");

      Worksheet ws = new Worksheet();

      EmbeddedTableAssembly left = TestWorksheets.tableWithColumns(ws, "LEFT_E", "ID", "NAME");
      left.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "ID", "NAME" },
            { "1", "a" },
         }));
      ws.addAssembly(left);

      EmbeddedTableAssembly right = TestWorksheets.tableWithColumns(ws, "RIGHT_E", "ID", "CITY");
      right.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "ID", "CITY" },
            { "2", "LA" },
         }));
      ws.addAssembly(right);

      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("LEFT_E");
      op.setRightTable("RIGHT_E");
      op.setLeftAttribute(new AttributeRef(null, "ID"));
      op.setRightAttribute(new AttributeRef(null, "ID"));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);
      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      RelationalJoinTableAssembly joined = new RelationalJoinTableAssembly(
         ws, "JOINED_EMPTY", new TableAssembly[]{ left, right }, new TableAssemblyOperator[]{ top });
      ws.addAssembly(joined);

      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      // Deliberately no box.refreshColumnSelection("JOINED_EMPTY", false) here -- the join's
      // merged column selection is left unresolved (colCount == 0), which is what actually
      // triggers XEmbeddedTable's EmptyTableToEmbeddedException below, not an empty row set.

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);

      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      when(sessions.resolve(eq("TOK-CTE-EMPTY"), any())).thenReturn(session("TOK-CTE-EMPTY"));
      when(runtimeAccess.getSheetForPairing(any(), any(), any())).thenReturn(rws);

      WorksheetEditService editSvc = new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), mock(SecurityEngine.class), mock(InnerJoinService.class));

      WorksheetAgentController ctrl = controller(featureOn(),
         mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(WorksheetReadService.class), editSvc, mock(WorksheetService.class));

      PairingException ex = assertThrows(PairingException.class,
         () -> ctrl.edit("TOK-CTE-EMPTY", convertToEmbeddedRequest("JOINED_EMPTY"), agent));
      assertTrue(ex.getMessage().contains("no data to convert"));
   }
}
