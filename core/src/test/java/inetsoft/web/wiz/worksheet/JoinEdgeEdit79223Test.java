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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79223: edit_join edits the edge the caller names (not the first pair in hash order) and
 * remove_join_edge removes one edge, both in place so a mirror built on the join stays attached.
 * Everything runs through the real {@link WorksheetEditService.Editor} and the real
 * {@link InnerJoinService}. Join readbacks are sorted, because the stored pairs sit in a HashMap.
 */
@Tag("core")
@WizAgentTestSupport
class JoinEdgeEdit79223Test {
   private static final int INNER = TableAssemblyOperator.INNER_JOIN;
   private static final int LEFT = TableAssemblyOperator.LEFT_JOIN;
   private static final int RIGHT = TableAssemblyOperator.RIGHT_JOIN;

   private final Principal agent = TestPrincipals.user("alice", "host-org");

   // ------------------------------------------------------------------ edit_join, edge selector

   @Test
   void editingOneEdgeLeavesTheOtherPairsAndTheMirrorUntouched() throws Exception {
      Worksheet ws = chainWithMirror();
      List<String> before = readback(ws, "J");
      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [3:bc=c1]"), before);

      edit(ws, ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "B"));

      assertEquals(List.of("A->B [7:a1=b1, 7:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
      assertMirrorAttached(ws, "J");
   }

   @Test
   void editingTheOtherEdgeLeavesTheFirstOneAlone() throws Exception {
      Worksheet ws = chainWithMirror();

      edit(ws, ed -> ed.editJoin("J", null, null, "FULL", null, null, "B", "C"));

      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [15:bc=c1]"), readback(ws, "J"));
      assertMirrorAttached(ws, "J");
   }

   /** The trap: getOperator(callerL, callerR) hands back a reversed COPY, so mutating it no-ops. */
   @Test
   void reversedOrderOnAnInnerPairChangesTheStoredPairMirrored() throws Exception {
      Worksheet ws = chainWithMirror();

      // "B LEFT JOIN A" is stored as A RIGHT B
      edit(ws, ed -> ed.editJoin("J", null, null, "LEFT", null, null, "B", "A"));

      assertEquals(List.of("A->B [11:a1=b1, 11:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
   }

   @Test
   void reversedOrderOnAnOuterPairMirrorsToo() throws Exception {
      Worksheet ws = chainWithMirror();
      edit(ws, ed -> ed.editJoin("J", null, null, "LEFT", null, null, "B", "C"));

      // "C RIGHT JOIN B" is the same as "B LEFT JOIN C": already stored LEFT, a no-op
      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "RIGHT", null, null, "C", "B")));
      assertTrue(ex.getMessage().contains("already LEFT"), ex.getMessage());

      edit(ws, ed -> ed.editJoin("J", null, null, "RIGHT", null, null, "B", "C"));
      assertEquals("B->C [11:bc=c1]", readback(ws, "J").get(1));
   }

   @Test
   void nonEquiOperatorsOfThePairArePreserved() throws Exception {
      Worksheet ws = chainWithMirror();
      TableAssemblyOperator ab = join(ws, "J").getOperator("A", "B");
      ab.getOperator(1).setOperation(TableAssemblyOperator.GREATER_JOIN);
      int greater = TableAssemblyOperator.GREATER_JOIN;

      edit(ws, ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "B"));

