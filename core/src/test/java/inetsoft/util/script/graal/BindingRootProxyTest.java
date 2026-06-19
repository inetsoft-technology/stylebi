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

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class BindingRootProxyTest {
   static class MapScope implements ScriptScope {
      final Map<String, Object> m = new LinkedHashMap<>();
      private final ScriptScope parent;
      MapScope(ScriptScope parent) { this.parent = parent; }
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
      public ScriptScope getParentScope() { return parent; }
   }

   private Context ctx;
   private ScriptScope[] execScope = new ScriptScope[1];

   @BeforeEach void setup() { ctx = Context.create("js"); }
   @AfterEach void teardown() { ctx.close(); }

   /** Bind the proxy as __scope__ and eval the expression inside a with-block. */
   private int evalWith(ScriptScope global, String expr) {
      BindingRootProxy root = new BindingRootProxy(global, () -> execScope[0]);
      ctx.getBindings("js").putMember("__scope__", root);
      return ctx.eval("js", "with(__scope__){ " + expr + " }").asInt();
   }

   @Test void resolvesFromGlobal() {
      MapScope global = new MapScope(null);
      global.putMember("g", 7);
      assertEquals(7, evalWith(global, "g"));
   }

   @Test void resolvesFromExecScopeWhenMissingInGlobal() {
      MapScope global = new MapScope(null);
      MapScope exec = new MapScope(null);
      exec.putMember("e", 9);
      execScope[0] = exec;
      assertEquals(9, evalWith(global, "e"));
   }

   @Test void resolvesDynamicallyOnMiss() {
      // exec scope value injected AFTER the proxy is bound -> must resolve live
      MapScope global = new MapScope(null);
      MapScope exec = new MapScope(null);
      execScope[0] = exec;
      BindingRootProxy root = new BindingRootProxy(global, () -> execScope[0]);
      ctx.getBindings("js").putMember("__scope__", root);
      exec.putMember("late", 42);   // added after binding
      assertEquals(43, ctx.eval("js", "with(__scope__){ late + 1 }").asInt());
   }

   @Test void walksParentChain() {
      MapScope grandparent = new MapScope(null);
      grandparent.putMember("gp", 5);
      MapScope global = new MapScope(grandparent);
      assertEquals(5, evalWith(global, "gp"));
   }

   // Rhino scope-chain write semantics: an unqualified assignment to a name that
   // lives in a PARENT scope must write back to that parent, not shadow it on
   // the root. (Regression guard for the BindingRootProxy.putMember fix.)
   @Test void unqualifiedWriteGoesToOwningParentScope() {
      MapScope parent = new MapScope(null);
      parent.putMember("pv", "orig");
      MapScope global = new MapScope(parent);   // root whose parent owns "pv"
      BindingRootProxy root = new BindingRootProxy(global, () -> null);
      ctx.getBindings("js").putMember("__scope__", root);

      ctx.eval("js", "with(__scope__){ pv = 'updated'; }");

      assertEquals("updated", parent.getMember("pv"));   // parent updated
      assertNull(global.getMember("pv"));                // no stale shadow on root
   }
}
