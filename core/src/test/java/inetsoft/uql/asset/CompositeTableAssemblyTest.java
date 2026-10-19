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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.composer.model.ws.DependencyType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CompositeTableAssemblyTest {
   private static final int UNION = TableAssemblyOperator.UNION;
   private static final int MINUS = TableAssemblyOperator.MINUS;
   private static final int INTERSECT = TableAssemblyOperator.INTERSECT;

   /**
    * Operators are keyed by ADJACENT pair, so reordering the subtables strands every operator
    * whose two tables stopped being neighbours: {@code A UNION B MINUS C} reordered to
    * {@code C, A, B} leaves nothing stored for the new first pair {@code (C,A)}.
    *
    * <p>Both callers of this method used to patch that up themselves -- read the operators by
    * position, reorder, then write them back by position. That is the right intent (carry the
    * operators over positionally) implemented in the one place it cannot be done safely: writing
    * back does not remove what is already there, so the pre-reorder pairs survived alongside the
    * new ones and the map ended up holding more operators than {@code tnames} can index. Since
    * {@code getOperatorCount()} reports the map's size while {@code getOperator(int)} indexes
    * {@code tnames}, the two then disagree and any caller iterating them together walks off the
    * end of the array -- which reached users as an {@code ArrayIndexOutOfBoundsException} that
    * made an entire worksheet unreadable after a single reorder.</p>
    *
    * <p>So the carry-over belongs here, where the map can be rebuilt rather than added to.</p>
    */
   @Test
   void reorderingSubtablesCarriesOperatorsOverByPositionWithoutStrandingAny() {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A");
      TableAssembly b = table(ws, "B");
      TableAssembly c = table(ws, "C");
      // A UNION B MINUS C -- mixed, so a lost or misplaced operator is visible.
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ TableAssemblyOperator.UNION, TableAssemblyOperator.MINUS }, a, b, c);

      assertTrue(concat.reorderTableAssemblies(new TableAssembly[]{ c, a, b }));

      assertEquals(List.of("C", "A", "B"), List.of(concat.getTableNames()),
                   "the reorder itself must take effect");
      assertEquals(concat.getTableNames().length - 1, concat.getOperatorCount(),
                   "one operator per adjacent pair -- a larger count means a stale pair survived");

      // Position is what carries over: the first pair keeps the first operation whichever
      // tables now sit there.
      assertEquals(TableAssemblyOperator.UNION, operation(concat.getOperator(0)));
      assertEquals(TableAssemblyOperator.MINUS, operation(concat.getOperator(1)));
   }

   /**
    * The smallest concatenation, where {@code ops.length == 1}. Worth its own case because the
    * two-source shape is both the most common and the one where the original defect was
    * unrecoverable: the only call that clears a stale pair is removing a subtable, and below two
    * sources that deletes the whole assembly.
    */
   @Test
   void reorderingTwoSourcesKeepsTheirOperator() {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A");
      TableAssembly b = table(ws, "B");
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ TableAssemblyOperator.UNION }, a, b);

      assertTrue(concat.reorderTableAssemblies(new TableAssembly[]{ b, a }));

      assertEquals(List.of("B", "A"), List.of(concat.getTableNames()));
      assertEquals(1, concat.getOperatorCount(), "no stale pair may survive");
      assertEquals(TableAssemblyOperator.UNION, operation(concat.getOperator(0)));
   }

   /**
    * Reordering into the same order must be a no-op, not a rebuild that loses anything. This is
    * the negative control for the whole fix: it is the one reorder that does NOT change which
    * subtables are adjacent, and live reproduction confirmed it never triggered the defect -- so a
    * failure here would mean the repair itself broke something the bug never touched.
    */
   @Test
   void reorderingIntoTheSameOrderChangesNothing() {
      Worksheet ws = new Worksheet();
      TableAssembly a = table(ws, "A");
      TableAssembly b = table(ws, "B");
      TableAssembly c = table(ws, "C");
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ TableAssemblyOperator.UNION, TableAssemblyOperator.MINUS }, a, b, c);

      assertTrue(concat.reorderTableAssemblies(new TableAssembly[]{ a, b, c }));

      assertEquals(List.of("A", "B", "C"), List.of(concat.getTableNames()));
      assertEquals(2, concat.getOperatorCount());
      assertEquals(TableAssemblyOperator.UNION, operation(concat.getOperator(0)));
      assertEquals(TableAssemblyOperator.MINUS, operation(concat.getOperator(1)));
   }

   /**
    * A JOIN table's operator map is not a linear chain, and the positional carry-over must not be
    * applied to one.
    *
    * <p>{@code CompositeTableAssemblyInfo.Pair} is an arbitrary {@code (ltable, rtable)} with no
    * adjacency constraint, and the Composer creates joins between any two subtables -- a star join
    * (one fact table joined directly to several dimensions) holds {@code n-1} operators that do
    * <em>not</em> sit at consecutive {@code tnames} indices. Reading operators by position there
    * finds nothing for most indices, so clearing the map to rebuild it from those readings would
    * silently delete every join whose two tables are not neighbours -- and reordering does not
    * even have to change anything for that to happen.</p>
    *
    * <p>Concatenations are the only assemblies where "one operator per adjacent pair" holds, which
    * is why the carry-over belongs to {@code ConcatenatedTableAssembly} and not to this base
    * class. It lived here briefly and this is the case that catches it.</p>
    */
   @Test
   void reorderingAStarJoinKeepsTheOperatorBetweenNonAdjacentTables() {
      Worksheet ws = new Worksheet();
      TableAssembly f = table(ws, "F");
      TableAssembly d1 = table(ws, "D1");
      TableAssembly d2 = table(ws, "D2");

      RelationalJoinTableAssembly star = new RelationalJoinTableAssembly(
         ws, "J", new TableAssembly[]{ f, d1, d2 },
         new TableAssemblyOperator[]{ join("F", "D1"), join("D1", "D2") });
      ws.addAssembly(star);

      // Reshape the chain the constructor built into a star: F joined to BOTH dimensions, so
      // (F,D2) spans tnames positions 0 and 2 and no adjacent-index read can find it.
      star.removeOperator("D1", "D2");
      star.setOperator("F", "D2", join("F", "D2"));

      assertTrue(star.reorderTableAssemblies(new TableAssembly[]{ d1, f, d2 }));

      assertNotNull(star.getOperator("F", "D1"),
                    "the adjacent join must survive the reorder");
      assertNotNull(star.getOperator("F", "D2"),
                    "the NON-adjacent join must survive it too -- dropping it deletes a join "
                       + "condition the user never touched");
   }

   /**
    * A concatenation is a left fold, {@code (A op1 B) op2 C}, so each operator belongs to the
    * table on its right. Removing a subtable used to copy the first pair's operator onto every
    * remaining pair, so {@code A UNION B MINUS C} without {@code A} became {@code B UNION C} and
    * the rows the user subtracted came back.
    */
   @Test
   void removingTheFirstOfThreeSubtablesKeepsTheOtherPairsOperator() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS }, "A", 1);
   }

   /**
    * The middle table takes its own (left-hand) operator with it; the right-hand one now joins
    * its neighbours: {@code A UNION B MINUS C} without {@code B} is {@code A MINUS C}.
    */
   @Test
   void removingTheMiddleOfThreeSubtablesKeepsTheRightHandOperator() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS }, "B", 1);
   }

   @Test
   void removingTheLastOfThreeSubtablesKeepsTheFirstPairsOperator() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS }, "C", 0);
   }

   @Test
   void removingTheFirstOfFourSubtablesKeepsTheOtherOperators() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS, INTERSECT }, "A", 1, 2);
   }

   @Test
   void removingTheSecondOfFourSubtablesKeepsTheOtherOperators() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS, INTERSECT }, "B", 1, 2);
   }

   @Test
   void removingTheThirdOfFourSubtablesKeepsTheOtherOperators() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS, INTERSECT }, "C", 0, 2);
   }

   @Test
   void removingTheLastOfFourSubtablesKeepsTheOtherOperators() {
      assertRemovalKeepsOwnOperators(new int[]{ UNION, MINUS, INTERSECT }, "D", 0, 1);
   }

   /** Native Composer removes several subtables one at a time, so the rule must compose. */
   @Test
   void removingSubtablesOneAfterAnotherKeepsEachRemainingTablesOperator() {
      Worksheet ws = new Worksheet();
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ UNION, MINUS, INTERSECT, MINUS }, tables(ws, "A", "B", "C", "D", "E"));
      TableAssemblyOperator[] before = operators(concat);

      assertFalse(concat.removeTable("B"));
      assertFalse(concat.removeTable("D"));

      // A UNION B MINUS C INTERSECT D MINUS E -> A MINUS C MINUS E
      assertEquals(List.of("A", "C", "E"), List.of(concat.getTableNames()));
      assertEquals(2, concat.getOperatorCount());
      assertSame(before[1], concat.getOperator(0));
      assertSame(before[3], concat.getOperator(1));
   }

   /** The negative control: a uniform concatenation looks the same under the old and new rule. */
   @Test
   void removingASubtableFromAUniformConcatenationKeepsItUniform() {
      Worksheet ws = new Worksheet();
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ UNION, UNION }, tables(ws, "A", "B", "C"));

      assertFalse(concat.removeTable("B"));

      assertEquals(List.of("A", "C"), List.of(concat.getTableNames()));
      assertEquals(1, concat.getOperatorCount());
      assertEquals(UNION, operation(concat.getOperator(0)));
   }

   /** A name that is not a subtable removes nothing, so no operator may change either. */
   @Test
   void removingAnUnknownSubtableChangesNoOperator() {
      Worksheet ws = new Worksheet();
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ UNION, MINUS, INTERSECT }, tables(ws, "A", "B", "C", "D"));
      TableAssemblyOperator[] before = operators(concat);

      assertFalse(concat.removeTable("Z"));

      assertEquals(List.of("A", "B", "C", "D"), List.of(concat.getTableNames()));
      TableAssemblyOperator[] after = operators(concat);

      for(int i = 0; i < before.length; i++) {
         assertSame(before[i], after[i], "pair " + i + " must keep its own operator");
      }
   }

   /**
    * A concatenation with a pair that has no operator must still lose a subtable without
    * throwing, leaving that pair empty and the others untouched.
    */
   @Test
   void removingASubtableFromAConcatenationWithAMissingPairKeepsTheOthers() {
      Worksheet ws = new Worksheet();
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ UNION, MINUS, INTERSECT }, tables(ws, "A", "B", "C", "D"));
      TableAssemblyOperator minus = concat.getOperator(1);
      concat.getCompositeTableInfo().removeOperator("A", "B");

      assertFalse(concat.removeTable("D"));

      assertEquals(List.of("A", "B", "C"), List.of(concat.getTableNames()));
      assertNull(concat.getOperator(0));
      assertSame(minus, concat.getOperator(1));
      assertEquals(1, concat.getOperatorCount());
   }

   /** Below two sources the whole assembly goes; the remaining one is left as it was. */
   @Test
   void removingOneOfTwoSubtablesReportsTheAssemblyAsInvalid() {
      Worksheet ws = new Worksheet();
      ConcatenatedTableAssembly concat = concat(
         ws, "U", new int[]{ MINUS }, tables(ws, "A", "B"));
      TableAssemblyOperator op = concat.getOperator(0);

      assertTrue(concat.removeTable("B"));

      assertEquals(List.of("A", "B"), List.of(concat.getTableNames()));
      assertSame(op, concat.getOperator(0));
   }

   /**
    * Removes {@code removed} from a concatenation of tables A, B, C... joined by
    * {@code operations}, then checks that pair {@code i} holds the very operator object that
    * was pair {@code keptPairs[i]} before (so its operation and distinct flag too, and no two
    * pairs share one), and that every remaining table keeps the dependency label native
    * Composer shows for it -- except the new first table, which becomes the base.
    */
   private static void assertRemovalKeepsOwnOperators(int[] operations, String removed,
                                                      int... keptPairs)
   {
      Worksheet ws = new Worksheet();
      String[] names = new String[operations.length + 1];

      for(int i = 0; i < names.length; i++) {
         names[i] = String.valueOf((char) ('A' + i));
      }

      ConcatenatedTableAssembly concat = concat(ws, "U", operations, tables(ws, names));
      TableAssemblyOperator[] before = operators(concat);

      for(int i = 0; i < before.length; i++) {
         before[i].getKeyOperator().setDistinct(i % 2 == 0);
      }

      Map<String, DependencyType> labelsBefore = concatLabels(concat);

      assertFalse(concat.removeTable(removed));

      List<String> expectedNames = new ArrayList<>(List.of(names));
      expectedNames.remove(removed);
      assertEquals(expectedNames, List.of(concat.getTableNames()));
      assertEquals(keptPairs.length, concat.getOperatorCount(),
                   "one operator per adjacent pair");

      for(int i = 0; i < keptPairs.length; i++) {
         TableAssemblyOperator op = concat.getOperator(i);
         assertSame(before[keptPairs[i]], op, "pair " + i + " must keep its own operator");
         assertEquals(operations[keptPairs[i]], operation(op));
         assertEquals(keptPairs[i] % 2 == 0, op.getKeyOperator().isDistinct());
      }

      Map<String, DependencyType> labelsAfter = concatLabels(concat);
      assertEquals(DependencyType.BASE_CONCATENATED_TABLE, labelsAfter.get(expectedNames.get(0)));

      for(String name : expectedNames.subList(1, expectedNames.size())) {
         assertEquals(labelsBefore.get(name), labelsAfter.get(name),
                      name + " must keep the operator it was concatenated with");
      }
   }

   private static Map<String, DependencyType> concatLabels(ConcatenatedTableAssembly concat) {
      Set<DependencyType> concatTypes = EnumSet.of(
         DependencyType.BASE_CONCATENATED_TABLE, DependencyType.UNION,
         DependencyType.INTERSECTION, DependencyType.MINUS);
      Map<String, Set<DependencyType>> dependeds = new HashMap<>();
      concat.getAugmentedDependeds(dependeds);
      Map<String, DependencyType> labels = new HashMap<>();

      dependeds.forEach((name, types) -> types.stream()
         .filter(concatTypes::contains)
         .forEach(type -> assertNull(labels.put(name, type), name + " has one concat label")));

      return labels;
   }

   private static TableAssemblyOperator[] operators(ConcatenatedTableAssembly concat) {
      TableAssemblyOperator[] ops = new TableAssemblyOperator[concat.getTableNames().length - 1];

      for(int i = 0; i < ops.length; i++) {
         ops[i] = concat.getOperator(i);
      }

      return ops;
   }

   private static TableAssembly[] tables(Worksheet ws, String... names) {
      return Arrays.stream(names).map(name -> table(ws, name)).toArray(TableAssembly[]::new);
   }

   private static TableAssemblyOperator join(String ltable, String rtable) {
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      op.setLeftTable(ltable);
      op.setRightTable(rtable);
      op.setLeftAttribute(new ColumnRef(new AttributeRef(ltable, "col")));
      op.setRightAttribute(new ColumnRef(new AttributeRef(rtable, "col")));
      op.setOperation(TableAssemblyOperator.INNER_JOIN);

      TableAssemblyOperator top = new TableAssemblyOperator();
      top.addOperator(op);

      return top;
   }

   private static int operation(TableAssemblyOperator operator) {
      assertNotNull(operator, "every adjacent pair must have an operator after a reorder");
      return operator.getKeyOperator().getOperation();
   }

   private static TableAssembly table(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(null, "col")));
      table.setColumnSelection(cs, false);
      ws.addAssembly(table);

      return table;
   }

   /** One operation per adjacent pair, so a mixed concatenation can be built. */
   private static ConcatenatedTableAssembly concat(Worksheet ws, String name, int[] operations,
                                                   TableAssembly... sources)
   {
      TableAssemblyOperator[] operators = new TableAssemblyOperator[sources.length - 1];

      for(int i = 0; i < operators.length; i++) {
         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operations[i]);
         top.addOperator(op);
         operators[i] = top;
      }

      ConcatenatedTableAssembly concat =
         new ConcatenatedTableAssembly(ws, name, sources, operators);
      ws.addAssembly(concat);

      return concat;
   }
}
