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
class ArrayProxyTest {
   /** Minimal array-shaped ScriptScope: indexed elements plus a 'length' member. */
   static class ListScope implements ScriptArrayScope {
      final List<Object> elements = new ArrayList<>();
      final Map<String, Object> members = new LinkedHashMap<>();

      public long getArraySize() { return elements.size(); }
      public Object getArrayElement(long index) { return elements.get((int) index); }
      public Object getMember(String name) {
         return "length".equals(name) ? elements.size() : members.get(name);
      }
      public boolean hasMember(String name) {
         return "length".equals(name) || members.containsKey(name);
      }
      public void putMember(String name, Object v) { members.put(name, v); }
      public Object[] getMemberKeys() { return members.keySet().toArray(); }
   }

   private Context ctx;

   @BeforeEach void setup() { ctx = Context.create("js"); }
   @AfterEach void teardown() { ctx.close(); }

   @Test void indexedAccessFromScript() {
      ListScope s = new ListScope();
      s.elements.add("a");
      s.elements.add("b");
      s.elements.add("c");
      ctx.getBindings("js").putMember("arr", new ArrayProxy(s));
      assertEquals("b", ctx.eval("js", "arr[1]").asString());
   }

   @Test void lengthAndIterationFromScript() {
      ListScope s = new ListScope();
      s.elements.add(10);
      s.elements.add(20);
      ctx.getBindings("js").putMember("arr", new ArrayProxy(s));
      // array length reported via ProxyArray.getSize()
      assertEquals(2, ctx.eval("js", "arr.length").asInt());
      // a JS loop over indexed elements works
      assertEquals(30, ctx.eval("js", "arr[0] + arr[1]").asInt());
   }

   @Test void namedMemberStillAccessible() {
      ListScope s = new ListScope();
      s.putMember("label", "myArray");
      ctx.getBindings("js").putMember("arr", new ArrayProxy(s));
      assertEquals("myArray", ctx.eval("js", "arr.label").asString());
   }

   @Test void arrayIsReadOnly() {
      ListScope s = new ListScope();
      s.elements.add("x");
      ArrayProxy p = new ArrayProxy(s);
      assertThrows(UnsupportedOperationException.class,
                   () -> p.set(0, org.graalvm.polyglot.Value.asValue("y")));
   }
}
