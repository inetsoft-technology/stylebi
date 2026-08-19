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
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.web.wiz.pairing.TestWorksheets;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.model.WorksheetModel;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class WorksheetReadServiceTest {

   @Test
   void readsColumnsAggregatesConditionsSort() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "a", "b");
      TestWorksheets.withGroupSumAndSort(t, "a", "b");
      ws.addAssembly(t);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);

      assertFalse(m.tables().isEmpty());
      WorksheetModel.TableModel tm = m.tables().get(0);
      assertEquals("T", tm.name());
      assertTrue(tm.columns().stream().anyMatch(c -> "a".equals(c.name())));
      assertNotNull(tm.aggregates());
      assertEquals(1, tm.aggregates().groups().size());
      assertEquals("a", tm.aggregates().groups().get(0).field());
      assertNull(tm.aggregates().groups().get(0).dateLevel());
      assertEquals(1, tm.aggregates().aggregates().size());
      assertFalse(tm.sorts().isEmpty());
   }

   @Test
   void readsDateGroupLevelOnGroupedColumn() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "orderDate", "total");
      ((ColumnRef) t.getColumnSelection(false).getAttribute("orderDate"))
         .setDataType(inetsoft.uql.schema.XSchema.DATE);
      ws.addAssembly(t);

      // Round-trip through the actual production mutator (not a hand-built GroupRef)
      // so this exercises the real shape applyAggregateInfo produces for a dateLevel
      // group - a GroupRef wrapping ColumnRef(DateRangeRef(...)), not a plain ColumnRef
      // with only setDateGroup() called - which is what WorksheetReadService's
      // field-extraction branch actually has to unwrap.
      WorksheetMutationSupport.applyAggregateInfo(t,
         List.of(new WorksheetMutationSupport.GroupSpec("orderDate", "QUARTER")),
         List.of(new WorksheetMutationSupport.AggregateSpec("total", "SUM", null)));

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);
      WorksheetModel.AggregateModel.GroupModel group = m.tables().get(0).aggregates().groups().get(0);
      assertEquals("orderDate", group.field());
      assertEquals("QUARTER", group.dateLevel());
   }

   @Test
   void nullOrEmptyAggregateInfoReturnsNullAggregates() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T2", "x");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);

      WorksheetModel m = new WorksheetReadService().read(rws);
      assertNull(m.tables().get(0).aggregates());
      assertTrue(m.tables().get(0).sorts().isEmpty());
   }

   @Test
   void tableTypeIsEmbeddedForEmbeddedTable() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "E", "col");
      ws.addAssembly(t);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      WorksheetModel m = new WorksheetReadService().read(rws);
      assertEquals("EMBEDDED", m.tables().get(0).type());
   }

   private static WorksheetModel.TableModel tableNamed(WorksheetModel m, String name) {
      return m.tables().stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
   }

   private static WorksheetModel read(Worksheet ws) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      return new WorksheetReadService().read(rws);
   }

   private static ConcatenatedTableAssembly concat(Worksheet ws, String name, int operation,
                                                   TableAssembly... sources)
   {
      TableAssemblyOperator[] operators = new TableAssemblyOperator[sources.length - 1];

      for(int i = 0; i < operators.length; i++) {
         TableAssemblyOperator top = new TableAssemblyOperator();
         TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
         op.setOperation(operation);
         top.addOperator(op);
         operators[i] = top;
      }

      return new ConcatenatedTableAssembly(ws, name, sources, operators);
   }

   /**
    * The order is the point, not just the membership: a concatenation takes its whole column list
    * from whichever subtable is first, so a caller that knows the names but not their order still
    * cannot tell which table decides the shape.
    */
   @Test
   void concatenationReportsItsSubtablesInOrder() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(concat(ws, "U", TableAssemblyOperator.UNION, a, b));

      WorksheetModel.TableModel u = tableNamed(read(ws), "U");

      assertEquals("CONCAT", u.type());
      assertEquals(List.of("A", "B"), u.sources());
      assertEquals("UNION", u.concatType());
   }

   @Test
   void concatenationReportsIntersectAndMinus() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly a = TestWorksheets.tableWithColumns(ws, "A", "col");
      EmbeddedTableAssembly b = TestWorksheets.tableWithColumns(ws, "B", "col");
      ws.addAssembly(a);
      ws.addAssembly(b);
      ws.addAssembly(concat(ws, "I", TableAssemblyOperator.INTERSECT, a, b));
      ws.addAssembly(concat(ws, "M", TableAssemblyOperator.MINUS, a, b));

      WorksheetModel m = read(ws);

      assertEquals("INTERSECT", tableNamed(m, "I").concatType());
      assertEquals("MINUS", tableNamed(m, "M").concatType());
   }

   /**
    * Reports the flag's <em>effective</em> value, which for a mirror whose source lives in the same
    * worksheet is always {@code true} no matter what was set: {@code MirrorAssemblyImpl} answers
    * {@code auto || !isOuterMirror()} and its setter returns early for anything that is not an
    * outer mirror, so the assignment is dropped without a word. That is exactly why this field is
    * worth exposing — a caller that sets the flag and reads back {@code true} can tell the setting
    * did not take, which was previously unknowable through any API.
    */
   @Test
   void mirrorReportsItsSourceAndEffectiveAutoUpdateFlag() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly base = TestWorksheets.tableWithColumns(ws, "BASE", "col");
      ws.addAssembly(base);
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", base);
      ws.addAssembly(mirror);
      mirror.setAutoUpdate(false);

      WorksheetModel.TableModel m = tableNamed(read(ws), "M");

      assertEquals("MIRROR", m.type());
      assertEquals(List.of("BASE"), m.sources());
      assertEquals(Boolean.TRUE, m.autoUpdate());
      assertNull(m.concatType());
   }

   /**
    * A hidden column stays in the model but leaves the data, and is also excluded from the public
    * column selection a concatenation counts. Without this flag a caller comparing the model
    * against real rows reads a hidden column as a missing one, and a caller comparing source
    * column counts gets a number the server would not agree with.
    */
   @Test
   void hiddenColumnIsReportedButMarkedNotVisible() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "T", "keep", "hide");
      ((ColumnRef) t.getColumnSelection(false).getAttribute("hide")).setVisible(false);
      ws.addAssembly(t);

      WorksheetModel.TableModel tm = tableNamed(read(ws), "T");

      assertEquals(2, tm.columns().size());
      assertTrue(columnNamed(tm, "keep").visible());
      assertFalse(columnNamed(tm, "hide").visible());
   }

   private static WorksheetModel.ColumnModel columnNamed(WorksheetModel.TableModel t, String name) {
      return t.columns().stream().filter(c -> name.equals(c.name())).findFirst().orElseThrow();
   }

   /**
    * A plain table has no sources, and must not be confused with a composite whose sources simply
    * were not reported — which is exactly the ambiguity this field exists to remove.
    */
   @Test
   void plainTableReportsNoSourcesAndNoCompositeFields() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(TestWorksheets.tableWithColumns(ws, "T", "col"));

      WorksheetModel.TableModel t = tableNamed(read(ws), "T");

      assertTrue(t.sources().isEmpty());
      assertNull(t.concatType());
      assertNull(t.autoUpdate());
   }
}
