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
 * Bug #78846: reorder_concat_subtables is refused before anything is mutated when putting another
 * subtable first would silently drop state the concatenation or its dependents hold, and the
 * state the guard says is lost is what the real refresh drops. Runs the real column refresh, as
 * {@code MirrorResyncGuard78814Test} does for update_mirror.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class ConcatReorderGuard78846Test {
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   // ------------------------------------------------------------------- first subtable changes

   @Test
   void aFirstSubtableWithOtherNamesIsRefusedNamingTheTableAndItsDependents() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" } });
      concat(ws, "C", "A", "B");
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

      PairingException ex = assertThrows(PairingException.class, () -> reorder(ws, false, "B", "A"));

      assertTrue(ex.getMessage().contains("Putting 'B' first in 'C'"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'G' (pre-condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'H' (aggregate/group-by"), ex.getMessage());
      assertTrue(ex.getMessage().contains("table 'C' itself (pre-condition"), ex.getMessage());
      assertTrue(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      assertEquals(before, state(ws), "the guard changed the worksheet");
   }

   @Test
   void aFirstSubtableWithTheSameNamesStillLosesTheTablesOwnFilterAndIsRefused()
      throws Exception
   {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" } });
      concat(ws, "C", "A", "B");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "v", ">", "5");
         ed.addMirror("G", "C");
         ed.addFilter("G", "v", "<", "50");
      });

      PairingException ex = assertThrows(PairingException.class, () -> reorder(ws, false, "B", "A"));

      assertTrue(ex.getMessage().contains("table 'C' itself would lose pre-condition"),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("changes the source its columns come from"),
                 ex.getMessage());
      // the mirror reads C's column by name, and the name survives
      assertFalse(ex.getMessage().contains("'G'"), ex.getMessage());
   }

   @Test
   void theRealRefreshRepointsTheColumnsAtTheNewFirstSubtableAsTheGuardPredicts()
      throws Exception
   {
      for(String[][] columns : new String[][][] { { { "k", "v" }, { "k", "v" } },
                                                  { { "id", "qty" }, { "id", "amount" } } })
      {
         Worksheet ws = worksheet(columns);
         concat(ws, "C", "A", "B");
         service(ws).apply("TOK", agent, ed -> ed.addFilter("C", columns[0][1], ">", "5"));
         TableAssembly c = (TableAssembly) ws.getAssembly("C");
         ColumnRef condition = (ColumnRef) ((ConditionList) c.getPreConditionList())
            .getConditionItem(0).getAttribute();
         assertTrue(c.getColumnSelection().containsAttribute(condition));

         reorder(ws, true, "B", "A");
         realRefresh(ws, "C");

         // the columns are the new first subtable's, so what the table held on the old ones
         // no longer resolves, even when the names are the same
         assertEquals(List.of(columns[1]), attributes(c));
         assertEquals("B", ((ColumnRef) c.getColumnSelection().getAttribute(0)).getEntity());
         assertFalse(c.getColumnSelection().containsAttribute(condition));
      }
   }

   @Test
   void anAliasedOrHiddenColumnIsAStateTheReorderLoses() throws Exception {
      for(String kind : new String[] { "alias", "hidden" }) {
         Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" } });
         concat(ws, "C", "A", "B");
         ColumnSelection columns = ((TableAssembly) ws.getAssembly("C")).getColumnSelection();
         ColumnRef column = (ColumnRef) columns.getAttribute(1);

         if(kind.equals("alias")) {
            column.setAlias("value");
         }
         else {
            column.setVisible(false);
         }

         PairingException ex =
            assertThrows(PairingException.class, () -> reorder(ws, false, "B", "A"), kind);
         assertTrue(
            ex.getMessage().contains(kind.equals("alias") ? "alias of column" : "hidden column"),
            ex.getMessage());

         // the same column state is kept when the first subtable does not change
         reorder(ws, false, "A", "B");
      }
   }

   @Test
   void anAggregateOnAColumnThatLeavesIsAStateTheReorderLoses() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "w" } });
      concat(ws, "C", "A", "B");
      service(ws).apply("TOK", agent, ed -> {
         ed.setGroupAggregate(
            "C", List.of(new WorksheetMutationSupport.GroupSpec("k")),
            List.of(new WorksheetMutationSupport.AggregateSpec("v", "Sum", null)), false, false);
      });

      PairingException ex = assertThrows(PairingException.class, () -> reorder(ws, false, "B", "A"));

      assertTrue(ex.getMessage().contains("aggregate/group-by"), ex.getMessage());
   }

   @Test
   void confirmedProceedsAndThePermutationIsApplied() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" } });
      concat(ws, "C", "A", "B");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "qty", ">", "5");
      });

      reorder(ws, true, "B", "A");

      assertArrayEquals(new String[] { "B", "A" },
                        ((ConcatenatedTableAssembly) ws.getAssembly("C")).getTableNames());
   }

   // ------------------------------------------------------------------- keys and nesting

   @Test
   void aJoinKeyOnAColumnThatLeavesIsRefusedEvenWhenConfirmed() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" } });
      concat(ws, "C", "A", "B");
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
            assertThrows(PairingException.class, () -> reorder(ws, confirmed, "B", "A"));
         assertTrue(ex.getMessage().contains("'J' (join key on"), ex.getMessage());
         assertFalse(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      }
   }

   @Test
   void aConcatenationFedByAChangedColumnListIsRefusedEvenWhenConfirmed() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "id", "qty" }, { "id", "amount" },
                                                { "id", "qty" } });
      concat(ws, "C", "A", "B");
      concat(ws, "D", "C", "X");

      for(boolean confirmed : new boolean[] { false, true }) {
         PairingException ex =
            assertThrows(PairingException.class, () -> reorder(ws, confirmed, "B", "A"));
         assertTrue(ex.getMessage().contains("concatenation(s) 'D'"), ex.getMessage());
         assertFalse(ex.getMessage().contains("confirmed:true"), ex.getMessage());
      }
   }

   // ------------------------------------------------------------------- reorders that stay allowed

   @Test
   void aReorderKeepingTheFirstSubtableIsAllowedAndKeepsTheState() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" }, { "k", "v" } });
      concat(ws, "C", "A", "B", "X");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "v", ">", "5");
         ed.addMirror("G", "C");
         ed.addFilter("G", "v", "<", "50");
      });
      TableAssembly c = (TableAssembly) ws.getAssembly("C");
      TableAssembly g = (TableAssembly) ws.getAssembly("G");
      ColumnRef condition = (ColumnRef) ((ConditionList) c.getPreConditionList())
         .getConditionItem(0).getAttribute();

      reorder(ws, false, "A", "X", "B");
      realRefresh(ws, "C");

      assertTrue(c.getColumnSelection().containsAttribute(condition),
                 "the first subtable is the same, so the columns are too");

      assertArrayEquals(new String[] { "A", "X", "B" },
                        ((ConcatenatedTableAssembly) c).getTableNames());
      assertEquals(1, conditions(c));
      assertEquals(1, conditions(g));
   }

   @Test
   void theCurrentOrderIsAccepted() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" } });
      concat(ws, "C", "A", "B");
      service(ws).apply("TOK", agent, ed -> {
         ed.addFilter("C", "v", ">", "5");
      });

      reorder(ws, false, "A", "B");

      assertEquals(1, conditions((TableAssembly) ws.getAssembly("C")));
   }

   // ------------------------------------------------------------------- the list itself

   @Test
   void aListThatIsNotAPermutationIsRefusedLoudlyAndNothingChanges() throws Exception {
      Worksheet ws = worksheet(new String[][] { { "k", "v" }, { "k", "v" }, { "k", "v" } });
      concat(ws, "C", "A", "B");
      List<String> before = state(ws);

      for(String[] list : new String[][] { { "A", "A" }, { "A", "X" }, { "A" }, { "A", "B", "X" },
                                          { "B", "B" } })
      {
         for(boolean confirmed : new boolean[] { false, true }) {
            PairingException ex = assertThrows(
               PairingException.class, () -> reorder(ws, confirmed, list), Arrays.toString(list));
            assertTrue(ex.getMessage().contains("exactly once"), ex.getMessage());
            assertTrue(ex.getMessage().contains("A, B"), ex.getMessage());
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

   /** What reorder_concat_subtables does: resolve, guard, then reorder. */
   private void reorder(Worksheet ws, boolean confirmed, String... names) throws Exception {
      ConcatenatedTableAssembly c = (ConcatenatedTableAssembly) ws.getAssembly("C");
      TableAssembly[] reordered = ConcatReorderGuard.resolvePermutation(ws, c, List.of(names));
      ConcatReorderGuard.requireReorderKeepsState(ws, c, reordered, confirmed);
      assertTrue(c.reorderTableAssemblies(reordered));
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

   /** Everything the guard must leave alone: the assemblies, their columns and conditions. */
   private static List<String> state(Worksheet ws) {
      List<String> state = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         String line = assembly.getName();

         if(assembly instanceof ConcatenatedTableAssembly concat) {
            line += " " + Arrays.toString(concat.getTableNames());
         }

         if(assembly instanceof TableAssembly table) {
            line += " " + table.getPreConditionList() + " " +
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
