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
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
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
 * Bug #78888: changing the first subtable of a concatenation (reorder_concat_subtables,
 * remove_concat_subtable) is refused only for the state the refresh really drops. The
 * concatenation's sort, expression and range columns and aggregate follow a column by name, so
 * they are refused only when the column's name is not among the new first subtable's; conditions,
 * rankings, aliases and hidden flags are lost whatever the name. Every case runs through both
 * entry points.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context the real column refresh needs
class ConcatGuard78888Test {
   private static final String[][] SAME = { { "k", "v", "w", "d" }, { "k", "v", "w", "d" },
                                            { "k", "v", "w", "d" } };
   // the new first subtable (B) has no column "v" or "d"
   private static final String[][] LEAVING = { { "k", "v", "w", "d" }, { "k", "x", "w", "e" },
                                               { "k", "x", "w", "e" } };

   private final Principal agent = TestPrincipals.user("alice", "host-org");

   /** The two ways the first subtable of C changes, from A to B. */
   private enum Entry { REORDER, REMOVE }

   private interface Setup {
      void run(Worksheet ws, WorksheetEditService.Editor ed) throws Exception;
   }

   // ------------------------------------------------------------------- kept on a surviving name

   @Test
   void aSortOnASurvivingNameIsNotRefused() throws Exception {
      allowed(SAME, (ws, ed) -> ed.setSort("C", "v", "DESC"));
   }

   @Test
   void anExpressionColumnOnASurvivingNameIsNotRefused() throws Exception {
      allowed(SAME, (ws, ed) ->
         ed.addExpressionColumn("C", "ex", "field['v'] * 2", "integer", false));
   }

   @Test
   void aNumericRangeColumnOnASurvivingNameIsNotRefused() throws Exception {
      allowed(SAME, (ws, ed) -> ed.addNumericRangeColumn("C", "v", new double[] { 15 }, null));
   }

   @Test
   void aPlainAggregateOnSurvivingNamesIsNotRefused() throws Exception {
      allowed(SAME, (ws, ed) -> ed.setGroupAggregate("C", groups("k"), sum("v")));
   }

   @Test
   void aDateLevelGroupOnASurvivingDateColumnIsNotRefused() throws Exception {
      allowed(SAME, (ws, ed) -> ed.setGroupAggregate("C", dateGroup(), sum("v"), false, false));
   }

   @Test
   void aTwoColumnAggregateOnSurvivingNamesIsNotRefused() throws Exception {
      allowed(SAME, (ws, ed) -> ed.setGroupAggregate("C", groups("k"), correlation("w", "v")));
   }

   // ------------------------------------------------------------------- lost on a surviving name

   @Test
   void aPreConditionOnASurvivingNameIsStillRefused() throws Exception {
      refused(SAME, (ws, ed) -> ed.addFilter("C", "v", ">", "5"), "pre-condition");
   }

   @Test
   void aRankingOnASurvivingNameIsStillRefused() throws Exception {
      refused(SAME, (ws, ed) -> {
         ed.setGroupAggregate("C", groups("k"), sum("v"));
         ed.setRanking("C", new WorksheetMutationSupport.RankingSpec("v", 2, "TOP_N", false));
      }, "ranking condition");
   }

   @Test
   void anAliasIsStillRefused() throws Exception {
      refused(SAME, (ws, ed) -> column(ws).setAlias("value"), "alias of column");
   }

   @Test
   void aHiddenColumnIsStillRefused() throws Exception {
      refused(SAME, (ws, ed) -> column(ws).setVisible(false), "hidden column");
   }

   // ------------------------------------------------------------------- aggregate on a leaving name

   @Test
   void aGroupOnALeavingNameIsRefused() throws Exception {
      refused(LEAVING, (ws, ed) -> ed.setGroupAggregate("C", groups("v"), sum("w")),
              "aggregate/group-by");
   }

   @Test
   void anAggregateInputOnALeavingNameIsRefused() throws Exception {
      refused(LEAVING, (ws, ed) -> ed.setGroupAggregate("C", groups("k"), sum("v")),
              "aggregate/group-by");
   }

   @Test
   void aDateLevelGroupOnALeavingBaseColumnIsRefused() throws Exception {
      refused(LEAVING, (ws, ed) ->
                 ed.setGroupAggregate("C", dateGroup(), sum("w"), false, false),
              "aggregate/group-by");
   }

   @Test
   void aTwoColumnAggregateWithItsSecondaryOnALeavingNameIsRefused() throws Exception {
      refused(LEAVING, (ws, ed) ->
                 ed.setGroupAggregate("C", groups("k"), correlation("w", "v")),
              "aggregate/group-by");
   }

   // ------------------------------------------------------------------- harness

