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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76780: Rhino handed script a wrapped {@code java.lang.String}, so Java
 * String methods resolved on it; GraalJS surfaces a host String as a guest
 * string, where they are absent. A dashboard onLoad script that called one of
 * them threw, and because the export path swallows an onLoad failure the result
 * was a valid PDF of unfiltered data on a runaway page.
 */
@Tag("core")
class LegacyStringCompatTest {
   static class MapScope implements ScriptScope {
      final Map<String, Object> m = new LinkedHashMap<>();
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
   }

   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach
   void teardown() {
      engine.close();
   }

   private Object eval(String expr) throws Exception {
      MapScope scope = new MapScope();
      // exactly what CalendarVSAScriptable.getSelectedObjectsArray() hands script
      scope.putMember("selected", new String[]{ "m2020-7", "m2020-8" });
      scope.putMember("sb", new StringBuilder("abcd"));
      return engine.exec(engine.compile(expr), scope, scope);
   }

   private static int num(Object o) {
      return (int) ((Number) o).doubleValue();
   }

   // The expression from the three Protecht dashboards' onLoad script that
   // regressed. It must produce the date string the rest of the script parses.
   @Test
   void javaLengthCallOnHostStringElementWorks() throws Exception {
      assertEquals("2020-7", eval("selected[0].substring(1, selected[0].length())"));
   }

   @Test
   void javaLengthCallReturnsTheCharacterCount() throws Exception {
      assertEquals(7, num(eval("selected[0].length()")));
   }

   // The rewrite must not disturb the JS property; both spellings now work.
   @Test
   void jsLengthPropertyStillReturnsTheNumber() throws Exception {
      assertEquals(7, num(eval("selected[0].length")));
      assertEquals(3, num(eval("'abc'.length")));
      assertEquals(2, num(eval("[1,2].length")));
   }

   // A host CharSequence is the one receiver for which `.length()` already
   // resolved to a real Java method. Rewriting to a bare `.length` would have
   // yielded the method object instead of the count, so the rewrite goes through
   // a helper that handles both; pin that it still returns the length.
   @Test
   void javaLengthCallOnHostCharSequenceStillWorks() throws Exception {
      assertEquals(4, num(eval("sb.length()")));
   }

   @Test
   void javaLengthCallToleratesWhitespace() throws Exception {
      assertEquals(7, num(eval("selected[0].length ( )")));
   }

   // The rewrite is token-aware: an occurrence inside a literal or a comment is
   // source text, not a call site.
   @Test
   void occurrencesInsideLiteralsAndCommentsAreNotRewritten() throws Exception {
      assertEquals("call .length() on it", eval("'call .length() on it'"));
      assertEquals("call .length() on it", eval("\"call .length() on it\""));
      assertEquals(1, num(eval("/* use .length() here */ 1")));
      assertEquals(1, num(eval("1 // .length()")));
   }

   // An identifier that merely ends in "length" is a different name.
   @Test
   void similarlyNamedMembersAreNotRewritten() throws Exception {
      assertEquals(5, num(eval("({ xlength: function(){ return 5; } }).xlength()")));
      assertEquals(6, num(eval("({ lengths: function(){ return 6; } }).lengths()")));
   }

   // length() takes no arguments in Java; a same-named call that does is something
   // else and must be left alone.
   @Test
   void lengthCallWithArgumentsIsNotRewritten() throws Exception {
      assertEquals(9, num(eval("({ length: function(n){ return n; } }).length(9)")));
   }

   @Test
   void javaStringMethodsAbsentFromJsAreRestored() throws Exception {
      assertEquals(Boolean.TRUE, eval("selected[0].equals('m2020-7')"));
      assertEquals(Boolean.FALSE, eval("selected[0].equals('other')"));
      assertEquals(Boolean.FALSE, eval("selected[0].equals(null)"));
      assertEquals(Boolean.TRUE, eval("selected[0].equalsIgnoreCase('M2020-7')"));
      assertEquals(Boolean.TRUE, eval("selected[0].contains('2020')"));
      assertEquals(Boolean.FALSE, eval("selected[0].isEmpty()"));
      assertEquals(Boolean.TRUE, eval("''.isEmpty()"));
      assertEquals(Boolean.TRUE, eval("'  '.isBlank()"));
      assertEquals("a-bXc", eval("'aXbXc'.replaceFirst('X', '-')"));
      assertEquals("202", eval("selected[0].subSequence(1, 4)"));
   }

   // java.lang.String.compareTo returns a magnitude, not just a sign, and scripts
   // have been seen to use the number itself.
   @Test
   void compareToMatchesJavaSemantics() throws Exception {
      assertEquals("b".compareTo("a"), num(eval("'b'.compareTo('a')")));
      assertEquals("abc".compareTo("abcd"), num(eval("'abc'.compareTo('abcd')")));
      assertEquals(0, num(eval("'abc'.compareTo('abc')")));
      assertEquals(0, num(eval("'ABC'.compareToIgnoreCase('abc')")));
   }

   // Java's matches() anchors the whole string; JS RegExp.test() does not.
   @Test
   void matchesAnchorsTheWholeStringLikeJava() throws Exception {
      assertEquals(Boolean.TRUE, eval("selected[0].matches('m2020-.')"));
      assertEquals(Boolean.FALSE, eval("selected[0].matches('2020')"));
   }

   // Built-ins must keep JS semantics: replace() replaces only the first
   // occurrence of a literal and replaceAll() takes a literal, both unlike Java.
   // Redefining them would break scripts that work today, so they are left alone.
   @Test
   void jsBuiltInsAreNotShadowed() throws Exception {
      assertEquals("a-bXc", eval("'aXbXc'.replace('X', '-')"));
      assertEquals("a-b-c", eval("'aXbXc'.replaceAll('X', '-')"));
      assertEquals(Boolean.TRUE, eval("'abc'.includes('b')"));
      assertEquals("bc", eval("'abc'.substring(1)"));
      assertEquals(Boolean.TRUE, eval("'abc'.startsWith('a')"));
   }

   // The compatibility members must not turn up in a for...in over a string: the
   // enumeration must still yield only the character indices it always did.
   @Test
   void compatibilityMembersAreNotEnumerable() throws Exception {
      assertEquals("01", eval("var k = ''; for(var p in 'ab') { k += p; } k"));
   }

   // The whole onLoad fragment the three dashboards share, end to end.
   @Test
   void protechtOnLoadFragmentEvaluates() throws Exception {
      Object result = eval(
         "var date1 = selected[0].substring(1, selected[0].length());\n" +
         "var strings1 = date1.split('-', 2);\n" +
         "'' + parseInt(strings1[0]) + '|' + parseInt(strings1[1])");
      assertEquals("2020|7", result);
   }
}
