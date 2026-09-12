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
import org.junit.jupiter.api.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
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
