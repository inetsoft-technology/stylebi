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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.uql.jdbc.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.function.DoublePredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80155: runs conditions through SubMV (the real condition path, with the Long or String
 * value a variable delivers) against an integer measure column holding nulls, and compares the
 * rows with a row by row evaluation of the SQL semantics: a null never satisfies a comparison.
 * The incremental delete additionally removes the null rows of a comparison.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubMVIntColumnBoundOracleTest {
   private static final double[] BOUNDS = {
      -4294967297.0, -3e9, -2147483649.0, -2147483648.0, -100, -1, 0, 1, 2, 24, 25, 26,
      99, 100, 101, 124, 125, 126, 2147483646.0, 2147483647.0, 2147483648.0, 3e9, 4294967296.0,
      4294967297.0 };

   @Test
   void comparisonsMatchSqlSemantics() throws Exception {
      for(double[] data : new double[][] { data(1, 25), data(100, 125) }) {
         SubMV sub = sub(data);

         for(double b : BOUNDS) {
            for(Object val : new Object[] { (long) b, Long.toString((long) b) }) {
               String m = "bound " + val + " " + val.getClass().getSimpleName() +
                  " data0=" + data[1];
               check(m + " >", sub, data, ">", val, false, v -> v > b);
               check(m + " >=", sub, data, ">=", val, false, v -> v >= b);
               check(m + " <", sub, data, "<", val, false, v -> v < b);
               check(m + " <=", sub, data, "<=", val, false, v -> v <= b);
               check(m + " =", sub, data, "=", val, false, v -> v == b);
               check(m + " !=", sub, data, "=", val, true, v -> v != b);
               check(m + " not >", sub, data, ">", val, true, v -> v <= b);
               check(m + " not <", sub, data, "<", val, true, v -> v >= b);
               check(m + " not >=", sub, data, ">=", val, true, v -> v < b);
               check(m + " not <=", sub, data, "<=", val, true, v -> v > b);
               check(m + " in", sub, data, "IN", new Object[] { val, "2", "25" }, false,
                     v -> v == b || v == 2 || v == 25);
               check(m + " not in", sub, data, "IN", new Object[] { val, "2", "25" }, true,
                     v -> v != b && v != 2 && v != 25);
            }
         }
      }
   }

   @Test
   void betweenMatchesSqlSemantics() throws Exception {
      double[] data = data(1, 25);
      SubMV sub = sub(data);

      for(double lo : BOUNDS) {
         for(double hi : BOUNDS) {
            for(boolean li : new boolean[] { true, false }) {
               for(boolean ri : new boolean[] { true, false }) {
                  XSet set = new XSet(XSet.AND);
                  set.addChild(cond(li ? ">=" : ">", (long) lo));
                  set.addChild(cond(ri ? "<=" : "<", (long) hi));
                  BitSet expect = oracle(data, v -> (li ? v >= lo : v > lo) &&
                     (ri ? v <= hi : v < hi), false);
                  assertEquals(expect, sub.getRows(set, false), "between " + lo + ".." + hi +
                     " " + li + ri);
               }
            }
         }
      }
   }

   @Test
   void deleteAddsNullRows() throws Exception {
      double[] data = data(1, 25);
      SubMV sub = sub(data);

      for(double b : BOUNDS) {
         for(String op : new String[] { ">", ">=", "<", "<=", "=" }) {
            BitSet expect = oracle(data, v -> eval(op, v, b), true);
            assertEquals(expect, sub.getRows(cond(op, (long) b), true), "delete " + op + " " + b);
         }
      }
   }

   private static boolean eval(String op, double v, double b) {
      switch(op) {
      case ">": return v > b;
      case ">=": return v >= b;
      case "<": return v < b;
      case "<=": return v <= b;
      default: return v == b;
      }
   }

   private static void check(String m, SubMV sub, double[] data, String op, Object val,
                             boolean not, DoublePredicate p)
   {
      XBinaryCondition c = cond(op, val);
      c.setIsNot(not);
      assertEquals(oracle(data, p, false), sub.getRows(c, false), m);
   }

   private static XBinaryCondition cond(String op, Object val) {
      XBinaryCondition c = new XBinaryCondition(new XExpression("col", XExpression.FIELD),
         new XExpression(val, XExpression.VALUE), op);
      c.setAttribute("containsNull", "true");
      return c;
   }

   private static BitSet oracle(double[] data, DoublePredicate p, boolean addNulls) {
      BitSet set = new BitSet();

      for(int i = 0; i < data.length; i++) {
         if(data[i] == Tool.NULL_DOUBLE) {
            if(addNulls) { set.set(i); }
         }
         else if(p.test(data[i])) {
            set.set(i);
         }
      }

      return set;
   }

   // null, min, min+1..max, null, null
   private static double[] data(int min, int max) {
      double[] data = new double[max - min + 1 + 3];
      data[0] = Tool.NULL_DOUBLE;
      int n = 1;

      for(int v = min; v <= max; v++) {
         data[n++] = v;
      }

      data[n++] = Tool.NULL_DOUBLE;
      data[n] = Tool.NULL_DOUBLE;
      return data;
   }

   private static SubMV sub(double[] data) throws Exception {
      double min = Double.MAX_VALUE;
      double max = -Double.MAX_VALUE;

      for(double d : data) {
         if(d != Tool.NULL_DOUBLE) {
            min = Math.min(min, d);
            max = Math.max(max, d);
         }
      }

      MVIntColumn col = new MVIntColumn(data.length, (int) min, (int) max);

      for(int i = 0; i < data.length; i++) {
         col.setValue(i, data[i]);
      }

      DefaultTableBlock table = mock(DefaultTableBlock.class);
      when(table.indexOfHeader("col")).thenReturn(0);
      SubMV sub = new SubMV(new XDimIndex[] { col }, table);
      Field f = SubMV.class.getDeclaredField("rtdims");
      f.setAccessible(true);
      f.set(sub, new XDimIndex[] { col });
      return sub;
   }
}
