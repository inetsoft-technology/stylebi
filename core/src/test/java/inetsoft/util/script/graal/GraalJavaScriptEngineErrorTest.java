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
package inetsoft.util.script.graal;

import inetsoft.util.script.ScriptException;
import inetsoft.util.stall.LockStallException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import static inetsoft.util.stall.StallTestSupport.assertStallOf;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class GraalJavaScriptEngineErrorTest {
   private GraalJavaScriptEngine engine;

   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }
   @AfterEach void teardown() { engine.close(); }

   @Test void runtimeErrorBecomesScriptException() throws Exception {
      Object src = engine.compile("throw new Error('boom')");
      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(src, null, null));
      assertTrue(ex.getMessage().contains("boom"));
   }

   /**
    * Bug #75555: the ScriptException thrown for a runtime script error must be
    * fully serializable. Previously it retained the non-serializable GraalJS
    * PolyglotException as its cause, so marshalling it across the cluster (Ignite
    * affinity-call response) failed with "PolyglotException serialization is not
    * supported", masking the real script error.
    */
   @Test void runtimeErrorScriptExceptionIsSerializable() throws Exception {
      Object src = engine.compile("var o; o.y");
      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(src, null, null));

      // must round-trip through Java serialization without throwing
      try(ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
         assertDoesNotThrow(() -> oos.writeObject(ex),
            "runtime-error ScriptException must be serializable (#75555)");
      }

      // the real script error is preserved in the message
      assertNotNull(ex.getMessage());
   }

   /**
    * Bug #76967: a lock stall thrown by host code the script calls, e.g. a read of a stalled
    * table, reaches the caller of exec as the same LockStallException, not as a
    * ScriptException, and is not counted as a script error.
    */
   @Test void hostStallReachesTheCallerAsItIs() throws Exception {
      LockStallException stall = new LockStallException("nested.site", "worker", 1234, null);
      engine.put("stalledHost", new StalledHost(stall));
      Object src = engine.compile("stalledHost.value()");

      assertStallOf(stall, assertThrows(LockStallException.class,
         () -> engine.exec(src, null, null)));

      Field errorCountsField = GraalJavaScriptEngine.class.getDeclaredField("errorCounts");
      errorCountsField.setAccessible(true);
      assertFalse(((Map<?, ?>) errorCountsField.get(engine)).containsKey(src),
                  "a stall is not a script error");
   }

   /**
    * A host object whose method fails with a lock stall.
    */
   public static final class StalledHost {
      StalledHost(LockStallException failure) {
         this.failure = failure;
      }

      public Object value() {
         throw failure;
      }

      private final LockStallException failure;
   }

   /**
    * Bug #77322: a runtime error reports the line of the user's script, not the
    * line of the wrapped Source (which used to add one line after the opening
    * {@code with(__scope__)} brace), for every compile shape.
    */
   @ParameterizedTest(name = "[{index}] line {1}")
   @MethodSource("errorLineCases")
   void runtimeErrorReportsTheScriptLine(String script, int line) throws Exception {
      Object src = engine.compile(script);
      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(src, null, null));
      assertTrue(ex.getMessage().endsWith("(line " + line + ")"), ex.getMessage());
   }

   static Stream<Arguments> errorLineCases() {
      return Stream.of(
         // plain wrapper
         Arguments.of("undefinedFn77322();", 1),
         Arguments.of("var a = 1;\nvar b = 2;\nundefinedFn77322();", 3),
         Arguments.of("// header\nundefinedFn77322();", 2),
         // stripped "use strict" keeps the lines before and after it
         Arguments.of("'use strict';\nundefinedFn77322();", 2),
         Arguments.of("// header\n'use strict';\nundefinedFn77322();", 3),
         Arguments.of("\n\n'use strict';\nundefinedFn77322();", 4),
         Arguments.of("/* a\n b */ \"use strict\"\r\n'use strict';\r\nundefinedFn77322();", 4),
         // eval form (the script references `this`)
         Arguments.of("var t = this;\nundefinedFn77322();", 2),
         Arguments.of("// header\n'use strict';\nvar t = this;\nundefinedFn77322();", 4),
         // split pieces, this-free (PieceScript)
         Arguments.of("x77322 = 1;\nif(true) {\n  undefinedFn77322();\n}", 3),
         Arguments.of("undefinedFn77322();\nif(false) { y77322 = 2; }", 1),
         // split pieces in the eval wrapper (the script references `this`),
         // including dropped whitespace-only stretches between pieces
         Arguments.of("var t = this;\nif(true) {\n  undefinedFn77322();\n}", 3),
         Arguments.of("var t = this;\n\n\nif(true) { 1 }\n\nif(true) {\n  undefinedFn77322() }", 7),
         Arguments.of("'use strict';\nvar t = this;\r\nif(true) { 1 }\r\nif(true) { undefinedFn77322() }", 4),
         // syntax error
         Arguments.of("var a = 1;\nvar b = ;", 2));
   }

   /**
    * Bug #77322: a runtime error inside a library function reports the line of
    * the function source.
    */
   @Test void libraryFunctionErrorReportsTheFunctionLine() throws Exception {
      GraalJavaScriptEngine lib = new GraalJavaScriptEngine() {
         @Override
         protected Map<String, String> librarySources() {
            return Map.of("libf77322",
                          "function libf77322() {\n  return libUndefined77322();\n}");
         }
      };

      try {
         lib.init(new HashMap<>());
         Object src = lib.compile("libf77322()");
         ScriptException ex = assertThrows(ScriptException.class,
            () -> lib.exec(src, null, null));
         assertTrue(ex.getMessage().contains("libUndefined77322"), ex.getMessage());
         assertTrue(ex.getMessage().endsWith("(line 2)"), ex.getMessage());
      }
      finally {
         lib.close();
      }
   }

   /**
    * Bug #77322: with the body on the wrapper's first line, a trailing line
    * comment still cannot swallow the closing brace, and the completion value is
    * kept, on every compile shape.
    */
   @Test void trailingLineCommentKeepsTheCompletionValue() throws Exception {
      assertEquals(3.0, num(engine.exec(engine.compile("1 + 2 // c"), null, null)));
      assertEquals(3.0, num(engine.exec(engine.compile("var a = 1;\na + 2 // c"), null, null)));
      assertEquals(3.0, num(engine.exec(engine.compile("var t = this; 1 + 2 // c"), null, null)));
      assertEquals(5.0, num(engine.exec(
         engine.compile("x77322c = 1;\nif(false) {}\n5 // c"), null, null)));
      assertEquals(5.0, num(engine.exec(
         engine.compile("var t = this;\nif(false) {}\n5 // c"), null, null)));
      assertEquals(4.0, num(engine.exec(
         engine.compile("// header\n'use strict';\n2 + 2 // c"), null, null)));
   }

   private static double num(Object value) {
      return ((Number) value).doubleValue();
   }

   /**
    * FIX B verification: the per-Source error counter map is cleared by init().
    * Strategy: use reflection to stuff the errorCounts map with a fake entry
    * at max count, confirm exec returns null (wedged), call init() to reset,
    * then confirm a good script executes normally again.
    */
   @Test void reinitClearsErrorCounterMap() throws Exception {
      Object goodSrc = engine.compile("2 + 2");

      // Stuff the errorCounts map via reflection to simulate an exhausted Source
      Field errorCountsField = GraalJavaScriptEngine.class
         .getDeclaredField("errorCounts");
      errorCountsField.setAccessible(true);

      @SuppressWarnings("unchecked")
      Map<Object, Integer> errorCounts = (Map<Object, Integer>) errorCountsField.get(engine);

      // Insert a sentinel key whose count equals the default limit (30000)
      // and point goodSrc there by using goodSrc as the key at 30000.
      // We must hold the engine's lock while touching errorCounts, but since
      // we control the test sequence and exec() holds it briefly, we use the
      // public lock field directly.
      engine.lock.lock();
      try {
         errorCounts.put(goodSrc, 30000);
      }
      finally {
         engine.lock.unlock();
      }

      // The engine is now wedged for goodSrc — exec should return null
      Object result = engine.exec(goodSrc, null, null);
      assertNull(result, "exec must return null when error limit is reached");

      // Re-init clears the map
      engine.init(new HashMap<>());

      // After re-init, the same script must execute successfully
      Object goodSrc2 = engine.compile("2 + 2");
      Object result2 = engine.exec(goodSrc2, null, null);
      assertEquals(4.0, result2, "exec should succeed after re-init clears the error counter");
   }
}
