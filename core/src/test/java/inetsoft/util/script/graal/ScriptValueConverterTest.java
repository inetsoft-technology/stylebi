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
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptValueConverterTest {
   private Context ctx;

   @BeforeEach void setup() { ctx = Context.create("js"); }
   @AfterEach void teardown() { ctx.close(); }

   private Object toHost(String js) {
      return ScriptValueConverter.toHost(ctx.eval("js", js));
   }

   @Test void numberBecomesDouble() {
      Object v = toHost("40 + 2");
      assertInstanceOf(Double.class, v);
      assertEquals(42.0, (Double) v, 0.0);
   }

   @Test void nanBecomesNull() {
      assertNull(toHost("0/0"));
   }

   @Test void infinityBecomesNull() {
      assertNull(toHost("1/0"));
   }

   @Test void stringStaysString() {
      assertEquals("hi", toHost("'hi'"));
   }

   @Test void booleanStaysBoolean() {
      assertEquals(Boolean.TRUE, toHost("true"));
   }

   @Test void nullAndUndefinedBecomeNull() {
      assertNull(toHost("null"));
      assertNull(toHost("undefined"));
   }

   @Test void arrayBecomesObjectArray() {
      Object v = toHost("[1, 'a', true]");
      assertInstanceOf(Object[].class, v);
      Object[] arr = (Object[]) v;
      assertEquals(3, arr.length);
      assertEquals(1.0, arr[0]);
      assertEquals("a", arr[1]);
      assertEquals(Boolean.TRUE, arr[2]);
   }

   @Test void jsDateBecomesUtilDate() {
      Object v = toHost("new Date(0)");
      assertInstanceOf(Date.class, v);
      assertEquals(0L, ((Date) v).getTime());
   }

   @Test void hostObjectIsUnwrapped() {
      Date d = new Date(123);
      ctx.getBindings("js").putMember("d", d);
      Object v = ScriptValueConverter.toHost(ctx.getBindings("js").getMember("d"));
      assertSame(d, v);
   }

   // Bug #75549: toHost must be symmetric with toGuest — a ScopeProxy-wrapped
   // ScriptScope round-trips back to the underlying scope, so Java callers that
   // read a published scope global back (e.g. senv.get("viewsheet") in
   // CalcTableLens) still receive the real ScriptScope, not the proxy bridge.
   @Test void scopeProxyUnwrapsToUnderlyingScope() {
      ScriptScope scope = new SimpleScope();
      ctx.getBindings("js").putMember("s", ScriptValueConverter.toGuest(scope));
      Object v = ScriptValueConverter.toHost(ctx.getBindings("js").getMember("s"));
      assertSame(scope, v);
   }

   /** Minimal ScriptScope so toGuest produces a ScopeProxy. */
   private static class SimpleScope implements ScriptScope {
      public Object getMember(String name) { return null; }
      public boolean hasMember(String name) { return false; }
      public void putMember(String name, Object value) { }
      public Object[] getMemberKeys() { return new Object[0]; }
   }
}
