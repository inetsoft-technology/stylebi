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

import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77249: a this-free body that the #75688 completion split cuts into pieces compiles to
 * parsed-once pieces, not to a per-exec direct eval of each piece.
 */
@Tag("core")
class GraalJavaScriptEnginePieceScriptTest {
   @BeforeEach void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach void teardown() {
      engine.close();
   }

   @Test void theIssueFormulasCompileToPieces() throws Exception {
      for(String f : new String[] {
         "var v = field['value']; if(v > 100) { 0 } else { v + 1 }",
         "var acc = (acc || 0) + field['value']; if(acc < 0) { acc = 0; } acc",
         "if(price > 500) { [237,211,237] } if(row > 1) { 1 }" })
      {
         Object script = engine.compile(f);
         assertInstanceOf(GraalJavaScriptEngine.PieceScript.class, script, f);

         for(Source piece : ((GraalJavaScriptEngine.PieceScript) script).pieces()) {
            assertFalse(piece.getCharacters().toString().contains("eval("), f);
         }
      }
   }

   @Test void aThisBodyKeepsTheEvalWrapper() throws Exception {
      Object script = engine.compile("var a = this.x; if(a) { a }");
      assertInstanceOf(Source.class, script);
      assertTrue(((Source) script).getCharacters().toString().contains("eval("));
   }

   @Test void theLastNonUndefinedPieceIsTheResult() throws Exception {
      assertEquals(10.0, run("var p = 10; if(p > 5) { p } if(p > 100) { 0 }"));
      assertEquals(1.0, run("1; if(false) {}"));
      // null is a value, as `!== undefined` in the eval wrapper
      assertNull(run("null; if(false) {}"));
      assertEquals(2.0, run("if(false) {1} else {3} if(true) {2}"));
      assertNull(run("if(false) {1} if(false) {2}"));
   }

   @Test void aVarOfAPieceIsVisibleToTheNextAndToLaterScripts() throws Exception {
      assertEquals(6.0, run("var k77249 = 3; if(k77249) { k77249 * 2 } var q77249 = 1;"));
      assertEquals("number:3:1", run("typeof k77249 + ':' + k77249 + ':' + q77249"));
      assertEquals(7.0, run("function g77249(){ return 7 } if(true) { g77249() }"));
      assertEquals(7.0, run("g77249()"));
   }

   // a var of a split formula starts undefined on every run, as with the eval wrapper
   @Test void aVarStartsUndefinedOnEveryRun() throws Exception {
      Object pieces = engine.compile("var s77249 = (s77249 || 0) + 1; if(false) {} s77249");
      MapScope scope = new MapScope();

      for(int i = 1; i <= 3; i++) {
         assertEquals(1.0, num(engine.exec(pieces, scope, scope)), "run " + i);
      }
   }

   // a viewsheet runs its scripts on one shared Context: once the condition turns
   // false, the var must not keep the value of the run where it was true
   @Test void aConditionallyAssignedVarDoesNotKeepTheOldValue() throws Exception {
      Object script = engine.compile(
         "var msg77249; if(sel) { msg77249 = 'filtered' } msg77249");
      MapScope scope = new MapScope();

      scope.putMember("sel", true);
      assertEquals("filtered", engine.exec(script, scope, scope));
      scope.putMember("sel", false);
      assertNull(engine.exec(script, scope, scope));
      assertNull(engine.exec(script, scope, scope));
   }

   // a per-cell color formula: the color of one cell must not leak to the next
   @Test void aCellColorDoesNotLeakToTheNextCell() throws Exception {
      Object script = engine.compile(
         "var col77249; if(value > 100) { col77249 = [255,0,0] } col77249");
      MapScope cell1 = new MapScope();
      cell1.putMember("value", 500);
      MapScope cell2 = new MapScope();
      cell2.putMember("value", 5);

      Object color = engine.exec(script, cell1, cell1);
      assertArrayEquals(new Object[] { 255.0, 0.0, 0.0 }, nums((Object[]) color));
      assertNull(engine.exec(script, cell2, cell2));
   }

   // a var in a block of a later piece is reset too (var is function scoped)
   @Test void aVarInABlockOfALaterPieceIsReset() throws Exception {
      Object script = engine.compile(
         "var a = 1; if(flag) { var b77249 = 'set' } if(a) { b77249 }");
      MapScope scope = new MapScope();

      scope.putMember("flag", true);
      assertEquals("set", engine.exec(script, scope, scope));
      scope.putMember("flag", false);
      assertNull(engine.exec(script, scope, scope));
   }

   // a split formula that redeclares an onInit var without assigning it reads undefined,
   // as with the eval wrapper (whose var shadowed the global)
   @Test void aRedeclaredVarOfAnotherScriptReadsUndefined() throws Exception {
      run("var total77249 = 5");
      assertNull(run("var total77249; if(false) { total77249 = 1 } total77249"));
   }

   // the reset writes the global, never a same-named scope member
   @Test void theResetDoesNotTouchTheScope() throws Exception {
      Object script = engine.compile("var own77249 = own77249 + 1; if(false) {} own77249");
      MapScope scope = new MapScope();
      scope.putMember("own77249", 1.0);

      assertEquals(2.0, num(engine.exec(script, scope, scope)));
      assertEquals(3.0, num(engine.exec(script, scope, scope)));
      assertEquals(3.0, num(scope.getMember("own77249")));
   }

