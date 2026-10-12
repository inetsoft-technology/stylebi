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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redmine #76480 / VBS-004 (P3 reproduction, rows v4p-O5 and v4s-O6). An aggregate calc field
 * whose expression references no detected aggregate tag reaches the engine as a ZERO-child
 * {@link CalcFieldFormula}; its script runs in a scope with no {@code field}, throws
 * {@link ScriptException}, and {@link SummaryFilter#process1} swallows that for the whole table.
 *
 * <p>Assertions encode TODAY's behaviour (they pass while the defect exists).</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcFieldNoSubAggregate76480Test {
   private AssetQuerySandbox box;

   @BeforeEach
   void setUp() throws Exception {
      box = new AssetQuerySandbox(new Worksheet());
   }

   private CalcFieldFormula zeroChild(String expression) {
      return new CalcFieldFormula(expression, new String[0], new Formula[0], new int[0],
                                  box.getScriptEnv(), box.getScope());
   }

   @Test
   void v4pO5_zeroChildOuterCallFormThrowsScriptException() {
      CalcFieldFormula f = zeroChild("sum(field['A'])");
      f.addValue(new Object[]{ 1.0 });

      ScriptException ex = assertThrows(ScriptException.class, f::getResult);
      System.out.println("[76480 v4p-O5] getResult threw: " + ex.getMessage());
   }

   @Test
   void v4pO5_zeroChildScalarAddValueFailsBeforeTheScript() {
      CalcFieldFormula f = zeroChild("sum(field['A'])");
      Throwable t = assertThrows(Throwable.class, () -> f.addValue(1.0));
      System.out.println("[76480 v4p-O5 scalar] addValue(1.0) threw: " + t);
   }

   @Test
   void v4pO5_controlOneChildDocumentedFormEvaluates() {
      // The detected tag is the whole accessor; generateRuntimeScript rewrites it to an identifier.
      CalcFieldFormula f = new CalcFieldFormula("field['Sum(A)'] * 2", new String[]{ "field['Sum(A)']" },
         new Formula[]{ new SumFormula() }, new int[]{ 0 }, box.getScriptEnv(), box.getScope());
      f.addValue(new Object[]{ null, 3.0 });
      Object result = f.getResult();
      System.out.println("[76480 v4p-O5 control] documented form result: " + result);
      assertEquals(6.0, ((Number) result).doubleValue(), 1e-9);
   }

   @Test
   void v4sO6_oneZeroChildCalcEmptiesEveryMeasureOfTheSummary() {
      Tool.getUserMessage(); // drain anything left on this thread
      SummaryFilter bad = new SummaryFilter(lens(), new int[]{ 0 }, new int[]{ 1, 2, 3 },
         new Formula[]{ new SumFormula(), new SumFormula(), zeroChild("field['X']") }, null);
      bad.moreRows(TableLens.EOT);
      int badRows = bad.getRowCount();
      System.out.println("[76480 v4s-O6] with zero-child calc: rowCount=" + badRows +
                         " userMessage=" + Tool.getUserMessage());
      dump("[76480 v4s-O6] bad", bad);

      SummaryFilter ok = new SummaryFilter(lens(), new int[]{ 0 }, new int[]{ 1, 2 },
         new Formula[]{ new SumFormula(), new SumFormula() }, null);
      ok.moreRows(TableLens.EOT);
      System.out.println("[76480 v4s-O6] control without calc: rowCount=" + ok.getRowCount());
      dump("[76480 v4s-O6] ok", ok);

      assertTrue(ok.getRowCount() > 1, "control must have data rows");
      assertEquals(1, badRows, "header only: every measure lost, not just the calc column");
   }

   private static TableLens lens() {
      return new DefaultTableLens(new Object[][]{
         { "g", "a", "b", "x" },
         { "p", 1.0, 10.0, 5.0 },
         { "p", 2.0, 20.0, 6.0 },
         { "q", 3.0, 30.0, 7.0 },
      });
   }

   private static void dump(String label, TableLens t) {
      for(int r = 0; t.moreRows(r); r++) {
         StringBuilder sb = new StringBuilder(label).append(" row ").append(r).append(':');

         for(int c = 0; c < t.getColCount(); c++) {
            sb.append(' ').append(t.getObject(r, c));
         }

         System.out.println(sb);
      }
   }
}
