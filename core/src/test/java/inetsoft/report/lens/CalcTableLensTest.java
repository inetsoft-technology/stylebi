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

package inetsoft.report.lens;

import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.script.formula.CalcRef;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CalcTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      CalcTableLens originalTable = new CalcTableLens(XTableUtil.getDefaultTableLens());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CalcTableLens.class, deserializedTable.getClass());
   }

   /**
    * Bug #75595: a bare named-cell reference ($name) as a formula result must be
    * resolved to its referenced value so a live CalcRef never leaks into the
    * cached cell data (which later crashed JSON serialization of
    * LoadTableDataCommand and hung the table on "loading").
    */
   @Test
   public void testUnwrapTopLevelCalcRef() {
      CalcRef ref = mock(CalcRef.class);
      when(ref.unwrap()).thenReturn("value1");

      Assertions.assertEquals("value1", CalcTableLens.unwrapCalcRefs(ref));
   }

   /**
    * Bug #75595: a CalcRef nested inside an array result (e.g. [$a, $b]) must
    * also be resolved, since ScriptValueConverter.toHost unwraps each array
    * element back to its raw host object.
    */
   @Test
   public void testUnwrapCalcRefInArray() {
      CalcRef a = mock(CalcRef.class);
      CalcRef b = mock(CalcRef.class);
      when(a.unwrap()).thenReturn(1);
      when(b.unwrap()).thenReturn(2);

      Object result = CalcTableLens.unwrapCalcRefs(new Object[]{ a, "x", b });

      Assertions.assertArrayEquals(new Object[]{ 1, "x", 2 }, (Object[]) result);
   }

   /**
    * Bug #75595: CalcRefs nested in arrays-of-arrays must be resolved too.
    */
   @Test
   public void testUnwrapCalcRefInNestedArray() {
      CalcRef a = mock(CalcRef.class);
      when(a.unwrap()).thenReturn(1);

      Object result = CalcTableLens.unwrapCalcRefs(new Object[]{ new Object[]{ a }, "y" });

      Object[] outer = (Object[]) result;
      Assertions.assertArrayEquals(new Object[]{ 1 }, (Object[]) outer[0]);
      Assertions.assertEquals("y", outer[1]);
   }

   /**
    * Bug #75595: results with no CalcRef must be returned unchanged (no
    * regression for ordinary numeric/string/array formula results).
    */
   @Test
   public void testNonCalcRefResultUnchanged() {
      Assertions.assertEquals("plain", CalcTableLens.unwrapCalcRefs("plain"));
      Assertions.assertEquals(42.0, CalcTableLens.unwrapCalcRefs(42.0));
      Assertions.assertNull(CalcTableLens.unwrapCalcRefs(null));

      Object[] arr = { 1, "a", null };
      Assertions.assertArrayEquals(new Object[]{ 1, "a", null }, (Object[]) CalcTableLens.unwrapCalcRefs(arr));
   }

   /**
    * Bug #76665: a $name reference that repeats across an expand region
    * unrelated to the current cell's own expand cursor unwraps to an
    * ambiguous, multi-element Object[] (the whole, unfiltered set of
    * instances). Returning that raw array as a bare cell value previously
    * leaked it into the cached cell data (rendering as the array's default
    * toString(), e.g. "[Ljava.lang.Object;@<hash>"); it must throw instead.
    */
   @Test
   public void testUnwrapTopLevelCalcRefThrowsOnAmbiguousArray() {
      CalcRef ref = mock(CalcRef.class);
      when(ref.unwrap()).thenReturn(new Object[]{ 100.0, 200.0 });
      when(ref.getCellName()).thenReturn("AmtDetail");

      Assertions.assertThrows(inetsoft.util.script.ScriptException.class,
                              () -> CalcTableLens.unwrapCalcRefs(ref));
   }

   /**
    * A hand-typed formula cell (content:"formula") referencing a bare, unqualified
    * column name (e.g. Sum(PAID) instead of Sum(field['PAID'])) is invalid syntax in
    * the calc-table script scope and always throws a ReferenceError, regardless of
    * grouping. The rendered cell must lead with that ReferenceError and the
    * GraalJavaScriptEnv.getSuggestion() fix-it hint, not the generic "Assembly: <name>"
    * context label, since a cell only has room to render one line.
    */
   @Test
   public void testFormulaErrorSurfacesReferenceErrorAndHintFirst() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "id" },
         { 1 }
      });

      CalcTableVSAssembly assembly = new CalcTableVSAssembly();
      assembly.setScriptTable(data);
      assembly.setScriptEnv(new GraalJavaScriptEnv());

      CalcTableLens calc = new CalcTableLens(data);
      calc.setElement(assembly);
      calc.setFormula(1, 0, "Sum(PAID)");

      Object value = calc.getValue(1, 0);

      Assertions.assertTrue(value instanceof String, "expected an ERROR string, got: " + value);
      String message = (String) value;
      String firstLine = message.split("\n", 2)[0];
      Assertions.assertTrue(
         firstLine.contains("ReferenceError") && firstLine.contains("is not defined"),
         "first line should be the actual JS error, not the Assembly label: " + message);
      Assertions.assertTrue(message.contains("data['columnName']"),
         "message should include the getSuggestion() fix-it hint: " + message);
      Assertions.assertFalse(message.contains("referenced using field['columnName']"),
         "calc-table hint must not teach the silent-0 field['columnName'] form: " + message);
   }

   private RuntimeCalcTableLens boundGroupLens(String formula) {
      return groupLens("toList(data['id'])", formula);
   }

   private RuntimeCalcTableLens groupLens(String groupFormula, String formula) {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "id", "amt" },
         { 1, 10 },
         { 2, 20 },
         { 3, 30 }
      });

      CalcTableVSAssembly assembly = new CalcTableVSAssembly();
      assembly.setScriptTable(data);
      assembly.setScriptEnv(new GraalJavaScriptEnv());

      // column 0 is a bound-column style group (a plain list, no source row scope),
      // column 1 is the formula under test
      CalcTableLens calc = new CalcTableLens(2, 2);
      calc.setElement(assembly);
      calc.setFormula(1, 0, groupFormula);
      calc.setExpansion(1, 0, CalcTableLens.EXPAND_VERTICAL);
      calc.setFormula(1, 1, formula);
      return calc.process0();
   }

   /**
    * Bug #76665 (mode 3): Sum(field["X"]) in a calc-table cell whose group is not a
    * rowList reads field as the calc table's own row. A column that is not in that
    * scope (nonexistent, or a real source column) previously read as undefined and
    * summed to a silent 0; every instance must now be a named error.
    */
   @Test
   public void testFieldMissColumnFailsLoudOnEveryInstance() {
      for(String col : new String[] { "NOPE", "amt" }) {
         RuntimeCalcTableLens calc = boundGroupLens("Sum(field[\"" + col + "\"])");
         Assertions.assertTrue(calc.getRowCount() >= 4, "rows: " + calc.getRowCount());

         for(int r = 1; r <= 3; r++) {
            Object value = calc.getObject(r, 1);
            Assertions.assertTrue(value instanceof String && ((String) value).startsWith("ERROR"),
               "row " + r + " col " + col + " should be an error, got: " + value);
            Assertions.assertTrue(((String) value).contains("\"" + col + "\" not found"),
               "error should name the column: " + value);
            Assertions.assertTrue(((String) value).contains("rowList"),
               "error should explain field scope: " + value);
         }
      }
   }

   /**
    * String coercion of the strict field probes valueOf/toString/join; it must not
    * throw, so only a real column read is an error.
    */
   @Test
   public void testStrictFieldStringCoercionDoesNotThrow() {
      for(String f : new String[] { "'' + field", "String(field)", "`${field}`" }) {
         RuntimeCalcTableLens calc = boundGroupLens(f);
         Object value = calc.getObject(1, 1);
         Assertions.assertFalse(value instanceof String && ((String) value).startsWith("ERROR"),
            f + " should not error, got: " + value);
      }
   }

   /**
    * rowList-driven groups install a source-row field scope that is not strict and
    * must keep resolving columns.
    */
   @Test
   public void testRowListFieldStillResolves() {
      RuntimeCalcTableLens calc = groupLens("rowList(data, 'id')", "field[\"amt\"]");
      Object value = calc.getObject(1, 1);
      Assertions.assertEquals(10.0, ((Number) value).doubleValue(), "got: " + value);
   }

   /**
    * Bug #78599: sum(data["NOPE"]) in a calc-table cell must be a named error on every
    * instance, while a real column still computes.
    */
   @Test
   public void testDataMissColumnFailsLoud() {
      RuntimeCalcTableLens calc = boundGroupLens("sum(data[\"NOPE\"])");

      for(int r = 1; r <= 3; r++) {
         Object value = calc.getObject(r, 1);
         Assertions.assertTrue(value instanceof String && ((String) value).startsWith("ERROR"),
            "row " + r + " should be an error, got: " + value);
         Assertions.assertTrue(((String) value).contains("\"NOPE\" not found"),
            "error should name the column: " + value);
      }

      calc = boundGroupLens("sum(data[\"amt\"])");
      Assertions.assertEquals(60.0, ((Number) calc.getObject(1, 1)).doubleValue());
   }

   /**
    * Bug #78685: a condition of data["amt?cond"] that names something undefined must be a
    * named error on every instance, not the unfiltered total; resolvable conditions keep
    * filtering.
    */
   @Test
   public void testDataMissConditionNameFailsLoud() {
      for(String cond : new String[] { "NOPE > 1", "id == NOPE", "field['NOPE'] > 1" }) {
         RuntimeCalcTableLens calc = boundGroupLens("sum(data[\"amt?" + cond + "\"])");

         for(int r = 1; r <= 3; r++) {
            Object value = calc.getObject(r, 1);
            Assertions.assertTrue(value instanceof String && ((String) value).startsWith("ERROR"),
               cond + " row " + r + " should be an error, got: " + value);
            Assertions.assertTrue(((String) value).contains("NOPE\" not found"),
               "error should name NOPE: " + value);
         }
      }

      String[][] controls = {
         { "amt?id > 1", "50.0" }, { "amt?Math.abs(id) > 1", "50.0" },
         { "amt?parseInt('1') == 1", "60.0" }, { "amt?typeof NOPE == 'undefined'", "60.0" } };

      for(String[] control : controls) {
         RuntimeCalcTableLens calc = boundGroupLens("sum(data[\"" + control[0] + "\"])");
         Assertions.assertEquals(Double.parseDouble(control[1]),
            ((Number) calc.getObject(1, 1)).doubleValue(), control[0]);
      }
   }
}