   // pieces are padded with line breaks only, and only the first piece has the reset
   @Test void piecesArePaddedWithLineBreaksOnly() throws Exception {
      Source[] pieces = ((GraalJavaScriptEngine.PieceScript)
         engine.compile("x77249 = 1;   if(x77249) { 2 }\n  if(false) { 3 }")).pieces();
      assertEquals(3, pieces.length);
      assertEquals("with(__scope__){x77249 = 1;   \n}", pieces[0].getCharacters().toString());
      assertEquals("with(__scope__){if(x77249) { 2 }\n  \n}",
                   pieces[1].getCharacters().toString());
      assertEquals("with(__scope__){\nif(false) { 3 }\n}", pieces[2].getCharacters().toString());

      Source[] reset = ((GraalJavaScriptEngine.PieceScript)
         engine.compile("var y77249 = 1; if(y77249) { 2 }")).pieces();
      assertTrue(reset[0].getCharacters().toString().contains("y77249=void 0;"));
      assertTrue(reset[1].getCharacters().toString().startsWith("with(__scope__){"));
   }

   // two compiles of one formula are equal, so a recompile keeps the error count
   @Test void aRecompiledFormulaKeepsItsErrorCount() throws Exception {
      String f = "var e = 1; if(e) { undefinedFnE77249() }";
      Object first = engine.compile(f);
      Object second = engine.compile(f);
      assertEquals(first, second);
      assertEquals(first.hashCode(), second.hashCode());
      assertNotEquals(first, engine.compile("var e = 2; if(e) { undefinedFnE77249() }"));

      MapScope scope = new MapScope();
      assertThrows(Exception.class, () -> engine.exec(first, scope, scope));
      assertEquals(1, engine.errorCounts().get(second));
   }

   // #77181: a let without initializer starts undefined on every run, in any piece
   @Test void anInitializerlessLetIsResetEveryRun() throws Exception {
      Object script = engine.compile("var x = 1; let r77249; if(x < 0) { r77249 = 1 } r77249");
      run("r77249 = 5");
      assertNull(engine.exec(script, new MapScope(), null));
      run("r77249 = 6");
      assertNull(engine.exec(script, new MapScope(), null));
   }

   @Test void aClassStaysInItsPiece() throws Exception {
      assertEquals("undefined", run("class K77249 {} if(true) { typeof K77249 }"));
      assertEquals("undefined", run("typeof K77249"));
   }

   @Test void aThrowingPieceSkipsTheRest() throws Exception {
      assertThrows(Exception.class,
                   () -> run("side77249 = 1; undefinedFn77249(); if(true) { side77249 = 2 }"));
      assertEquals(1.0, run("side77249"));
   }

   // error lines count as on the plain path: a body line n reports line n (#77322)
   @Test void errorLinesMatchThePlainPath() {
      String piecesMsg = assertThrows(Exception.class,
         () -> run("var a = 1;\nif(a) {\n  undefinedFnP77249() }")).getMessage();
      String plainMsg = assertThrows(Exception.class,
         () -> run("var a = 1;\n{\n  undefinedFnQ77249() }")).getMessage();
      assertTrue(piecesMsg.contains("(line 3)"), piecesMsg);
      assertTrue(plainMsg.contains("(line 3)"), plainMsg);

      String crlf = assertThrows(Exception.class,
         () -> run("var a = 1;\r\nif(a) { 1 }\r\n\r\nif(a) { undefinedFnR77249() }")).getMessage();
      assertTrue(crlf.contains("(line 4)"), crlf);
   }

   // the compiled form holds no Value of the Context that compiled it
   @Test void piecesRunOnAnotherEngine() throws Exception {
      Object script = engine.compile("var v = value; if(v > 100) { 0 } else { v + 1 }");
      GraalJavaScriptEngine other = new GraalJavaScriptEngine();
      other.init(new HashMap<>());

      try {
         MapScope scope = new MapScope();
         scope.putMember("value", 4);
         assertEquals(5.0, num(other.exec(script, scope, scope)));
         assertEquals(5.0, num(engine.exec(script, scope, scope)));
      }
      finally {
         other.close();
      }
   }

   private Object run(String script) throws Exception {
      MapScope scope = new MapScope();
      Object v = engine.exec(engine.compile(script), scope, scope);
      return v instanceof Number n ? n.doubleValue() : v;
   }

   private static double num(Object o) {
      return ((Number) o).doubleValue();
   }

   private static Object[] nums(Object[] a) {
      Object[] r = new Object[a.length];

      for(int i = 0; i < a.length; i++) {
         r[i] = num(a[i]);
      }

      return r;
   }

   private static final class MapScope implements ScriptScope {
      public Object getMember(String n) { return m.get(n); }
      public boolean hasMember(String n) { return m.containsKey(n); }
      public void putMember(String n, Object v) { m.put(n, v); }
      public Object[] getMemberKeys() { return m.keySet().toArray(); }
      private final Map<String, Object> m = new HashMap<>();
   }

   private GraalJavaScriptEngine engine;
}
