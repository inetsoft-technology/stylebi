/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XTable;
import inetsoft.util.Tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Behavior shared by the summary formulas: the agent chosen for a column class, the default
 * formula and the default result. This is in the product package because the agent factory is
 * package private.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasCommonFeatureTest {
   @Test
   @DisplayName("gap:report.summary-formulas.common#agent-chosen-by-column-class")
   void agentChosenByColumnClass() {
      assertInstanceOf(FormulaAgent.DoubleFormulaAgent.class,
                       FormulaAgent.getAgent(Double.class, true));
      assertInstanceOf(FormulaAgent.FloatFormulaAgent.class,
                       FormulaAgent.getAgent(Float.class, true));
      assertInstanceOf(FormulaAgent.LongFormulaAgent.class,
                       FormulaAgent.getAgent(Long.class, true));
      assertInstanceOf(FormulaAgent.IntFormulaAgent.class,
                       FormulaAgent.getAgent(Integer.class, true));
      assertInstanceOf(FormulaAgent.ShortFormulaAgent.class,
                       FormulaAgent.getAgent(Short.class, true));

      // a column that is not primitive, no class, or another class: the object agent
      assertInstanceOf(FormulaAgent.ObjectFormulaAgent.class,
                       FormulaAgent.getAgent(Integer.class, false));
      assertInstanceOf(FormulaAgent.ObjectFormulaAgent.class,
                       FormulaAgent.getAgent(null, true));
      assertInstanceOf(FormulaAgent.ObjectFormulaAgent.class,
                       FormulaAgent.getAgent(String.class, true));
      assertInstanceOf(FormulaAgent.ObjectFormulaAgent.class,
                       FormulaAgent.getAgent(java.util.Date.class, true));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#default-formula-keeps-last-value")
   void defaultFormulaKeepsFirstValue() {
      DefaultFormula formula = new DefaultFormula();

      // nothing added
      assertNull(formula.getResult());
      assertTrue(formula.isNull());

      // the first value added wins (Bug #79768)
      formula.addValue(1.5);
      formula.addValue(2.5);
      assertEquals(1.5, formula.getResult());
      assertEquals(1.5, formula.getDoubleResult());
      assertFalse(formula.isNull());

      // an object wins over the primitives
      formula.addValue("text");
      assertEquals("text", formula.getResult());
      formula.addValue("later");
      assertEquals("text", formula.getResult());

      // a short only is returned when nothing else was added
      DefaultFormula shorts = new DefaultFormula();
      shorts.addValue((short) 7);
      assertEquals((short) 7, shorts.getResult());
      assertTrue(shorts.isNull());

      // reset clears the numbers
      formula.reset();
      assertTrue(formula.isNull());
      assertEquals(0.0, formula.getDoubleResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#default-formula-keeps-last-value")
   void defaultFormulaResetClearsObject() {
      DefaultFormula formula = new DefaultFormula();
      formula.addValue("text");
      formula.reset();

      assertNull(formula.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#default-result-returns-zero")
   void defaultResultReturnsZero() {
      Formula[] formulas = {
         new SumFormula(), new ProductFormula(), new AverageFormula(),
         new WeightedAverageFormula()
      };

      for(Formula formula : formulas) {
         String name = formula.getClass().getSimpleName();

         // nothing to aggregate: null by default, 0 once the default result is on
         assertNull(formula.getResult(), name);
         formula.setDefaultResult(true);
         assertTrue(formula.isDefaultResult(), name);
         assertEquals(Double.valueOf(0), formula.getResult(), name);
         formula.setDefaultResult(false);
         assertNull(formula.getResult(), name);
      }
   }

   /**
    * A table of one column whose cells are the given values, with every typed getter answering.
    */
   private static XTable typedTable(Object... values) {
      XTable table = mock(XTable.class);

      for(int r = 0; r < values.length; r++) {
         when(table.getObject(r, 0)).thenReturn(values[r]);

         if(values[r] == null) {
            when(table.getDouble(r, 0)).thenReturn(Tool.NULL_DOUBLE);
            when(table.getFloat(r, 0)).thenReturn(Tool.NULL_FLOAT);
            when(table.getLong(r, 0)).thenReturn(Tool.NULL_LONG);
            when(table.getInt(r, 0)).thenReturn(Tool.NULL_INTEGER);
            when(table.getShort(r, 0)).thenReturn(Tool.NULL_SHORT);
         }
         else {
            Number n = (Number) values[r];
            when(table.getDouble(r, 0)).thenReturn(n.doubleValue());
            when(table.getFloat(r, 0)).thenReturn(n.floatValue());
            when(table.getLong(r, 0)).thenReturn(n.longValue());
            when(table.getInt(r, 0)).thenReturn(n.intValue());
            when(table.getShort(r, 0)).thenReturn(n.shortValue());
         }
      }

      return table;
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#primitive-typed-column-fast-path")
   void primitiveTypedColumnFastPath() {
      Class<?>[] classes = {
         Double.class, Float.class, Long.class, Integer.class, Short.class
      };

      for(Class<?> cls : classes) {
         XTable table = typedTable(4, 6, 10);
         FormulaAgent typed = FormulaAgent.getAgent(cls, true);
         SumFormula fast = new SumFormula();
         CountFormula count = new CountFormula();

         for(int r = 0; r < 3; r++) {
            typed.add(fast, table, r, 0);
            typed.add(count, table, r, 0);
         }

         // the same as the values fed one by one as objects
         SumFormula slow = new SumFormula();
         FormulaAgent object = FormulaAgent.getAgent(cls, false);

         for(int r = 0; r < 3; r++) {
            object.add(slow, table, r, 0);
         }

         assertEquals(20.0, ((Number) fast.getResult()).doubleValue(), cls.getSimpleName());
         assertEquals(slow.getResult(), fast.getResult(), cls.getSimpleName());
         assertEquals(3, ((Number) count.getResult()).intValue(), cls.getSimpleName());
      }

      // a cell that holds the null sentinel of its type is not summed or counted
      for(Class<?> cls : classes) {
         XTable table = typedTable(5, null, 7);
         FormulaAgent typed = FormulaAgent.getAgent(cls, true);
         SumFormula sum = new SumFormula();
         CountFormula count = new CountFormula();

         for(int r = 0; r < 3; r++) {
            typed.add(sum, table, r, 0);
            typed.add(count, table, r, 0);
         }

         assertEquals(12.0, ((Number) sum.getResult()).doubleValue(), cls.getSimpleName());
         assertEquals(2, ((Number) count.getResult()).intValue(), cls.getSimpleName());
      }
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#result-type-defaults-to-input-type")
   void resultTypeDefaultsToInputType() {
      // the formulas that take the type of their input say so with null
      Formula[] sameType = {
         new MaxFormula(), new MinFormula(), new FirstFormula(), new LastFormula(),
         new DefaultFormula()
      };

      for(Formula formula : sameType) {
         assertNull(formula.getResultType(), formula.getClass().getSimpleName());
      }

      // the ones that always give another type say which
      assertEquals(Double.class, new SumFormula().getResultType());
      assertEquals(Double.class, new ProductFormula().getResultType());
      assertEquals(Double.class, new AverageFormula().getResultType());
      assertEquals(Double.class, new CorrelationFormula(1).getResultType());
      assertEquals(Double.class, new CovarianceFormula(1).getResultType());
      assertEquals(Double.class, new PopulationVarianceFormula().getResultType());
      assertEquals(Integer.class, new CountFormula().getResultType());
      assertEquals(Integer.class, new DistinctCountFormula().getResultType());
      assertEquals(String.class, new ConcatFormula().getResultType());

      // a formula that does not override it gets the default
      Formula plain = new DefaultFormula() {
         @Override
         public String toString() {
            return "plain";
         }
      };
      assertNull(plain.getResultType());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#typed-agent-skips-null-cell")
   void typedAgentSkipsNullCell() {
      XTable table = typedTable(new Object[] {null});
      Class<?>[] classes = {
         Double.class, Float.class, Long.class, Integer.class, Short.class
      };

      for(Class<?> cls : classes) {
         Formula formula = mock(Formula.class);
         FormulaAgent.getAgent(cls, true).add(formula, table, 0, 0);

         // nothing is added, not even the sentinel the typed getter answers
         verify(formula, never()).addValue(anyDouble());
         verify(formula, never()).addValue(anyFloat());
         verify(formula, never()).addValue(anyLong());
         verify(formula, never()).addValue(anyInt());
         verify(formula, never()).addValue(anyShort());
         verify(formula, never()).addValue(any(Object.class));
      }

      // the object agent hands the null to the formula to deal with
      Formula formula = mock(Formula.class);
      FormulaAgent.getAgent(Integer.class, false).add(formula, table, 0, 0);
      verify(formula).addValue((Object) null);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#pair-formula-fed-secondary-column-values")
   void pairFormulaFedSecondaryColumnValues() {
      // a group by column 0, the weighted average of column 1 by the weights of column 2
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         {"g", "x", "w"},
         {"a", 10, 2},
         {"a", 30, 3},
         {"b", 5, 2}
      });
      SummaryFilter filter = new SummaryFilter(
         table, new int[] {0}, new int[] {1}, new WeightedAverageFormula(2), null);
      filter.moreRows(Integer.MAX_VALUE);
      assertEquals(3, filter.getRowCount());
      assertEquals("a", filter.getObject(1, 0));
      assertEquals(22.0, ((Number) filter.getObject(1, 1)).doubleValue(), 1e-9);
      assertEquals("b", filter.getObject(2, 0));
      assertEquals(5.0, ((Number) filter.getObject(2, 1)).doubleValue(), 1e-9);

      // a row with no weight is left out (13.8 also checked this through the report-only
      // RunningTotal, which StyleBI does not have)
      DefaultTableLens gap = new DefaultTableLens(new Object[][] {
         {"g", "x", "w"}, {"a", 10, 1}, {"a", 99, null}, {"a", 30, 3}
      });
      SummaryFilter skipping = new SummaryFilter(
         gap, new int[] {0}, new int[] {1}, new WeightedAverageFormula(2), null);
      skipping.moreRows(Integer.MAX_VALUE);
      assertEquals(25.0, ((Number) skipping.getObject(1, 1)).doubleValue(), 1e-9);
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#second-column-formulas-need-pairs")
   void secondColumnFormulasNeedPairs() {
      Formula2[] pairs = {
         new WeightedAverageFormula(3), new CorrelationFormula(3), new CovarianceFormula(3),
         new FirstFormula(3), new LastFormula(3)
      };

      for(Formula2 pair : pairs) {
         String name = pair.getClass().getSimpleName();
         assertArrayEquals(new int[] {3}, pair.getSecondaryColumns(), name);

         // one value alone has no second column to go with it
         for(Runnable single : new Runnable[] {
            () -> pair.addValue(1.0), () -> pair.addValue(1.0f), () -> pair.addValue(1L),
            () -> pair.addValue(1), () -> pair.addValue((short) 1)})
         {
            RuntimeException ex = assertThrows(RuntimeException.class, single::run, name);
            assertEquals("Unsupported method called!", ex.getMessage(), name);
         }
      }

      // the value and its second column value as a pair are taken
      WeightedAverageFormula weighted = new WeightedAverageFormula(3);
      weighted.addValue(new Object[] {10, 1});
      weighted.addValue(new Object[] {40, 2});
      assertEquals(30.0, ((Number) weighted.getResult()).doubleValue(), 1e-9);

      // a pair of nulls, or no pair, adds nothing
      WeightedAverageFormula empty = new WeightedAverageFormula(3);
      empty.addValue(new Object[] {null, null});
      empty.addValue("not a pair");
      empty.addValue((Object) null);
      assertNull(empty.getResult());

      // the default constructors use the first column after the primary one
      assertArrayEquals(new int[] {0}, new WeightedAverageFormula().getSecondaryColumns());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.common#formula-reused-after-reset")
   void formulaReusedAfterReset() throws Exception {
      Formula[] formulas = {
         new SumFormula(), new AverageFormula(), new MaxFormula(), new MinFormula(),
         new CountFormula(), new ProductFormula()
      };

      for(Formula formula : formulas) {
         String name = formula.getClass().getSimpleName();
         Formula fresh = (Formula) formula.clone();
         formula.addValue(4.0);
         formula.addValue(6.0);
         assertFalse(formula.isNull(), name);
         Object first = formula.getResult();
         assertNotNull(first, name);

         // a clone is its own copy
         Formula copy = (Formula) formula.clone();
         assertEquals(first, copy.getResult(), name);
         copy.addValue(100.0);
         copy.addValue(-50.0);
         assertEquals(first, formula.getResult(), name);
         assertNotEquals(first, copy.getResult(), name);

         // reset starts over, and it can take another group
         formula.reset();
         assertEquals(fresh.isNull(), formula.isNull(), name);
         assertEquals(fresh.getResult(), formula.getResult(), name);
         formula.addValue(4.0);
         formula.addValue(6.0);
         assertEquals(first, formula.getResult(), name);

         // the copy was not reset by it
         assertNotEquals(first, copy.getResult(), name);
      }

      // a pair formula starts over too
      WeightedAverageFormula weighted = new WeightedAverageFormula(1);
      weighted.addValue(new Object[] {10, 1});
      assertFalse(weighted.isNull());
      weighted.reset();
      assertTrue(weighted.isNull());
      assertNull(weighted.getResult());
      weighted.addValue(new Object[] {20, 1});
      assertEquals(20.0, ((Number) weighted.getResult()).doubleValue(), 1e-9);
   }
}
