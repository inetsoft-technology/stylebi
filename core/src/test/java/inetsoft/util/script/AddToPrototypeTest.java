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
package inetsoft.util.script;

import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for {@link JavaScriptEngine#addToPrototype}. This is the
 * method that chains a viewsheet scope to its worksheet {@code AssetQueryScope}
 * (and vice versa) so that worksheet table names resolve in viewsheet scripts.
 * It was reduced to a no-op during the GraalJS migration, breaking that
 * resolution (Bug #75526).
 */
@Tag("core")
class AddToPrototypeTest {
   /** Minimal dynamic scope with a settable parent link. */
   static class ChainScope implements DynamicScope {
      private ScriptScope parent;
      public Object getMember(String n) { return null; }
      public boolean hasMember(String n) { return false; }
      public void putMember(String n, Object v) { }
      public Object[] getMemberKeys() { return new Object[0]; }
      public ScriptScope getParentScope() { return parent; }
      public void setParentScope(ScriptScope p) { this.parent = p; }
   }

   @Test void appendsParentWhenChainEmpty() {
      ChainScope a = new ChainScope();
      ChainScope b = new ChainScope();

      JavaScriptEngine.addToPrototype(a, b);

      assertSame(b, a.getParentScope());
   }

   @Test void appendsAtEndOfExistingChain() {
      ChainScope a = new ChainScope();
      ChainScope b = new ChainScope();
      ChainScope c = new ChainScope();
      a.setParentScope(b);

      JavaScriptEngine.addToPrototype(a, c);

      // c is appended at the end of the chain, not in front of b
      assertSame(b, a.getParentScope());
      assertSame(c, b.getParentScope());
   }

   @Test void doesNothingWhenAlreadyPresent() {
      ChainScope a = new ChainScope();
      ChainScope b = new ChainScope();
      a.setParentScope(b);

      JavaScriptEngine.addToPrototype(a, b);

      assertSame(b, a.getParentScope());
      assertNull(b.getParentScope());
   }

   @Test void doesNothingWhenScopeEqualsObject() {
      ChainScope a = new ChainScope();

      JavaScriptEngine.addToPrototype(a, a);

      assertNull(a.getParentScope());
   }

   @Test void doesNotFormCycle() {
      ChainScope a = new ChainScope();
      ChainScope b = new ChainScope();
      // b already leads back to a; appending a -> b would close a loop
      b.setParentScope(a);

      JavaScriptEngine.addToPrototype(a, b);

      assertNull(a.getParentScope());
      assertSame(a, b.getParentScope());
   }

   @Test void ignoresNonScriptScopeArguments() {
      ChainScope a = new ChainScope();

      assertDoesNotThrow(() -> JavaScriptEngine.addToPrototype("not a scope", a));
      assertDoesNotThrow(() -> JavaScriptEngine.addToPrototype(a, "not a scope"));
      assertNull(a.getParentScope());
   }
}
