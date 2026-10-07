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
package inetsoft.util.script.graal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapReadInterruptedException;
import inetsoft.util.swap.XIntFragment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77916: a script timeout interrupts the exec thread, so a swap read of Java code the
 * script calls fails with a {@link SwapReadInterruptedException}. exec reports it as the
 * timeout, a stopped script, not as a lost swap file. An interrupt that is not this exec's
 * timeout, e.g. a cancel, still reaches the caller as the swap read failure.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraalJavaScriptEngineSwapInterruptTest {
   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refreshTimeout();
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      host = new Host();
      engine.put("host", host);
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      host.fragment.dispose();
      engine.close();
      SreeEnv.setProperty("script.execution.timeout", previousTimeout);
      refreshTimeout();
   }

   @Test
   void timeoutDuringHostSwapReadStopsTheScript() throws Exception {
      Object src = engine.compile("host.loop()");

      ScriptException ex = assertThrows(ScriptException.class, () -> engine.exec(src, null, null));
      assertTrue(ex.isStopped(), "a timeout is a stopped script");
      assertTrue(ScriptTimeoutGuard.isStop(ex));
      assertNull(SwapFileReadException.find(ex), "a timeout is not a lost swap file");
      assertInstanceOf(SwapReadInterruptedException.class, host.failure,
                       "the timeout did not interrupt a swap read");
      assertFalse(Thread.currentThread().isInterrupted(), "the timeout left the flag set");
      assertEquals(1005, host.fragment.getSafely(5));
   }

   @Test
   void cancelDuringHostSwapReadReachesTheCaller() throws Exception {
      Object src = engine.compile("host.cancelledRead()");

      SwapReadInterruptedException ex = assertThrows(SwapReadInterruptedException.class,
         () -> engine.exec(src, null, null));
      assertSame(host.failure, ex);
      Thread.interrupted();
      assertEquals(1005, host.fragment.getSafely(5));
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   /**
    * A host object whose methods read a swapped fragment.
    */
   public static final class Host {
      Host() {
         int[] values = new int[100];

         for(int i = 0; i < values.length; i++) {
            values[i] = 1000 + i;
         }

         fragment = new XIntFragment(values);
      }

      /**
       * Swap the fragment out and read it back until the timeout interrupts a read.
       */
      public Object loop() {
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

         return null;
      }

      /**
       * Read the swapped fragment on a thread that a cancel interrupted.
       */
      public Object cancelledRead() {
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
      volatile RuntimeException failure;
   }

   private String previousTimeout;
   private GraalJavaScriptEngine engine;
   private Host host;
}
