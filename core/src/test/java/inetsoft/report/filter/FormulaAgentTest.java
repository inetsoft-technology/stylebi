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

import inetsoft.uql.XTable;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for FormulaAgent: the agent chosen for a column class and how the typed agents feed a
 * formula. This is in the product package because the agent factory is package private.
 */
@Tag("core")
public class FormulaAgentTest {
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

   // gap:report.summary-formulas.common#agent-chosen-by-column-class
   @Test
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

   // gap:report.summary-formulas.common#primitive-typed-column-fast-path
   @Test
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

   // gap:report.summary-formulas.common#typed-agent-skips-null-cell
   @Test
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
}
