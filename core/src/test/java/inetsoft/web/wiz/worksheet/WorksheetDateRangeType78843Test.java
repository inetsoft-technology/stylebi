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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78843: a date range column was pinned to {@code string} in the live worksheet, hiding
 * its real type (timeInstant / integer / time) from read_worksheet_model, mirrors and joins.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetDateRangeType78843Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;

   @BeforeEach
   void setUp() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(orders(ws, "U"));
      svc = service(ws);
   }

   @Test
   void truncatingOptionIsTimeInstant() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("U", "od", "MONTH"));
      assertEquals(XSchema.TIME_INSTANT, typeOf("Month(od)"));
   }

   @Test
   void partOptionIsInteger() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("U", "od", "QUARTER_OF_YEAR"));
      assertEquals(XSchema.INTEGER, typeOf("QuarterOfYear(od)"));
   }

   @Test
   void hourOnTimeSourceStaysTime() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("U", "tm", "HOUR"));
      assertEquals(XSchema.TIME, typeOf("Hour(tm)"));
   }

   @Test
   void editFlipsTypeBetweenTruncatingAndPartAndBack() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("U", "od", "MONTH"));
      svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("U", "Month(od)", "QUARTER_OF_YEAR"));
      assertEquals(XSchema.INTEGER, typeOf("QuarterOfYear(od)"));
      svc.apply("TOK", agent, ed -> ed.editDateRangeColumn("U", "QuarterOfYear(od)", "MONTH"));
      assertEquals(XSchema.TIME_INSTANT, typeOf("Month(od)"));
   }

   @Test
   void rangeOnRangeColumnIsRefused() throws Exception {
      svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("U", "od", "MONTH"));
      PairingException ex = assertThrows(PairingException.class, () ->
         svc.apply("TOK", agent, ed -> ed.addDateRangeColumn("U", "Month(od)", "YEAR")));
      assertTrue(ex.getMessage().contains("Month(od)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("already a date range column"), ex.getMessage());
   }

   private String typeOf(String column) {
      TableAssembly t = (TableAssembly) ws.getAssembly("U");
      DataRef ref = t.getColumnSelection(false).getAttribute(column);
      assertNotNull(ref, column);
      return ((ColumnRef) ref).getDataType();
   }

   private static EmbeddedTableAssembly orders(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "od", "tm");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "date", "time" },
         new Object[][]{
            { "od", "tm" },
            { java.sql.Date.valueOf("2024-01-15"), java.sql.Time.valueOf("10:30:00") },
         }));
      ColumnSelection cs = t.getColumnSelection(false);
      ((ColumnRef) cs.getAttribute("od")).setDataType(XSchema.DATE);
      ((ColumnRef) cs.getAttribute("tm")).setDataType(XSchema.TIME);
      return t;
   }

   private static WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any()))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess,
         mock(SheetAgentBroadcastService.class), securityEngine, mock(InnerJoinService.class));
   }
}
