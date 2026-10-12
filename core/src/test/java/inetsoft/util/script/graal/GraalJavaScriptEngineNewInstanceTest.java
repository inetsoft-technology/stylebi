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

import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the newInstance() script global honors the script class filter
 * (Bug #77420), and that the check runs before the class is loaded.
 */
@Tag("core")
class GraalJavaScriptEngineNewInstanceTest {
   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
      sentinelInitialized = false;
      sentinelConstructed = false;
   }

   @AfterEach
   void teardown() {
      engine.close();
   }

   private Object eval(String src) throws Exception {
      Object s = engine.compile(src);
      return engine.exec(s, null, null);
   }

   /**
    * Asserts the class is refused both from script and by a direct call. The
    * engine rethrows script errors without their cause (#75555) and
    * ScriptFunction rewraps host exceptions, so the SecurityException from the
    * class filter is only checked on the direct call.
    */
   private void assertRejected(String cls) {
      ScriptException ex = assertThrows(ScriptException.class,
                                        () -> eval("newInstance('" + cls + "')"));
      assertTrue(ex.getMessage().contains("newInstance"), ex::getMessage);

      SecurityException se = assertThrows(SecurityException.class,
                                          () -> JavaScriptEngine.newInstance(cls));
      assertTrue(se.getMessage().contains(cls), se::getMessage);
   }

   @Test
   void blockedJdkClassIsRejected() {
      assertFalse(ScriptHostAccess.classFilter().test("java.util.concurrent.ConcurrentHashMap"));
      assertRejected("java.util.concurrent.ConcurrentHashMap");
   }

   @Test
   void blockedSecurityClassIsRejected() {
      assertFalse(ScriptHostAccess.classFilter()
                     .test("inetsoft.sree.security.VirtualAuthorizationProvider"));
      assertRejected("inetsoft.sree.security.VirtualAuthorizationProvider");
   }

   @Test
   void blockedClassIsNotInitialized() {
      String cls = Sentinel.class.getName();
      assertFalse(ScriptHostAccess.classFilter().test(cls));
      assertRejected(cls);
      assertFalse(sentinelInitialized, "static initializer of a rejected class ran");
      assertFalse(sentinelConstructed, "constructor of a rejected class ran");
   }

   @Test
   void allowedClassIsConstructed() throws Exception {
      Object result = eval("var l = newInstance('java.util.ArrayList'); l.add('a'); l.size()");
      assertInstanceOf(Number.class, result);
      assertEquals(1, ((Number) result).intValue());
   }

   @Test
   void blankClassNameIsRejected() {
      assertThrows(IllegalArgumentException.class, () -> JavaScriptEngine.newInstance(null));
      assertThrows(IllegalArgumentException.class, () -> JavaScriptEngine.newInstance(" "));
   }

   static volatile boolean sentinelInitialized;
   static volatile boolean sentinelConstructed;

   /**
    * Lives in inetsoft.util.script.graal, which the class filter blocks.
    */
   public static class Sentinel {
      static {
         sentinelInitialized = true;
      }

      public Sentinel() {
         sentinelConstructed = true;
      }
   }
}
