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
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82342: add_join / edit_join carry a comparison operator per key condition (=, &gt;, &lt;,
 * &gt;=, &lt;=, &lt;&gt;), like the Composer's join-line menu. Everything runs through the real
 * {@link WorksheetEditService.Editor} and the real {@link InnerJoinService}; joins are read back
 * as "L-&gt;R [operation:leftKey=rightKey, ...]" with the stored operation ints.
 */
@Tag("core")
@WizAgentTestSupport
class JoinOperator82342Test {
   private static final int INNER = TableAssemblyOperator.INNER_JOIN;
   private static final int LEFT = TableAssemblyOperator.LEFT_JOIN;
   private static final int GT = TableAssemblyOperator.GREATER_JOIN;
   private static final int GE = TableAssemblyOperator.GREATER_EQUAL_JOIN;
   private static final int LT = TableAssemblyOperator.LESS_JOIN;
   private static final int LE = TableAssemblyOperator.LESS_EQUAL_JOIN;
   private static final int NE = TableAssemblyOperator.NOT_EQUAL_JOIN;

   private final Principal agent = TestPrincipals.user("alice", "host-org");

   // ------------------------------------------------------------------ add_join, two tables

   @Test
   void everyOperatorIsStoredOnTheKeyCondition() throws Exception {
      String[][] cases = {
         { ">", String.valueOf(GT) }, { "<", String.valueOf(LT) }, { ">=", String.valueOf(GE) },
         { "<=", String.valueOf(LE) }, { "<>", String.valueOf(NE) }, { "!=", String.valueOf(NE) },
         { "=", String.valueOf(INNER) }, { "GREATER_EQUAL_JOIN", String.valueOf(GE) } };

      for(String[] c : cases) {
         Worksheet ws = twoTables();
         edit(ws, ed -> ed.addJoin("J", "A", "a1", "B", "b1", "INNER", null, null,
                                   List.of(c[0])));
         assertEquals(List.of("A->B [" + c[1] + ":a1=b1]"), readback(ws, "J"), c[0]);
      }
   }

   @Test
   void noOperatorKeepsTheEquiJoinTypeAsBefore() throws Exception {
      Worksheet ws = twoTables();
      edit(ws, ed -> ed.addJoin("J", "A", "a1", "B", "b1", "LEFT", null, null));
      assertEquals(List.of("A->B [" + LEFT + ":a1=b1]"), readback(ws, "J"));

      Worksheet other = twoTables();
      edit(other, ed -> ed.addJoin("J", "A", "a1", "B", "b1", "LEFT", null, null, List.of()));
      assertEquals(readback(ws, "J"), readback(other, "J"));
   }

   @Test
   void mixedKeysGiveTheEquiKeyTheJoinTypeAndTheOtherItsOperator() throws Exception {
      Worksheet ws = twoTables();

      edit(ws, ed -> ed.addJoin("J", "A", null, "B", null, "LEFT", List.of("a1", "a2"),
                                List.of("b1", "b2"), java.util.Arrays.asList("=", ">")));

      assertEquals(List.of("A->B [" + LEFT + ":a1=b1, " + GT + ":a2=b2]"), readback(ws, "J"));
   }

