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
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81523: remove_column on a column a variable's query picker reads is refused naming the
 * variable (unless confirmed), and the column and the picker stay unchanged.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetRemoveColumnVariablePicker81523Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(
         eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent))).thenReturn(rws);
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      new InnerJoinService(null, null));
   }

   private TableAssembly tableWithPicker(Worksheet ws) throws Exception {
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "ST", "NAME", "N");
      ws.addAssembly(t);
      AssetVariable var = new AssetVariable();
      var.setName("V");
      var.setTableName("T");
      var.setValueAttribute(t.getColumnSelection(false).getAttribute("ST"));
      var.setLabelAttribute(t.getColumnSelection(false).getAttribute("NAME"));
      DefaultVariableAssembly va = new DefaultVariableAssembly(ws, "V");
      va.setVariable(var);
      ws.addAssembly(va);
      return t;
   }

   @Test
   void removingThePickerValueColumnIsRefusedNamingTheVariable() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithPicker(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeColumn("T", "ST", false)));

      assertTrue(ex.getMessage().contains("'V'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      assertNotNull(t.getColumnSelection(false).getAttribute("ST"));
      assertEquals("T", ((DefaultVariableAssembly) ws.getAssembly("V")).getVariable()
         .getTableName());
   }

   @Test
   void removingThePickerLabelColumnIsRefusedNamingTheVariable() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithPicker(ws);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.removeColumn("T", "NAME", false)));

      assertTrue(ex.getMessage().contains("'V'"), ex.getMessage());
      assertNotNull(t.getColumnSelection(false).getAttribute("NAME"));
   }

   @Test
   void removingAColumnThePickerDoesNotReadStillWorks() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithPicker(ws);

      svc.apply("TOK", agent, ed -> ed.removeColumn("T", "N", false));

      assertNull(t.getColumnSelection(false).getAttribute("N"));
   }

   @Test
   void confirmedRemovalOfAPickerColumnProceeds() throws Exception {
      Worksheet ws = new Worksheet();
      WorksheetEditService svc = service(ws);
      TableAssembly t = tableWithPicker(ws);

      svc.apply("TOK", agent, ed -> ed.removeColumn("T", "ST", true));

      assertNull(t.getColumnSelection(false).getAttribute("ST"));
   }
}
