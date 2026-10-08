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
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.script.ScriptException;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77910: with {@code script.max.errors} in force, a lost swap file or a lock stall from
 * host code passes through the engine as itself and never counts toward the limit, while an
 * ordinary host failure or a script error is still a counted ScriptException and the limit
 * still cuts the script off.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraalJavaScriptEngineUnavailableCountTest {
   @BeforeEach
   void setUp() throws Exception {
      SreeEnv.setProperty("script.max.errors", "2");
      refreshMaxErrors();
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach
   void tearDown() {
      engine.close();
      SreeEnv.remove("script.max.errors");
      refreshMaxErrors();
   }

   // the engine caches the property for 10s
   private static void refreshMaxErrors() {
      try {
         Field field = GraalJavaScriptEngine.class.getDeclaredField("MAX_ERRORS_PROP");
         field.setAccessible(true);
         ((SreeEnv.Value) field.get(null)).updateValue();
      }
      catch(ReflectiveOperationException ex) {
         throw new AssertionError(ex);
      }
   }

   @Test
   void unavailableDataIsNotCountedAndOrdinaryErrorsStillReachTheLimit() throws Exception {
      Host host = new Host();
      engine.put("host", host);
      Object src = engine.compile("host.value()");
      SwapFileReadException swap =
         new SwapFileReadException(new File("lost.tdat"), new IOException("gone"));
      LockStallException stall = new LockStallException("probe.site", "worker", 1234, null);

      // more lost swap files and stalls than the limit: each reaches the caller as itself
      for(int i = 0; i < 3; i++) {
         host.failure = swap;
         assertSame(swap, assertThrows(SwapFileReadException.class, () -> engine.exec(src, null, null)));
         host.failure = new IllegalStateException("wrapped", swap);
         assertSame(swap, assertThrows(SwapFileReadException.class, () -> engine.exec(src, null, null)));
         host.failure = stall;
         assertSame(stall, assertThrows(LockStallException.class, () -> engine.exec(src, null, null)));
      }

      assertFalse(errorCounts().containsKey(src), "unavailable data is not a script error");

      // a stall below a swap failure in the chain wins
      host.failure = new SwapFileReadException(new File("x.tdat"), stall);
      assertSame(stall, assertThrows(LockStallException.class, () -> engine.exec(src, null, null)));

      // ordinary host failures: counted, cause-less ScriptException, until the limit
      host.failure = new IllegalStateException("bad cell");

      for(int i = 1; i <= 2; i++) {
         ScriptException ex = assertThrows(ScriptException.class, () -> engine.exec(src, null, null));
         assertNull(ex.getCause());
         assertTrue(ex.getMessage().contains("bad cell"), ex.getMessage());
         assertEquals(i, errorCounts().get(src));
      }

      // the limit is reached: the script no longer runs, even if its host would now fail
      // with a lost swap file
      host.failure = swap;
      assertNull(engine.exec(src, null, null));
      assertEquals(12, host.calls, "the capped script is not run");
   }

   /**
    * Bug #78071: the load failure of a table the script read stays a ScriptException for the
    * callers that take any script error, with the failure as its cause for a calc table that
    * must fail with it, and it is not counted toward the limit.
    */
   @Test
   void loadFailureIsTheCauseAndNotCounted() throws Exception {
      Host host = new Host();
      engine.put("host", host);
      Object src = engine.compile("host.value()");
      TableLoadException load = new TableLoadException("db down", new IOException("db down"));

      for(int i = 0; i < 3; i++) {
         host.failure = i == 0 ? load : new IllegalStateException("wrapped", load);
         ScriptException ex =
            assertThrows(ScriptException.class, () -> engine.exec(src, null, null));
         assertSame(load, ex.getCause());
      }

      assertFalse(errorCounts().containsKey(src), "a load failure is not a script error");
      assertEquals(3, host.calls);
   }

   @Test
   void scriptErrorIsStillCounted() throws Exception {
      Object src = engine.compile("throw new Error('js failure')");
      ScriptException ex = assertThrows(ScriptException.class, () -> engine.exec(src, null, null));
      assertTrue(ex.getMessage().contains("js failure"), ex.getMessage());
      assertEquals(1, errorCounts().get(src));
   }

   private Map<?, ?> errorCounts() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("errorCounts");
      field.setAccessible(true);
      return (Map<?, ?>) field.get(engine);
   }

   public static final class Host {
      public Object value() {
         calls++;
         throw failure;
      }

      volatile RuntimeException failure;
      volatile int calls;
   }

   private GraalJavaScriptEngine engine;
}
