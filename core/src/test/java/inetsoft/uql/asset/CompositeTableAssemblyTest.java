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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CompositeTableAssemblyTest {
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