   @Test
   void anOuterTypeNeedsAnEquiKeyToCarryIt() throws Exception {
      Worksheet ws = twoTables();

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.addJoin("J", "A", "a1", "B", "b1", "LEFT", null, null, List.of(">="))));

      assertTrue(ex.getMessage().contains("outer join type"), ex.getMessage());
      assertNull(ws.getAssembly("J"));
   }

   @Test
   void aNonEquiOperatorIsRefusedOnCrossAndMerge() throws Exception {
      for(String type : new String[] { "CROSS", "MERGE" }) {
         Worksheet ws = twoTables();

         PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
            ed -> ed.addJoin("J", "A", "a1", "B", "b1", type, null, null, List.of(">"))), type);

         assertTrue(ex.getMessage().contains("CROSS or MERGE"), ex.getMessage());
         assertNull(ws.getAssembly("J"), type);
      }
   }

   @Test
   void anUnknownOperatorAndALengthMismatchAreRefusedAndCreateNothing() throws Exception {
      Worksheet ws = twoTables();

      PairingException unknown = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.addJoin("J", "A", "a1", "B", "b1", "INNER", null, null, List.of("~"))));
      assertTrue(unknown.getMessage().contains("operator must be one of"), unknown.getMessage());
      assertTrue(unknown.getMessage().contains("\"~\""), unknown.getMessage());

      PairingException length = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.addJoin("J", "A", null, "B", null, "INNER", List.of("a1", "a2"),
                          List.of("b1", "b2"), List.of(">"))));
      assertTrue(length.getMessage().contains("one entry per key"), length.getMessage());

      assertNull(ws.getAssembly("J"));
   }

   @Test
   void parseJoinOperatorRoundTripsEveryNameAReadReports() throws Exception {
      for(int operation : new int[] { GT, LT, GE, LE, NE }) {
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operation);

         assertEquals(operation, WorksheetEditService.Editor.parseJoinOperator(op.getName()), op.getName());
         assertEquals(operation, WorksheetEditService.Editor.parseJoinOperator(op.getName2()),
                      op.getName2());
      }

      // an equi key reads back with its join type's name; it is still "an equi key"
      for(String name : new String[] { "=", "==", "INNER_JOIN", "LEFT_JOIN", "right_join" }) {
         assertEquals(INNER, WorksheetEditService.Editor.parseJoinOperator(name), name);
      }

      assertThrows(PairingException.class, () -> WorksheetEditService.Editor.parseJoinOperator(null));
      assertThrows(PairingException.class, () -> WorksheetEditService.Editor.parseJoinOperator("=>"));
   }

   // ------------------------------------------------------------------ add_join, joinPaths / extend

   @Test
   void aJoinPathEdgeCarriesItsOwnOperator() throws Exception {
      Worksheet ws = threeTables();

      edit(ws, ed -> ed.addJoin("J", List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "a1", "B", "b1", "INNER", null),
         new WorksheetMutationSupport.JoinPathSpec("B", "bc", "C", "c1", "INNER", "<="))));

      assertEquals(List.of("A->B [" + INNER + ":a1=b1]", "B->C [" + LE + ":bc=c1]"),
                   readback(ws, "J"));
   }

   @Test
   void aJoinPathOperatorIsRefusedOnACrossEdgeAndOnAnOuterEdge() throws Exception {
      Worksheet ws = threeTables();

      assertThrows(PairingException.class, () -> edit(ws, ed -> ed.addJoin("J", List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "a1", "B", "b1", "CROSS", ">")))));
      assertThrows(PairingException.class, () -> edit(ws, ed -> ed.addJoin("J", List.of(
         new WorksheetMutationSupport.JoinPathSpec("A", "a1", "B", "b1", "LEFT", ">")))));
      assertNull(ws.getAssembly("J"));
   }

   @Test
   void extendingAJoinInPlaceKeepsItsNonEquiConditionAndAddsTheNewOperator() throws Exception {
      Worksheet ws = threeTables();
      edit(ws, ed -> ed.addJoin("J", "A", "a1", "B", "b1", "INNER", null, null, List.of(">")));

      edit(ws, ed -> ed.addJoin("J", "J", "bc", "C", "c1", "INNER", null, null, List.of("<>")));

      assertEquals(List.of("A->B [" + GT + ":a1=b1]", "B->C [" + NE + ":bc=c1]"),
                   readback(ws, "J"));
   }

   // ------------------------------------------------------------------ edit_join

   @Test
   void editJoinSetsAnOperatorOnNewKeys() throws Exception {
      Worksheet ws = twoTables();
      edit(ws, ed -> ed.addJoin("J", "A", "a1", "B", "b1", "INNER", null, null));

      edit(ws, ed -> ed.editJoin("J", "a2", "b2", "INNER", null, null, "A", "B", List.of(">=")));

      assertEquals(List.of("A->B [" + GE + ":a2=b2]"), readback(ws, "J"));
   }

   @Test
   void aReversedEdgeMirrorsEachOperator() throws Exception {
      Worksheet ws = twoTables();
      edit(ws, ed -> ed.addJoin("J", "A", "a1", "B", "b1", "INNER", null, null));

      // "B.b1 > A.a1" is stored as A.a1 < B.b1
      edit(ws, ed -> ed.editJoin("J", "b1", "a1", "INNER", null, null, "B", "A", List.of(">")));
      assertEquals(List.of("A->B [" + LT + ":a1=b1]"), readback(ws, "J"));

      edit(ws, ed -> ed.editJoin("J", "b2", "a2", "INNER", null, null, "B", "A", List.of("<=")));
      assertEquals(List.of("A->B [" + GE + ":a2=b2]"), readback(ws, "J"));

      // symmetric operators stay as they are
      edit(ws, ed -> ed.editJoin("J", "b1", "a1", "INNER", null, null, "B", "A", List.of("<>")));
      assertEquals(List.of("A->B [" + NE + ":a1=b1]"), readback(ws, "J"));
   }

   @Test
   void aKeyEditWithoutOperatorsKeepsTheStoredNonEquiConditions() throws Exception {
      Worksheet ws = joinWithNonEquiCondition();

      edit(ws, ed -> ed.editJoin("J", "a1", "b1", "LEFT", null, null, "A", "B"));

      assertEquals(List.of("A->B [" + LEFT + ":a1=b1, " + GT + ":a2=b2]"), readback(ws, "J"));
   }

   @Test
   void aKeyEditWithoutOperatorsRefusesANewKeyOnTheSameColumnsAsAKeptCondition()
      throws Exception
   {
      Worksheet ws = joinWithNonEquiCondition();
      List<String> before = readback(ws, "J");

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", "a2", "b2", "INNER", null, null, "A", "B")));

      assertTrue(ex.getMessage().contains("already holds >"), ex.getMessage());
      assertEquals(before, readback(ws, "J"));
   }

   @Test
   void operatorsAreTheWholeConditionListOfTheEdge() throws Exception {
      Worksheet ws = joinWithNonEquiCondition();

      // restating both conditions keeps the non-equi one (the names a read reports round-trip)
      edit(ws, ed -> ed.editJoin("J", null, null, "LEFT", List.of("a1", "a2"),
                                 List.of("b1", "b2"), "A", "B", List.of("LEFT_JOIN", "GREATER_JOIN")));
      assertEquals(List.of("A->B [" + LEFT + ":a1=b1, " + GT + ":a2=b2]"), readback(ws, "J"));

      // listing only the equi key drops the non-equi one
      edit(ws, ed -> ed.editJoin("J", "a1", "b1", "INNER", null, null, "A", "B", List.of("=")));
      assertEquals(List.of("A->B [" + INNER + ":a1=b1]"), readback(ws, "J"));
   }

   @Test
   void operatorsWithoutNewKeysAreRefused() throws Exception {
      Worksheet ws = joinWithNonEquiCondition();
      List<String> before = readback(ws, "J");

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "B", List.of(">"))));

      assertTrue(ex.getMessage().contains("operators"), ex.getMessage());
      assertEquals(before, readback(ws, "J"));
   }

   @Test
   void aTypeOnlyEditStillKeepsTheNonEquiConditionAndNamesItInRefusals() throws Exception {
      Worksheet ws = joinWithNonEquiCondition();

      edit(ws, ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "B"));
      assertEquals(List.of("A->B [" + LEFT + ":a1=b1, " + GT + ":a2=b2]"), readback(ws, "J"));

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "B")));
      assertTrue(ex.getMessage().contains("already LEFT"), ex.getMessage());
   }

   // ------------------------------------------------------------------ fixtures

   /** J = A-B with an equi key a1=b1 and a GREATER condition a2=b2. */
   private Worksheet joinWithNonEquiCondition() throws Exception {
      Worksheet ws = twoTables();
      edit(ws, ed -> ed.addJoin("J", "A", null, "B", null, "INNER", List.of("a1", "a2"),
                                List.of("b1", "b2"), java.util.Arrays.asList("=", ">")));
      assertEquals(List.of("A->B [" + INNER + ":a1=b1, " + GT + ":a2=b2]"), readback(ws, "J"));
      return ws;
   }

   private Worksheet twoTables() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1", "a2"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1", "b2", "bc"));
      return ws;
   }

   private Worksheet threeTables() throws Exception {
      Worksheet ws = twoTables();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "C", "c1"));
      return ws;
   }

   private void edit(Worksheet ws, ThrowingConsumer<WorksheetEditService.Editor> body)
      throws Exception
   {
      service(ws).apply("TOK", agent, body);
   }

   private WorksheetEditService service(Worksheet ws) throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      JoinSession s = new JoinSession("TOK", "Worksheet/ws1", "alice~;~host-org",
                                      SheetType.WORKSHEET, 0L, Long.MAX_VALUE,
                                      JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("TOK"), any())).thenReturn(s);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq("Worksheet/ws1"),
                                            eq(agent)))
         .thenReturn(rws);

      return new WorksheetEditService(sessions, runtimeAccess,
                                      mock(SheetAgentBroadcastService.class), securityEngine,
                                      new InnerJoinService(null, null));
   }

   /** Stored pairs as "L->R [op:lkey=rkey, ...]", sorted by pair; keys in stored order. */
   private static List<String> readback(Worksheet ws, String name) {
      return JoinEdgeEdit79223Test.readback(ws, name);
   }
}
