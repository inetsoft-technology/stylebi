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
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.CoreTool;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79595: a stored aggregate/group/sort input whose column vanished from the upstream table
 * was silently dropped by PreAssetQuery.validate(), so the downstream table lost the column with
 * no warning. The runtime preview now warns, naming the table and the column.
 */
@Tag("core")
@org.junit.jupiter.api.extension.ExtendWith(
   org.springframework.test.context.junit.jupiter.SpringExtension.class)
@org.springframework.test.context.ContextConfiguration(
   classes = { inetsoft.test.BaseTestConfiguration.class,
               inetsoft.test.SwapperTestConfiguration.class,
               inetsoft.test.dc.DcInMemoryViewsheetFixture.Config.class },
   initializers = inetsoft.test.ConfigurationContextInitializer.class)
@inetsoft.test.SreeHome
class UpstreamColumnLoss79595Test {
   @BeforeEach
   void prep() {
      inetsoft.report.composition.execution.AssetDataCache.getCache().clearCache();
      inetsoft.util.ThreadContext.setPrincipal(inetsoft.sree.internal.SUtil.getPrincipal(
         new inetsoft.sree.security.IdentityID(inetsoft.uql.XPrincipal.SYSTEM,
            inetsoft.sree.security.OrganizationManager.getInstance().getCurrentOrgID()),
         null, false));
   }

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;
   private AssetQuerySandbox box;
   private RuntimeWorksheet rws;

   /** U(pid, oid, qty, od) with aggregate mirror M = group by pid, Sum(qty). */
   private void setup() throws Exception {
      ws = new Worksheet();
      ws.addAssembly(orders(ws, "U"));
      svc = service(ws);
      svc.apply("TOK", agent, ed -> ed.addMirror("M", "U"));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("M",
         List.of(new WorksheetMutationSupport.GroupSpec("pid")),
         List.of(new WorksheetMutationSupport.AggregateSpec("qty", "SUM", null))));
   }

   /** Simulate a schema change: U loses the given column, then reopen (fresh sandbox). */
   private void dropFromU(String column) {
      TableAssembly u = (TableAssembly) ws.getAssembly("U");
      List<String> names = new java.util.ArrayList<>(List.of("pid", "oid", "qty", "od"));
      int drop = names.indexOf(column);
      Object[][] full = {
         { 1, 10501, 5, java.sql.Date.valueOf("2024-01-15") },
         { 1, 10502, 7, java.sql.Date.valueOf("2024-04-15") },
         { 2, 10501, 3, java.sql.Date.valueOf("2024-07-15") },
      };
      String[] types = { "integer", "integer", "integer", "date" };
      names.remove(drop);
      String[] ntypes = new String[3];
      Object[][] data = new Object[4][3];
      data[0] = names.toArray();

      for(int c = 0, k = 0; c < 4; c++) {
         if(c == drop) {
            continue;
         }

         ntypes[k] = types[c];

         for(int r = 0; r < full.length; r++) {
            data[r + 1][k] = full[r][c];
         }

         k++;
      }

      ((EmbeddedTableAssembly) u).setEmbeddedData(new XEmbeddedTable(ntypes, data));

      for(boolean pub : new boolean[]{ false, true }) {
         ColumnSelection cs = u.getColumnSelection(pub);
         cs.removeAttribute(cs.getAttribute(column));
         u.setColumnSelection(cs, pub);
      }

      reopen();
   }

   private void reopen() {
      inetsoft.report.composition.execution.AssetDataCache.getCache().clearCache();
      box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
   }

   private List<String> warnings(String table) throws Exception {
      CoreTool.clearUserMessage();
      return new WorksheetPreviewService().preview(rws, table, 0, 50).warnings();
   }

   private static boolean mentions(List<String> warnings, String table, String column) {
      return warnings.stream().anyMatch(w -> w.contains(table) && w.contains(column));
   }

   @Test
   void droppedAggregateInputWarnsNamingTableAndColumn() throws Exception {
      setup();
      assertEquals(List.of(), warnings("M"));
      dropFromU("qty");
      List<String> warnings = warnings("M");
      assertTrue(mentions(warnings, "M", "qty"), "warnings: " + warnings);
      assertEquals(1, warnings.stream().filter(w -> w.contains("qty")).count(),
                   "deduped: " + warnings);
   }

   @Test
   void droppedGroupInputWarnsNamingTableAndColumn() throws Exception {
      setup();
      dropFromU("pid");
      List<String> warnings = warnings("M");
      assertTrue(mentions(warnings, "M", "pid"), "warnings: " + warnings);
   }

   @Test
   void healthyWorksheetDoesNotWarn() throws Exception {
      setup();
      assertEquals(List.of(), warnings("U"));
      assertEquals(List.of(), warnings("M"));
   }

   @Test
   void unrelatedUpstreamColumnDropDoesNotWarn() throws Exception {
      setup();
      dropFromU("oid");
      assertEquals(List.of(), warnings("M"));
   }

   @Test
   void crosstabDoesNotWarn() throws Exception {
      setup();
      svc.apply("TOK", agent, ed -> ed.addMirror("X", "U"));
      svc.apply("TOK", agent, ed -> ed.setGroupAggregate("X",
         List.of(new WorksheetMutationSupport.GroupSpec("pid"),
                 new WorksheetMutationSupport.GroupSpec("oid")),
         List.of(new WorksheetMutationSupport.AggregateSpec("qty", "SUM", null)), true));
      assertTrue(((AbstractTableAssembly) ws.getAssembly("X")).isCrosstab(), "crosstab applied");
      dropFromU("qty");
      List<String> warnings = warnings("X");
      assertFalse(mentions(warnings, "X", "qty"), "warnings: " + warnings);
   }

   @Test
   void vpmFilteredSandboxDoesNotWarn() throws Exception {
      setup();
      dropFromU("qty");
      box.setVPMUser(new XPrincipal(new inetsoft.sree.security.IdentityID(
         "vpmuser", inetsoft.sree.security.OrganizationManager.getInstance().getCurrentOrgID())));
      List<String> warnings = warnings("M");
      assertFalse(mentions(warnings, "M", "qty"), "warnings: " + warnings);
   }

   private static EmbeddedTableAssembly orders(Worksheet ws, String name) {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, name, "pid", "oid", "qty", "od");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "integer", "integer", "integer", "date" },
         new Object[][]{
            { "pid", "oid", "qty", "od" },
            { 1, 10501, 5, java.sql.Date.valueOf("2024-01-15") },
            { 1, 10502, 7, java.sql.Date.valueOf("2024-04-15") },
            { 2, 10501, 3, java.sql.Date.valueOf("2024-07-15") },
            { 2, 10503, 9, java.sql.Date.valueOf("2024-10-15") },
         }));
      ColumnSelection cs = t.getColumnSelection(false);
      for(String c : List.of("pid", "oid", "qty")) {
         ((ColumnRef) cs.getAttribute(c)).setDataType(XSchema.INTEGER);
      }
      ((ColumnRef) cs.getAttribute("od")).setDataType(XSchema.DATE);
      return t;
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), any(String.class), any(ResourceAction.class))).thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org", SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), any())).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, mock(SheetAgentBroadcastService.class), securityEngine, new InnerJoinService(null, null));
   }
}
