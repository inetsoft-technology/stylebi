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
import inetsoft.report.TableLens;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapReadInterruptedException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77916: a script timeout that interrupts a swap read of the base of a freehand (calc)
 * table formula is a swap read failure, like a lost swap file (bug #77909): the reader gets
 * it, the cell is not cached as an "ERROR: ..." text and the table is not processed as null,
 * so a later read evaluates the formula again.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class CalcTableLensSwapInterruptTest {
   @BeforeEach
   public void setUp() throws Exception {
      previousTimeout = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();
      table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "key", "value" });

      for(int i = 1; i <= N; i++) {
         table.addRow(new Object[] { "k" + i, i * 10 });
      }

      table.complete();
      base = new SpinBase(table);
   }

   @AfterEach
   public void tearDown() throws Exception {
      Thread.interrupted();
      table.dispose();
      SreeEnv.setProperty("script.execution.timeout", previousTimeout);
      refreshTimeout();
   }

   @Test
   public void timeoutDuringBaseSwapReadIsNotCachedAsAnErrorCell() {
      CalcTableLens calc = calcTable("while(data['spin'][2] == 1) {} data['value'][2]", false);

      assertThrows(SwapReadInterruptedException.class, () -> calc.getValue(0, 0));
      assertInstanceOf(SwapReadInterruptedException.class, base.failure,
                       "the timeout did not interrupt a swap read");
      Thread.interrupted();
      base.armed = false;

      // twice: the formula must be evaluated again, not read back as a cached error cell
      assertEquals(30, ((Number) calc.getValue(0, 0)).intValue());
      assertEquals(30, ((Number) calc.getValue(0, 0)).intValue());
   }

   @Test
   public void timeoutDuringBaseSwapReadInExpansionIsNotANullTable() {
      CalcTableLens calc = calcTable("while(data['spin'][2] == 1) {} data['value']", true);

      assertThrows(SwapReadInterruptedException.class, calc::process);
      assertInstanceOf(SwapReadInterruptedException.class, base.failure,
                       "the timeout did not interrupt a swap read");
      Thread.interrupted();
      base.armed = false;

      List<Object> expected = List.of(10, 20, 30, 40, 50, 60, 70, 80);
      assertEquals(expected, cells(calc.process()));
      assertEquals(expected, cells(calc.process()));
   }

   /**
    * A 1x1 calc table over the spinning base whose only cell is {@code formula}, evaluated
    * by a real GraalJS env.
    */
   private CalcTableLens calcTable(String formula, boolean expand) {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getScriptEnv()).thenReturn(env);
      when(elem.getID()).thenReturn("CalcTable1");
      when(elem.getScriptTable()).thenReturn(base);

      CalcTableLens calc = new CalcTableLens(1, 1);
      calc.setElement(elem);
      calc.setObject(0, 0, new CalcTableLens.Formula(formula));

      if(expand) {
         calc.setExpansion(0, 0, CalcTableLens.EXPAND_VERTICAL);
      }

      return calc;
   }

   private static List<Object> cells(RuntimeCalcTableLens runtime) {
      assertNotNull(runtime, "the calc table was not processed");
      List<Object> values = new ArrayList<>();

      for(int r = 0; runtime.moreRows(r); r++) {
         Object value = runtime.getObject(r, 0);
         values.add(value instanceof Number ? ((Number) value).intValue() : value);
      }

      return values;
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   /**
    * A base over a swappable table with a "spin" column. While armed, a read of the column
    * swaps the table out and reads it back many times, so the timeout lands in a swap read
    * rather than in the script, and it is 1; else it is 0.
    */
   private static final class SpinBase extends AbstractTableLens {
      SpinBase(XSwappableTable table) {
         this.table = table;
      }

      @Override
      public boolean moreRows(int row) {
         return table.moreRows(row);
      }

      @Override
      public int getRowCount() {
         return table.getRowCount();
      }

      @Override
      public int getColCount() {
         return 3;
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      @Override
      public Object getObject(int r, int c) {
         if(c < 2) {
            return table.getObject(r, c);
         }

         if(r == 0) {
            return "spin";
         }

         if(!armed) {
            return 0;
         }

         try {
            for(int i = 0; i < 500; i++) {
               table.getTables()[0].swap(false);
               table.getObject(r, 1);
            }
         }
         catch(SwapFileReadException ex) {
            if(failure == null) {
               failure = ex;
            }

            throw ex;
         }

         return 1;
      }

      private final XSwappableTable table;
      volatile boolean armed = true;
      volatile RuntimeException failure;
   }

   private static final int N = 8;
   private String previousTimeout;
   private XSwappableTable table;
   private SpinBase base;
}
