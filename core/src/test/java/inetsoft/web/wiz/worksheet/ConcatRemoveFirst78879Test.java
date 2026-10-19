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
 * Bug #78879: remove_concat_subtable is refused before anything is mutated when removing the
 * first subtable would make the next one supply the columns and silently drop state the
 * concatenation or its dependents hold (the same check as reorder_concat_subtables, Bug #78846),
 * and a name that is not a subtable fails loudly instead of reporting a removal that never
 * happened. Runs the real column refresh, as {@code ConcatReorderGuard78846Test} does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class ConcatRemoveFirst78879Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   // ------------------------------------------------------------------- the first subtable removed

   @Test
   void removingTheFirstWithOtherNamesIsRefusedNamingTheTableAndItsDependents()
      throws Exception
   {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" },
                                                { "id", "amount" } });
      concat(ws, "C", "A", "B", "X");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "qty", ">", "5");
         ed.addMirror("G", "C");
         ed.addFilter("G", "qty", "<", "50");
         ed.addMirror("H", "C");
         ed.setGroupAggregate(
            "H", List.of(new WorksheetMutationSupport.GroupSpec("id")),
            List.of(new WorksheetMutationSupport.AggregateSpec("qty", "Sum", null)), false, false);
      });
      List<String> before = state(ws);

      PairingException ex = assertThrows(PairingException.class, () -> remove(ws, "A", false));

      assertTrue(ex.getMessage().contains(
         "Removing 'A' from 'C' (which makes 'B' its first subtable)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'G' (pre-condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'H' (aggregate/group-by"), ex.getMessage());
      assertTrue(ex.getMessage().contains("table 'C' itself (pre-condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      assertEquals(before, state(ws), "the refusal changed the worksheet");
   }

   @Test
   void removingTheFirstWithTheSameNamesStillLosesTheTablesOwnFilterAndIsRefused()
      throws Exception
   {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" }, { "k", "v" } });
      concat(ws, "C", "A", "B", "X");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "v", ">", "5");
         ed.addMirror("G", "C");
         ed.addFilter("G", "v", "<", "50");
      });
      List<String> before = state(ws);

      PairingException ex = assertThrows(PairingException.class, () -> remove(ws, "A", false));

      assertTrue(ex.getMessage().contains("table 'C' itself would lose pre-condition"),
                 ex.getMessage());
      // the mirror reads C's column by name, and the name survives
      assertFalse(ex.getMessage().contains("'G'"), ex.getMessage());
      assertEquals(before, state(ws), "the refusal changed the worksheet");
   }

   @Test
   void confirmedRemovesTheFirstAndTheRealRefreshRepointsTheColumnsAtTheNext()
      throws Exception
   {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" },
                                                { "id", "amount" } });
      concat(ws, "C", "A", "B", "X");
      service(ws).apply("TOK", agent, ed -> ed.addFilter("C", "qty", ">", "5"));
      TableAssembly c = (TableAssembly) ws.getAssembly("C");

      remove(ws, "A", true);
      realRefresh(ws, "C");

      assertArrayEquals(new String[] { "B", "X" },
                        ((ConcatenatedTableAssembly) ws.getAssembly("C")).getTableNames());
      assertEquals(List.of("id", "amount"), attributes(c));
      assertEquals("B", ((ColumnRef) c.getColumnSelection().getAttribute(0)).getEntity());
   }

   @Test
   void aJoinKeyOnAColumnThatLeavesIsRefusedEvenWhenConfirmed() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" },
                                                { "id", "amount" } });
      concat(ws, "C", "A", "B", "X");
      // the join needs the key types to match
      EmbeddedTableAssembly r = TestWorksheets.tableWithColumns(ws, "R", "k");
      ((ColumnRef) r.getColumnSelection().getAttribute(0)).setDataType(
         ((TableAssembly) ws.getAssembly("C")).getColumnSelection().getAttribute(1).getDataType());
      ws.addAssembly(r);
      service(ws).apply("TOK", agent, ed -> {
         ed.addJoin("J", "C", "qty", "R", "k", "INNER", null, null);
      });

      for(boolean confirmed : new boolean[] { false, true }) {
         PairingException ex =
            assertThrows(PairingException.class, () -> remove(ws, "A", confirmed));
         assertTrue(ex.getMessage().contains("'J' (join key on"), ex.getMessage());
         assertTrue(ex.getMessage().contains("remove a subtable other than the first"),
                    ex.getMessage());
         assertFalse(ex.getMessage().contains("keep the current first subtable"),
                     ex.getMessage());
         assertFalse(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      }

      assertArrayEquals(new String[] { "A", "B", "X" },
                        ((ConcatenatedTableAssembly) ws.getAssembly("C")).getTableNames());
   }

   // ------------------------------------------------------------------- removals that stay allowed

   @Test
   void removingANonFirstSubtableIsAllowedWithoutConfirmationAndKeepsTheState()
      throws Exception
   {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" },
                                                { "id", "amount" } });
      concat(ws, "C", "A", "B", "X");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "qty", ">", "5");
         ed.addMirror("G", "C");
         ed.addFilter("G", "qty", "<", "50");
      });
      TableAssembly c = (TableAssembly) ws.getAssembly("C");
      TableAssembly g = (TableAssembly) ws.getAssembly("G");
      ColumnRef condition = (ColumnRef) ((ConditionList) c.getPreConditionList())
         .getConditionItem(0).getAttribute();

      remove(ws, "B", false);
      realRefresh(ws, "C");

      assertArrayEquals(new String[] { "A", "X" },
                        ((ConcatenatedTableAssembly) c).getTableNames());
      assertTrue(c.getColumnSelection().containsAttribute(condition),
                 "the first subtable is the same, so the columns are too");
      assertEquals(1, conditions(c));
      assertEquals(1, conditions(g));
   }

   @Test
   void removingTheFirstOfTwoStillDeletesTheConcatenationWithoutAStateCheck()
      throws Exception
   {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" } });
      concat(ws, "C", "A", "B");
      service(ws).apply("TOK", agent, ed -> ed.addFilter("C", "qty", ">", "5"));

      remove(ws, "A", false);

      assertNull(ws.getAssembly("C"));
   }

   // ------------------------------------------------------------------- the name itself

   @Test
   void aNameThatIsNotASubtableIsRefusedLoudlyAndNothingChanges() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" }, { "k", "v" } });
      concat(ws, "C", "A", "B");
      List<String> before = state(ws);

      for(String name : new String[] { "NOPE", "X" }) {
         for(boolean confirmed : new boolean[] { false, true }) {
            PairingException ex =
               assertThrows(PairingException.class, () -> remove(ws, name, confirmed), name);
            assertTrue(ex.getMessage().contains("is not a subtable of \"C\""), ex.getMessage());
            assertTrue(ex.getMessage().contains("A, B"), ex.getMessage());
            assertTrue(ex.getMessage().contains("Nothing was changed."), ex.getMessage());
         }
      }

      assertEquals(before, state(ws));
      assertArrayEquals(new String[] { "A", "B" },
                        ((ConcatenatedTableAssembly) ws.getAssembly("C")).getTableNames());
   }

   // ------------------------------------------------------------------- fixtures

   /** A concatenation as the tool builds it, with the columns the refresh gives it. */
   private void concat(Worksheet ws, String name, String... tables) throws Exception {
      service(ws).apply("TOK", agent, ed -> ed.addConcatenation(name, List.of(tables), "UNION"));
      ((ConcatenatedTableAssembly) ws.getAssembly(name)).initDefaultColumnSelection();
   }

   /** What remove_concat_subtable does: the edit, then the refresh of every table. */
   private void remove(Worksheet ws, String name, boolean confirmed) throws Exception {
      service(ws).apply("TOK", agent, ed -> ed.removeConcatSubtable("C", name, confirmed));
   }

   private static void realRefresh(Worksheet ws, String name) throws Exception {
      new AssetQuerySandbox(ws).refreshColumnSelection(name, true);
   }

   private static List<String> attributes(TableAssembly table) {
      List<String> names = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      return names;
   }

   /** The number of conditions in the table's pre-condition list. */
   private static int conditions(TableAssembly table) {
      return table.getPreConditionList() instanceof ConditionList list
         ? (list.getConditionSize() + 1) / 2 : 0;
   }

   /**
    * Tables A, B, X (as many as {@code columns} has rows), each holding the named integer columns.
    */
   private static Worksheet worksheet(String[][] columns) {
      Worksheet ws = new Worksheet();
      String[] names = { "A", "B", "X" };

      for(int i = 0; i < columns.length; i++) {
         EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, names[i], columns[i]);
         ColumnSelection selection = table.getColumnSelection();

         for(int j = 0; j < selection.getAttributeCount(); j++) {
            ((ColumnRef) selection.getAttribute(j)).setDataType("integer");
         }

         // a header row, then two rows of values
         String[] types = new String[columns[i].length];
         Object[][] rows = new Object[3][columns[i].length];
         Arrays.fill(types, "integer");

         for(int j = 0; j < types.length; j++) {
            rows[0][j] = columns[i][j];
            rows[1][j] = 10 * (i + 1) + j;
            rows[2][j] = 20 * (i + 1) + j;
         }

         table.setEmbeddedData(new XEmbeddedTable(types, rows));
         ws.addAssembly(table);
      }

      return ws;
   }

   /** Everything a refusal must leave alone: the assemblies, their columns and conditions. */
   private static List<String> state(Worksheet ws) {
      List<String> state = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         String line = assembly.getName();

         if(assembly instanceof ConcatenatedTableAssembly concat) {
            line += " " + Arrays.toString(concat.getTableNames());
         }

         if(assembly instanceof TableAssembly table) {
            line += " " + table.getColumnSelection() + " " + table.getPreConditionList() + " " +
               Arrays.toString(table.getAggregateInfo().getAggregates());
         }

         state.add(line);
      }

      Collections.sort(state);
      return state;
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
