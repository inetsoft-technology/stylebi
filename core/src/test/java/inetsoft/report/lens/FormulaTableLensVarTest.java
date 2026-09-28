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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The top-level {@code var}s of a formula table's formulas belong to the table (Testing
 * #77123, context-pool regression brief B1/B2): a {@code var} accumulator counts over the
 * rows of the table on every compile path (plain {@code with}, the {@code this}/eval path and
 * the multi-statement path), however the table is read, with the context pool on or off; it
 * is never shared with another table or left as a global; and it starts over when the table
 * is computed again.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormulaTableLensVarTest {
   // the three compile paths of GraalJavaScriptEngine.compile
   static final String PLAIN = "var acc = (acc || 0) + field['value']; acc";
   static final String EVAL = "var acc = (acc || 0) + this.field['value']; acc";
   static final String MULTI = "var acc = (acc || 0) + field['value']; if(acc < 0) { acc = 0; } acc";

   @AfterEach
   void retire() {
      for(ScriptEnv env : envs) {
         if(env instanceof WorksheetScriptEnv) {
            ((WorksheetScriptEnv) env).retire();
         }
      }

      envs.clear();
   }

   static Stream<Arguments> accumulators() {
      List<Arguments> args = new ArrayList<>();

      for(boolean pool : new boolean[] { false, true }) {
         for(String path : new String[] { "plain", "eval", "multi" }) {
            for(String read : new String[] { "sequential", "row1000First", "pages100", "eotFirst" }) {
               args.add(Arguments.of(pool, path, read));
            }
         }
      }

      return args.stream();
   }

   @ParameterizedTest(name = "pool={0} path={1} read={2}")
   @MethodSource("accumulators")
   void varAccumulatorCountsEveryRowOfTheTable(boolean pool, String path, String read)
      throws Exception
   {
      TableLens lens = make(pool, base(ROWS), formula(path));
      double[] v = read(read).apply(lens);
      assertCounts(v, 1, path + " " + read);
   }

   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("paths")
   void aSecondTableStartsOverAndNothingLeaksToTheGlobalScope(boolean pool, String path)
      throws Exception
   {
      // an own name, so no other test of this JVM can have left a global of it
      String f = formula(path).replace("acc", "p1LeakAcc");
      AssetQuerySandbox box = box(pool);
      assertCounts(sequential(make(box, base(ROWS), f, "T1")), 1, "first table");
      assertCounts(sequential(make(box, base(ROWS), f, "T2")), 1, "second table");

      // the #75596 declaration hoist does not copy the table's var to the global scope
      ScriptEnv env = box.getScriptEnv();
      assertEquals("undef", env.exec(env.compile(
         "typeof p1LeakAcc == 'undefined' ? 'undef' : p1LeakAcc"), null, null, null));

      // nor can another table read it as an undeclared name
      double[] r = sequential(make(box, base(ROWS),
                                   "typeof p1LeakAcc == 'undefined' ? -1 : p1LeakAcc", "R"));

      for(int i = 1; i <= ROWS; i++) {
         assertEquals(-1.0, r[i], "reader row " + i);
      }
   }

   @ParameterizedTest(name = "pool={0} path={1}")
   @MethodSource("paths")
   void aRecomputedTableStartsOver(boolean pool, String path) throws Exception {
      FormulaTableLens lens = (FormulaTableLens) make(pool, base(ROWS), formula(path));
      assertCounts(sequential(lens), 1, "first computation");

      // a rebuilt row table (setTable, a base change event) computes the rows again
      lens.invalidate();
      assertCounts(sequential(lens), 1, "after invalidate");

      lens.invalidate();
      assertCounts(eot(lens), 1, "after invalidate, read to the end");
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void twoColumnsShareAVar(boolean pool) throws Exception {
      AssetQuerySandbox box = box(pool);
      TableLens t = PostProcessor.formula(
         base(ROWS), new String[] { "c1", "c2" },
         new String[] { "var s = (s || 0) + field['value']; s", "var t = s * 10; t" },
         box.getScriptEnv(), box.getScope(), null, "T", null,
         List.of(Double.class, Double.class), new boolean[] { false, false });

      for(int r = 1; t.moreRows(r) && r <= ROWS; r++) {
         assertEquals(r, num(t.getObject(r, 2)), "c1 row " + r);
         assertEquals(r * 10.0, num(t.getObject(r, 3)), "c2 row " + r);
      }
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aVarNamedLikeAColumnIsTheCell(boolean pool) throws Exception {
      AssetQuerySandbox box = box(pool);
      TableLens t = make(box, base(ROWS), "var value = value + 100; value", "B");
      double[] v = sequential(t);

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(101.0, v[r], "row " + r);
         assertEquals(101.0, num(t.getObject(r, 0)), "base cell of row " + r);
      }

      // the formula's own column, as FormulaTableLensSelfReferenceTest
      TableLens self = PostProcessor.formula(
         base(ROWS), new String[] { "acc" }, new String[] { "var acc = (acc || 0) + 1; acc" },
         box.getScriptEnv(), box.getScope(), null, "S", null, List.of(Double.class),
         new boolean[] { false });
      double[] s = sequential(self);

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(1.0, s[r], "own column row " + r);
      }
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void letAndConstStayPerRow(boolean pool) throws Exception {
      AssetQuerySandbox box = box(pool);
      // #77181: a let without initializer starts undefined on every row
      TableLens let = make(box, base(ROWS),
                           "let r; if(field['id'] % 2 == 0) { r = field['id']; } r", "L");
      TableLens cst = make(box, base(ROWS), "const k = field['id'] * 2; k", "C");

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(let.moreRows(r));
         assertEquals(r % 2 == 0 ? (Object) (double) r : null, numOrNull(let.getObject(r, 2)),
                      "let row " + r);
         assertTrue(cst.moreRows(r));
         assertEquals(r * 2.0, num(cst.getObject(r, 2)), "const row " + r);
      }
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aLetInOneColumnIsNotTakenOverByAVarOfAnother(boolean pool) throws Exception {
      AssetQuerySandbox box = box(pool);
      // multi-statement path: c1's let r stays per row although c2 declares var r
      TableLens multi = PostProcessor.formula(
         base(ROWS), new String[] { "c1", "c2" },
         new String[] { "let r; if(field['id'] % 2 == 0) { r = field['id']; } r",
                        "var r = -field['id']; r" },
         box.getScriptEnv(), box.getScope(), null, "D", null,
         List.of(Double.class, Double.class), new boolean[] { false, false });
      // plain path
      TableLens plain = PostProcessor.formula(
         base(ROWS), new String[] { "c1", "c2" },
         new String[] { "let q; q", "var q = -field['id']; q" },
         box.getScriptEnv(), box.getScope(), null, "E", null,
         List.of(Double.class, Double.class), new boolean[] { false, false });

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(multi.moreRows(r));
         assertEquals(r % 2 == 0 ? (Object) (double) r : null, numOrNull(multi.getObject(r, 2)),
                      "multi c1 row " + r);
         assertEquals(-r, num(multi.getObject(r, 3)), "multi c2 row " + r);
         assertTrue(plain.moreRows(r));
         assertNull(plain.getObject(r, 2), "plain c1 row " + r);
         assertEquals(-r, num(plain.getObject(r, 3)), "plain c2 row " + r);
      }
   }

   /**
    * The changed patterns of the release note, locked in on purpose: a var without initializer
    * that is assigned only under a condition, or inside a try, keeps the previous row's value
    * (Rhino semantics, and main's plain path already did); use let for a per-row variable.
    */
   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aConditionallyAssignedVarKeepsThePreviousRowsValue(boolean pool) throws Exception {
      AssetQuerySandbox box = box(pool);
      TableLens cond = make(box, base(ROWS),
                            "var r; if(field['id'] % 2 == 0) { r = field['id']; } r", "A");
      TableLens tryc = make(box, base(ROWS),
                            "var x; try { if(field['id'] % 3 == 0) throw 'e'; x = field['id']; }" +
                               " catch(e) {} x", "L");

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(cond.moreRows(r));
         Object expected = r == 1 ? null : (double) (r % 2 == 0 ? r : r - 1);
         assertEquals(expected, numOrNull(cond.getObject(r, 2)), "conditional row " + r);
         assertTrue(tryc.moreRows(r));
         assertEquals((double) (r % 3 == 0 ? r - 1 : r), num(tryc.getObject(r, 2)),
                      "try row " + r);
      }
   }

   @ParameterizedTest(name = "pool={0}")
   @ValueSource(booleans = { false, true })
   void aNonPrimitiveVarIsKeptWhileTheTableRunsOnOneContext(boolean pool) throws Exception {
      AssetQuerySandbox box = box(pool);
      String[] formulas = {
         "var d = d || new Date(0); d.setTime(d.getTime() + 1000); d.getTime() / 1000",
         "var a = a || []; a.push(1); a.length",
         "var o = o || {n: 0}; o.n++; o.n",
         "var f = f || function(x) { return x + 1; }; var k = f(k || 0); k",
         "var a1 = a1 || []; var a2 = a2 || a1; a1.push(1); a2.length"
      };

      for(String f : formulas) {
         FormulaTableLens t = (FormulaTableLens) make(box, base(ROWS), f, "N");
         assertCounts(sequential(t), 1, f);

         // once the table is complete it runs no formula until it is computed again, so it
         // does not keep the context's objects alive; a primitive var is kept
         assertFalse(t.moreRows(TableLens.EOT));
         Object scope = ownedScope(t);
         Map<?, ?> valmap = (Map<?, ?>) field(scope, "valmap");

         for(Object value : valmap.values()) {
            assertFalse(value instanceof org.graalvm.polyglot.Value,
                        "a completed table holds a script object: " + f);
         }
      }
   }

   // --- helpers ---

   static Stream<Arguments> paths() {
      List<Arguments> args = new ArrayList<>();

      for(boolean pool : new boolean[] { false, true }) {
         for(String path : new String[] { "plain", "eval", "multi" }) {
            args.add(Arguments.of(pool, path));
         }
      }

      return args.stream();
   }

   private static String formula(String path) {
      return switch(path) {
      case "plain" -> PLAIN;
      case "eval" -> EVAL;
      default -> MULTI;
      };
   }

   private static Function<TableLens, double[]> read(String read) {
      return switch(read) {
      case "sequential" -> FormulaTableLensVarTest::sequential;
      case "row1000First" -> FormulaTableLensVarTest::from1000;
      case "pages100" -> FormulaTableLensVarTest::pages;
      default -> FormulaTableLensVarTest::eot;
      };
   }

   private AssetQuerySandbox box(boolean pool) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      envs.add(box.getScriptEnv());
      return box;
   }

   private TableLens make(boolean pool, TableLens base, String expr) throws Exception {
      return make(box(pool), base, expr, "T");
   }

   // the output column is not named like a var of the formula: such a var is the cell
   static TableLens make(AssetQuerySandbox box, TableLens base, String expr, String name) {
      return PostProcessor.formula(base, new String[] { "out" }, new String[] { expr },
                                   box.getScriptEnv(), box.getScope(), null, name, null,
                                   List.of(Double.class), new boolean[] { false });
   }

   static Object ownedScope(FormulaTableLens lens) throws Exception {
      Object tableRow = field(lens, "tableRow");
      return field(tableRow, "thisScope");
   }

   static Object field(Object obj, String name) throws Exception {
      for(Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
         try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
         }
         catch(NoSuchFieldException ignore) {
            // the superclass
         }
      }

      throw new NoSuchFieldException(name);
   }

   static DefaultTableLens base(int rows) {
      Object[][] d = new Object[rows + 1][];
      d[0] = new Object[] { "value", "id" };

      for(int i = 1; i <= rows; i++) {
         d[i] = new Object[] { 1, i };
      }

      return new DefaultTableLens(d);
   }

   static double[] sequential(TableLens t) {
      double[] v = new double[ROWS + 1];

      for(int r = 1; r <= ROWS && t.moreRows(r); r++) {
         v[r] = num(t.getObject(r, 2));
      }

      return v;
   }

   static double[] eot(TableLens t) {
      t.moreRows(TableLens.EOT);
      return sequential(t);
   }

   static double[] from1000(TableLens t) {
      t.moreRows(1000);
      double[] v = new double[ROWS + 1];
      v[1000] = num(t.getObject(1000, 2));

      for(int r = 1; r <= ROWS && t.moreRows(r); r++) {
         v[r] = num(t.getObject(r, 2));
      }

      return v;
   }

   static double[] pages(TableLens t) {
      double[] v = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 100) {
         t.moreRows(s + 99);

         for(int r = s; r < s + 100 && r <= ROWS; r++) {
            v[r] = num(t.getObject(r, 2));
         }
      }

      return v;
   }

   static void assertCounts(double[] v, int from, String what) {
      for(int r = from; r <= ROWS; r++) {
         if(v[r] != r) {
            fail(what + ": row " + r + " is " + v[r] + ", expected " + r);
         }
      }
   }

   static double num(Object o) {
      return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN;
   }

   static Object numOrNull(Object o) {
      return o == null ? null : num(o);
   }

   static final int ROWS = 3000;
   private final List<ScriptEnv> envs = new ArrayList<>();
}
