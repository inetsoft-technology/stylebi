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

import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapReadInterruptedException;
import inetsoft.util.swap.XIntFragment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77916: a script timeout that interrupts a swap read of Java code a formula calls is a
 * swap read failure of the formula lens, like a lost swap file (bug #77912): the reader gets
 * it, and the row is not kept, so a later read computes the row again instead of reading a
 * null cell.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
public class FormulaTableLensSwapInterruptTest {
   @BeforeEach
   public void setUp() throws Exception {
      previousTimeout = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();
      host = new Host();
   }

   @AfterEach
   public void tearDown() throws Exception {
      Thread.interrupted();
      host.fragment.dispose();
      SreeEnv.setProperty("script.execution.timeout", previousTimeout);
      refreshTimeout();
   }

   @Test
   public void timeoutDuringHostSwapReadIsNotKeptAsANullCell() {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();
      env.init();
      env.put("host", host);
      FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data()),
         new String[] { "f" },
         new String[] { "(field['value'] == 3 && host.armed() ? host.loop() : 0) + " + RUNNING_TOTAL },
         env, null);

      SwapFileReadException ex = assertThrows(SwapFileReadException.class, () -> drain(lens));
      assertInstanceOf(SwapReadInterruptedException.class, ex);
      assertInstanceOf(SwapReadInterruptedException.class, host.failure,
                       "the timeout did not interrupt a swap read");
      Thread.interrupted();

      List<Object> control = drain(new FormulaTableLens(new DefaultTableLens(data()),
         new String[] { "f" }, new String[] { RUNNING_TOTAL }, env, null));
      assertEquals(List.of(1.0, 3.0, 6.0, 10.0, 15.0, 21.0, 28.0, 36.0), toDoubles(control));
      // twice: the row must be computed again, not read back as a kept null cell
      assertEquals(control, drain(lens));
      assertEquals(control, drain(lens));
   }

   private static List<Object> drain(TableLens table) {
      List<Object> values = new ArrayList<>();

      for(int r = 1; table.moreRows(r); r++) {
         values.add(table.getObject(r, 2));
      }

      return values;
   }

   private static List<Double> toDoubles(List<Object> values) {
      List<Double> doubles = new ArrayList<>();

      for(Object value : values) {
         doubles.add(value == null ? null : ((Number) value).doubleValue());
      }

      return doubles;
   }

   private static Object[][] data() {
      Object[][] data = new Object[N + 1][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= N; i++) {
         data[i] = new Object[] { "k" + i, i };
      }

      return data;
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   /**
    * A host object whose loop swaps a fragment out and reads it back until the timeout
    * interrupts a read. It loops once only.
    */
   public static final class Host {
      Host() {
         int[] values = new int[100];

         for(int i = 0; i < values.length; i++) {
            values[i] = i;
         }

         fragment = new XIntFragment(values);
      }

      public boolean armed() {
         return !done;
      }

      public int loop() {
         done = true;
         long end = System.currentTimeMillis() + 10000;

         try {
            while(System.currentTimeMillis() < end) {
               fragment.swap();
               fragment.getSafely(5);
            }
         }
         catch(RuntimeException ex) {
            failure = ex;
            throw ex;
         }

         return 0;
      }

      final XIntFragment fragment;
      volatile boolean done;
      volatile RuntimeException failure;
   }

   private static final int N = 8;
   private static final String RUNNING_TOTAL = "field['value'] + (field[-1] == null || " +
      "field[-1]['f'] == null || isNaN(field[-1]['f']) ? 0 : field[-1]['f'])";

   private String previousTimeout;
   private Host host;
}