      assertEquals(List.of("A->B [7:a1=b1, " + greater + ":a2=b2]", "B->C [3:bc=c1]"),
                   readback(ws, "J"));
   }

   @Test
   void keysReplaceThePairsWholeKeyListAndOtherPairsStay() throws Exception {
      Worksheet ws = chainWithMirror();

      // same type, different keys: not a no-op
      edit(ws, ed -> ed.editJoin("J", "a1", "b2", "INNER", null, null, "A", "B"));
      assertEquals(List.of("A->B [3:a1=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));

      // reversed caller order: the key sides flip with it
      edit(ws, ed -> ed.editJoin("J", null, null, "INNER", List.of("b1", "b2"),
                                 List.of("a1", "a2"), "B", "A"));
      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
      assertMirrorAttached(ws, "J");
   }

   @Test
   void unknownKeyColumnIsRefused() throws Exception {
      Worksheet ws = chainWithMirror();

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", "nope", "b1", "INNER", null, null, "A", "B")));

      assertTrue(ex.getMessage().contains("nope"), ex.getMessage());
      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
   }

   // ------------------------------------------------------------------ edit_join, refusals

   @Test
   void multiEdgeJoinWithoutSelectorIsRefusedNamingTheEdges() throws Exception {
      Worksheet ws = chainWithMirror();

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "LEFT", null, null)));

      assertTrue(ex.getMessage().contains("A-B (INNER)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("B-C (INNER)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("leftTable"), ex.getMessage());
      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
   }

   @Test
   void unknownPairIsRefusedListingTheCandidates() throws Exception {
      Worksheet ws = chainWithMirror();

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "C")));

      assertTrue(ex.getMessage().contains("A-B (INNER)"), ex.getMessage());
      assertTrue(ex.getMessage().contains("B-C (INNER)"), ex.getMessage());
   }

   @Test
   void editingToTheTypeTheEdgeAlreadyHasIsRefusedWithASelector() throws Exception {
      Worksheet ws = chainWithMirror();

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "INNER", null, null, "A", "B")));
      assertTrue(ex.getMessage().contains("edge A-B is already INNER; nothing to change"),
                 ex.getMessage());

      PairingException same = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "INNER", List.of("a1", "a2"), List.of("b1", "b2"),
                           "A", "B")));
      assertTrue(same.getMessage().contains("nothing to change"), same.getMessage());
   }

   @Test
   void aSinglePairNoOpWithoutASelectorStillSucceeds() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1"));
      edit(ws, ed -> ed.addJoin("J2", "A", "a1", "B", "b1", "INNER", null, null));

      edit(ws, ed -> ed.editJoin("J2", null, null, "INNER", null, null));

      assertEquals(List.of("A->B [3:a1=b1]"), readback(ws, "J2"));
   }

   @Test
   void nonEquiOnlyPairIsRefusedNamingTheOperators() throws Exception {
      Worksheet ws = chainWithMirror();
      TableAssemblyOperator ab = join(ws, "J").getOperator("A", "B");
      ab.getOperator(0).setOperation(TableAssemblyOperator.GREATER_JOIN);
      ab.getOperator(1).setOperation(TableAssemblyOperator.LESS_JOIN);

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "LEFT", null, null, "A", "B")));

      assertTrue(ex.getMessage().contains("non-equi"), ex.getMessage());
      assertEquals("A->B [" + TableAssemblyOperator.GREATER_JOIN + ":a1=b1, " +
                   TableAssemblyOperator.LESS_JOIN + ":a2=b2]", readback(ws, "J").get(0));
   }

   @Test
   void aCrossPairIsNeverTurnedIntoAKeylessInnerJoin() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1"));
      edit(ws, ed -> ed.addCrossJoin("X", "A", "B"));

      assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("X", null, null, "INNER", null, null, "A", "B")));
      assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("X", null, null, "INNER", null, null)));
      assertEquals(TableAssemblyOperator.CROSS_JOIN,
                   join(ws, "X").getOperator("A", "B").getOperator(0).getOperation());

      // with keys it becomes a keyed join, which is a legitimate edit
      edit(ws, ed -> ed.editJoin("X", "a1", "b1", "INNER", null, null, "A", "B"));
      assertEquals(List.of("A->B [3:a1=b1]"), readback(ws, "X"));
   }

   @Test
   void crossOnAMultiEdgeJoinIsRefused() throws Exception {
      Worksheet ws = chainWithMirror();

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.editJoin("J", null, null, "CROSS", null, null, "A", "B")));

      assertTrue(ex.getMessage().contains("CROSS"), ex.getMessage());
      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
   }

   // ------------------------------------------------------------------ remove_join_edge

   @Test
   void removingAnEdgeKeepsTheRestOfTheJoinAndTheMirror() throws Exception {
      Worksheet ws = triangleWithMirror();
      assertEquals(List.of("A->B [3:a1=b1]", "A->C [3:ac=c2]", "B->C [3:bc=c1]"),
                   readback(ws, "T"));
      RelationalJoinTableAssembly before = join(ws, "T");

      // named in reverse order, as a caller may
      edit(ws, ed -> ed.removeJoinEdge("T", "C", "A", null, null));

      assertEquals(List.of("A->B [3:a1=b1]", "B->C [3:bc=c1]"), readback(ws, "T"));
      assertSame(before, join(ws, "T"), "the join must be edited in place");
      assertArrayEquals(new String[] { "A", "B", "C" }, join(ws, "T").getTableNames());
      assertMirrorAttached(ws, "T");
   }

   @Test
   void removingOneKeyOfAPairKeepsTheOtherKeyAndPairs() throws Exception {
      Worksheet ws = chainWithMirror();

      // reversed caller order: key sides are the caller's
      edit(ws, ed -> ed.removeJoinEdge("J", "B", "A", "b2", "a2"));

      assertEquals(List.of("A->B [3:a1=b1]", "B->C [3:bc=c1]"), readback(ws, "J"));
      assertMirrorAttached(ws, "J");
   }

   @Test
   void removingTheLastKeyOfAPairIsAPairRemoval() throws Exception {
      Worksheet ws = triangleWithMirror();

      edit(ws, ed -> ed.removeJoinEdge("T", "A", "C", "ac", "c2"));

      assertEquals(List.of("A->B [3:a1=b1]", "B->C [3:bc=c1]"), readback(ws, "T"));
   }

   @Test
   void removingAnEdgeThatSplitsAChainIsRefused() throws Exception {
      Worksheet ws = four("W", List.of(path("A", "a1", "B", "b1"), path("B", "bc", "C", "c1"),
                                       path("C", "c2", "D", "d1")));
      List<String> before = readback(ws, "W");

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("W", "B", "C", null, null)));

      assertTrue(ex.getMessage().contains("disconnect"), ex.getMessage());
      assertEquals(before, readback(ws, "W"));
   }

   @Test
   void removingTheEdgeOfAStarLeafIsRefusedNamingTheStrandedTable() throws Exception {
      Worksheet ws = four("W", List.of(path("A", "a1", "B", "b1"), path("A", "a2", "C", "c1"),
                                       path("A", "ac", "D", "d1")));
      List<String> before = readback(ws, "W");

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("W", "A", "C", null, null)));

      assertTrue(ex.getMessage().contains("[C]"), ex.getMessage());
      assertEquals(before, readback(ws, "W"));
   }

   @Test
   void removingTheOnlyEdgeOfATwoTableJoinPointsToRemoveJoin() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1"));
      edit(ws, ed -> ed.addJoin("J2", "A", "a1", "B", "b1", "INNER", null, null));

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("J2", "A", "B", null, null)));
      assertTrue(ex.getMessage().contains("remove_join"), ex.getMessage());

      // dropping the last key is the same removal
      assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("J2", "A", "B", "a1", "b1")));
      assertEquals(List.of("A->B [3:a1=b1]"), readback(ws, "J2"));
   }

   @Test
   void unknownPairOrKeyIsRefusedListingTheCandidates() throws Exception {
      Worksheet ws = chainWithMirror();

      PairingException pair = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("J", "A", "C", null, null)));
      assertTrue(pair.getMessage().contains("A-B (INNER)"), pair.getMessage());

      PairingException key = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("J", "A", "B", "a1", "b2")));
      assertTrue(key.getMessage().contains("a1"), key.getMessage());

      PairingException half = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("J", "A", "B", "a1", null)));
      assertTrue(half.getMessage().contains("leftKey and rightKey"), half.getMessage());
      assertEquals(List.of("A->B [3:a1=b1, 3:a2=b2]", "B->C [3:bc=c1]"), readback(ws, "J"));
   }

   @Test
   void aCrossPairCannotBeRemovedAsAnEdge() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1"));
      edit(ws, ed -> ed.addCrossJoin("X", "A", "B"));

      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("X", "A", "B", null, null)));

      assertTrue(ex.getMessage().contains("CROSS"), ex.getMessage());
   }

   @Test
   void aJoinHoldingACrossPairStillAllowsAnUnrelatedEdgeRemoval() throws Exception {
      Worksheet ws = new Worksheet();
      for(String[] t : new String[][] {
         { "A", "a1", "a2" }, { "B", "b1", "b2" }, { "C", "c1" }, { "D", "d1", "d2" } })
      {
         ws.addAssembly(TestWorksheets.tableWithColumns(ws, t[0],
                                                        java.util.Arrays.copyOfRange(t, 1, t.length)));
      }

      // triangle A-B, B-D, A-D with C attached to B by a CROSS pair
      edit(ws, ed -> ed.addJoin("J", List.of(path("A", "a1", "B", "b1"), path("B", "b2", "D", "d1"),
                                             path("A", "a2", "D", "d2"))));
      RelationalJoinTableAssembly j = join(ws, "J");
      TableAssemblyOperator cross = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable("B");
      op.setRightTable("C");
      op.setOperation(TableAssemblyOperator.CROSS_JOIN);
      cross.addOperator(op);
      j.setTableAssemblies(new TableAssembly[] {
         (TableAssembly) ws.getAssembly("A"), (TableAssembly) ws.getAssembly("B"),
         (TableAssembly) ws.getAssembly("C"), (TableAssembly) ws.getAssembly("D") });
      j.setOperator("B", "C", cross);
      List<String> before = readback(ws, "J");
      assertTrue(before.contains("B->C [" + TableAssemblyOperator.CROSS_JOIN + ":null=null]"),
                 before.toString());

      edit(ws, ed -> ed.removeJoinEdge("J", "B", "D", null, null));

      List<String> after = readback(ws, "J");
      assertTrue(after.contains("A->B [3:a1=b1]"), after.toString());
      assertTrue(after.contains("A->D [3:a2=d2]"), after.toString());
      assertTrue(after.stream().anyMatch(s -> s.startsWith("B->C [" +
                                                           TableAssemblyOperator.CROSS_JOIN)),
                 after.toString());
      assertTrue(after.stream().noneMatch(s -> s.startsWith("B->D")), after.toString());

      // dropping A-D as well would strand D: three components instead of the two it has now
      PairingException ex = assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("J", "A", "D", null, null)));
      assertTrue(ex.getMessage().contains("disconnect"), ex.getMessage());
   }

   @Test
   void editingAMissingOrMergeJoinIsRefused() throws Exception {
      Worksheet ws = new Worksheet();

      assertThrows(PairingException.class, () -> edit(ws,
         ed -> ed.removeJoinEdge("NOPE", "A", "B", null, null)));
   }

   // ------------------------------------------------------------------ fixtures

   private static WorksheetMutationSupport.JoinPathSpec path(String l, String lk, String r,
                                                              String rk)
   {
      return new WorksheetMutationSupport.JoinPathSpec(l, lk, r, rk, "INNER");
   }

   /** A(a1,a2,ac), B(b1,b2,bc), C(c1,c2); J = A-B on two keys, B-C on one; mirror M of J. */
   private Worksheet chainWithMirror() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1", "a2", "ac"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1", "b2", "bc"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "C", "c1", "c2"));
      edit(ws, ed -> ed.addJoin("J", List.of(path("A", "a1", "B", "b1"),
                                             path("A", "a2", "B", "b2"),
                                             path("B", "bc", "C", "c1"))));
      edit(ws, ed -> ed.addMirror("M", "J"));
      return ws;
   }

   /** T = triangle A-B, B-C, A-C; mirror M of T. */
   private Worksheet triangleWithMirror() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1", "ac"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1", "bc"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "C", "c1", "c2"));
      edit(ws, ed -> ed.addJoin("T", List.of(path("A", "a1", "B", "b1"),
                                             path("B", "bc", "C", "c1"),
                                             path("A", "ac", "C", "c2"))));
      edit(ws, ed -> ed.addMirror("M", "T"));
      return ws;
   }

   private Worksheet four(String join, List<WorksheetMutationSupport.JoinPathSpec> paths)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "A", "a1", "a2", "ac"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "B", "b1", "bc"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "C", "c1", "c2"));
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "D", "d1"));
      edit(ws, ed -> ed.addJoin(join, paths));
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

   private static RelationalJoinTableAssembly join(Worksheet ws, String name) {
      return (RelationalJoinTableAssembly) ws.getAssembly(name);
   }

   /** Stored pairs as "L->R [op:lkey=rkey, ...]", sorted by pair; keys in stored order. */
   static List<String> readback(Worksheet ws, String name) {
      RelationalJoinTableAssembly join = join(ws, name);
      List<String> out = new ArrayList<>();

      for(Enumeration<?> e = join.getOperatorTables(); e.hasMoreElements(); ) {
         String[] p = (String[]) e.nextElement();
         TableAssemblyOperator top = join.getOperator(p[0], p[1]);
         List<String> keys = new ArrayList<>();

         for(TableAssemblyOperator.Operator op : top.getOperators()) {
            keys.add(op.getOperation() + ":" +
                     (op.getLeftAttribute() == null ? "null" : op.getLeftAttribute().getAttribute()) +
                     "=" +
                     (op.getRightAttribute() == null ? "null" : op.getRightAttribute().getAttribute()));
         }

         out.add(p[0] + "->" + p[1] + " " + keys);
      }

      out.sort(null);
      return out;
   }

   private static void assertMirrorAttached(Worksheet ws, String joinName) {
      Assembly m = ws.getAssembly("M");
      assertInstanceOf(MirrorTableAssembly.class, m, "the mirror must survive the edit");
      assertEquals(joinName, ((MirrorTableAssembly) m).getAssemblyName(),
                   "the mirror must still be built on the join");
      assertEquals(1, ws.getDependings(join(ws, joinName).getAssemblyEntry()).length,
                   "the mirror must still depend on the join");
   }
}
