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

import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.*;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82259: a script constructor call that binds a {@code String...}
 * constructor only through a lenient String mapping ({@code new TimeScale(date,
 * 45000)}) must not make later, unrelated constructions of the same class
 * ({@code new TimeScale(date1, date2)}) reuse that varargs constructor.
 *
 * <p>Each test runs on its own fresh polyglot {@link Engine}: the shared
 * engine's constructor node may already be past Truffle's overload cache
 * limit because of earlier test classes in the same JVM, which would make the
 * test pass without the fix. A failure here after a GraalVM upgrade can also
 * mean truffle's {@code HostExecuteNode.LIMIT} outgrew the warm-up of
 * {@link JavaClassProxy#forceUncachedConstructorSelection}.
 */
@Tag("core")
class JavaClassProxyOverloadCacheTest {
   private static final String DATES =
      "new TimeScale(new Date(2024,2,5), new Date(45000)).getMax()";
   private static final String DATE_NUMBER =
      "new TimeScale(new Date(2024,2,5), 45000).getMax()";

   private Engine polyglotEngine;
   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      polyglotEngine = Engine.newBuilder()
         .allowExperimentalOptions(true)
         .option("engine.WarnInterpreterOnly", "false")
         .build();
      engine = new FreshEngineScriptEngine(polyglotEngine);
      engine.init(new HashMap<>());
   }

   @AfterEach
   void teardown() {
      try {
         engine.close();
      }
      finally {
         polyglotEngine.close();
      }
   }

   @Test
   void varargsTimeScaleCallDoesNotRebindDateConstructor() throws Exception {
      assertTrue(JavaClassProxy.isUncachedConstructorSelection(polyglotEngine),
                 "the constructor overload warm-up did not complete");

      assertEquals(45000.0, num(eval(DATES)), "first (Date, Date)");
      // binds TimeScale(String...) through the Number -> String mapping (#82139)
      assertEquals(0.0, num(eval(DATE_NUMBER)), "(Date, Number) binds String...");
      assertEquals(45000.0, num(eval(DATES)), "(Date, Date) after the varargs call");
      assertEquals(num(eval("new Date(2024,2,6).getTime()")),
                   num(eval("new TimeScale(new Date(2024,2,5), new Date(2024,2,6)).getMax()")),
                   "other (Date, Date) after the varargs call");
      // the legacy shim reaches the same shared constructor node
      assertEquals(45000.0, num(eval(
         "new inetsoft.graph.scale.TimeScale(new Date(2024,2,5), new Date(45000)).getMax()")),
                   "legacy shim (Date, Date) after the varargs call");
      // Rhino's no-new form shares the proxy's construction path
      assertEquals(45000.0, num(eval(
         "TimeScale(new Date(2024,2,5), new Date(45000)).getMax()")),
                   "no-new (Date, Date) after the varargs call");
   }

   @Test
   void varargsLinearScaleCallDoesNotRebindDoubleConstructor() throws Exception {
      assertEquals(100.0, num(eval("new LinearScale(0, 100).getMax()")), "first (0, 100)");
      // binds LinearScale(String...) through the Number -> String mapping (#76778)
      assertEquals("[Sales, 5]", String.valueOf(eval(
         "java.util.Arrays.toString(new LinearScale('Sales', 5).getFields())")),
                   "('Sales', 5) binds String...");
      assertEquals(100.0, num(eval("new LinearScale(0, 100).getMax()")),
                   "(0, 100) after the varargs call");
      assertEquals("[]", String.valueOf(eval(
         "java.util.Arrays.toString(new LinearScale(0, 100).getFields())")),
                   "(0, 100) after the varargs call must not bind String...");
   }

   private Object eval(String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   private static double num(Object value) {
      assertNotNull(value);
      return value instanceof Number ? ((Number) value).doubleValue()
         : Double.parseDouble(value.toString());
   }

   /**
    * A script engine whose Contexts use the given polyglot Engine instead of
    * the JVM-wide shared one.
    */
   private static final class FreshEngineScriptEngine extends GraalJavaScriptEngine {
      FreshEngineScriptEngine(Engine polyglotEngine) {
         this.polyglotEngine = polyglotEngine;
      }

      @Override
      protected Engine polyglotEngine() {
         return polyglotEngine;
      }

      private final Engine polyglotEngine;
   }
}
