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
package inetsoft.util.script;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature #77123 (context-pool brief §5 P2): the read-before-write script state detector and its
 * once-per-node warning.
 */
@Tag("core")
class ScriptStateLintTest {
   @BeforeEach
   void setUp() {
      ScriptStateLint.reset();
      logger = (Logger) LoggerFactory.getLogger(ScriptStateLint.LOGGER_NAME);
      level = logger.getLevel();
      logger.setLevel(Level.WARN);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      logger.setLevel(level);
      ScriptStateLint.reset();
   }

   static Stream<Arguments> hits() {
      return Stream.of(new String[][] {
         { "var acc = (acc || 0) + field['Sales']; acc", "R1:acc" },
         { "var x = x || 10;\nx", "R1:x" },
         { "var x;\nx = (x || 0) + field['a'];\nx", "R1:x" },
         { "var n;\nn++;\nn", "R1:n" },
         { "var n; n += field['q']; n", "R1:n" },
         { "var s; if(typeof s == 'undefined') { s = 0; } s = s + 1; s", "R1:s" },
         { "var t; if(!t) t = 0; t += field['v']; t", "R1:t" },
         { "let r = (r || 0) + 1; r", "R1:r" },
         { "var m = {}; var cache = cache || {}; cache", "R1:cache" },
         { "total = (total || 0) + field['Sales']; total", "R2:total" },
         { "x += 1; x", "R2:x" },
         { "count++; count", "R2:count" },
         { "++count; count", "R2:count" },
         { "sum += field['x']\nsum", "R2:sum" },
         { "if(typeof seen == 'undefined') seen = {}; seen[field['k']] = 1; 1", "R2:seen" },
         { "seen ||= {}; seen", "R2:seen" },
         // accumulators in a loop: the read is part of the first write (review r1 I1)
         { "for(var i=0;i<3;i++){ total = (total||0) + i } total", "R2:total" },
         { "var acc; for(var i=0;i<3;i++){ acc = (acc||0)+i } acc", "R1:acc" },
         { "for(var i=0;i<3;i++) total += i; total", "R2:total" },
         { "while(cond) { n++ }", "R2:n" },
         { "for(var i=0;i<3;i++) { x = x + 1 } x", "R2:x" },
         { "for(var i=0;i<3;i++) { x += 1 } x", "R2:x" },
         { "var x; for(var i=0;i<3;i++) { x = x + 1 } x", "R1:x" },
         { "var x; for(var i=0;i<3;i++) { x += 1 } x", "R1:x" },
         { "var i = 0; do { n++; i++ } while(i < 3); n", "R2:n" },
         { "for(var i=0;i<3;i++) { var t = (t || 0) + i } t", "R1:t" },
         { "var s = ''; for(var k in field) { out = (out || '') + k } out", "R2:out" },
         { "var sum; for(var i=0;i<3;i++){ sum = sum ? sum + i : i } sum", "R1:sum" },
      }).map(c -> Arguments.of(c[0], c[1]));
   }

