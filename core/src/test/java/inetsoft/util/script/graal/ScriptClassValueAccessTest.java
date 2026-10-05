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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77521: a class value that reaches a script gives no access to a class the
 * class filter refuses by exact name, and the engine's own conversion hands out
 * class values only for classes a script could look up by name.
 */
@Tag("core")
class ScriptClassValueAccessTest {
   private Context context;

   @BeforeEach
   void setup() {
      Predicate<String> filter = ScriptHostAccess.classFilter(Set.of(), new String[0], false);
      context = Context.newBuilder("js")
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(filter)
         .allowIO(false)
         .allowCreateThread(false)
         .allowNativeAccess(false)
         .allowCreateProcess(false)
         .build();
      ScriptHostAccess.installTypeLookupCheck(context, filter);
      // hands out a raw class value, as a Java method a script calls would return it
      context.getBindings("js").putMember("classOf", (ProxyExecutable) args -> {
         try {
            return Class.forName(args[0].asString());
         }
         catch(ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
         }
      });
   }

   @AfterEach
   void teardown() {
      context.close(true);
   }

   @Test
   void blockedClassesAreDeniedByType() throws Exception {
      Method allows = HostAccess.class.getDeclaredMethod("allowsAccess", AnnotatedElement.class);
      allows.setAccessible(true);
      HostAccess hostAccess = ScriptHostAccess.hostAccess();
      List<String> open = new ArrayList<>();
      int loaded = 0;

      for(String name : ScriptHostAccess.blockedClasses()) {
         Class<?> type;

         try {
            type = Class.forName(name, false, getClass().getClassLoader());
         }
         catch(ClassNotFoundException ex) {
            continue;
         }

         loaded++;
         List<Member> members = new ArrayList<>();
         members.addAll(Arrays.asList(type.getDeclaredMethods()));
         members.addAll(Arrays.asList(type.getDeclaredFields()));
         members.addAll(Arrays.asList(type.getDeclaredConstructors()));

         for(Member member : members) {
            if(Modifier.isPublic(member.getModifiers()) &&
               (boolean) allows.invoke(hostAccess, member))
            {
               open.add(name + "." + member.getName());
            }
         }
      }

      assertTrue(loaded > 30, "most blocked classes are on the class path: " + loaded);
      assertEquals(List.of(), open, "members of name-blocked classes allowed by HostAccess");
   }

   @Test
   void classValueOfBlockedClassGivesNoStaticAccess() {
      assertEquals("undefined", evalString(
         "typeof classOf('inetsoft.util.Plugins').static.getInstance"));
      assertEquals("undefined", evalString(
         "typeof classOf('java.util.ServiceLoader').static.load"));
      assertEquals("undefined", evalString(
         "typeof classOf('inetsoft.util.script.FormulaContext').static.getTable"));
   }

   @Test
   void classValueOfBlockedClassCannotConstruct() {
      assertThrows(PolyglotException.class, () -> eval("new (classOf('java.io.File'))('x')"));
      assertThrows(PolyglotException.class,
                   () -> eval("new (classOf('java.io.File').static)('x')"));
   }

   @Test
   void classValueOfAllowedClassStillWorks() {
      assertEquals("x", evalString("new (classOf('java.lang.StringBuilder'))('x').toString()"));
      assertEquals(3, eval("classOf('java.lang.Math').static.max(2, 3)").asInt());
   }

   @Test
   void conversionRefusesClassValueOfRefusedClass() {
      assertThrows(SecurityException.class,
                   () -> ScriptValueConverter.toGuest(inetsoft.util.Plugins.class));
      assertThrows(SecurityException.class,
                   () -> ScriptValueConverter.toGuest(java.io.File.class));
      // com./org. classes are refused unless javascript.java.com_org is on
      assertThrows(SecurityException.class,
                   () -> ScriptValueConverter.toGuest(org.apache.commons.lang3.StringUtils.class));
      assertThrows(SecurityException.class,
                   () -> ScriptValueConverter.toGuest(new Class<?>[] { String.class, java.io.File.class }));
      assertThrows(SecurityException.class,
                   () -> ScriptValueConverter.toGuest(java.io.File[].class));
   }

   @Test
   void conversionPassesClassValueOfVisibleClass() {
      for(Class<?> type : new Class<?>[] {
         String.class, Integer.class, Double.class, java.util.Date.class,
         java.sql.Timestamp.class, int.class, Integer.TYPE, String[].class, int[].class })
      {
         assertSame(type, ScriptValueConverter.toGuest(type), type.getName());
      }

      Class<?>[] types = { String.class, null, Integer.class };
      assertSame(types, ScriptValueConverter.toGuest(types));
   }

   @Test
   void scopeMemberHoldingRefusedClassValueIsRefused() {
      ScriptScope scope = new ScriptScope() {
         @Override
         public Object getMember(String name) {
            return "type".equals(name) ? inetsoft.util.Plugins.class : String.class;
         }

         @Override
         public boolean hasMember(String name) {
            return "type".equals(name) || "ok".equals(name);
         }

         @Override
         public void putMember(String name, Object value) {
         }

         @Override
         public Object[] getMemberKeys() {
            return new Object[] { "type", "ok" };
         }
      };
      context.getBindings("js").putMember("scope", new ScopeProxy(scope));

      assertThrows(PolyglotException.class, () -> eval("scope.type"));
      assertEquals("java.lang.String", evalString("Java.typeName(scope.ok.static)"));
   }

   private Value eval(String script) {
      return context.eval("js", script);
   }

   private String evalString(String script) {
      return eval(script).asString();
   }
}
