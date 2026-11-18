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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.AssetVariable;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.wiz.pairing.EditorContext;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.PaneState;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.PaneContext;
import inetsoft.web.wiz.worksheet.WorksheetEditService;
import inetsoft.web.wiz.worksheet.WorksheetReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81557: a worksheet pane session is served the same read-only context a viewsheet pane is,
 * with the syntax following the dialog's Script/SQL switch, the table's columns with types and the
 * worksheet variables as parameters.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetPaneContextControllerTest {
   private final Principal agent = () -> "admin";
   private final RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
   private SheetSessionService sessions;
   private SheetAgentFeature feature;
   private WorksheetPaneContextController controller;

   private static JoinSession session(String token, EditorContext ctx) {
      return new JoinSession(token, "Worksheet/ws-1", "admin", SheetType.WORKSHEET, 0L,
         Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, ctx);
   }

   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Orders", "price", "cost"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "Customers", "name"));
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "Region");
      variable.setVariable(new AssetVariable("Region"));
      ws.addAssembly(variable);
      when(rws.getWorksheet()).thenReturn(ws);

      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      sessions = mock(SheetSessionService.class);
      when(sessions.paneStateOf(any())).thenReturn(PaneState.DEFAULT);
      WorksheetEditService edit = mock(WorksheetEditService.class);
      when(edit.resolve(any(), any())).thenReturn(rws);
      ScriptApiService api = mock(ScriptApiService.class);
      when(api.functionNames()).thenReturn(List.of("runQuery"));

      controller = new WorksheetPaneContextController(feature, sessions, edit,
         new WorksheetPaneContextService(new WorksheetReadService(), api));
   }

   private PaneContext serve(String token, EditorContext ctx) throws Exception {
      when(sessions.resolve(eq(token), eq("admin"))).thenReturn(session(token, ctx));
      return controller.paneContext(token, agent);
   }

   @Test
   void aWorksheetExpressionIsServedWithItsTablesColumnsVariablesAndScriptFunctions()
      throws Exception
   {
      PaneContext ctx = serve("T1", new EditorContext("worksheetExpression", "Orders", "Margin",
                                                      null, true));

      assertEquals("worksheetExpression", ctx.kind());
      assertEquals("javascript", ctx.syntax().language());
      assertFalse(ctx.syntax().note().contains("not served"), ctx.syntax().note());
      assertEquals(List.of("Orders"), ctx.columns().stream().map(PaneContext.Table::name).toList());
      assertEquals(Boolean.TRUE, ctx.columns().get(0).current());
      assertEquals(List.of("price", "cost"),
                   ctx.columns().get(0).fields().stream().map(PaneContext.Field::column).toList());
      assertTrue(ctx.parameters().contains("Region"), ctx.parameters().toString());
      assertTrue(ctx.parameters().contains("_USER_"), ctx.parameters().toString());
      assertEquals(List.of("runQuery"), ctx.functions());
      assertTrue(ctx.notes().stream().anyMatch(n -> n.contains("pending")), ctx.notes().toString());
      assertEquals(2, ctx.components().size());
   }

   @Test
   void theSqlSwitchTheDialogReportedEmptiesTheFunctionsAndChangesTheSyntax() throws Exception {
      for(String kind : new String[] {"worksheetExpression", "worksheetConditionValue"}) {
         EditorContext grant = new EditorContext(kind, "Orders", "price", null);
         JoinSession s = session("T-" + kind, grant);
         when(sessions.resolve(eq("T-" + kind), eq("admin"))).thenReturn(s);

         assertEquals("javascript", controller.paneContext("T-" + kind, agent).syntax().language());

         when(sessions.paneStateOf(s)).thenReturn(new PaneState(true));
         PaneContext sql = controller.paneContext("T-" + kind, agent);

         assertEquals("sql", sql.syntax().language(), kind);
         assertTrue(sql.functions().isEmpty(), kind);
         assertTrue(sql.notes().stream().anyMatch(n -> n.contains("SQL mode")), kind);
         // The columns are the same in either mode.
         assertEquals(List.of("price", "cost"),
                      sql.columns().get(0).fields().stream().map(PaneContext.Field::column).toList());

         when(sessions.paneStateOf(s)).thenReturn(PaneState.DEFAULT);
         assertEquals("javascript",
                      controller.paneContext("T-" + kind, agent).syntax().language(),
                      "switching back needs no re-pairing");
      }
   }

   @Test
   void aTableThatIsNotInTheWorksheetBecomesANoteAndEveryTableIsListed() throws Exception {
      PaneContext ctx =
         serve("T2", new EditorContext("worksheetConditionValue", "Gone", "price", null));

      assertEquals(2, ctx.columns().size());
      assertTrue(ctx.notes().stream().anyMatch(n -> n.contains("'Gone' is not among")),
                 ctx.notes().toString());
   }

   @Test
   void aWholeSheetWorksheetSessionIsServedToo() throws Exception {
      PaneContext ctx = serve("WHOLE", null);

      assertEquals("sheet", ctx.kind());
      assertEquals(2, ctx.columns().size());
   }

   @Test
   void anExpiredTokenIsRefusedAsExpired() throws Exception {
      when(sessions.resolve(eq("GONE"), eq("admin"))).thenReturn(null);

      PairingException ex =
         assertThrows(PairingException.class, () -> controller.paneContext("GONE", agent));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind());
      assertEquals(HttpStatus.NOT_FOUND, controller.handlePairingException(ex).getStatusCode());
   }

   @Test
   void aDisabledFeatureIsRefused() {
      when(feature.isEnabled()).thenReturn(false);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                                () -> controller.paneContext("T", agent));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
   }
}
