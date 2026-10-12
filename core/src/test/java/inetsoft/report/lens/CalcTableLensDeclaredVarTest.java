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
package inetsoft.report.lens;

import inetsoft.report.FormulaTable;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A freehand (calc) table cell formula's top-level var named like a member of the table's
 * scope chain - a summary function of its CalcTableScope (max, count, min, sum, ...), a
 * member such as field, or the assembly's value - is the cell's own var, in every compile
 * shape: it does not read the member, and a write does not replace the member on the scope
 * every cell and row of the table shares (Bug #78247). A var declared without an
 * initializer starts undefined on every run, so it never reads what another cell or row
 * left in the table's var store.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalcTableLensDeclaredVarTest {
   @BeforeEach
   void setUp() {
      env = new GraalJavaScriptEnv();
      env.init();
   }

   // the reporter's shape, split into pieces and run as the eval wrapper (max is a CALC
   // global, Bug #77331); a cell that never assigns it, and the summary function itself
   @Test
   void aSplitVarNamedLikeASummaryFunctionIsFreshPerRowAndCell() {
      CalcTableLens calc = calcTable(3, "var max; if(row == 1) max = 5; max",
                                     "var max; if(false) max = 1; max", "max([3,7])");

      assertEquals(Arrays.asList(
         Arrays.asList(null, null, 7.0),
         Arrays.asList(5.0, null, 7.0),
         Arrays.asList(null, null, 7.0)), values(calc));
   }

   // the production path: the runtime table of process(), which evaluates column by column,
   // so the cells of the later columns run after every row of the first one assigned it
   @Test
   void runtimeTableKeepsTheSummaryFunctionAfterACellAssignsItsVar() {
      RuntimeCalcTableLens runtime = calcTable(3, "var max; if(row == 1) max = 5; max",
                                               "var max; if(false) max = 1; max",
                                               "max([3,7])").process();
      List<List<Object>> values = new ArrayList<>();

      for(int r = 0; r < 3; r++) {
         values.add(Arrays.asList(num(runtime.getObject(r, 0)), num(runtime.getObject(r, 1)),
                                  num(runtime.getObject(r, 2))));
      }

      assertEquals(Arrays.asList(
         Arrays.asList(null, null, 7.0),
         Arrays.asList(5.0, null, 7.0),
         Arrays.asList(null, null, 7.0)), values);
   }

   // the reported shapes, assigning the named cell ($total) of a vertically expanded row
   // on the runtime table, and the summary function after them
   @Test
   void aVarAssignedANamedCellIsFreshPerRowAndCell() {
      CalcTableLens calc = calcTable(1, "[100, 300, 120]",
                                     "var max; if($total > 150) max = $total; max",
                                     "var count; if($total > 150) count = $total; count",
                                     "var max; if(this == null) max = 1; max",
                                     "var max; if(false) max = 1; max", "max([3,7])");
      calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      calc.setCellName(0, 0, "total");

      RuntimeCalcTableLens runtime = calc.process();
      List<List<Object>> values = new ArrayList<>();

      for(int r = 0; r < runtime.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < runtime.getColCount(); c++) {
            row.add(num(runtime.getObject(r, c)));
         }

         values.add(row);
      }

      assertEquals(Arrays.asList(
         Arrays.asList(100.0, null, null, null, null, 7.0),
         Arrays.asList(300.0, 300.0, 300.0, null, null, 7.0),
         Arrays.asList(120.0, null, null, null, null, 7.0)), values);
   }

   // a single-piece body, the plain with path
   @Test
   void aPlainVarNamedLikeASummaryFunctionIsTheCellsOwn() {
      CalcTableLens calc = calcTable(3, "var min; typeof min", "var count = row * 2; count",
                                     "count([1,2,3])");

      assertEquals(Arrays.asList(
         Arrays.asList("undefined", 0.0, 3.0),
         Arrays.asList("undefined", 2.0, 3.0),
         Arrays.asList("undefined", 4.0, 3.0)), values(calc));
   }

   // a split body whose name is no CALC global, run piece by piece (#77249)
   @Test
   void aSplitVarNamedLikeAScopeMemberIsFreshPerRow() {
      CalcTableLens calc = calcTable(3, "var field; if(row == 1) field = 5; field",
                                     "typeof field");

      assertEquals(Arrays.asList(
         Arrays.asList(null, "object"),
         Arrays.asList(5.0, "object"),
         Arrays.asList(null, "object")), values(calc));
   }

   // a body with this, run as the eval wrappers (#75550): split, and a single piece
   @Test
   void aVarOfABodyWithThisIsTheCellsOwn() {
      CalcTableLens calc = calcTable(2, "var count; if(this == null) count = 1; count",
                                     "var count = 3; this != null ? count : -1",
                                     "count([1,2])");

      assertEquals(Arrays.asList(
         Arrays.asList(null, 3.0, 2.0),
         Arrays.asList(null, 3.0, 2.0)), values(calc));
   }

   // an accumulator keeps its value in the table's var store across cells, as a var with
   // an initializer of the plain path does (CalcTableLensStallVarTest), and does not read
   // the sum function
   @Test
   void anAccumulatorNamedLikeASummaryFunctionPersistsAcrossCells() {
      CalcTableLens calc = calcTable(1, "var sum = (sum || 0) + 1; sum",
                                     "var sum = (sum || 0) + 1; sum");

      assertEquals(List.of(List.of(1.0, 2.0)), values(calc));
   }

   // the reopened report: a plain `var max; max` cell must not read the max another cell
   // (an eval wrapper, which hoists its vars to the table's store) assigned, whichever cell
   // and row is read first (Bug #78247)
   @Test
   void aPlainInitializerlessVarDoesNotReadAnotherCellsValue() {
      String a = "var max; if(this != null) max = 5; max";
      String b = "var max; max";
      List<List<Object>> ab = Arrays.asList(Arrays.asList(5.0, null, 7.0),
         Arrays.asList(5.0, null, 7.0), Arrays.asList(5.0, null, 7.0));
      List<List<Object>> ba = Arrays.asList(Arrays.asList(null, 5.0, 7.0),
         Arrays.asList(null, 5.0, 7.0), Arrays.asList(null, 5.0, 7.0));

      assertEquals(ab, values(calcTable(3, a, b, "max([3,7])")));
      assertEquals(ab, columnMajorValues(calcTable(3, a, b, "max([3,7])")));
      assertEquals(ba, values(calcTable(3, b, a, "max([3,7])")));
      assertEquals(ba, columnMajorValues(calcTable(3, b, a, "max([3,7])")));
      assertEquals(ab, values(calcTable(3, a, b, "max([3,7])").process()));
      assertEquals(ba, values(calcTable(3, b, a, "max([3,7])").process()));
   }

   // the live report: the reader runs before a split writer (which resets the name on
   // every run and assigns the named cell $total on some rows) in the same row
   @Test
   void aPlainInitializerlessVarDoesNotReadTheNamedCellAnotherCellAssigned() {
      CalcTableLens calc = calcTable(1, "[100, 300, 120, 400, 50]", "var zz; zz",
                                     "var zz; if($total > 150) zz = $total; zz");
      calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      calc.setCellName(0, 0, "total");

      assertEquals(Arrays.asList(
         Arrays.asList(100.0, null, null),
         Arrays.asList(300.0, null, 300.0),
         Arrays.asList(120.0, null, null),
         Arrays.asList(400.0, null, 400.0),
         Arrays.asList(50.0, null, null)), values(calc.process()));
   }

   // the whole live table: both readers of max (before and after the this writer) and the
   // zz reader before its split writer, all in one table with the named cell
   @Test
   void theLiveTableReadersAreEmptyOnEveryRow() {
      String b = "var max; max";
      CalcTableLens calc = calcTable(1, "[100, 300, 120, 400, 50]", b,
                                     "var max; if(this != null) max = 5; max", "var zz; zz",
                                     "var zz; if($total > 150) zz = $total; zz", b);
      calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      calc.setCellName(0, 0, "total");

      assertEquals(Arrays.asList(
         Arrays.asList(100.0, null, 5.0, null, null, null),
         Arrays.asList(300.0, null, 5.0, null, 300.0, null),
         Arrays.asList(120.0, null, 5.0, null, null, null),
         Arrays.asList(400.0, null, 5.0, null, 400.0, null),
         Arrays.asList(50.0, null, 5.0, null, null, null)), values(calc.process()));
   }

   // any name, a plain writer: only the initializer-less declarations are reset (b reads
   // the writer's b in its initializer). A cell without the var still reads the store, as
   // in Rhino, so it reads what the last cell that ran left there: undefined after a
   // reset of the name, the writer's value otherwise
   @Test
   void onlyTheInitializerlessVarsOfAPlainCellAreReset() {
      CalcTableLens calc = calcTable(3, "var zz = row + 20, a = 1, b = row + 10; zz",
                                     "var zz; zz",
                                     "zz", "var a, b = b; String(a) + '|' + b");

      assertEquals(Arrays.asList(
         Arrays.asList(20.0, null, null, "undefined|10"),
         Arrays.asList(21.0, null, null, "undefined|11"),
         Arrays.asList(22.0, null, null, "undefined|12")), values(calc));

      calc = calcTable(3, "var zz = row + 20; zz", "zz");

      assertEquals(Arrays.asList(
         Arrays.asList(20.0, 20.0),
         Arrays.asList(21.0, 21.0),
         Arrays.asList(22.0, 22.0)), values(calc));
   }

   // an accumulator with an initializer still counts across rows; one whose var has no
   // initializer starts undefined on every run, as in a split or this body
   @Test
   void anInitializerlessVarStartsUndefinedOnEveryRun() {
      assertEquals(Arrays.asList(List.of(1.0), List.of(2.0), List.of(3.0)),
                   values(calcTable(3, "var acc = (acc || 0) + 1; acc")));
      assertEquals(Arrays.asList(List.of(1.0), List.of(1.0), List.of(1.0)),
                   values(calcTable(3, "var x; x = (x || 0) + 1; x")));
      assertEquals(Arrays.asList(List.of(1.0), List.of(1.0), List.of(1.0)),
                   values(calcTable(3, "var x; x = (x || 0) + 1; if(false) x = 0; x")));
   }

   // the assembly's value, a member of the parent scope of the table's scope; a cell that
   // does not declare it still reads the assembly's
   @Test
   void aVarNamedLikeTheAssemblyValueIsTheCellsOwn() {
      VSAScriptable assembly = mock(VSAScriptable.class);
      when(assembly.hasMember("value")).thenReturn(true);
      when(assembly.getMember("value")).thenReturn("assembly");
      ViewsheetScope viewsheet = mock(ViewsheetScope.class);
      when(viewsheet.getVSAScriptable("CalcTable1")).thenReturn(assembly);
      env.put("viewsheet", viewsheet);

      CalcTableLens calc = calcTable(3, "var value; if(row == 1) value = 5; value",
                                     "var value; if(false) value = 1; value", "value");

      assertEquals(Arrays.asList(
         Arrays.asList(null, null, "assembly"),
         Arrays.asList(5.0, null, "assembly"),
         Arrays.asList(null, null, "assembly")), values(calc));
      verify(assembly, never()).putMember(eq("value"), any());
   }

   private CalcTableLens calcTable(int rows, String... formulas) {
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(new Object[][] {
         { "name", "value" }, { "a", 1 }, { "b", 2 } }));

      CalcTableLens calc = new CalcTableLens(rows, formulas.length);
      calc.setElement(elem);

      for(int r = 0; r < rows; r++) {
         for(int c = 0; c < formulas.length; c++) {
            calc.setObject(r, c, new CalcTableLens.Formula(formulas[c]));
         }
      }

      return calc;
   }

   // the values row by row, each row left to right
   private static List<List<Object>> values(CalcTableLens calc) {
      List<List<Object>> values = new ArrayList<>();

      for(int r = 0; r < calc.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < calc.getColCount(); c++) {
            row.add(num(calc.getValue(r, c)));
         }

         values.add(row);
      }

      return values;
   }

   // the values column by column, each column top to bottom, returned row by row
   private static List<List<Object>> columnMajorValues(CalcTableLens calc) {
      Object[][] values = new Object[calc.getRowCount()][calc.getColCount()];

      for(int c = 0; c < calc.getColCount(); c++) {
         for(int r = 0; r < calc.getRowCount(); r++) {
            values[r][c] = num(calc.getValue(r, c));
         }
      }

      List<List<Object>> rows = new ArrayList<>();

      for(Object[] row : values) {
         rows.add(Arrays.asList(row));
      }

      return rows;
   }

   // the values of the runtime table of process(), row by row, each row left to right
   private static List<List<Object>> values(RuntimeCalcTableLens runtime) {
      List<List<Object>> values = new ArrayList<>();

      for(int r = 0; r < runtime.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < runtime.getColCount(); c++) {
            row.add(num(runtime.getObject(r, c)));
         }

         values.add(row);
      }

      return values;
   }

   private static Object num(Object value) {
      return value instanceof Number n ? n.doubleValue() : value;
   }

   private GraalJavaScriptEnv env;
}
