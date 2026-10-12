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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import inetsoft.util.swap.DataUnavailable;
import inetsoft.util.swap.SwapReadInterruptedException;
import inetsoft.util.swap.XIntFragment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77916: a script timeout or a cancel that interrupts a swap read of Java code a formula
 * calls fails the read with a {@link SwapReadInterruptedException}. Bug #78098: the swap file is
 * not lost, the exec was stopped, so the row takes the stop path (bug #77949): the reader gets
 * the stop, the row is kept and its cell fails every later read with the stop, and the row is
 * not computed again from the vars the stopped exec already changed. A formula that keeps a var
 * across rows (bug #77123) so gives the other rows the values it gives with no interrupt.
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

      if(env != null) {
         env.remove("host");

         if(env instanceof WorksheetScriptEnv pooled) {
            pooled.retire();
         }
      }

      SreeEnv.setProperty("script.execution.timeout", previousTimeout);
      refreshTimeout();
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void timeoutDuringHostSwapReadStopsTheRowAndAppliesTheVarOnce(boolean pool)
      throws Exception
   {
      TableLens lens = lens(pool, "host.loop()");

      assertStop(assertThrows(RuntimeException.class, () -> drain(lens)));
      assertInstanceOf(SwapReadInterruptedException.class, host.failure,
                       "the timeout did not interrupt a swap read");
      Thread.interrupted();

      assertStoppedAtRow3(lens);
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   public void cancelDuringHostSwapReadStopsTheRowAndAppliesTheVarOnce(boolean pool)
      throws Exception
   {
      TableLens lens = lens(pool, "host.cancelledRead()");

      assertStop(assertThrows(RuntimeException.class, () -> drain(lens)));
      assertInstanceOf(SwapReadInterruptedException.class, host.failure,
                       "the cancel did not interrupt a swap read");
      assertTrue(Thread.interrupted(), "the cancel's interrupt flag was cleared");

      assertStoppedAtRow3(lens);
   }

   /**
    * Read the lens twice more: row 3 fails with the stop every time, and the other rows have
    * the values the formula gives with no interrupt.
    */
   private static void assertStoppedAtRow3(TableLens lens) {
      List<Object> expected = List.of(1, 2, STOP, 4, 5, 6, 7, 8);
      assertEquals(expected, cells(lens));
      assertEquals(expected, cells(lens));
   }

   /**
    * A formula lens built as a worksheet builds it, whose formula keeps a var across rows and
    * calls {@code trigger} on the host at row 3, once.
    */
   private TableLens lens(boolean pool, String trigger) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      env = box.getScriptEnv();
      env.put("host", host);
      String formula = "var k = (k || 0) + 1; " +
         "var x = (field['value'] == 3 && host.armed() ? " + trigger + " : 0); k";
      return PostProcessor.formula(new DefaultTableLens(data()), new String[] { "f" },
                                   new String[] { formula }, env, box.getScope(), null, "T",
                                   null, List.of(Integer.class), new boolean[] { false });
   }

   private static void assertStop(Throwable ex) {
      assertTrue(ScriptTimeoutGuard.isStop(ex), "not a stop: " + ex);
      assertNull(DataUnavailable.find(ex), "the stop reads as a lost swap file: " + ex);
   }

   private static void drain(TableLens table) {
      for(int r = 1; table.moreRows(r); r++) {
         table.getObject(r, 2);
      }
   }

   /**
    * The cells of the formula column, {@link #STOP} for a cell whose read fails with a stop.
    */
   private static List<Object> cells(TableLens table) {
      List<Object> values = new ArrayList<>();

      for(int r = 1; r <= N; r++) {
         try {
            assertTrue(table.moreRows(r), "row " + r);
            Object value = table.getObject(r, 2);
            values.add(value instanceof Number ? ((Number) value).intValue() : value);
         }
         catch(RuntimeException ex) {
            assertStop(ex);
            values.add(STOP);
         }
      }

      return values;
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
    * A host object whose read of a swapped fragment is interrupted, once.
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

      /**
       * Swap the fragment out and read it back until the timeout interrupts a read.
       */
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

      /**
       * Read the swapped fragment on a thread that a cancel interrupted.
       */
      public int cancelledRead() {
         done = true;
         assertTrue(fragment.swap(), "fragment was not swapped");
         Thread.currentThread().interrupt();

         try {
            return fragment.getSafely(5);
         }
         catch(RuntimeException ex) {
            failure = ex;
            throw ex;
         }
      }

      final XIntFragment fragment;
      volatile boolean done;
      volatile RuntimeException failure;
   }

   private static final int N = 8;
   private static final String STOP = "STOP";

   private String previousTimeout;
   private Host host;
   private ScriptEnv env;
}
