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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class GraalJavaScriptEngineTest {
   private GraalJavaScriptEngine engine;

   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
   }

   @AfterEach void teardown() { engine.close(); }

   @Test void compileAndExecArithmetic() throws Exception {
      Object src = engine.compile("2 + 3");
      assertEquals(5.0, engine.exec(src, null, null));
   }

   @Test void compileAndExecString() throws Exception {
      Object src = engine.compile("'a' + 'b'");
      assertEquals("ab", engine.exec(src, null, null));
   }

   @Test void checkFunctionRejectsBadSyntax() {
      assertThrows(Exception.class, () -> engine.checkFunction("f", "function {{"));
   }

   @Test void checkFunctionAcceptsGoodSyntax() throws Exception {
      engine.checkFunction("f", "function f(x){ return x*2; }");
   }
}
