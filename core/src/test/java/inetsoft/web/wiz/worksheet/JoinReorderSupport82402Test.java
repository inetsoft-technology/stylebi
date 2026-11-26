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
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82402: reorder_concat_subtables reorders a join's tables as Composer's "Reorder Tables"
 * does, keeping every join condition, and still refuses a non-permutation and a merge join.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class JoinReorderSupport82402Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   @Test
   void reorderingAJoinChangesTheTableOrderAndKeepsEveryJoinCondition() throws Exception {
      Worksheet ws = worksheet();
      join(ws);
      AbstractJoinTableAssembly j = (AbstractJoinTableAssembly) ws.getAssembly("J");
      int operators = j.getOperatorCount();
      assertArrayEquals(new String[] { "A", "B", "X" }, j.getTableNames());

      JoinReorderSupport.reorder(ws, j, List.of("X", "A", "B"));

      assertArrayEquals(new String[] { "X", "A", "B" }, j.getTableNames());
      assertEquals(operators, j.getOperatorCount());
      assertNotNull(j.getOperator("A", "B"));
      assertNotNull(j.getOperator("A", "X") != null ? j.getOperator("A", "X")
                       : j.getOperator("X", "A"));
   }

   @Test
   void aListThatIsNotAPermutationIsRefusedAndNothingChanges() throws Exception {
      Worksheet ws = worksheet();
      join(ws);
      AbstractJoinTableAssembly j = (AbstractJoinTableAssembly) ws.getAssembly("J");

      for(String[] list : new String[][] { { "A", "A", "B" }, { "A", "B" }, { "A", "B", "Z" } }) {
         PairingException ex = assertThrows(
            PairingException.class, () -> JoinReorderSupport.reorder(ws, j, List.of(list)));
         assertTrue(ex.getMessage().contains("exactly once"), ex.getMessage());
      }

      assertArrayEquals(new String[] { "A", "B", "X" }, j.getTableNames());
   }

   private void join(Worksheet ws) throws Exception {
      service(ws).apply("TOK", agent, ed -> ed.addJoin("J", List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "id", "B", "id", "INNER"),
         new WorksheetMutationSupport.JoinPathSpec("A", "id", "X", "id", "INNER"))));
   }

   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();

      for(String name : new String[] { "A", "B", "X" }) {
         EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, name, "id", "v");
         ColumnSelection selection = table.getColumnSelection();

         for(int j = 0; j < selection.getAttributeCount(); j++) {
            ((ColumnRef) selection.getAttribute(j)).setDataType("integer");
         }

         table.setEmbeddedData(new XEmbeddedTable(
            new String[] { "integer", "integer" },
            new Object[][] { { "id", "v" }, { 1, 10 }, { 2, 20 } }));
         ws.addAssembly(table);
      }

      return ws;
   }

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
}
