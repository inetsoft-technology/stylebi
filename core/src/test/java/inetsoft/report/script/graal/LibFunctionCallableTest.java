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

package inetsoft.report.script.graal;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that user-defined library script functions registered via
 * {@link LibManager} are installed as callable JS globals at engine init, so
 * that any formula/script can call them by name (Task 5.3a, Feature #75423).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class LibFunctionCallableTest {
   @Test
   void libFunctionIsCallableFromFormula() throws Exception {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("addTwo", "function addTwo(a, b) { return a + b; }");

      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

      try {
         engine.init(new HashMap<>());
         Object src = engine.compile("addTwo(40, 2)");
         Object result = engine.exec(src, null, null);
         assertEquals(42.0, result);
      }
      finally {
         engine.close();
      }
   }

   @Test
   void libFunctionResolvesExecScopeMember() throws Exception {
      // Bug #75525: a library function whose body references an unqualified name
      // (e.g. setActionVisible) provided only by the executing scope must resolve
      // it dynamically (Rhino scope-chain behavior), not throw
      // "setActionVisible is not defined".
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("callsScopeFn",
                    "function callsScopeFn() { setActionVisible('AxisResize'); return 'ok'; }");

      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      final boolean[] called = { false };

      try {
         engine.init(new HashMap<>());

         // scope exposing setActionVisible as a callable member, mirroring how a
         // VSAScriptable exposes assembly script functions to the engine.
         ScriptScope scope = new ScriptScope() {
            private final Object fn = (ProxyExecutable) args -> {
               called[0] = true;
               return null;
            };

            public Object getMember(String n) {
               return "setActionVisible".equals(n) ? fn : null;
            }

            public boolean hasMember(String n) {
               return "setActionVisible".equals(n);
            }

            public void putMember(String n, Object v) {
            }

            public Object[] getMemberKeys() {
               return new Object[] { "setActionVisible" };
            }
         };

         Object src = engine.compile("callsScopeFn()");
         Object result = engine.exec(src, scope, null);
         assertEquals("ok", result);
         assertTrue(called[0],
                    "library function should resolve setActionVisible from the executing scope");
      }
      finally {
         engine.close();
      }
   }

   @Test
   void malformedLibFunctionDoesNotBreakInit() throws Exception {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("goodFunc", "function goodFunc(a) { return a * 10; }");
      mgr.setScript("badFunc", "function bad( {");

      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

      try {
         // a malformed lib function source must not abort engine init
         engine.init(new HashMap<>());

         // other (well-formed) library functions remain callable
         Object src = engine.compile("goodFunc(5)");
         Object result = engine.exec(src, null, null);
         assertEquals(50.0, result);
      }
      finally {
         engine.close();
      }
   }
}
