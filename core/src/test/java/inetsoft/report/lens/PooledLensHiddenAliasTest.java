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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableRowScope;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Aliases that the hand-off of lens-owned objects cannot see (Testing #77123, B1 residual):
 * a var lost at a hand-off for a function, a getter, a WeakMap or a class instance may have
 * reached an object of a var that the hand-off keeps, through a closure or a weak entry. The
 * kept var is then a copy that no longer shares anything with the lost var once it is created
 * again. That is never silent: the lost var's warning names every such kept var.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensHiddenAliasTest {
   @BeforeEach
   void capture() {
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void retire() {
      logger.detachAppender(appender);

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      envs.clear();
   }

   static Stream<Arguments> staleShapes() {
      String factory = "function mk() { var s = []; " +
         "return function(x) { s.push(x); return s; }; } ";
      // name, the lost var, the kept copy, the formula (row r gives r while they share)
      return Stream.of(
         // a closure's private array, aliased by a kept var
         Arguments.of("closure", "f", "a", "var f = f || (function() { var s = []; " +
            "return function(x) { s.push(x); return s; }; })(); " +
            "var a = a || f(0); f(field['id']); a.length - 1"),
         // the same, from a factory declared in the formula
         Arguments.of("factory", "f", "a", factory +
            "var f = f || mk(); var a = a || f(0); f(field['id']); a.length - 1"),
         // a getter's private object
         Arguments.of("getter", "o", "a", "var o = o || (function() { var s = {n: 0}; " +
            "return {get s() { return s; }}; })(); var a = a || o.s; a.n++; o.s.n"),
         // a WeakMap entry's value
         Arguments.of("weakMap", "wm", "a", "var wm = wm || new WeakMap(); " +
            "var k = k || {}; if(!wm.has(k)) wm.set(k, {n: 0}); var a = a || wm.get(k); " +
            "a.n++; wm.get(k).n"),
         // a class instance's private field
         Arguments.of("classInstance", "o", "a", "var o = o || new (class { #s = {n: 0}; " +
            "s() { return this.#s; } })(); var a = a || o.s(); a.n++; o.s().n"),
         // the closure inside a plain object var
         Arguments.of("functionInObject", "st", "a", factory +
            "var st = st || {}; st.f = st.f || mk(); var a = a || st.f(0); " +
            "st.f(field['id']); a.length - 1"));
   }

   /**
    * The stale shapes: the lost var's warning names the kept copy (before the fix it named
    * only the lost var, and the copy's stale values were silent). Every row matches pool-off
    * before the hand-off.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("staleShapes")
   void aKeptCopyOfAnObjectALostVarReachedIsNamed(String name, String lost, String copy,
                                                  String formula) throws Exception
   {
      double[] off = rows(false, formula);
      double[] on = rows(true, formula);

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, off[r], name + " pool off, row " + r);
      }

      for(int r = 1; r <= 200; r++) {
         assertEquals(r, on[r], name + " before the hand-off, row " + r);
      }

      List<String> warns = warnings().stream()
         .filter(x -> x.contains("\"" + lost + "\" holds")).toList();
      assertEquals(1, warns.size(), () -> "one warning for the lost var: " + warnings());
      String w = warns.get(0);
      int from = w.indexOf(" The same hand-off kept the variable");
      int to = w.indexOf(" of this table as a copy");
      assertTrue(from > 0 && to > from && w.substring(from, to).contains("\"" + copy + "\""),
                 () -> name + ": the kept copy is named: " + w);
   }

   /**
    * A var assigned a function on every row, next to a kept accumulator, is never read at a
    * hand-off (a write's name lookup does not read an owned var): no warning, every row
    * exact. Before the fix, the lookup read and lost it, with a warning.
    */
   @Test
   void aFunctionAssignedOnEveryRowIsNotWarned() throws Exception {
      double[] on = rows(true, "var fmt = function(x) { return x; }; var a = a || []; " +
         "a.push(fmt(field['id'])); a.length");

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, on[r], "row " + r);
      }

      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A lost value that hides no reference (a RegExp) names no copy: its kept neighbour can
    * share nothing with it unseen.
    */
   @Test
   void aLostValueThatHidesNothingNamesNoCopy() throws Exception {
      double[] on = rows(true, "var re = re || /x/g; var a = a || []; a.push(1); " +
         "(re ? 0 : 0) + a.length");

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, on[r], "a is kept: row " + r);
      }

      List<String> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"re\" holds a RegExp"), warns.get(0));
      assertFalse(warns.get(0).contains("as a copy"), warns.get(0));
   }

   /**
    * Only a kept var that holds a copied array or object is named (review L1): a Date or a
    * bigint is a value no closure can share with the lost var, and a number is not handed off
    * at all. A Date with an object in a property holds a copy, so it is named. Before, every
    * kept var of the hand-off was named.
    */
   @Test
   void onlyKeptVarsThatHoldCopiedObjectsAreNamed() throws Exception {
      double[] on = rows(true, "var f = f || (function() { var s = []; " +
         "return function(x) { s.push(x); return s; }; })(); f(1); " +
         "var t = t || {n: 0}; t.n++; var d = d || new Date(0); " +
         "var big = big || (2n ** 80n + 1n); " +
         "var dd = dd || (function() { var x = new Date(0); x.o = {n: 0}; return x; })(); " +
         "var k = (k || 0) + 1; t.n");

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, on[r], "t is kept: row " + r);
      }

      List<String> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"f\" holds a function"), warns.get(0));
      assertEquals(Set.of("t", "dd"), Set.copyOf(copiesIn(warns.get(0))), warns.get(0));
   }

   /**
    * A var that already warned does not warn again when a later hand-off keeps a new copy
    * (review L3): the new copy is named on a line of its own, once, and the lost var's
    * warning stays one per var. Before, the lost var's whole warning was logged again.
    */
   @Test
   void aLaterHandOffNamesANewCopyWithoutWarningTheLostVarAgain() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      // u holds an object only once the gate opens, after the first hand-off: that one keeps
      // t, the second keeps t and u (rows are computed in batches ahead of the reads)
      AtomicBoolean gate = new AtomicBoolean();
      w.put("gate", gate);
      int rows = 1200;
      TableLens t = make(box, base(rows), "var f = f || (function() { var s = []; " +
         "return function(x) { s.push(x); return s; }; })(); f(1); " +
         "var t = t || {n: 0}; t.n++; var u = u || (gate.get() ? {n: 0} : null); t.n", "T");
      double[] v = new double[rows + 1];
      read(t, v, 1, 200);
      PoolTestSupport.handOffIdleHomes(w);
      gate.set(true);
      read(t, v, 201, 600);
      PoolTestSupport.handOffIdleHomes(w);
      read(t, v, 601, rows);
      assertTrue(PoolTestSupport.metric(w, "HandOffs") >= 2, "two hand-offs ran");

      for(int r = 1; r <= rows; r++) {
         assertEquals(r, v[r], "t is kept: row " + r);
      }

      List<String> warns = warnings();
      assertEquals(2, warns.size(), () -> "f's warning and one line for u: " + warns);
      assertTrue(warns.get(0).contains("\"f\" holds a function"), () -> "" + warns);
      assertEquals(List.of("t"), copiesIn(warns.get(0)), () -> "" + warns);
      assertFalse(warns.get(1).contains("\"f\" holds"), () -> "f warns once: " + warns);
      assertEquals(List.of("u"), copiesIn(warns.get(1)), () -> "" + warns);
   }

   // the vars a warning names as kept copies
   static List<String> copiesIn(String warning) {
      int from = warning.indexOf("kept the variable");
      int to = warning.indexOf(" of this table as a copy");

      if(from < 0 || to < from) {
         return List.of();
      }

      List<String> names = new ArrayList<>();
      Matcher m = NAME.matcher(warning.substring(from, to));

      while(m.find()) {
         names.add(m.group(1));
      }

      return names;
   }

   // rows 1..200, a hand-off of the idle homes (pool on), the rest
   private double[] rows(boolean pool, String formula) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      TableLens t = make(box, base(ROWS), formula, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);

      if(pool) {
         WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
         envs.add(w);
         PoolTestSupport.handOffIdleHomes(w);
         assertTrue(PoolTestSupport.metric(w, "HandOffs") >= 1, "a hand-off ran");
      }

      read(t, v, 201, ROWS);
      return v;
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   private List<String> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage).toList();
   }

   private static final int ROWS = 600;
   private static final Pattern NAME = Pattern.compile("\"([^\"]+)\"");
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
