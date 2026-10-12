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
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
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
 * Redmine #78395 item 3: worksheet {@code insert_row} with an index past the end silently
 * clamped to an append. It must refuse with "Row index out of range" like {@code delete_row}/
 * {@code edit_cell}, while {@code index == data rows} keeps appending at the end.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetInsertRowRange78395Test {
   @Test
   void insertAtTheDataRowCountAppends() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = table(ws);
      Principal agent = TestPrincipals.user("alice", "host-org");

      service(ws, agent).apply("TOK", agent, ed -> ed.insertRow("T", 2));

      XEmbeddedTable data = t.getEmbeddedData();
      assertEquals(4, data.getRowCount());
      assertEquals("x2", data.getObject(2, 0));
      assertNull(data.getObject(3, 0));
   }

   @Test
   void insertInsideTheTableShiftsRowsDown() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = table(ws);
      Principal agent = TestPrincipals.user("alice", "host-org");

      service(ws, agent).apply("TOK", agent, ed -> ed.insertRow("T", 0));

      XEmbeddedTable data = t.getEmbeddedData();
      assertEquals(4, data.getRowCount());
      assertNull(data.getObject(1, 0));
      assertEquals("x1", data.getObject(2, 0));
   }

   @Test
   void insertPastTheEndIsRefusedAndLeavesTheTableUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = table(ws);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);

      for(int index : new int[] { 3, 99 }) {
         PairingException ex = assertThrows(PairingException.class,
            () -> svc.apply("TOK", agent, ed -> ed.insertRow("T", index)));

         assertTrue(ex.getMessage().startsWith("Row index out of range: " + index),
                    ex.getMessage());
         assertTrue(ex.getMessage().contains("0..2"), ex.getMessage());
      }

      XEmbeddedTable data = t.getEmbeddedData();
      assertEquals(3, data.getRowCount());
      assertEquals("x2", data.getObject(2, 0));
   }

   @Test
   void negativeIndexIsStillRefused() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws);
      Principal agent = TestPrincipals.user("alice", "host-org");
      WorksheetEditService svc = service(ws, agent);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.insertRow("T", -1)));

      assertTrue(ex.getMessage().contains(">= 0"), ex.getMessage());
   }

   /** header + 2 data rows */
   private static EmbeddedTableAssembly table(Worksheet ws) {
      EmbeddedTableAssembly t = new EmbeddedTableAssembly(ws, "T");
      t.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING },
         new Object[][] { { "a", "b" }, { "x1", "y1" }, { "x2", "y2" } }));
      ws.addAssembly(t);
      return t;
   }

   private static WorksheetEditService service(Worksheet ws, Principal agent) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"), eq(agent)))
         .thenReturn(rws);
      return new WorksheetEditService(
         sessions, runtimeAccess, broadcast, securityEngine, mock(InnerJoinService.class));
   }
}
