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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.*;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hostile Date vars of a formula table across pooled script contexts (Testing #77123, B1
 * residual): a Date with a spoofed own constructor or a null prototype, a script that replaces
 * every Date / Object / Function prototype method the snapshot could dispatch to with an
 * endless loop, and a Date from a foreign realm handed in by a host object. None of them hangs
 * the batch end; a real Date is kept; an object that only inherits Date.prototype reads as
 * undefined with one warning.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensHostileDateTest {
   // row r -> r (hours since epoch), using no getTime / valueOf / call
   static final String HOURS = "Math.round(d.setUTCHours(d.getUTCHours() + 1) / 3600000)";
   // replaces everything the snapshot could dispatch to with an endless loop (the loop
   // function is local, so it is no owned var)
   static final String LOOP_EVERYTHING = "(function() { var L = function() { while(true) {} }; " +
      "Date.prototype.getTime = L; Date.prototype.valueOf = L; " +
      "Date.prototype[Symbol.toPrimitive] = L; Date.prototype.toString = L; " +
      "Date.prototype.toJSON = L; Object.prototype.toString = L; " +
      "Function.prototype.call = L; Function.prototype.bind = L; Function.prototype.apply = L; " +
      "})(); ";
   // witness: a function is not kept across a hand-off, with one warning ("holds a
   // function"), so it shows the batches crossed contexts; arrays and objects are kept now
   // (Testing #77123, B1 residual part 2)
   static final String WITNESS = "var w = w || function() {}; ";

   static String formula(String what) {
      return switch(what) {
         case "ctorData" -> "var d = d || (function() { var x = new Date(0); " +
            "x.constructor = Object; return x; })(); " + HOURS;
         case "ctorFunction" -> "var d = d || (function() { var x = new Date(0); " +
            "x.constructor = function F() {}; return x; })(); " + HOURS;
         case "ctorGetter" -> "var d = d || (function() { var x = new Date(0); " +
            "Object.defineProperty(x, 'constructor', { get: function() { while(true) {} } }); " +
            "return x; })(); " + HOURS;
         case "nullProto" -> "var d = d || Object.setPrototypeOf(new Date(0), null); " +
            "Math.round(Date.prototype.setUTCHours.call(d, " +
            "Date.prototype.getUTCHours.call(d) + 1) / 3600000)";
         case "loopEverything" -> LOOP_EVERYTHING + WITNESS + "var d = d || new Date(0); " + HOURS;
         case "loopEverythingInvalid" -> LOOP_EVERYTHING + WITNESS + "var made = made || 0; " +
            "var d = d || (made++, new Date(NaN)); var k = (k || 0) + 1; " +
            "made == 1 && d instanceof Date && isNaN(d.getUTCHours()) ? k : -made";
         case "foreignRealm" -> "var d = d || realm.date(); var k = (k || 0) + 1; " +
            "d.getUTCFullYear() == 1970 ? k : -1";
         default -> throw new IllegalArgumentException(what);
      };
   }

   /** Hands out a Date created in a separate polyglot context. */
   public static final class Realm {
      public Object date() {
         Context c = Context.create("js");
         contexts.add(c);
         return c.eval("js", "new Date(0)");
      }

      final List<Context> contexts = Collections.synchronizedList(new ArrayList<>());
   }

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
      SreeEnv.remove(MAX_HOMES);

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      envs.clear();

      for(Context c : realm.contexts) {
         c.close(true);
      }
   }

   /**
    * A real Date is kept across contexts however hostile its shape or its script's globals,
    * and saving it runs no script code (every getter / replaced method loops forever).
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "ctorData", "ctorFunction", "ctorGetter", "nullProto",
      "loopEverything", "loopEverythingInvalid", "foreignRealm" })
   void aHostileDateIsKeptAndNothingHangs(String what) {
      double[][] v = new double[1][];
      assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
         v[0] = crossSlot(formula(what));
      });
      List<String> bad = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         if(v[0][r] != r) {
            bad.add(r + "=" + v[0][r]);
         }
      }

      assertTrue(bad.isEmpty(), () -> what + ": " + bad.size() + " wrong rows, first " +
         bad.subList(0, Math.min(5, bad.size())));
      List<String> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);

      if(what.startsWith("loopEverything")) {
         // the batches crossed (the function witness was lost at the hand-off), and the Date
         // itself, saved in the same tree, is silent
         assertTrue(warns.get(0).contains("\"w\" holds a function"), warns.get(0));
      }
      else {
         // the Date was rebuilt on the other context as a plain Date, with the one warning
         assertTrue(warns.get(0).contains("\"d\" holds a Date"), warns.get(0));
      }
   }

   /**
    * With every method replaced by a loop, an object that only inherits Date.prototype still
    * reads as undefined with one warning, and is not rebuilt as an Invalid Date.
    */
   @Test
   void anObjectInheritingDatePrototypeUnderHostileGlobalsIsNoDate() {
      String f = LOOP_EVERYTHING + "var made = made || 0; " +
         "var o = o || (made++, Object.create(Date.prototype)); " +
         "Object.getPrototypeOf(o) === Date.prototype && !Object.getOwnPropertyNames(o).length " +
         "? made : -1";
      double[][] v = new double[1][];
      assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
         v[0] = crossSlot(f);
      });

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(v[0][r] >= 1.0, "row " + r + ": " + v[0][r]);
      }

      assertEquals(1.0, v[0][200]);
      assertTrue(v[0][ROWS] > 1.0 && v[0][ROWS] < 100, "made " + v[0][ROWS]);
      List<String> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"o\" holds an object that inherits Date.prototype"),
                 warns.get(0));
   }

   // rows 1..200 on this thread's context, 201..600 while it is held elsewhere, 601.. back.
   // No exclusive home: a table holding a script object other than a Date keeps it on its
   // home, which the other thread's claim would otherwise skip; so the claim takes the home
   // over after a hand-off and the table's next batch really runs on another context
   private double[] crossSlot(String formula) throws Exception {
      SreeEnv.setProperty(MAX_HOMES, "0");
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(w);
      w.put("realm", realm);
      TableLens t = make(box, base(ROWS), formula, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 201, 600));
      read(t, v, 601, ROWS);
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

   private static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   private static final int ROWS = 1200;
   private final Realm realm = new Realm();
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
