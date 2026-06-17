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

   @Test void nanBecomesZero() {
      assertEquals(0.0, (Double) toHost("0/0"), 0.0);
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
}
