/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.report.script;

import inetsoft.report.filter.CalcFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.util.script.FormulaContext;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.util.script.ScriptUtil;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import inetsoft.util.script.ScriptException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for #75423: a calc-table cell formula reads bound columns as
 * {@code data['Col']}. GraalJS gates member reads on {@code hasMember} (unlike
 * Rhino, whose {@code get()} was always invoked), so {@code TableArray.hasMember}
 * must report dynamic column references as present — otherwise {@code data['Col']}
 * reads as undefined and calc-table group expansion collapses to zero rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableArrayTest {
   private static XTable table() {
      return new DefaultTableLens(new Object[][] {
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "b", 3, 10.0 },
         { "c", 1, 2.5 }
      });
   }

   @Test
   void hasMemberReportsExistingColumnsPresent() {
      TableArray arr = new TableArray(table());

      // dynamic columns must be present so GraalJS dispatches getMember
      assertTrue(arr.hasMember("col1"), "existing column 'col1' must be present");
      assertTrue(arr.hasMember("col2"));
      assertTrue(arr.hasMember("length"));
      assertTrue(arr.hasMember("size"));
   }

   @Test
   void hasMemberReportsCellRangeExpressionsPresent() {
      TableArray arr = new TableArray(table());

      // group / condition / subtable range expressions resolve lazily in getMember
      assertTrue(arr.hasMember("col1@col2"));
      assertTrue(arr.hasMember("col3?col2=1"));
      assertTrue(arr.hasMember("*@col1"));
   }

   @Test
   void hasMemberReportsExpressionAndSummaryRangesPresent() {
      // #75662: table['=<expr>'] (expression range, evaluated per row) and
      // table['{<agg>}'] (summary range) are resolved lazily in getMember via
      // NamedCellRange. Util.findColumn can never match them as literal columns,
      // so hasMember must report them present -- otherwise the reference reads as
      // undefined and indexing it (e.g. table['=col2 + col3'][row]) throws.
      TableArray arr = new TableArray(table());

      assertTrue(arr.hasMember("=col1 + col2"),
         "leading-'=' expression range must be present so GraalJS dispatches getMember");
      assertTrue(arr.hasMember("{Sum(col2)}"),
         "'{...}' summary range must be present so GraalJS dispatches getMember");
   }

   @Test
   void hasMemberRejectsNonColumns() {
      TableArray arr = new TableArray(table());

      // non-columns (incl. JS-internal names) must not be over-claimed as present
      assertFalse(arr.hasMember("nonexistent"));
      assertFalse(arr.hasMember("toString"));
      assertFalse(arr.hasMember("then"));
   }

   /**
    * Minimal {@code CalcFilter} (e.g. a crosstab) stub exposing a fixed set of
    * measure/aggregate headers, without needing a full CrossTabFilter setup.
    */
   private static class MeasureTableLens extends DefaultTableLens implements CalcFilter {
      MeasureTableLens() {
         super(new Object[][] {
            { "col1", "col2" },
            { "a", 1 }
         });
      }

      @Override
      public void setMeasureNames(String[] names) {
      }

      @Override
      public List<String> getMeasureHeaders() {
         return Arrays.asList("Sum(category_id)", "Sum(customer_id)");
      }
   }

   @Test
   void hasMemberReportsBareMeasureNamesPresent() {
      // #75647: a calc table's grand-total cell has no row/col group, so its formula
      // is a bare reference like data['Sum(category_id)'] with no "@group:value"
      // qualifier. The measure name never appears as a literal column header (that
      // identity varies per data cell, not per fixed column), so hasMember must
      // special-case it the same way it already does for bare dimension names.
      TableArray arr = new TableArray(new MeasureTableLens());

      assertTrue(arr.hasMember("Sum(category_id)"),
         "bare measure name must be present so GraalJS dispatches getMember");
      assertTrue(arr.hasMember("Sum(customer_id)"));
      assertFalse(arr.hasMember("Sum(nonexistent)"),
         "a name that isn't a measure header must not be over-claimed as present");
   }

   @Test
   void isBaseTableReferenceDefaultsFalse() {
      // #75663: a plain TableArray (e.g. a calc-table's own `data`, or a chart/
      // table-bound array) is NOT a by-name worksheet-table reference, so it must
      // retain the grouped/crosstab summary-cell routing in NamedCellRange.
      assertFalse(new TableArray(table()).isBaseTableReference());
   }

   @Test
   void unwrapReturnsUnderlyingXTable() {
      // #75576: ScriptUtil.unwrap must unwrap a TableArray to its XTable so host
      // code (toList/mapList/etc.) can process a data['*@...'] subtable reference
      // instead of leaving the non-serializable wrapper in a cell value.
      XTable xtable = table();
      Object unwrapped = ScriptUtil.unwrap(new TableArray(xtable));

      assertInstanceOf(XTable.class, unwrapped);
      assertSame(xtable, unwrapped);
   }

   /**
    * A table whose row reads past the header fail with {@code failure}, as a stalled
    * FormulaTableLens row lock would in FAIL stall mode.
    */
   private static XTable failingTable(RuntimeException failure) {
      return new DefaultTableLens(new Object[][] {
         { "col1", "col2", "col3" },
         { "a", 1, 5.0 },
         { "b", 3, 10.0 }
      }) {
         @Override
         public boolean moreRows(int row) {
            if(row > 0) {
               throw failure;
            }

            return super.moreRows(row);
         }
      };
   }

   @Test
   void stalledColumnReadThrowsTheStall() {
      // #77123: a stalled table has no value to return (#76967), so a column read
      // must not turn the stall into a null script value.
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      TableArray arr = new TableArray(failingTable(stall));

      LockStallException thrown =
         assertThrows(LockStallException.class, () -> arr.getMember("col1"));
      assertSame(stall, thrown);
   }

   @Test
   void wrappedStalledColumnReadThrowsTheStall() {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      TableArray arr = new TableArray(failingTable(new IllegalStateException("wrapped", stall)));

      LockStallException thrown =
         assertThrows(LockStallException.class, () -> arr.getMember("col1"));
      assertSame(stall, thrown);
   }

   @Test
   void stalledSubtableReadThrowsTheStall() {
      LockStallException stall = new LockStallException("test.site", "worker", 1234, null);
      TableArray arr = new TableArray(failingTable(stall));

      LockStallException thrown =
         assertThrows(LockStallException.class, () -> arr.getMember("*"));
      assertSame(stall, thrown);
   }

   @Test
   void failedColumnReadStillFallsBackToMembers() {
      // a failure that is not a stall keeps the old behavior: logged, and the
      // script-set member (or null) is returned
      TableArray arr = new TableArray(failingTable(new IllegalStateException("broken")));

      assertNull(arr.getMember("col1"));

      arr.putMember("col1", "assigned");
      assertEquals("assigned", arr.getMember("col1"));
   }

   /**
    * #77910: a column read of a table whose swap file is lost (its moreRows reads the lost
    * fragment) must throw the swap file read failure, not read as null.
    */
   @Test
   void lostSwapFileColumnReadThrowsTheSwapFailure() {
      try(LostSwapFile lost = new LostSwapFile()) {
         TableArray arr = new TableArray(new LostSwapFile.Table(new Object[][] {
            { "col1", "col2", "col3" },
            { "a", 1, 5.0 },
            { "b", 3, 10.0 }
         }, 1, true, lost));

         assertEquals(lost.getFile(),
            assertThrows(SwapFileReadException.class, () -> arr.getMember("col1")).getFile());
         assertEquals(lost.getFile(),
            assertThrows(SwapFileReadException.class, () -> arr.getMember("*")).getFile());
      }
   }

   private static TableArray strictArray() {
      TableArray arr = new TableArray(table());
      arr.setCalcArray(true);
      arr.setStrictMissing(true);
      return arr;
   }

   /**
    * Bug #78599: a calc-table formula reading data['NOPE'] (bare, '@' and ':' forms)
    * must fail with a named error instead of reading as undefined/null.
    */
   @Test
   void strictMissingColumnThrowsNamedError() {
      TableArray arr = strictArray();

      for(String id : new String[] { "NOPE", "NOPE@col1:a", "NOPE?col2=1", "Ent:NOPE" }) {
         assertTrue(arr.hasMember(id), id);
         ScriptException ex = assertThrows(ScriptException.class, () -> arr.getMember(id), id);
         assertTrue(ex.getMessage().contains(id.replaceAll("[@?].*", "")), ex.getMessage());
         assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
      }
   }

   private static void assertNamesMissing(TableArray arr, String id, String name) {
      ScriptException ex = assertThrows(ScriptException.class, () -> arr.getMember(id), id);
      assertTrue(ex.getMessage().contains(name + "\" is not defined"), ex.getMessage());
      assertTrue(ex.getMessage().contains("column \"" + name + "\" not found"), ex.getMessage());
      assertTrue(ex.getMessage().contains(id.substring(id.indexOf('?') + 1)), ex.getMessage());
   }

   /**
    * Bug #78685: a name the condition of data['col?cond'] cannot resolve must fail with a
    * named error, not leave every row selected (the unfiltered range).
    */
   @Test
   void strictConditionWithUndefinedNameThrowsNamedError() {
      TableArray arr = strictArray();

      assertNamesMissing(arr, "col2?NOPE > 1", "NOPE");
      assertNamesMissing(arr, "col2?col1 == NOPE", "NOPE");
      assertNamesMissing(arr, "col2?undefinedFn(col2) > 1", "undefinedFn");
   }

   /**
    * Bug #78685 scope guard: conditions that resolve, or that probe for an undefined name,
    * keep filtering as before in strict mode.
    */
   @Test
   void strictConditionKeepsResolvableConditions() {
      TableArray arr = strictArray();

      assertArrayEquals(new Object[] { 3, 1 }, (Object[]) arr.getMember("col2?col2 != 1 || col1 == 'c'"));
      assertArrayEquals(new Object[] { 3 }, (Object[]) arr.getMember("col2?Math.abs(col2) > 2"));
      assertArrayEquals(new Object[] { 1, 3, 1 }, (Object[]) arr.getMember("col2?parseInt('1') == 1"));
      assertArrayEquals(new Object[] { 1, 3, 1 },
                        (Object[]) arr.getMember("col2?typeof NOPE == 'undefined'"));
   }

   /**
    * Bug #78685: a name provided by the surrounding (viewsheet-like) scope resolves, so it is
    * not reported as missing.
    */
   @Test
   void strictConditionResolvesNamesFromParentScope() {
      java.util.Map<String, Object> vars = new java.util.HashMap<>();
      vars.put("limit", 2);
      ScriptScope parent = new ScriptScope() {
         public Object getMember(String name) { return vars.get(name); }
         public boolean hasMember(String name) { return vars.containsKey(name); }
         public void putMember(String name, Object value) { vars.put(name, value); }
         public boolean removeMember(String name) { return vars.remove(name) != null; }
         public Object[] getMemberKeys() { return vars.keySet().toArray(); }
      };

      FormulaContext.pushScope(parent);

      try {
         TableArray arr = strictArray();
         assertArrayEquals(new Object[] { 3 }, (Object[]) arr.getMember("col2?col2 > limit"));
      }
      finally {
         FormulaContext.popScope();
      }
   }

   /**
    * Bug #78685 scope guard: outside a strict array an undefined name in a condition keeps
    * its old behaviour (logged, rows kept).
    */
   @Test
   void nonStrictConditionWithUndefinedNameStaysSilent() {
      for(boolean calc : new boolean[] { false, true }) {
         TableArray arr = new TableArray(table());
         arr.setCalcArray(calc);

         assertArrayEquals(new Object[] { 1, 3, 1 }, (Object[]) arr.getMember("col2?NOPE > 1"));
      }
   }

   @Test
   void strictKeepsLegitimateForms() {
      TableArray arr = strictArray();

      assertArrayEquals(new Object[] { "a", "b", "c" }, (Object[]) arr.getMember("col1"));
      assertArrayEquals(new Object[] { "a", "b", "c" }, (Object[]) arr.getMember("COL1"));
      assertNotNull(arr.getMember("col1@col2:1"));
      assertTrue(arr.getMember("*") instanceof TableArray);
      assertEquals(4, arr.getMember("length"));
      assertEquals(3, arr.getMember("size"));
      assertFalse(arr.hasMember("toString"));
      assertFalse(arr.hasMember("0"));
   }

   @Test
   void strictSkipsEmptyAndHeaderlessTables() {
      TableArray empty = new TableArray(new DefaultTableLens(0, 0));
      empty.setStrictMissing(true);
      assertNull(empty.getMember("NOPE"));

      TableArray headerless = new TableArray(new DefaultTableLens(new Object[][] {{ "a" }}) {
         @Override
         public int getHeaderRowCount() {
            return 0;
         }
      });
      headerless.setStrictMissing(true);
      assertNull(headerless.getMember("NOPE"));
   }

   @Test
   void defaultModeStaysSilentOnMissingColumn() {
      TableArray arr = new TableArray(table());
      assertFalse(arr.hasMember("NOPE"));
      assertNull(arr.getMember("Ent:NOPE"));
   }

   /**
    * Bug #78599 scope guard: outside a strict (calc-table) array a missing column keeps
    * reading as absent/null, in both calc and plain array modes.
    */
   @Test
   void nonStrictMissingColumnStaysSilent() {
      for(boolean calc : new boolean[] { false, true }) {
         TableArray arr = new TableArray(table());
         arr.setCalcArray(calc);

         assertFalse(arr.hasMember("NOPE"));
         assertNull(arr.getMember("NOPE"));
         assertNull(arr.getMember("NOPE@col1:a"));
         assertNull(arr.getMember("Ent:NOPE"));
      }
   }

   /**
    * Bug #78599 scope guard: strict mode leaves the condition form, index access and
    * entity:attribute names working as before.
    */
   @Test
   void strictKeepsConditionIndexAndEntityAttrForms() {
      TableArray arr = strictArray();

      TableArray plain = new TableArray(table());
      plain.setCalcArray(true);
      assertArrayEquals((Object[]) plain.getMember("col1?col2=1"),
                        (Object[]) arr.getMember("col1?col2=1"));
      assertTrue(arr.hasMember("col1?col2=1"));
      assertTrue(arr.getArrayElement(1) instanceof TableRow);
      assertEquals("a", ((TableRow) arr.getArrayElement(1)).getMember("col1"));

      XTable ent = new DefaultTableLens(new Object[][] {
         { "Ent:Attr", "col2" },
         { "x", 1 },
         { "y", 2 }
      });
      TableArray earr = new TableArray(ent);
      earr.setCalcArray(true);
      earr.setStrictMissing(true);
      assertArrayEquals(new Object[] { "x", "y" }, (Object[]) earr.getMember("Ent:Attr"));
      assertArrayEquals(new Object[] { "x", "y" }, (Object[]) earr.getMember("ent:attr"));
   }

   /**
    * Bug #78599 scope guard: '=expr' keys bypass the missing-column check and evaluate
    * exactly as in non-strict mode.
    */
   @Test
   void strictExpressionKeyEvaluatesAsBefore() {
      TableArray plain = new TableArray(table());
      plain.setCalcArray(true);
      TableArray arr = strictArray();
      String id = "=col2 * 2";

      assertTrue(arr.hasMember(id));
      Object expected = plain.getMember(id);
      assertNotNull(expected);
      assertArrayEquals((Object[]) expected, (Object[]) arr.getMember(id));
   }

   /**
    * Bug #78599 scope guard: '{summary}' keys bypass the missing-column check; on a
    * plain table they behave as in non-strict mode (no named missing-column error).
    */
   @Test
   void strictSummaryKeyBehavesAsBefore() {
      TableArray plain = new TableArray(table());
      plain.setCalcArray(true);
      TableArray arr = strictArray();
      String id = "{Sum(col2)}";

      assertTrue(arr.hasMember(id));
      assertEquals(plain.getMember(id), arr.getMember(id));
   }
}