   static Stream<String> nonHits() {
      return Stream.of(
         // plain formulas and scratch locals
         "field['a'] + field['b']",
         "var x = randbetween(0,8);\n(field['Agent'] == 'NA' ? 'Queue' : (x < 2 ? 'Active' : 'X'))",
         "var days = Math.random()*10\ndateAdd('d',days,field['Order1.Date'])",
         "var V = field['a']\nvar dis = field['b']-field['c']\nvar space = dis/5\n" +
            "var A = field['c'] + space\nif(isNull(V)){\n'No Value'\n} else if(V < A) { 'A' } else { 'B' }",
         "var s = 0; for(var i = 0; i < 10; i++) { s += i; } s",
         "var arr = []; for(var i = 0; i < 3; i++) arr.push(i); arr.length",
         "var prev; for(var i = 0; i < 5; i++) { if(i > 0) use(prev); prev = i; }",
         "for(var i = 0; i < 5; i++) { if(i > 0) use(prev); prev = i; }",
         "var n = 0; while(n < 3) { n++ } n",
         "var x = 0; for(var i = 0; i < 3; i++) { x = x + 1 } x",
         // initialized before the loop: reads inside the loop, in or outside the write, are safe
         "var fact = 1; for(var j = 2; j <= field['n']; j++) fact *= j; fact",
         "var r = ''; for(var k in obj) { r += k + ','; } r",
         "var count = 0; var i = 0; while(i < 10) { if(field['x'] > i) count++; i++; } count",
         "var max = null; for(var i = 0; i < a.length; i++) { if(max == null || a[i] > max) max = a[i]; } max",
         "var found = false; for(var i = 0; i < 3 && !found; i++) { if(a[i] == 1) found = true; } found",
         "var str = field['s']; var res = ''; for(var i = str.length - 1; i >= 0; i--) { res = res + str.charAt(i); } res",
         "var o = {total: 1, count: 2}; o.total + o.count",
         "field[-1]['RunningTotal'] + field['Sales']",
         "row == 0 ? field['Sales'] : field[-1]['Total'] + field['Sales']",
         "var x = 1; x = x + 1; x",
         "var a = 1, b = a + 1; b",
         "var d1 = dateAdd('d', 1, field['d']); var d2 = d1; d2",
         "var x = a ? b : c; x",
         "a = 1\nb = a + 1",
         "var x = 1\nx++\nx",
         "obj.count = (obj.count || 0) + 1",
         // let/const
         "let r; r = 5; r",
         "let r; if(field['a'] > 0) r = 1; r",
         // comments, strings, regex, templates
         "// x = x + 1\n'x = x' + field['a']",
         "/* total = total + 1 */ field['a']",
         "// typeof zz\nfield['a']",
         "var re = /x = x/g; re.test(field['a'])",
         "var t = `${x} = x`; t",
         "var t = `a ${field['b'] + '--'} c -- d`; t",
         "var s = 'a--b'; var q = \"c++d\"; s + q",
         "'select * from t -- x = x + 1'",
         "\"x = x; count++\"",
         // regex vs division
         "var a = field['a'], b = 2; a / 2 / b",
         "var x = field['a']; (x) / 2",
         "var a = field['a']; a /3/ 1",
         "var b = 2; field['a'] *100 /b",
         "var s = field['s']; s.match(field['c'] ? /a/ : /b/)",
         "/[/]x/.test(field['a'])",
         "field['a'].replace(/'/g, '')",
         "var a = (field['a'])\n/ 2",
         // Bug #77305: a regex literal placed directly after an if/while/for/with head's
         // closing `)` must not be misread as division
         "if(true) /z = z + 1/.test('z')",
         "while(false) /z = z + 1/.test('z')",
         "for(;;) /z = z + 1/.test('z')",
         // destructuring, labels, switch, do-while, loops, try/catch, ASI, ??=
         "var {a, b} = field['o']; a + b",
         "var [p, q] = [1, 2]; p + q",
         "outer: for(var i=0;i<2;i++){ break outer; }",
         "switch(field['a']) { case 1: 'one'; break; default: 'other' }",
         "var i = 0; do { i++ } while(i < 3); i",
         "for(i = 0; i < 3; i++) { } i",
         "try { var r = field['a'] / 1 } catch(err) { r = 0 } r",
         "try { f() } catch(e) { e.message }",
         "var res\nif(field['a'] > 0) res = 'P'\nelse res = 'N'\nres",
         "var z = field['z']; z ??= 0; z",
         "var q = field['a']\n++field['b']",
         "for(var k in field) { k }",
         // closures and functions
         "[1,2].map(function(v){ var w = v * 2; w += 1; return w; })",
         "[1,2].map(v => v + 1)",
         "var total = 0; function add(v){ total += v; } add(1); total",
         "function sum(a){ var s = 0; a.forEach(function(v){ s += v; }); return s; } sum([1,2])",
         "function sum(a){ var s = 0; a.forEach(v => { s += v }); return s; } sum(field['arr'])",
         "function outer(a){ function inner(){ a = a + 1; } inner(); return a; } outer(1)",
         "function f(){ var n = 0; for(var i = 0; i < 3; i++) { (function(){ n++ })() } return n } f()",
         "var k = 0; [1,2].map(v => k += v); k",
         "[1,2].map(v => k += v); k",   // the write is in a function: order unknown
         "var c; function inc(){ c = (c || 0) + 1; return c; } inc()",
         // typeof guards of names this script never writes
         "typeof myLibFunc == 'function' ? myLibFunc(field['a']) : field['a']",
         "if(typeof Region == 'undefined') { 'All' } else { Region }",
         "typeof StartDate != 'undefined' && StartDate != null ? 1 : true",
         "typeof val === 'undefined' ? 0 : val",
         "typeof threshold != 'undefined' ? field['a'] * threshold : field['a']",
         "typeof parameter.x == 'undefined' ? 0 : parameter.x",
         "typeof field['a'] == 'string'",
         // SQL-looking bodies: -- before a call or a paren is not a decrement
         "-- mdy(10,20,2004)\nfield['a']"
      );
   }