   /** The change is not refused through either entry point. */
   private void allowed(String[][] columns, Setup setup) throws Exception {
      for(Entry entry : Entry.values()) {
         Worksheet ws = prepared(columns, setup);
         assertDoesNotThrow(() -> change(ws, entry), entry.name());
      }
   }

   /** The change is refused through either entry point, naming {@code lost}, and nothing changes. */
   private void refused(String[][] columns, Setup setup, String lost) throws Exception {
      for(Entry entry : Entry.values()) {
         Worksheet ws = prepared(columns, setup);
         String before = state(ws);

         PairingException ex =
            assertThrows(PairingException.class, () -> change(ws, entry), entry.name());

         assertTrue(ex.getMessage().contains("table 'C' itself would lose"),
                    entry + ": " + ex.getMessage());
         assertTrue(ex.getMessage().contains(lost), entry + ": " + ex.getMessage());
         assertEquals(before, state(ws), entry + ": the refusal changed the worksheet");
      }
   }

   private Worksheet prepared(String[][] columns, Setup setup) throws Exception {
      Worksheet ws = worksheet(columns);
      service(ws).apply("TOK", agent,
                        ed -> ed.addConcatenation("C", List.of("A", "B", "X"), "UNION"));
      ConcatenatedTableAssembly c = (ConcatenatedTableAssembly) ws.getAssembly("C");
      c.initDefaultColumnSelection();

      for(int i = 0; i < c.getColumnSelection().getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) c.getColumnSelection().getAttribute(i);

         if(ref.getAttribute().equals("d")) {
            ref.setDataType("date");
         }
      }

      service(ws).apply("TOK", agent, ed -> setup.run(ws, ed));
      return ws;
   }

   /** Unconfirmed, as the tools call it: reorder to B, A, X, or remove A. */
   private void change(Worksheet ws, Entry entry) throws Exception {
      if(entry == Entry.REORDER) {
         ConcatenatedTableAssembly c = (ConcatenatedTableAssembly) ws.getAssembly("C");
         TableAssembly[] reordered =
            ConcatReorderGuard.resolvePermutation(ws, c, List.of("B", "A", "X"));
         ConcatReorderGuard.requireReorderKeepsState(ws, c, reordered, false);
         assertTrue(c.reorderTableAssemblies(reordered));
      }
      else {
         service(ws).apply("TOK", agent, ed -> ed.removeConcatSubtable("C", "A", false));
      }
   }

   private static ColumnRef column(Worksheet ws) {
      return (ColumnRef) ((TableAssembly) ws.getAssembly("C")).getColumnSelection()
         .getAttribute(1);
   }

   private static List<WorksheetMutationSupport.GroupSpec> groups(String... fields) {
      return Arrays.stream(fields).map(WorksheetMutationSupport.GroupSpec::new).toList();
   }

   private static List<WorksheetMutationSupport.GroupSpec> dateGroup() {
      return List.of(new WorksheetMutationSupport.GroupSpec("d", "YEAR"));
   }

   private static List<WorksheetMutationSupport.AggregateSpec> sum(String field) {
      return List.of(new WorksheetMutationSupport.AggregateSpec(field, "Sum", null));
   }

   private static List<WorksheetMutationSupport.AggregateSpec> correlation(String field,
                                                                          String secondary)
   {
      return List.of(new WorksheetMutationSupport.AggregateSpec(
         field, "Correlation", null, null, secondary, null));
   }

   /** Tables A, B, X, each holding the named columns: "d" and "e" dates, the rest integers. */
   private static Worksheet worksheet(String[][] columns) {
      Worksheet ws = new Worksheet();
      String[] names = { "A", "B", "X" };

      for(int i = 0; i < columns.length; i++) {
         EmbeddedTableAssembly table = TestWorksheets.tableWithColumns(ws, names[i], columns[i]);
         ColumnSelection selection = table.getColumnSelection();
         String[] types = new String[columns[i].length];
         Object[][] rows = new Object[3][columns[i].length];

         for(int j = 0; j < types.length; j++) {
            boolean date = columns[i][j].equals("d") || columns[i][j].equals("e");
            types[j] = date ? "date" : "integer";
            ((ColumnRef) selection.getAttribute(j)).setDataType(types[j]);
            // a header row, then two rows of values
            rows[0][j] = columns[i][j];
            rows[1][j] = date ? (Object) java.sql.Date.valueOf("2020-03-04") : 10 * (i + 1) + j;
            rows[2][j] = date ? (Object) java.sql.Date.valueOf("2021-05-06") : 20 * (i + 1) + j;
         }

         table.setEmbeddedData(new XEmbeddedTable(types, rows));
         ws.addAssembly(table);
      }

      return ws;
   }

   /** Everything a refusal must leave alone: the assemblies, their columns and state. */
   private static String state(Worksheet ws) {
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
      return String.join("\n", state);
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
