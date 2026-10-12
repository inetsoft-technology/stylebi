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

import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
      // each piece runs in the var store of its scope (Bug #77595), on its first line
      String locals = "with(__scope__.__inetsoft_locals__){";
      assertEquals(locals + "with(__scope__){x77249 = 1;   \n}}",
                   pieces[0].getCharacters().toString());
      assertEquals(locals + "with(__scope__){if(x77249) { 2 }\n  \n}}",
                   pieces[1].getCharacters().toString());
      assertEquals(locals + "with(__scope__){\nif(false) { 3 }\n}}",
                   pieces[2].getCharacters().toString());

      Source[] reset = ((GraalJavaScriptEngine.PieceScript)
         engine.compile("var y77249 = 1; if(y77249) { 2 }")).pieces();
      assertTrue(reset[0].getCharacters().toString().contains("y77249=void 0;"));
      assertTrue(reset[1].getCharacters().toString()
                    .startsWith("with(__scope__.__inetsoft_own_locals__){with(__scope__){"));
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

   // a piece declaring one name with function and var/let/const parses as a script but
   // not as the block of its with: it keeps the eval wrapper, with the values of main
   @Test void aFunctionAndVarOfOneNameKeepTheEvalWrapper() throws Exception {
      assertSharedContextValues(
         "function f77249(){ return 'f' + value } var f77249; if(value > 0) { f77249() }",
         "f5", "f7", null);
      assertSharedContextValues(
         "let lf77249 = 1; function lf77249(){ return 'l' } if(value > 0) { lf77249 }",
         1.0, 1.0, null);
      assertSharedContextValues(
         "const cf77249 = 1; function cf77249(){ return 'c' } if(value > 0) { cf77249 }",
         1.0, 1.0, null);
   }

   // the same name in different pieces, or two functions of one name, still split
   @Test void aFunctionAndVarInDifferentPiecesStillSplit() throws Exception {
      assertInstanceOf(GraalJavaScriptEngine.PieceScript.class,
         engine.compile("var g77249 = 1; if(value > 0) { g77249 } function g77249(){}"));
      assertInstanceOf(GraalJavaScriptEngine.PieceScript.class,
         engine.compile("function h77249(){ return 'a' } function h77249(){ return 'b' } " +
                        "if(value > 0) { h77249() }"));
      assertValues(engine.compile(
         "function h77249(){ return 'a' } function h77249(){ return 'b' + value } " +
         "if(value > 0) { h77249() }"), "b5", "b7", null);
   }

   // the reset of a split formula takes main's owned vars (review r4 dropped M1): a var of
   // a method shorthand, a class method or a function with a default object parameter is
   // reset like a top-level one (the formula's own values are unaffected, the method's var
   // is local); a lost reset instead would keep a stale value across runs
   @Test void theResetTakesMainsOwnedVarsOfMethodBodies() throws Exception {
      run("var t77249 = 5; var u77249 = 6; var w77249 = 7");
      assertEquals(9.0, run(
         "let o = { sq(x) { var t77249 = x * x; return t77249 } }; if(o) { o.sq(3) }"));
      assertNull(run("t77249"));
      assertEquals(4.0, run(
         "let C77249 = class { get two() { var u77249 = 2; return u77249 } m(a) { " +
         "var u77249 = a; return u77249 } }; if(true) { new C77249().m(2) * new C77249().two }"));
      assertNull(run("u77249"));
      assertEquals(1.0, run(
         "function d77249(a = { k: 1 }) { var w77249 = a.k; return w77249 } if(true) { d77249() }"));
      assertNull(run("w77249"));

      // a top-level var in a control-flow block is still reset
      run("var z77249 = 8");
      assertNull(run("if(false) { var z77249 = 1 } if(true) { z77249 }"));
   }

   // Bug #77249 (F2): a call ending a line without `;` followed by a bare block is a call
   // and a block statement (ASI), not a method head: the block's var is still reset
   @Test void aVarInABlockAfterACallIsReset() throws Exception {
      run("var asi77249 = 8; var bsi77249 = 9; function noop77249(){}");
      assertNull(run("noop77249(1)\n{ if(false) { var asi77249 = 1 } }\nif(true) { asi77249 }"));
      assertNull(run("if(true) noop77249(2)\n{ var bsi77249 }\nif(true) { bsi77249 }"));
   }

   // Bug #77249 (F1): a function declared in a block (Annex B) that the next run does not
   // enter is gone on that run, as on the eval wrapper, pool off and on; as a piece its
   // global kept the previous run's function
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aFunctionOfABlockDoesNotOutliveItsRun(boolean pool) throws Exception {
      WorksheetScriptEnv env = pool ? PoolTestSupport.env() : null;

      try {
         assertBlockFunctionValues(env,
            "if(value > 0) { function bf77249(){ return 'b' + value } } " +
            "if(typeof bf77249 == 'function') { bf77249() }", "b5", "b7", null);
         assertBlockFunctionValues(env,
            "switch(value > 0) { case true: function sf77249(){ return 's' + value } } " +
            "if(typeof sf77249 == 'function') { sf77249() }", "s5", "s7", null);
         assertBlockFunctionValues(env,
            "var n77249 = 1; if(value > 0) function nf77249(){ return 'n' + value }\n" +
            "if(typeof nf77249 == 'function') { nf77249() }", "n5", "n7", null);
      }
      finally {
         if(env != null) {
            env.retire();
         }
      }
   }

   // what counts as a function declared in a block: a false positive only keeps the eval
   // wrapper, a miss keeps a piece
   @Test void blockFunctionDeclarationsAreFound() throws Exception {
      for(String f : new String[] {
         "if(a) { function f(){} }", "if(a) function f(){}", "if(a) {} else function f(){}",
         "switch(a) { case 1: function f(){} }", "for(;;) { function f(){} }",
         "{ function f(){} }", "try { function f(){} } catch(e) {}", "foo(1)\n{ function f(){} }" })
      {
         assertTrue(GraalJavaScriptEngine.hasBlockFunctionDeclaration(f), f);
      }

      for(String f : new String[] {
         "function f(){} if(a) { f() }", "if(a) { var g = function(){} }",
         "if(a) { [1].map(function(x){ return x }) }", "if(a) { o = { k: function(){} } }",
         "function o(){ if(a) { function i(){} } }", "if(a) { x = b ? function(){} : null }",
         "let o = { m() { if(a) { function i(){} } } }", "if(a) { (function(){})() }",
         "if(a) { return function(){} }", "if(a) { async function f(){} }",
         "var s = 'if(a) { function f(){} }'; if(a) { s }" })
      {
         assertFalse(GraalJavaScriptEngine.hasBlockFunctionDeclaration(f), f);
      }

      // a top-level function still splits
      assertInstanceOf(GraalJavaScriptEngine.PieceScript.class,
         engine.compile("function tf77249(){ return 1 } if(value > 0) { tf77249() }"));
   }

   // Bug #77249 (I1): a block function after a statement that ends without `;` (ASI) is
   // found too, pool off and on; as a piece it outlived its run (a-1 instead of null)
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aFunctionOfABlockAfterASIDoesNotOutliveItsRun(boolean pool) throws Exception {
      WorksheetScriptEnv env = pool ? PoolTestSupport.env() : null;

      try {
         assertBlockFunctionValues(env,
            "if(value > 0) {\n var xa77249 = value\n function ia77249(){ return 'a' + value }\n}\n" +
            "if(typeof ia77249 == 'function') { ia77249() }", "a5", "a7", null);
         assertBlockFunctionValues(env,
            "if(value > 0) {\n nb77249 = 5\n function ib77249(){ return 'b' + value }\n}\n" +
            "if(typeof ib77249 == 'function') { ib77249() }", "b5", "b7", null);
         assertBlockFunctionValues(env,
            "var cc77249 = 0; if(value > 0) {\n cc77249++\n function ic77249(){ return 'c' + value }\n}\n" +
            "if(typeof ic77249 == 'function') { ic77249() }", "c5", "c7", null);
         assertBlockFunctionValues(env,
            "if(value > 0) {\n sd77249 = 'a'\n function id77249(){ return 'd' + value }\n}\n" +
            "if(typeof id77249 == 'function') { id77249() }", "d5", "d7", null);
         assertBlockFunctionValues(env,
            "if(value > 0) {\n ae77249 = [value][0]\n function ie77249(){ return 'e' + value }\n}\n" +
            "if(typeof ie77249 == 'function') { ie77249() }", "e5", "e7", null);
         assertBlockFunctionValues(env,
            "if(value > 0) {\n tf77249 = true\n function if77249(){ return 'f' + value }\n}\n" +
            "if(typeof if77249 == 'function') { if77249() }", "f5", "f7", null);
         assertBlockFunctionValues(env,
            "if(value > 0) {\n rg77249 = /x/\n function ig77249(){ return 'g' + value }\n}\n" +
            "if(typeof ig77249 == 'function') { ig77249() }", "g5", "g7", null);
      }
      finally {
         if(env != null) {
            env.retire();
         }
      }
   }

   // Bug #77249 (m1): a keyword used as an object key does not make the next block a
   // function body, pool off and on: its var is still reset and its function is found
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aKeywordKeyDoesNotHideTheNextBlock(boolean pool) throws Exception {
      WorksheetScriptEnv env = pool ? PoolTestSupport.env() : null;

      try {
         for(String key : new String[] { "class", "function" }) {
            String n = key.charAt(0) + "77249";
            // (an undefined completion keeps the key object's value, so String() it)
            // the var reset follows main's owned vars: a `function` key hides the next
            // block's var on main too (pre-existing, not reset: the -1 run keeps 7)
            String last = key.equals("class") ? "undefined" : "7";
            assertRunValues(env, false,
               "ok" + n + " = {" + key + ": 'c'}; if(value > 0) { var tk" + n + " = value } " +
               "if(true) { String(tk" + n + ") }", "5", "7", last);
            assertBlockFunctionValues(env,
               "ob" + n + " = {" + key + ": 'c'}; if(value > 0) { function bk" + n +
               "(){ return 'k' + value } } if(typeof bk" + n + " == 'function') { bk" + n + "() }" +
               " else { 'none' }", "k5", "k7", "none");
            assertRunValues(env, false,
               "[].concat({" + key + ": 1}); if(value > 0) { var tc" + n + " = value } " +
               "if(true) { String(tc" + n + ") }", "5", "7", last);
         }
      }
      finally {
         if(env != null) {
            env.retire();
         }
      }
   }

   // Bug #77249 (verify r3 C/D): a class field named `class`, a `static function =`
   // field, or `yield`/`await`/`of` as an identifier ending a line does not hide the var of
   // the next block, pool off and on: it is reset, so the -1 run reads undefined (r3 kept 7)
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aClassFieldOrContextualKeywordDoesNotHideTheNextBlock(boolean pool) throws Exception {
      WorksheetScriptEnv env = pool ? PoolTestSupport.env() : null;

      try {
         for(String f : new String[] {
            "class Ka77249 { static class = 1 }\nif(value > 0) { var kca77249 = value }\n" +
               "if(true) { String(kca77249) }",
            "class Kb77249 { class = 1 }\nif(value > 0) { var kcb77249 = value }\n" +
               "if(true) { String(kcb77249) }",
            "class Kc77249 { static function = function() { return 1 } }\n" +
               "if(value > 0) { var kcc77249 = value }\nif(true) { String(kcc77249) }",
            "var yield = 1; x = yield\n{ Math.abs(1)\n{ if(value > 0) { var kcd77249 = value } } }" +
               "\nif(true) { String(kcd77249) }",
            "var await = 1; x = await\n{ Math.abs(1)\n{ if(value > 0) { var kce77249 = value } } }" +
               "\nif(true) { String(kce77249) }",
            "var of = 1; x = of\n{ Math.abs(1)\n{ if(value > 0) { var kcf77249 = value } } }" +
               "\nif(true) { String(kcf77249) }" })
         {
            assertRunValues(env, false, f, "5", "7", "undefined");
         }
      }
      finally {
         if(env != null) {
            env.retire();
         }
      }
   }

   // the ASI and keyword-key shapes of #77249 I1/m1; and a top-level function after a
   // statement without `;` still splits
   @Test void blockFunctionDeclarationsAfterASIAndKeywordKeysAreFound() throws Exception {
      for(String f : new String[] {
         "if(v) { var n = 5\n function f(){} }", "if(v) { n = 5\n function f(){} }",
         "if(v) { n = x\n function f(){} }", "if(v) { x++\n function f(){} }",
         "if(v) { x--\n function f(){} }", "if(v) { s = 'a'\n function f(){} }",
         "if(v) { s = `a${b}`\n function f(){} }", "if(v) { r = /a/\n function f(){} }",
         "if(v) { a = b[0]\n function f(){} }", "if(v) { a = true\n function f(){} }",
         "if(v) { f()\n function g(){} }", "if(v) { o = {a: 1}\n function f(){} }",
         "if(v) { var a = [1]\n function f(){} }", "if(v) { var s = 'x'\n function f(){} }",
         "if(v) { n++\n function f(){} }","let {class: c} = o; if(v) { function f(){} }",
         "if(v) { l: function f(){} }", "if(v) { x = 1 /* c */\n function f(){} }",
         "oc = {class: 'c'}; if(v) { function f(){} }", "foo({function: 1}); if(v) { function f(){} }",
         "o = {get: 1, set: 2, static: 3, async: 4, if: 5}; if(v) { function f(){} }",
         "o = {'class': 1, ['function']: 2}; if(v) { function f(){} }",
         // a class field named by a keyword (verify r3 C)
         "class K { static class = 1 }\nif(v) { function f(){} }",
         "class K { class = 1 }\nif(v) { function f(){} }",
         "class K { static function = function() {} }\nif(v) { function f(){} }",
         "class K { function; }\nif(v) { function f(){} }",
         "class K { function }\nif(v) { function f(){} }" })
      {
         assertTrue(GraalJavaScriptEngine.hasBlockFunctionDeclaration(f), f);
      }

      for(String f : new String[] {
         "if(v) { x = y ||\n function(){} }", "if(v) { x = y +\n function(){} }",
         "if(v) { o = { function: 1 } }", "if(v) { o = { function() { return 1 } } }",
         "if(v) { o = { a: 1, function() {} } }", "if(v) { x = typeof function(){} }",
         "if(v) { x = new function(){} }", "if(v) { x = void function(){} }",
         "if(v) { foo(1,\n function(){}) }", "if(v) { x = [\n function(){}] }",
         "if(v) { x = !function(){}() }", "if(v) { x = a => function(){} }",
         "if(v) { x = o.function }" })
      {
         assertFalse(GraalJavaScriptEngine.hasBlockFunctionDeclaration(f), f);
      }

      for(String f : new String[] {
         "a = b\nfunction tf77249(){ return 1 } if(value > 0) { tf77249() }",
         "a = 5\nfunction tg77249(){ return 1 } if(value > 0) { tg77249() }",
         "Math.abs(1)\nfunction th77249(){ return 1 } if(value > 0) { th77249() }" })
      {
         assertFalse(GraalJavaScriptEngine.hasBlockFunctionDeclaration(f), f);
         assertInstanceOf(GraalJavaScriptEngine.PieceScript.class, engine.compile(f), f);
      }
   }

   private void assertBlockFunctionValues(WorksheetScriptEnv env, String f, Object... expected)
      throws Exception
   {
      assertRunValues(env, true, f, expected);
   }

   // run f with value 5, 7, -1 (on one claimed context when pooled); evalRoute: whether
   // f must compile to the eval wrapper
   private void assertRunValues(WorksheetScriptEnv env, boolean evalRoute, String f,
                                Object... expected)
      throws Exception
   {
      Object script = env != null ? env.compile(f) : engine.compile(f);
      int[] values = { 5, 7, -1 };

      // pooled, the runs share one claimed context, as the rows of a formula table batch
      try(SlotClaim claim = env != null ? env.claimSlot() : null) {
         for(int i = 0; i < values.length; i++) {
            MapScope scope = new MapScope();
            scope.putMember("value", values[i]);
            Object v = env != null ? env.exec(script, scope, scope, null) :
               engine.exec(script, scope, scope);
            assertEquals(expected[i], v instanceof Number num ? num.doubleValue() : v,
                         f + " value " + values[i]);
         }
      }

      if(evalRoute) {
         assertInstanceOf(Source.class, script, f);
      }
   }

   // the pieces count a line break as the plain path does
   @Test void errorLinesOfOtherLineBreaksMatchThePlainPath() {
      for(String br : new String[] { "\r", "\u2028", "\u2029", "\r\n", "\n" }) {
         String pieces = line(assertThrows(Exception.class, () ->
            run("var a = 1;" + br + "if(a) {" + br + "  undefinedFnS77249() }")).getMessage());
         String plain = line(assertThrows(Exception.class, () ->
            run("var a = 1;" + br + "{" + br + "  undefinedFnT77249() }")).getMessage());
         assertNotNull(plain, "plain line for " + (int) br.charAt(0));
         assertEquals(plain, pieces, "line break " + (int) br.charAt(0));
      }
   }

   private static String line(String msg) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(line \\d+\\)").matcher(msg);
      return m.find() ? m.group() : null;
   }

   // one compiled script run on one engine with value 5, 7, -1, as a viewsheet runs it
   private void assertSharedContextValues(String f, Object... expected) throws Exception {
      Object script = engine.compile(f);
      assertInstanceOf(Source.class, script, f);
      assertTrue(((Source) script).getCharacters().toString().contains("eval("), f);
      assertValues(script, expected);
   }

   private void assertValues(Object script, Object... expected) throws Exception {
      String f = script.toString();
      int[] values = { 5, 7, -1 };

      for(int i = 0; i < values.length; i++) {
         MapScope scope = new MapScope();
         scope.putMember("value", values[i]);
         Object v = engine.exec(script, scope, scope);
         assertEquals(expected[i], v instanceof Number n ? n.doubleValue() : v,
                      f + " value " + values[i]);
      }
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