   @ParameterizedTest
   @MethodSource("hits")
   void detectsReadBeforeWrite(String script, String expected) {
      assertEquals(expected, rules(script), script);
   }

   @ParameterizedTest
   @MethodSource("nonHits")
   void noFindingForSafeScripts(String script) {
      assertEquals("", rules(script), script);
   }

   @Test
   void findingCarriesLineOfFirstOffendingRead() {
      String script = "var y = 1;\nvar acc;\nacc = (acc || 0) + y;\nacc";
      List<ScriptStateLint.Finding> f = ScriptStateLint.detect(script);
      assertEquals(1, f.size());
      assertEquals(script.indexOf("(acc") + 1, f.get(0).offset());
      ScriptStateLint.check(script, "the expression column \"C\"", "C", n -> false);
      assertEquals(1, warnings().size());
      String msg = warnings().get(0).getFormattedMessage();
      assertTrue(msg.contains("line 3"), msg);
      assertTrue(msg.contains("rule R1"), msg);
   }

   @Test
   void hitWarnsOnceAndCounts() {
      String script = "var acc=(acc||0)+field['x']; acc";
      long scripts = ScriptStateLint.nodeStateHazardScripts();
      long r1 = ScriptStateLint.nodeStateHazardsByRule("R1");
      long checks = ScriptStateLint.nodeStateLintChecks();

      for(int i = 0; i < 5; i++) {
         ScriptStateLint.check(script, "the expression column \"RunningSales\" of table \"Q1\"",
                               "RunningSales", n -> false);
      }

      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size());
      String msg = warns.get(0).getFormattedMessage();
      assertEquals(Level.WARN, warns.get(0).getLevel());
      assertTrue(msg.contains("\"RunningSales\" of table \"Q1\""), msg);
      assertTrue(msg.contains("reads variable \"acc\""), msg);
      assertTrue(msg.contains("field[-1]['RunningSales']"), msg);
      assertTrue(msg.contains(ScriptStateLint.LOGGER_NAME), msg);
      assertEquals(scripts + 1, ScriptStateLint.nodeStateHazardScripts());
      assertEquals(r1 + 1, ScriptStateLint.nodeStateHazardsByRule("R1"));
      assertEquals(checks + 1, ScriptStateLint.nodeStateLintChecks());
   }

   @Test
   void undeclaredGlobalWarnsWithR2() {
      long r2 = ScriptStateLint.nodeStateHazardsByRule("R2");
      ScriptStateLint.check("x += 1; x", "a condition script", null, n -> false);
      ScriptStateLint.check("x += 1; x", "a condition script", null, n -> false);
      assertEquals(1, warnings().size());
      String msg = warnings().get(0).getFormattedMessage();
      assertTrue(msg.contains("reads global \"x\""), msg);
      assertTrue(msg.contains("rule R2"), msg);
      assertFalse(msg.contains("field[-1]"), msg);
      assertEquals(r2 + 1, ScriptStateLint.nodeStateHazardsByRule("R2"));
   }

   @Test
   void differentTextWarnsSeparately() {
      ScriptStateLint.check("count++; count", "a condition script", null, n -> false);
      ScriptStateLint.check("count++;  count", "a condition script", null, n -> false);
      assertEquals(2, warnings().size());
   }

   @Test
   void safeScriptDoesNotWarn() {
      long scripts = ScriptStateLint.nodeStateHazardScripts();
      ScriptStateLint.check("field[-1]['T'] + field['x']", "the expression column \"T\"", "T",
                            n -> false);
      assertEquals(0, warnings().size());
      assertEquals(scripts, ScriptStateLint.nodeStateHazardScripts());
   }

   @Test
   void warnedScriptStaysWarnedAcrossCheckedSetOverflow() {
      String script = "total = (total || 0) + field['Sales']; total";
      ScriptStateLint.check(script, "a condition script", null, n -> false);
      assertEquals(1, warnings().size());

      // overflow the checked set, which clears it
      for(int i = 0; i <= ScriptStateLint.MAX_CHECKED; i++) {
         ScriptStateLint.check("field['a'] + " + i, "a condition script", null, n -> false);
      }

      assertTrue(ScriptStateLint.checkedCount() < ScriptStateLint.MAX_CHECKED);
      ScriptStateLint.check(script, "a condition script", null, n -> false);
      assertEquals(1, warnings().size());
   }

   @Test
   void checkedSetIsBounded() {
      for(int i = 0; i < ScriptStateLint.MAX_CHECKED * 2; i++) {
         ScriptStateLint.check("field['a'] + " + i, "a condition script", null, n -> false);
      }

      assertTrue(ScriptStateLint.checkedCount() <= ScriptStateLint.MAX_CHECKED);
   }

   @Test
   void oversizedScriptIsSkipped() {
      StringBuilder buf = new StringBuilder("var acc = (acc || 0) + 1;\n");

      while(buf.length() <= ScriptStateLint.MAX_SCRIPT_LENGTH) {
         buf.append("// padding padding padding padding padding padding padding\n");
      }

      long checks = ScriptStateLint.nodeStateLintChecks();
      long skipped = ScriptStateLint.nodeStateLintSkipped();
      ScriptStateLint.check(buf.toString(), "a condition script", null, n -> false);
      assertEquals(0, warnings().size());
      assertEquals(checks, ScriptStateLint.nodeStateLintChecks());
      assertEquals(skipped + 1, ScriptStateLint.nodeStateLintSkipped());
   }

   @Test
   void exceptionInsideCheckDoesNotPropagate() {
      long errors = ScriptStateLint.nodeStateLintErrors();
      assertDoesNotThrow(() -> ScriptStateLint.check(
         "var acc = (acc || 0) + 1; acc", "a condition script", null,
         n -> { throw new IllegalStateException("boom"); }));
      assertEquals(0, warnings().size());
      assertEquals(errors + 1, ScriptStateLint.nodeStateLintErrors());

      // a throwing scope in a condition site: the compiled script is still returned
      Object compiled = new Object();
      ScriptScope scope = new ThrowingScope();
      assertSame(compiled, ScriptStateLint.checkCondition(compiled, "count++; count", scope,
                                                          "condition"));
      assertEquals(0, warnings().size());
   }

   @Test
   void columnNamesAreNotScriptState() {
      DefaultTableLens company = new DefaultTableLens(new Object[][] {
         { "Customers.Company" }, { "a" }, { "b" } });
      ScriptStateLint.checkColumn("var company = company.toUpperCase(); company", new Object(),
                                  company, 1, "C1", "T", null);
      DefaultTableLens sales = new DefaultTableLens(new Object[][] { { "SALES" }, { 1 }, { -1 } });
      ScriptStateLint.checkColumn("if(Sales<0) Sales=0; Sales", new Object(), sales, 1, "C2",
                                  "T", null);
      DefaultTableLens exact = new DefaultTableLens(new Object[][] { { "Sales" }, { 1 } });
      ScriptStateLint.checkColumn("var Sales = Sales * 2; Sales", new Object(), exact, 1, "C3",
                                  "T", null);
      assertEquals(0, warnings().size());

      // control: the same shape on a name that is not a column warns
      ScriptStateLint.checkColumn("var acc = acc.toUpperCase(); acc", new Object(), company, 1,
                                  "C4", "T", null);
      assertEquals(1, warnings().size());
   }

   /**
    * Review r1 M1: a text whose findings were all host names on one table is checked again on
    * a table where the name is not a column.
    */
   @Test
   void textSuppressedByAColumnIsCheckedAgainElsewhere() {
      String script = "var Sales = Sales * 2; Sales";
      DefaultTableLens exact = new DefaultTableLens(new Object[][] { { "Sales" }, { 1 } });
      ScriptStateLint.checkColumn(script, new Object(), exact, 1, "C", "T", null);
      assertEquals(0, warnings().size());

      DefaultTableLens other = new DefaultTableLens(new Object[][] { { "Amount" }, { 1 } });
      ScriptStateLint.checkColumn(script, new Object(), other, 1, "C", "T2", null);
      assertEquals(1, warnings().size());
   }

   /**
    * Review r1 N3: a stack overflow inside the check never reaches the query.
    */
   @Test
   void stackOverflowInsideCheckDoesNotPropagate() throws Exception {
      StringBuilder buf = new StringBuilder("var acc = (acc || 0) + ");

      for(int i = 0; i < 10000; i++) {
         buf.append("`${");
      }

      for(int i = 0; i < 10000; i++) {
         buf.append("}`");
      }

      String script = buf.toString();
      assertTrue(script.length() <= ScriptStateLint.MAX_SCRIPT_LENGTH);
      long errors = ScriptStateLint.nodeStateLintErrors();
      Throwable[] thrown = new Throwable[1];
      Thread thread = new Thread(null, () -> {
         try {
            ScriptStateLint.check(script, "a condition script", null, n -> false);
         }
         catch(Throwable ex) {
            thrown[0] = ex;
         }
      }, "state-lint-small-stack", 64 * 1024);
      thread.start();
      thread.join();

      assertNull(thrown[0], () -> "the check threw " + thrown[0]);
      assertEquals(errors + 1, ScriptStateLint.nodeStateLintErrors());
   }

   @Test
   void columnCheckSkipsFailedCompileAndHeaderlessTable() {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] { { "x" }, { 1 } });
      ScriptStateLint.checkColumn("var acc = (acc || 0) + 1", null, tbl, 1, "C", "T", null);
      ScriptStateLint.checkColumn("var acc = (acc || 0) + 2", new Object(), tbl, 0, "C", "T", null);
      assertEquals(0, warnings().size());
   }

   @Test
   void conditionScopeMembersAreNotScriptState() {
      ScriptScope scope = new MemberScope("condition", "p");
      ScriptStateLint.checkCondition(new Object(), "if(condition==null) condition = 'a'; " +
                                     "condition += ' AND b'", scope, "condition");
      ScriptStateLint.checkCondition(new Object(), "var p = p || 10; p", scope, "condition");
      assertEquals(0, warnings().size());

      assertNull(ScriptStateLint.checkCondition(null, "n++; n", scope, "condition"));
      assertEquals(0, warnings().size());

      ScriptStateLint.checkCondition(new Object(), "n++; n", scope, "condition");
      assertEquals(1, warnings().size());
      assertTrue(warnings().get(0).getFormattedMessage().contains("a condition script"));
   }

   private static String rules(String script) {
      return ScriptStateLint.detect(script).stream()
         .map(f -> f.rule() + ":" + f.name()).collect(Collectors.joining(","));
   }

   /**
    * Testing #77123 (review r2 m-1): the finding says whether a top-level write of the name
    * can store a script object, which is what the pool keeps only within one batch.
    */
   static Stream<Arguments> objectValues() {
      return Stream.of(
         Arguments.of("var a = a || []; a.push(1); a.length", true),
         Arguments.of("var o = o || {}; o.n = 1; o", true),
         Arguments.of("var d; if(!d) { d = new Date(); } d", true),
         Arguments.of("var f = f || function(x) { return x; }; f(1)", true),
         Arguments.of("var g = g || (x => x); g(1)", true),
         Arguments.of("var c; c ||= []; c", true),
         Arguments.of("var acc = (acc || 0) + field['x']; acc", false),
         Arguments.of("var s = (s || '') + field['a'][0]; s", false),
         Arguments.of("var m = Math.max(m || 0, field['x']); m", false),
         Arguments.of("var t = (t || 0) + field['arr'][1]; t", false)
      );
   }

   @ParameterizedTest
   @MethodSource("objectValues")
   void findingTellsAnObjectValue(String script, boolean object) {
      List<ScriptStateLint.Finding> f = ScriptStateLint.detect(script);
      assertEquals(1, f.size(), script);
      assertEquals("R1", f.get(0).rule(), script);
      assertEquals(object, f.get(0).objectValue(), script);
   }

   /**
    * A var the table owns: a primitive accumulator never warns; an object one warns only with
    * the pool on, with the batch explanation; a name the table does not own (a let/const of it
    * in another formula) and an undeclared global (R2) still warn.
    */
   @Test
   void ownedVarsOfAColumn() {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] { { "x" }, { 1 } });
      String acc = "var acc = (acc || 0) + field['x']; acc";
      String list = "var list = list || []; list.push(field['x']); list.length";

      ScriptStateLint.checkColumn(acc, new Object(), tbl, 1, "C", "T", null, Set.of("acc"), false);
      ScriptStateLint.checkColumn(acc, new Object(), tbl, 1, "C", "T", null, Set.of("acc"), true);
      ScriptStateLint.checkColumn(list, new Object(), tbl, 1, "C", "T", null, Set.of("list"),
                                  false);
      assertEquals(0, warnings().size(), () -> "warnings: " + appender.list);

      ScriptStateLint.checkColumn(list, new Object(), tbl, 1, "C", "T", null, Set.of("list"),
                                  true);
      assertEquals(1, warnings().size());
      String msg = warnings().get(0).getFormattedMessage();
      assertTrue(msg.contains("assigns it an object, array, Date or function"), msg);
      assertTrue(msg.contains("kept only within one batch"), msg);
      assertTrue(msg.contains("A number, string or boolean is always kept"), msg);
      assertFalse(msg.contains("not reset between tables"), msg);

      String notOwned = "var k = (k || 0) + 1; k";
      ScriptStateLint.checkColumn(notOwned, new Object(), tbl, 1, "C", "T", null, Set.of(),
                                  false);
      assertEquals(2, warnings().size());
      msg = warnings().get(1).getFormattedMessage();
      assertTrue(msg.contains("with let or const"), msg);
      assertTrue(msg.contains("not reset between tables"), msg);

      String global = "total = (total || 0) + field['x']; total";
      ScriptStateLint.checkColumn(global, new Object(), tbl, 1, "C", "T", null, Set.of("total"),
                                  false);
      assertEquals(3, warnings().size());
      msg = warnings().get(2).getFormattedMessage();
      assertTrue(msg.contains("rule R2"), msg);
   }

   /**
    * Bug #77305, cross-column false negative (the centerpiece shape), exercised through the real
    * {@code collectOwnedVarNames} -> {@code checkColumn} pipeline (see also
    * {@code GraalJavaScriptEngineOwnedVarTest.crossColumnLetSwallowNoLongerHidesLexicalDeclaration}
    * for the {@code owned}-set-level assertion): a control-head-adjacent regex in one column also
    * contains a stray unterminated-string-starting quote that swallows the rest of that formula's
    * text, including a real {@code let} of a name a different column's clean accumulator needs to
    * not-own. Before the fix, the swallow hid the {@code let}, so {@code owned} wrongly kept "k",
    * and {@code checkColumn} stayed silent; after the fix, {@code owned} correctly excludes "k",
    * so the clean accumulator column now gets its {@code REPORT_NOT_OWNED} warning.
    */
   @Test
   void crossColumnLetSwallowNoLongerSuppressesNotOwnedWarning() {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] { { "x" }, { 1 } });
      String acc = "var k = (k||0) + field['x']; k";
      String poisonedLetDecl = "if(true) /'/.test(s); let k = 5;";
      Set<String> owned = GraalJavaScriptEngine.collectOwnedVarNames(List.of(acc, poisonedLetDecl));
      assertEquals(Set.of(), owned);

      ScriptStateLint.checkColumn(acc, new Object(), tbl, 1, "C", "T", null, owned, false);
      assertEquals(1, warnings().size(), () -> "warnings: " + appender.list);
      String msg = warnings().get(0).getFormattedMessage();
      assertTrue(msg.contains("with let or const"), msg);
   }

   /**
    * Bug #77305, cross-column false positive (the third, recheck-round shape): a column with no
    * {@code var} of its own relies entirely on a sibling column's real {@code var} declaration,
    * and that sibling's declaration is swallowed by a control-head-adjacent poisoned regex.
    * Before the fix, {@code collectOwnedVarNames} never saw the swallowed {@code var}. Note: the
    * reading column's own finding is classified R2 (no declaration of "v" in that script), and
    * {@code checkColumn}'s R2 disposition does not consult {@code owned} (only R1 does, see the
    * class-level doc comment and {@code checkColumn}'s "R2 stays as is" branch) — so the
    * observable, fixed effect of this shape is on the {@code owned} set itself, asserted directly
    * here and in
    * {@code GraalJavaScriptEngineOwnedVarTest.crossColumnVarSwallowNoLongerHidesOwnership}.
    */
   @Test
   void crossColumnVarSwallowNoLongerHidesOwnership() {
      String reliesOnOtherColumn = "v = (v || 0) + field['x']; v";
      String poisonedVarDecl = "if(true) /\"/.test(x); var v = 1;";
      assertEquals(Set.of("v"), GraalJavaScriptEngine.collectOwnedVarNames(
         List.of(reliesOnOtherColumn, poisonedVarDecl)));
   }

   private List<ILoggingEvent> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
   }

   private static class MemberScope implements ScriptScope {
      MemberScope(String... names) {
         this.names = List.of(names);
      }

      @Override
      public boolean hasMember(String name) {
         return names.contains(name);
      }

      @Override
      public Object getMember(String name) {
         return null;
      }

      @Override
      public void putMember(String name, Object value) {
      }

      @Override
      public Object[] getMemberKeys() {
         return names.toArray();
      }

      private final List<String> names;
   }

   private static class ThrowingScope extends MemberScope {
      @Override
      public boolean hasMember(String name) {
         throw new IllegalStateException("scope failed");
      }
   }

   private Logger logger;
   private Level level;
   private ListAppender<ILoggingEvent> appender;
}
