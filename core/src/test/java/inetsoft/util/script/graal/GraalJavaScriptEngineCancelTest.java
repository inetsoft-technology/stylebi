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

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123 (CX1): an engine's init on a cancelled thread (its interrupt flag set) keeps
 * the cancel and still builds the whole engine. Graal turns a set flag into "Thread was
 * interrupted." at the init's first guest safepoint and clears it; before the fix,
 * installHostGlobals swallowed that, so the cancel was lost and the engine kept no host global
 * set for its life (every bug #77181 let/const reset skipped).
 */
@Tag("core")
class GraalJavaScriptEngineCancelTest {
   @BeforeEach
   @AfterEach
   void clearFlag() {
      Thread.interrupted();
   }

   @Test
   void initOnACancelledThreadKeepsTheCancelAndTheHostGlobals() throws Exception {
      Set<Object> expected;
      GraalJavaScriptEngine clean = new GraalJavaScriptEngine();

      try {
         clean.init(new HashMap<>());
         expected = new TreeSet<>(Arrays.asList(clean.getMemberKeys()));
      }
      finally {
         clean.close();
      }

      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

      try {
         Thread.currentThread().interrupt();
         engine.init(new HashMap<>());
         assertTrue(Thread.interrupted(), "the init lost the cancel");
         assertNotNull(hostGlobals(engine), "the engine has no host global set");
         assertEquals(expected, new TreeSet<>(Arrays.asList(engine.getMemberKeys())),
                      "the engine's globals");
         assertEquals("object", engine.exec(engine.compile(
            "typeof __inetsoft_host_globals__"), null, null));
      }
      finally {
         engine.close();
      }
   }

   /**
    * A cancel that lands during the init, as the host global set is taken (the init only
    * clears a flag that was set before it): the set is still installed and the cancel kept.
    */
   @Test
   void aCancelDuringTheHostGlobalInstallKeepsTheCancelAndTheHostGlobals() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

      try {
         engine.init(new HashMap<>());
         Method install = GraalJavaScriptEngine.class.getDeclaredMethod("installHostGlobals");
         install.setAccessible(true);
         engine.getExecutionLock().lock();

         try {
            Thread.currentThread().interrupt();
            install.invoke(engine);
         }
         finally {
            engine.getExecutionLock().unlock();
         }

         assertTrue(Thread.interrupted(), "the install lost the cancel");
         assertNotNull(hostGlobals(engine), "the engine has no host global set");
      }
      finally {
         engine.close();
      }
   }

   /**
    * The pool-off env creates its engine at the first put (as AssetQuerySandbox.getScope does).
    */
   @Test
   void theEnvsFirstPutOnACancelledThreadKeepsTheCancel() throws Exception {
      GraalJavaScriptEnv env = new GraalJavaScriptEnv();

      try {
         Thread.currentThread().interrupt();
         env.put("x", 5);
         assertTrue(Thread.interrupted(), "the env's init lost the cancel");
         assertEquals(5, ((Number) env.exec(env.compile("x"), null, null, null)).intValue());
      }
      finally {
         env.reset();
      }
   }

   private static Object hostGlobals(GraalJavaScriptEngine engine) throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("hostGlobals");
      field.setAccessible(true);
      return field.get(engine);
   }
}
