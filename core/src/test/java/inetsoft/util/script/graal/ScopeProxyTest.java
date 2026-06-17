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
class ScopeProxyTest {
   /** Minimal map-backed ScriptScope for tests. */
   static class MapScope implements ScriptScope {
      final Map<String, Object> m = new LinkedHashMap<>();
      public Object getMember(String name) { return m.get(name); }
      public boolean hasMember(String name) { return m.containsKey(name); }
      public void putMember(String name, Object v) { m.put(name, v); }
      public void removeMember(String name) { m.remove(name); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
   }

   private Context ctx;

   @BeforeEach void setup() { ctx = Context.create("js"); }
   @AfterEach void teardown() { ctx.close(); }

   @Test void scriptReadsMember() {
      MapScope s = new MapScope();
      s.putMember("answer", 42);
      ctx.getBindings("js").putMember("obj", new ScopeProxy(s));
      assertEquals(42, ctx.eval("js", "obj.answer").asInt());
   }

   @Test void scriptWritesMember() {
      MapScope s = new MapScope();
      ctx.getBindings("js").putMember("obj", new ScopeProxy(s));
      ctx.eval("js", "obj.created = 'x'");
      assertEquals("x", s.getMember("created"));
   }

   @Test void hasMemberReflectsScope() {
      MapScope s = new MapScope();
      s.putMember("present", 1);
      ScopeProxy p = new ScopeProxy(s);
      assertTrue(p.hasMember("present"));
      assertFalse(p.hasMember("absent"));
   }

   @Test void memberKeysListed() {
      MapScope s = new MapScope();
      s.putMember("a", 1);
      s.putMember("b", 2);
      Object keys = new ScopeProxy(s).getMemberKeys();
      assertArrayEquals(new Object[]{"a", "b"}, (Object[]) keys);
   }
}
